"""Razorpay subscription service. Money is integer paise; times are UTC seconds.

One service process, durable local SQLite volume. Provider requests are serialized
with state changes. A committed creation reservation survives uncertain HTTP results.
"""
import base64
import hashlib
import hmac
import json
import re
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class Error(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message
        super().__init__(message)


def identifier(value, prefix):
    if not isinstance(value, str) or not re.fullmatch(prefix + r'_[A-Za-z0-9]+', value):
        raise Error(400, 'Invalid provider identifier')
    return value


def signature_valid(secret, body, signature):
    expected = hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
    return isinstance(signature, str) and hmac.compare_digest(expected, signature)


PERIODS = {
    'monthly': ('Monthly', 'monthly', 1, 120),
    'quarterly': ('Quarterly', 'monthly', 3, 40),
    'six_monthly': ('Six months', 'monthly', 6, 20),
    'yearly': ('Yearly', 'yearly', 1, 10),
}
TERMINAL = {'cancelled', 'completed', 'expired', 'rejected'}


class Razorpay:
    def __init__(self, key_id, secret):
        self.key_id, self.secret = key_id, secret

    def request(self, method, path, data=None):
        auth = base64.b64encode(f'{self.key_id}:{self.secret}'.encode()).decode()
        request = Request('https://api.razorpay.com/v1/' + path,
                          data=json.dumps(data).encode() if data is not None else None,
                          headers={'Authorization': 'Basic ' + auth, 'Content-Type': 'application/json'},
                          method=method)
        try:
            with urlopen(request, timeout=20) as response:
                return json.load(response)
        except (HTTPError, URLError, TimeoutError, ValueError, OSError):
            # Do not expose credentials/provider responses or automatically retry POST.
            raise Error(503, 'Payment provider unavailable; check status before retrying') from None

    def all(self, path, params=None):
        for skip in range(0, 100000, 100):
            page = self.request('GET', path + '?' + urlencode({**(params or {}), 'count': 100, 'skip': skip}))
            items = page.get('items')
            if not isinstance(items, list):
                raise Error(503, 'Invalid provider response')
            yield from items
            if len(items) < 100:
                return
        raise Error(503, 'Provider pagination requires operator review')


class GoogleIdentity:
    def __init__(self, client_id):
        from google.auth.transport.requests import Request as GoogleRequest
        self.client_id = client_id
        self.request = GoogleRequest()

    def __call__(self, token):
        from google.oauth2 import id_token
        try:
            claims = id_token.verify_oauth2_token(token, self.request, self.client_id)
            if not claims.get('sub') or claims.get('email_verified') is not True:
                raise ValueError('Unverified account')
            return 'google:' + claims['sub']
        except Exception:
            raise Error(401, 'Sign in again to manage your subscription') from None


class Service:
    def __init__(self, database, provider, plans, webhook_secret, account_id,
                 catalog_version='1', clock=time.time, terms_url='', privacy_url='',
                 support_url='', checkout_enabled=True):
        if set(plans) != set(PERIODS):
            raise ValueError('Configure all four billing periods')
        for plan_id, plan in plans.items():
            identifier(plan['provider_plan_id'], 'plan')
            if type(plan['amount']) is not int or plan['amount'] <= 0:
                raise ValueError('Plan amounts must be positive integer paise')
        self.provider, self.plans, self.secret = provider, plans, webhook_secret
        self.account_id, self.version, self.clock = account_id, catalog_version, clock
        self.terms_url, self.privacy_url, self.support_url = terms_url, privacy_url, support_url
        self.checkout_enabled = checkout_enabled
        self.lock = threading.RLock()
        self.db = sqlite3.connect(database, check_same_thread=False, isolation_level=None)
        self.db.row_factory = sqlite3.Row
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('PRAGMA foreign_keys=ON')
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS attempts (
                id TEXT PRIMARY KEY, account TEXT NOT NULL, request_key TEXT NOT NULL,
                plan TEXT NOT NULL, provider_plan TEXT NOT NULL, amount INTEGER NOT NULL,
                subscription TEXT UNIQUE, status TEXT NOT NULL, created INTEGER NOT NULL,
                cancel_requested INTEGER NOT NULL DEFAULT 0, checked INTEGER NOT NULL DEFAULT 0,
                UNIQUE(account, request_key));
            CREATE UNIQUE INDEX IF NOT EXISTS one_open_mandate ON attempts(account)
                WHERE status NOT IN ('cancelled','completed','expired','rejected');
            CREATE TABLE IF NOT EXISTS grants (
                payment TEXT PRIMARY KEY, invoice TEXT UNIQUE NOT NULL,
                attempt TEXT NOT NULL REFERENCES attempts(id), start INTEGER NOT NULL,
                end INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0);
            CREATE TABLE IF NOT EXISTS events (
                id TEXT PRIMARY KEY, digest TEXT NOT NULL, subscription TEXT, payment TEXT,
                received INTEGER NOT NULL, processed INTEGER NOT NULL DEFAULT 0);
        ''')

    @contextmanager
    def transaction(self):
        self.db.execute('BEGIN IMMEDIATE')
        try:
            yield
            self.db.execute('COMMIT')
        except Exception:
            self.db.execute('ROLLBACK')
            raise

    def catalog(self):
        return {'version': self.version, 'checkout_enabled': self.checkout_enabled,
                'terms_url': self.terms_url, 'privacy_url': self.privacy_url, 'support_url': self.support_url,
                'plans': [{'id': key, 'title': PERIODS[key][0], 'amount': plan['amount'],
                           'currency': 'INR', 'period': PERIODS[key][1], 'interval': PERIODS[key][2],
                           'total_count': PERIODS[key][3]} for key, plan in self.plans.items()]}

    def owned(self, account, attempt_id):
        row = self.db.execute('SELECT * FROM attempts WHERE id=? AND account=?', (attempt_id, account)).fetchone()
        if row is None:
            raise Error(404, 'Subscription not found')
        return row

    def attempt_view(self, row):
        return {'id': row['id'], 'plan_id': row['plan'], 'amount': row['amount'], 'currency': 'INR',
                'subscription_id': row['subscription'],
                'status': row['status'], 'cancel_requested': bool(row['cancel_requested']),
                'key_id': self.provider.key_id, 'checkout_allowed': self.checkout_enabled
                and row['status'] == 'created' and not row['cancel_requested']}

    def create(self, account, data, key):
        if not isinstance(key, str) or not re.fullmatch(r'[A-Za-z0-9-]{16,80}', key):
            raise Error(400, 'An idempotency key is required')
        plan_id = data.get('plan_id')
        if not isinstance(plan_id, str) or plan_id not in self.plans:
            raise Error(400, 'Unknown plan')
        if set(data) != {'plan_id', 'catalog_version'}:
            raise Error(400, 'Only a catalog plan can be purchased')
        with self.lock:
            existing = self.db.execute('SELECT * FROM attempts WHERE account=? AND request_key=?', (account, key)).fetchone()
            if existing:
                if existing['plan'] != plan_id:
                    raise Error(409, 'Idempotency key already used for another plan')
                self.reconcile(existing['id'])
                return self.attempt_view(self.owned(account, existing['id']))
            if not self.checkout_enabled:
                raise Error(503, 'New subscriptions are temporarily unavailable')
            if data.get('catalog_version') != self.version:
                raise Error(409, 'Prices changed; reload the plans')
            active = self.db.execute("SELECT id FROM attempts WHERE account=? AND status NOT IN ('cancelled','completed','expired','rejected')", (account,)).fetchone()
            if active:
                raise Error(409, 'A subscription already exists; use Check status or Cancel renewal')
            # Avoid overlapping paid access even after cancel-at-cycle-end.
            if self.entitlements(account)['active']:
                raise Error(409, 'Your paid period is still active')
            plan = self.plans[plan_id]
            remote_plan = self.provider.request('GET', 'plans/' + plan['provider_plan_id'])
            _, period, interval, count = PERIODS[plan_id]
            if (remote_plan.get('id') != plan['provider_plan_id'] or remote_plan.get('period') != period
                    or remote_plan.get('interval') != interval or remote_plan.get('item', {}).get('amount') != plan['amount']
                    or remote_plan.get('item', {}).get('currency') != 'INR'):
                raise Error(503, 'Merchant plan configuration does not match the catalog')
            attempt_id = uuid.uuid4().hex
            with self.transaction():
                self.db.execute('INSERT INTO attempts(id,account,request_key,plan,provider_plan,amount,status,created) VALUES(?,?,?,?,?,?,?,?)',
                                (attempt_id, account, key, plan_id, plan['provider_plan_id'], plan['amount'], 'creating', int(self.clock())))
            # Never retry this POST after a timeout/crash: the reservation remains.
            response = self.provider.request('POST', 'subscriptions', {
                'plan_id': plan['provider_plan_id'], 'total_count': count, 'quantity': 1,
                'customer_notify': True, 'expire_by': int(self.clock()) + 86400,
                'notes': {'attempt_id': attempt_id}})
            self.attach(attempt_id, response)
            return self.attempt_view(self.owned(account, attempt_id))

    def attach(self, attempt_id, subscription):
        row = self.db.execute('SELECT * FROM attempts WHERE id=?', (attempt_id,)).fetchone()
        identifier(subscription.get('id'), 'sub')
        if subscription.get('plan_id') != row['provider_plan'] or subscription.get('notes', {}).get('attempt_id') != attempt_id:
            raise Error(503, 'Provider subscription mismatch')
        self.db.execute('UPDATE attempts SET subscription=?,status=? WHERE id=?',
                        (subscription['id'], subscription['status'], attempt_id))

    def reconcile(self, attempt_id):
        with self.lock:
            row = self.db.execute('SELECT * FROM attempts WHERE id=?', (attempt_id,)).fetchone()
            if not row['subscription']:
                matches = [s for s in self.provider.all('subscriptions', {'from': row['created'] - 60})
                           if isinstance(s.get('notes'), dict) and s['notes'].get('attempt_id') == attempt_id]
                if len(matches) > 1:
                    raise Error(503, 'Ambiguous subscriptions require support review')
                if not matches:
                    return  # Never infer failure or create a second mandate.
                self.attach(attempt_id, matches[0])
                row = self.db.execute('SELECT * FROM attempts WHERE id=?', (attempt_id,)).fetchone()
            sub = self.provider.request('GET', 'subscriptions/' + row['subscription'])
            if sub.get('id') != row['subscription'] or sub.get('plan_id') != row['provider_plan']:
                raise Error(503, 'Provider subscription mismatch')
            grants = []
            fetched_payments = set()
            for invoice in self.provider.all('invoices', {'subscription_id': row['subscription']}):
                if invoice.get('status') != 'paid' or invoice.get('subscription_id') != row['subscription']:
                    continue
                start, end = invoice.get('billing_start'), invoice.get('billing_end')
                if (type(start) is not int or type(end) is not int or not 0 < start < end
                        or invoice.get('amount') != row['amount'] or invoice.get('amount_paid') != row['amount']
                        or invoice.get('amount_due') != 0 or invoice.get('currency') != 'INR'):
                    continue  # An authorization deposit is not a paid service period.
                if end <= int(self.clock()):
                    continue  # Expired periods cannot confer access; retain existing audit rows.
                payment_id = identifier(invoice.get('payment_id'), 'pay')
                payment = self.provider.request('GET', 'payments/' + payment_id)
                fetched_payments.add(payment_id)
                if (payment.get('id') != payment_id or payment.get('invoice_id') != invoice.get('id')
                        or payment.get('amount') != row['amount'] or payment.get('currency') != 'INR'
                        or payment.get('status') not in {'captured', 'refunded'}):
                    continue
                refunded = payment.get('status') == 'refunded' or payment.get('amount_refunded', 0) > 0
                grants.append((payment_id, invoice['id'], attempt_id, start, end, int(refunded)))
            # Recheck already-granted payments even if their invoices no longer appear as paid.
            revocations = []
            for grant in self.db.execute('SELECT payment FROM grants WHERE attempt=? AND revoked=0 AND end>?', (attempt_id, int(self.clock()))):
                if grant['payment'] in fetched_payments:
                    continue
                payment = self.provider.request('GET', 'payments/' + grant['payment'])
                if payment.get('status') == 'refunded' or payment.get('amount_refunded', 0) > 0:
                    revocations.append(grant['payment'])
            with self.transaction():
                for grant in grants:
                    old = self.db.execute('SELECT attempt,invoice FROM grants WHERE payment=? OR invoice=?', grant[:2]).fetchone()
                    if old and (old['attempt'] != attempt_id or old['invoice'] != grant[1]):
                        raise Error(409, 'Payment already belongs to another subscription')
                    self.db.execute('''INSERT INTO grants VALUES(?,?,?,?,?,?) ON CONFLICT(payment)
                        DO UPDATE SET revoked=MAX(grants.revoked,excluded.revoked)''', grant)
                for payment in revocations:
                    self.db.execute('UPDATE grants SET revoked=1 WHERE payment=?', (payment,))
                # A cancellation is terminal locally, including when provider reads lag behind.
                status = row['status'] if row['status'] in TERMINAL else sub['status']
                self.db.execute('UPDATE attempts SET status=?,checked=? WHERE id=?', (status, int(self.clock()), attempt_id))

    def status(self, account, attempt_id):
        with self.lock:
            self.owned(account, attempt_id)
            self.reconcile(attempt_id)
            return self.attempt_view(self.owned(account, attempt_id))

    def verify(self, account, attempt_id, data):
        with self.lock:
            row = self.owned(account, attempt_id)
            payment = identifier(data.get('razorpay_payment_id'), 'pay')
            if not row['subscription'] or data.get('razorpay_subscription_id') != row['subscription']:
                raise Error(400, 'Subscription mismatch')
            if not signature_valid(self.provider.secret, f"{payment}|{row['subscription']}".encode(), data.get('razorpay_signature')):
                raise Error(400, 'Invalid checkout signature')
            self.reconcile(attempt_id)
            return self.entitlements(account)

    def entitlements(self, account, refresh=False):
        with self.lock:
            if refresh:
                for row in self.db.execute('SELECT id FROM attempts WHERE account=?', (account,)).fetchall():
                    self.reconcile(row['id'])
            now = int(self.clock())
            grant = self.db.execute('''SELECT g.end FROM grants g JOIN attempts a ON a.id=g.attempt
                WHERE a.account=? AND g.revoked=0 AND g.start<=? AND g.end>? ORDER BY g.end DESC LIMIT 1''', (account, now, now)).fetchone()
            attempts = self.db.execute('SELECT * FROM attempts WHERE account=? ORDER BY created DESC, rowid DESC', (account,)).fetchall()
            return {'account_id': account, 'active': grant is not None, 'expires_at': grant['end'] if grant else 0,
                    'server_time': now, 'lease_until': min(grant['end'], now + 86400 * 3) if grant else 0,
                    'subscriptions': [self.attempt_view(row) for row in attempts]}

    def cancel(self, account, attempt_id):
        with self.lock:
            self.owned(account, attempt_id)
            self.reconcile(attempt_id)
            row = self.owned(account, attempt_id)
            if row['status'] in TERMINAL or row['cancel_requested']:
                return self.attempt_view(row)
            if not row['subscription']:
                raise Error(409, 'Creation is being recovered; check status before cancelling')
            # Immediate mandate cancellation, with access preserved by paid grants.
            result = self.provider.request('POST', 'subscriptions/' + row['subscription'] + '/cancel', {'cancel_at_cycle_end': 0})
            if result.get('id') != row['subscription'] or result.get('status') != 'cancelled':
                raise Error(503, 'Cancellation not confirmed; check status')
            self.db.execute("UPDATE attempts SET cancel_requested=1,status='cancelled' WHERE id=?", (attempt_id,))
            return self.attempt_view(self.owned(account, attempt_id))

    def webhook(self, raw, signature, event_id):
        if not signature_valid(self.secret, raw, signature):
            raise Error(400, 'Invalid webhook signature')
        try:
            event = json.loads(raw)
            if event.get('account_id') != self.account_id:
                raise Error(400, 'Wrong merchant account')
            payload = event.get('payload', {})
            sub = payload.get('subscription', {}).get('entity', {}).get('id')
            invoice = payload.get('invoice', {}).get('entity', {})
            payment = payload.get('payment', {}).get('entity', {})
            refund = payload.get('refund', {}).get('entity', {})
            sub = sub or invoice.get('subscription_id')
            pay = payment.get('id') or refund.get('payment_id') or invoice.get('payment_id')
        except (ValueError, AttributeError, TypeError):
            raise Error(400, 'Invalid webhook body') from None
        if not event_id or len(event_id) > 200:
            raise Error(400, 'Missing event identifier')
        digest = hashlib.sha256(raw).hexdigest()
        with self.lock, self.transaction():
            old = self.db.execute('SELECT digest FROM events WHERE id=?', (event_id,)).fetchone()
            if old and old['digest'] != digest:
                raise Error(400, 'Event identifier conflict')
            self.db.execute('INSERT OR IGNORE INTO events(id,digest,subscription,payment,received) VALUES(?,?,?,?,?)',
                            (event_id, digest, sub, pay, int(self.clock())))
        # Acknowledge only after durable ingestion; worker retries provider failures.
        return {'received': True}

    def sweep(self):
        failures = 0
        with self.lock:
            # Also recheck cancelled/completed grants for refunds, independent of webhooks.
            for row in self.db.execute('''SELECT a.id FROM attempts a WHERE
                    a.status NOT IN ('cancelled','completed','expired','rejected') OR EXISTS
                    (SELECT 1 FROM grants g WHERE g.attempt=a.id AND g.end>?)''', (int(self.clock()),)).fetchall():
                try:
                    self.reconcile(row['id'])
                    unresolved = self.db.execute("SELECT 1 FROM attempts WHERE id=? AND status='creating' AND created<?",
                                                 (row['id'], int(self.clock()) - 300)).fetchone()
                    if unresolved:
                        failures += 1
                except Exception:
                    failures += 1
            for event in self.db.execute('SELECT * FROM events WHERE processed=0').fetchall():
                try:
                    row = None
                    if event['subscription']:
                        row = self.db.execute('SELECT id FROM attempts WHERE subscription=?', (event['subscription'],)).fetchone()
                    if not row and event['payment']:
                        row = self.db.execute('SELECT attempt AS id FROM grants WHERE payment=?', (event['payment'],)).fetchone()
                    if row:
                        self.reconcile(row['id'])
                    self.db.execute('UPDATE events SET processed=1 WHERE id=?', (event['id'],))
                except Exception:
                    failures += 1
        return failures


class Application:
    def __init__(self, service, authenticate):
        self.service, self.authenticate = service, authenticate

    def __call__(self, env, start_response):
        from http import HTTPStatus
        status = 200
        try:
            method, path = env['REQUEST_METHOD'], env.get('PATH_INFO', '')
            length = int(env.get('CONTENT_LENGTH') or 0)
            if not 0 <= length <= 262144:
                raise Error(413, 'Request too large')
            raw = env['wsgi.input'].read(length)
            if method == 'GET' and path == '/health':
                result = {'ok': True}
            elif method == 'POST' and path == '/v1/webhooks/razorpay':
                result = self.service.webhook(raw, env.get('HTTP_X_RAZORPAY_SIGNATURE'), env.get('HTTP_X_RAZORPAY_EVENT_ID'))
            else:
                authorization = env.get('HTTP_AUTHORIZATION', '')
                if not authorization.startswith('Bearer ') or len(authorization) > 8192:
                    raise Error(401, 'Sign in required')
                account = self.authenticate(authorization[7:])
                data = json.loads(raw or b'{}')
                if not isinstance(data, dict):
                    raise Error(400, 'Expected a JSON object')
                if method == 'GET' and path == '/v1/catalog':
                    result = self.service.catalog()
                elif method == 'GET' and path == '/v1/me/entitlements':
                    result = self.service.entitlements(account, refresh=True)
                elif method == 'POST' and path == '/v1/subscription-attempts':
                    result = self.service.create(account, data, env.get('HTTP_IDEMPOTENCY_KEY'))
                else:
                    match = re.fullmatch(r'/v1/subscription-attempts/([a-f0-9]{32})(?:/(verify|cancel))?', path)
                    if not match:
                        raise Error(404, 'Not found')
                    attempt_id, action = match.groups()
                    if method == 'GET' and action is None:
                        result = self.service.status(account, attempt_id)
                    elif method == 'POST' and action == 'verify':
                        result = self.service.verify(account, attempt_id, data)
                    elif method == 'POST' and action == 'cancel':
                        result = self.service.cancel(account, attempt_id)
                    else:
                        raise Error(405, 'Method not allowed')
        except Error as error:
            status, result = error.status, {'error': error.message}
        except (ValueError, TypeError):
            status, result = 400, {'error': 'Invalid request'}
        except Exception:
            status, result = 500, {'error': 'Billing temporarily unavailable'}
        body = json.dumps(result).encode()
        start_response(f'{status} {HTTPStatus(status).phrase}', [
            ('Content-Type', 'application/json'), ('Content-Length', str(len(body))),
            ('Cache-Control', 'no-store'), ('X-Content-Type-Options', 'nosniff')])
        return [body]
