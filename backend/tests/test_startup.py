import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class StartupTests(unittest.TestCase):
    def environment(self, directory):
        plans=Path(directory)/'plans.json'
        plans.write_text(json.dumps({key:{'provider_plan_id':'plan_'+key.replace('_',''),'amount':amount}
                         for key,amount in zip(['monthly','quarterly','six_monthly','yearly'],[9900,14900,29900,49900])}))
        return {'PATH':os.environ['PATH'], 'PYTHONPATH':str(Path(__file__).resolve().parents[1]),
                'RAZORPAY_KEY_ID':'rzp_test_placeholder','RAZORPAY_KEY_SECRET':'test-placeholder',
                'RAZORPAY_WEBHOOK_SECRET':'test-webhook','RAZORPAY_ACCOUNT_ID':'acc_test',
                'GOOGLE_WEB_CLIENT_ID':'test.apps.googleusercontent.com','BILLING_PLANS_FILE':str(plans),
                'BILLING_DATABASE':str(Path(directory)/'billing.sqlite'),
                'BILLING_TERMS_URL':'https://example.test/terms','BILLING_PRIVACY_URL':'https://example.test/privacy',
                'BILLING_SUPPORT_URL':'https://example.test/support'}

    def test_boots_with_durable_database_and_checkout_disabled_by_default(self):
        with tempfile.TemporaryDirectory() as directory:
            result=subprocess.run([sys.executable,'-c',
                'import server,threading; assert not server.service.catalog()["checkout_enabled"]; '
                'assert any(t.name == "billing-reconciliation" for t in threading.enumerate())'],
                env=self.environment(directory),capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertTrue((Path(directory)/'billing.sqlite').exists())

    def test_missing_credentials_fail_at_startup(self):
        with tempfile.TemporaryDirectory() as directory:
            env=self.environment(directory); del env['RAZORPAY_KEY_SECRET']
            result=subprocess.run([sys.executable,'-c','import server'],env=env,capture_output=True,text=True,timeout=10)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('Missing configuration: RAZORPAY_KEY_SECRET',result.stderr)

    def test_insecure_policy_url_fails_at_startup(self):
        with tempfile.TemporaryDirectory() as directory:
            env=self.environment(directory); env['BILLING_TERMS_URL']='http://example.test'
            result=subprocess.run([sys.executable,'-c','import server'],env=env,capture_output=True,text=True,timeout=10)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('must use HTTPS',result.stderr)
