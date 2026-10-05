import os
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
from billing import Service, PERIODS
from maintenance import main
from test_billing import FakeRazorpay


class MaintenanceTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.path=os.path.join(self.temp.name,'billing.sqlite')
        plans={key:{'provider_plan_id':'plan_'+key.replace('_',''),'amount':9900} for key in PERIODS}
        self.provider=FakeRazorpay(plans)
        self.service=Service(self.path,self.provider,plans,'secret','acc_test')
        self.service.db.execute("INSERT INTO attempts(id,account,request_key,plan,provider_plan,amount,status,created) VALUES(?,?,?,?,?,?,?,?)",
                                ('a'*32,'alice','k'*32,'monthly','plan_monthly',9900,'creating',1))

    def tearDown(self):
        self.service.db.close(); self.temp.cleanup()

    def run_command(self):
        with patch.dict(os.environ,{'BILLING_DATABASE':self.path,'RAZORPAY_KEY_ID':'key','RAZORPAY_KEY_SECRET':'secret'}), \
             patch('maintenance.Razorpay',return_value=self.provider), \
             patch('sys.argv',['maintenance.py','a'*32,'--confirm-no-provider-subscription']):
            main()

    def test_confirmed_missing_provider_attempt_can_be_released(self):
        self.run_command()
        self.assertEqual(self.service.owned('alice','a'*32)['status'],'rejected')
        self.assertEqual(self.provider.creates,0)

    def test_existing_provider_subscription_blocks_release(self):
        self.provider.subs['sub_1']={'id':'sub_1','notes':{'attempt_id':'a'*32}}
        with self.assertRaises(RuntimeError): self.run_command()
        self.assertEqual(self.service.owned('alice','a'*32)['status'],'creating')

    def test_attached_attempt_cannot_be_released(self):
        self.service.db.execute("UPDATE attempts SET subscription='sub_1'")
        with self.assertRaises(RuntimeError): self.run_command()

    def test_provider_outage_cannot_be_treated_as_missing_subscription(self):
        self.provider.unavailable=True
        with self.assertRaises(Exception): self.run_command()
        self.assertEqual(self.service.owned('alice','a'*32)['status'],'creating')
