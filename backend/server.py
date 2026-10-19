"""Run with: gunicorn --workers 1 --threads 4 --bind 127.0.0.1:8080 server:app"""
import json
import logging
import os
import threading
from billing import Application, GoogleIdentity, Razorpay, Service

required = ['RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET', 'RAZORPAY_WEBHOOK_SECRET',
            'RAZORPAY_ACCOUNT_ID', 'GOOGLE_WEB_CLIENT_ID', 'BILLING_PLANS_FILE',
            'BILLING_DATABASE', 'BILLING_TERMS_URL', 'BILLING_PRIVACY_URL', 'BILLING_SUPPORT_URL']
for name in required:
    if not os.environ.get(name):
        raise RuntimeError('Missing configuration: ' + name)
for name in ['BILLING_TERMS_URL', 'BILLING_PRIVACY_URL', 'BILLING_SUPPORT_URL']:
    if not os.environ[name].startswith('https://'):
        raise RuntimeError(name + ' must use HTTPS')
with open(os.environ['BILLING_PLANS_FILE']) as file:
    plans = json.load(file)
service = Service(os.environ['BILLING_DATABASE'], Razorpay(os.environ['RAZORPAY_KEY_ID'], os.environ['RAZORPAY_KEY_SECRET']),
                  plans, os.environ['RAZORPAY_WEBHOOK_SECRET'], os.environ['RAZORPAY_ACCOUNT_ID'],
                  catalog_version=os.environ.get('BILLING_CATALOG_VERSION', '1'),
                  terms_url=os.environ['BILLING_TERMS_URL'], privacy_url=os.environ['BILLING_PRIVACY_URL'],
                  support_url=os.environ['BILLING_SUPPORT_URL'], checkout_enabled=os.environ.get('BILLING_CHECKOUT_ENABLED') == 'true')
app = Application(service, GoogleIdentity(os.environ['GOOGLE_WEB_CLIENT_ID']))


def worker():
    while True:
        try:
            failures = service.sweep()
            if failures:
                logging.error('Billing reconciliation: %d operations require retry', failures)
        except Exception:
            logging.error('Billing reconciliation worker requires attention')
        threading.Event().wait(60)


threading.Thread(target=worker, name='billing-reconciliation', daemon=True).start()
