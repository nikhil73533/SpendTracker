import copy
import hashlib
import hmac
import io
import json
import os
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from billing import Application, Error, PERIODS, Service, signature_valid


class FakeRazorpay:
    key_id = 'rzp_test_example'
    secret = 'test-secret'

    def __init__(self, plans):
        self.plans = {plan['provider_plan_id']: {'id': plan['provider_plan_id'], 'period': PERIODS[key][1],
                       'interval': PERIODS[key][2], 'item': {'amount': plan['amount'], 'currency': 'INR'}} for key, plan in plans.items()}
        self.subs, self.invoices, self.payments = {}, {}, {}
        self.creates, self.cancels = 0, 0
        self.timeout_after_create = False
        self.unavailable = False

    def request(self, method, path, data=None):
        if self.unavailable:
            raise Error(503, 'offline')
        parts = path.split('/')
        if method == 'POST' and path == 'subscriptions':
            self.creates += 1
            sub = {'id': 'sub_' + str(self.creates), 'plan_id': data['plan_id'], 'status': 'created', 'notes': data['notes']}
            self.subs[sub['id']] = sub
            if self.timeout_after_create:
                raise Error(503, 'uncertain')
            return copy.deepcopy(sub)
        if method == 'POST' and path.endswith('/cancel'):
            self.cancels += 1
            self.subs[parts[1]]['status'] = 'cancelled'
            return copy.deepcopy(self.subs[parts[1]])
        source = {'plans': self.plans, 'subscriptions': self.subs, 'payments': self.payments}[parts[0]]
        return copy.deepcopy(source[parts[1]])

    def all(self, path, params=None):
        if self.unavailable:
            raise Error(503, 'offline')
        if path == 'subscriptions':
            return copy.deepcopy(list(self.subs.values()))
        return copy.deepcopy([v for v in self.invoices.values() if v['subscription_id'] == params['subscription_id']])


class BillingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.temp.name, 'billing.sqlite')
        self.plans = {key: {'provider_plan_id': 'plan_' + key.replace('_', ''), 'amount': amount}
                      for key, amount in zip(PERIODS, (9900, 14900, 29900, 49900))}
        self.provider = FakeRazorpay(self.plans)
        self.now = 1800000000
        self.service = self.new_service()
        self.app = Application(self.service, self.authenticate)

    def new_service(self):
        return Service(self.path, self.provider, self.plans, 'webhook-secret', 'acc_test', clock=lambda: self.now)

    def tearDown(self):
        self.service.db.close()
        self.temp.cleanup()

    @staticmethod
    def authenticate(token):
        if token not in {'alice', 'bob'}:
            raise Error(401, 'invalid identity')
        return token

    def create(self, account='alice', plan='monthly', key='a' * 32):
        return self.service.create(account, {'plan_id': plan, 'catalog_version': '1'}, key)

    def paid(self, attempt, payment='pay_1', start=None, end=None):
        sub = attempt['subscription_id']
        row = self.service.owned('alice', attempt['id'])
        self.provider.subs[sub]['status'] = 'active'
        self.provider.invoices['inv_' + payment] = {
            'id': 'inv_' + payment, 'subscription_id': sub, 'status': 'paid', 'payment_id': payment,
            'amount': row['amount'], 'amount_paid': row['amount'], 'amount_due': 0, 'currency': 'INR',
            'billing_start': self.now - 1 if start is None else start,
            'billing_end': self.now + 2678400 if end is None else end}
        self.provider.payments[payment] = {'id': payment, 'invoice_id': 'inv_' + payment, 'amount': row['amount'],
                                           'currency': 'INR', 'status': 'captured', 'amount_refunded': 0}

    def evidence(self, attempt):
        value = {'razorpay_payment_id': 'pay_1', 'razorpay_subscription_id': attempt['subscription_id']}
        value['razorpay_signature'] = hmac.new(self.provider.secret.encode(), ('pay_1|' + attempt['subscription_id']).encode(), hashlib.sha256).hexdigest()
        return value

    def http(self, method, path, body=None, token='alice', **headers):
        raw = json.dumps(body).encode() if body is not None else b''
        env = {'REQUEST_METHOD': method, 'PATH_INFO': path, 'CONTENT_LENGTH': str(len(raw)),
               'wsgi.input': io.BytesIO(raw), 'HTTP_AUTHORIZATION': 'Bearer ' + token, **headers}
        captured = []
        data = b''.join(self.app(env, lambda status, hdrs: captured.append((status, hdrs))))
        return int(captured[0][0].split()[0]), json.loads(data)

    def test_all_four_plans_and_server_prices(self):
        self.assertEqual([p['amount'] for p in self.service.catalog()['plans']], [9900,14900,29900,49900])
        for index, plan in enumerate(PERIODS):
            self.create(account='customer' + str(index), plan=plan)
        self.assertEqual(self.provider.creates, 4)

    def test_signed_paid_invoice_grants_access(self):
        a = self.create(); self.paid(a)
        access = self.service.verify('alice', a['id'], self.evidence(a))
        self.assertTrue(access['active'])
        self.assertEqual(access['lease_until'], self.now + 3 * 86400)

    def test_authorization_only_does_not_grant(self):
        a = self.create()
        self.provider.subs[a['subscription_id']]['status'] = 'authenticated'
        self.assertFalse(self.service.verify('alice', a['id'], self.evidence(a))['active'])

    def test_small_authorization_deposit_does_not_grant(self):
        a = self.create(); self.paid(a)
        self.provider.invoices['inv_pay_1']['amount'] = 100
        self.assertFalse(self.service.verify('alice', a['id'], self.evidence(a))['active'])

    def test_wrong_amount_currency_invoice_status_and_period_rejected(self):
        for field, value in [('amount', 1), ('currency','USD'), ('amount_paid', 1), ('amount_due', 1),
                             ('billing_start',None), ('billing_end', self.now-10), ('status', 'issued')]:
            with self.subTest(field=field):
                a = self.create(); self.paid(a)
                self.provider.invoices['inv_pay_1'][field] = value
                self.service.reconcile(a['id'])
                self.assertFalse(self.service.entitlements('alice')['active'])

    def test_uncaptured_or_wrong_payment_does_not_grant(self):
        a = self.create(); self.paid(a)
        for field,value in [('status','authorized'),('currency','USD'),('amount',1),('invoice_id','inv_other'),('id','pay_other')]:
            with self.subTest(field=field):
                original = self.provider.payments['pay_1'][field]
                self.provider.payments['pay_1'][field] = value
                self.service.reconcile(a['id'])
                self.assertFalse(self.service.entitlements('alice')['active'])
                self.provider.payments['pay_1'][field] = original

    def test_forged_signature_and_wrong_account_rejected(self):
        a = self.create(); self.paid(a)
        for who,data in [('bob', self.evidence(a)), ('alice',{**self.evidence(a),'razorpay_signature':'fake'}),
                         ('alice',{**self.evidence(a),'razorpay_subscription_id':'sub_other'})]:
            with self.assertRaises(Error): self.service.verify(who,a['id'],data)
        self.assertFalse(self.service.entitlements('alice')['active'])

    def test_cancel_and_status_require_ownership(self):
        a = self.create()
        for method in [self.service.cancel,self.service.status]:
            with self.assertRaises(Error) as error: method('bob',a['id'])
            self.assertEqual(error.exception.status,404)
        self.assertEqual(self.provider.cancels,0)

    def test_same_request_reuses_subscription(self):
        a = self.create()
        self.assertEqual(a['id'], self.create()['id'])
        self.assertEqual(self.provider.creates,1)

    def test_new_key_and_different_plan_cannot_overlap(self):
        self.create()
        for key, plan in [('b'*32,'yearly'),('a'*32,'yearly')]:
            with self.assertRaises(Error): self.create(plan=plan,key=key)
        self.assertEqual(self.provider.creates,1)

    def test_concurrent_requests_create_only_one_mandate(self):
        with ThreadPoolExecutor(max_workers=6) as pool:
            attempts = list(pool.map(lambda _: self.create(), range(6)))
        self.assertEqual(len({a['id'] for a in attempts}),1)
        self.assertEqual(self.provider.creates,1)

    def test_timeout_creation_recovered_after_restart_without_retry(self):
        self.provider.timeout_after_create = True
        with self.assertRaises(Error): self.create()
        self.service.db.close(); self.service = self.new_service()
        result = self.create()
        self.assertEqual(result['subscription_id'],'sub_1')
        self.assertEqual(self.provider.creates,1)

    def test_unknown_creation_never_blindly_retried(self):
        self.provider.timeout_after_create = True
        with self.assertRaises(Error): self.create()
        self.provider.subs.clear()
        self.assertEqual(self.create()['status'],'creating')
        self.assertEqual(self.provider.creates,1)

    def test_catalog_tampering_and_stale_version(self):
        for data in [{'plan_id':'monthly','catalog_version':'old'}, {'plan_id':'other','catalog_version':'1'},
                     {'plan_id':'monthly','catalog_version':'1','amount':1}, {'plan_id': [], 'catalog_version':'1'}]:
            with self.assertRaises(Error): self.service.create('alice',data,'a'*32)
        self.assertEqual(self.provider.creates,0)

    def test_provider_plan_must_match_price_and_frequency(self):
        self.provider.plans['plan_monthly']['interval'] = 6
        with self.assertRaises(Error): self.create()
        self.assertEqual(self.provider.creates,0)

    def test_cancel_stops_future_debits_preserves_paid_access_and_is_idempotent(self):
        a = self.create(); self.paid(a)
        self.service.cancel('alice',a['id'])
        self.service.cancel('alice',a['id'])
        self.assertTrue(self.service.entitlements('alice')['active'])
        self.assertEqual(self.provider.cancels,1)
        with self.assertRaises(Error): self.create(key='b'*32)

    def test_cancellation_stays_terminal_after_stale_provider_read(self):
        a = self.create(); self.service.cancel('alice',a['id'])
        self.provider.subs[a['subscription_id']]['status']='active'
        self.assertEqual(self.service.status('alice',a['id'])['status'],'cancelled')

    def test_full_and_partial_refunds_revoke_only_affected_grant(self):
        a = self.create(); self.paid(a)
        self.service.reconcile(a['id'])
        self.provider.payments['pay_1']['amount_refunded']=1
        self.service.reconcile(a['id'])
        self.assertFalse(self.service.entitlements('alice')['active'])
        self.paid(a,payment='pay_2')
        self.service.reconcile(a['id'])
        self.assertTrue(self.service.entitlements('alice')['active'])
        self.provider.payments['pay_2']['status']='refunded'
        self.provider.invoices['inv_pay_2']['status']='cancelled'
        self.service.reconcile(a['id'])
        self.assertFalse(self.service.entitlements('alice')['active'])

    def test_refund_is_monotonic_even_if_provider_response_is_stale(self):
        a = self.create(); self.paid(a)
        self.provider.payments['pay_1']['amount_refunded']=9900
        self.service.reconcile(a['id'])
        self.provider.payments['pay_1']['amount_refunded']=0
        self.service.reconcile(a['id'])
        self.assertFalse(self.service.entitlements('alice')['active'])

    def test_renewal_failure_keeps_paid_period_then_expires(self):
        a = self.create(); self.paid(a,end=self.now+100)
        self.service.reconcile(a['id'])
        self.provider.subs[a['subscription_id']]['status']='halted'
        self.service.reconcile(a['id'])
        self.assertTrue(self.service.entitlements('alice')['active'])
        self.now+=100
        self.assertFalse(self.service.entitlements('alice')['active'])
        self.paid(a,payment='pay_2')
        self.service.reconcile(a['id'])
        self.assertTrue(self.service.entitlements('alice')['active'])

    def test_future_period_does_not_unlock_early(self):
        a = self.create(); self.paid(a,start=self.now+100,end=self.now+1000)
        self.service.reconcile(a['id'])
        self.assertFalse(self.service.entitlements('alice')['active'])
        self.now+=100
        self.assertTrue(self.service.entitlements('alice')['active'])

    def webhook(self, a, event_id='evt_1', mutate=None):
        event={'account_id':'acc_test','event':'subscription.charged','payload':{'subscription':{'entity':{'id':a['subscription_id']}}}}
        if mutate: event.update(mutate)
        raw=json.dumps(event).encode()
        sig=hmac.new(b'webhook-secret',raw,hashlib.sha256).hexdigest()
        return self.service.webhook(raw,sig,event_id)

    def test_duplicate_webhook_durable_and_grants_once(self):
        a=self.create(); self.paid(a)
        self.webhook(a); self.webhook(a)
        self.assertEqual(self.service.db.execute('SELECT count(*) FROM events').fetchone()[0],1)
        self.service.sweep(); self.service.sweep()
        self.assertEqual(self.service.db.execute('SELECT count(*) FROM grants').fetchone()[0],1)

    def test_webhook_retries_after_provider_outage(self):
        a=self.create(); self.paid(a); self.webhook(a)
        self.provider.unavailable=True
        self.assertGreater(self.service.sweep(),0)
        self.assertEqual(self.service.db.execute('SELECT processed FROM events').fetchone()[0],0)
        self.provider.unavailable=False; self.service.sweep()
        self.assertTrue(self.service.entitlements('alice')['active'])

    def test_webhook_invalid_signature_account_and_reused_event_id(self):
        a=self.create(); self.webhook(a)
        with self.assertRaises(Error): self.service.webhook(b'{}','x','evt_other')
        with self.assertRaises(Error): self.webhook(a,'evt_other',{'account_id':'acc_attacker'})
        with self.assertRaises(Error): self.webhook(a,'evt_1',{'event':'different'})
        self.assertEqual(self.service.db.execute('SELECT count(*) FROM events').fetchone()[0],1)

    def test_kill_switch_blocks_new_checkout_but_restore_cancel_work(self):
        a=self.create(); self.paid(a)
        self.service.checkout_enabled=False
        self.assertTrue(self.service.entitlements('alice',refresh=True)['active'])
        self.assertFalse(self.service.status('alice',a['id'])['checkout_allowed'])
        self.service.cancel('alice',a['id'])
        with self.assertRaises(Error): self.create('bob')

    def test_expired_attempt_allows_new_subscription(self):
        a=self.create(); self.provider.subs[a['subscription_id']]['status']='expired'
        self.service.reconcile(a['id'])
        self.assertNotEqual(self.create(key='b'*32)['id'],a['id'])

    def test_restore_after_restart_and_other_account_separation(self):
        a=self.create(); self.paid(a); self.service.reconcile(a['id'])
        self.service.db.close(); self.service=self.new_service()
        self.assertTrue(self.service.entitlements('alice',True)['active'])
        self.assertFalse(self.service.entitlements('bob',True)['active'])

    def test_api_authenticated_lifecycle(self):
        status,a=self.http('POST','/v1/subscription-attempts',{'plan_id':'monthly','catalog_version':'1'},HTTP_IDEMPOTENCY_KEY='a'*32)
        self.assertEqual(status,200); self.paid(a)
        status,access=self.http('POST','/v1/subscription-attempts/'+a['id']+'/verify',self.evidence(a))
        self.assertEqual(status,200); self.assertTrue(access['active'])
        status,sub=self.http('POST','/v1/subscription-attempts/'+a['id']+'/cancel',{})
        self.assertEqual(status,200); self.assertEqual(sub['status'],'cancelled')
        self.assertTrue(self.http('GET','/v1/me/entitlements')[1]['active'])

    def test_api_rejects_bad_identity_wrong_owner_and_missing_key(self):
        self.assertEqual(self.http('GET','/v1/catalog',token='forged')[0],401)
        self.assertEqual(self.http('POST','/v1/subscription-attempts',{'plan_id':'monthly','catalog_version':'1'})[0],400)
        a=self.create()
        self.assertEqual(self.http('GET','/v1/subscription-attempts/'+a['id'],token='bob')[0],404)
        self.assertEqual(self.http('DELETE','/v1/subscription-attempts/'+a['id'])[0],405)

    def test_api_malformed_body_and_size_limit(self):
        self.assertEqual(self.http('POST','/v1/subscription-attempts',[])[0],400)
        self.assertEqual(self.http('POST','/v1/subscription-attempts',{'x':'a'*262145})[0],413)

    def test_webhook_http_ingestion_is_durable_before_worker(self):
        a=self.create(); self.paid(a)
        event={'account_id':'acc_test','payload':{'subscription':{'entity':{'id':a['subscription_id']}}}}
        raw=json.dumps(event).encode()
        signature=hmac.new(b'webhook-secret',raw,hashlib.sha256).hexdigest()
        status,response=self.http('POST','/v1/webhooks/razorpay',event,token='unused',
                                  HTTP_X_RAZORPAY_SIGNATURE=signature,HTTP_X_RAZORPAY_EVENT_ID='evt_http')
        self.assertEqual(status,200)
        self.assertTrue(response['received'])
        self.assertFalse(self.service.entitlements('alice')['active'])
        self.service.sweep()
        self.assertTrue(self.service.entitlements('alice')['active'])

    def test_webhook_before_creation_response_can_recover_later(self):
        self.provider.timeout_after_create=True
        with self.assertRaises(Error): self.create()
        row=self.service.db.execute('SELECT * FROM attempts').fetchone()
        a={'id':row['id'],'subscription_id':'sub_1'}
        self.paid(a); self.webhook(a)
        self.service.sweep()
        self.assertTrue(self.service.entitlements('alice')['active'])
        self.assertEqual(self.provider.creates,1)

    def test_out_of_order_payload_cannot_undo_refund(self):
        a=self.create(); self.paid(a); self.service.reconcile(a['id'])
        self.provider.payments['pay_1']['amount_refunded']=9900
        self.webhook(a,'evt_refund',{'event':'refund.processed'})
        self.service.sweep()
        self.webhook(a,'evt_late',{'event':'subscription.authenticated'})
        self.service.sweep()
        self.assertFalse(self.service.entitlements('alice')['active'])

    def test_old_catalog_subscription_retains_original_price(self):
        a=self.create()
        self.service.plans['monthly']['amount']=19900
        self.service.version='2'
        self.assertEqual(self.service.status('alice',a['id'])['amount'],9900)
        self.paid(a)
        self.service.reconcile(a['id'])
        self.assertTrue(self.service.entitlements('alice')['active'])

    def test_health_and_unknown_route(self):
        self.assertEqual(self.http('GET','/health',token='unused'),(200,{'ok':True}))
        self.assertEqual(self.http('GET','/not-found')[0],404)

    def test_raw_body_signature_changes_with_whitespace(self):
        sig=hmac.new(b'secret',b'{"a":1}',hashlib.sha256).hexdigest()
        self.assertTrue(signature_valid('secret',b'{"a":1}',sig))
        self.assertFalse(signature_valid('secret',b'{ "a":1}',sig))


if __name__=='__main__': unittest.main()
