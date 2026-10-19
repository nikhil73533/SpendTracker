import io
import json
import unittest
from unittest.mock import patch
from urllib.error import URLError
from billing import Error, Razorpay


class ProviderTransportTests(unittest.TestCase):
    def test_http_request_uses_basic_auth_and_timeout(self):
        provider=Razorpay('rzp_test_key','secret')
        with patch('billing.urlopen',return_value=io.BytesIO(b'{"id":"sub_1"}')) as request:
            self.assertEqual(provider.request('POST','subscriptions',{'quantity':1}),{'id':'sub_1'})
            call=request.call_args
            self.assertEqual(call.kwargs['timeout'],20)
            req=call.args[0]
            self.assertEqual(req.full_url,'https://api.razorpay.com/v1/subscriptions')
            self.assertTrue(req.headers['Authorization'].startswith('Basic '))
            self.assertEqual(json.loads(req.data),{'quantity':1})

    def test_post_failure_not_retried_and_provider_details_not_exposed(self):
        with patch('billing.urlopen',side_effect=URLError('private upstream detail')) as request:
            with self.assertRaises(Error) as error:
                Razorpay('key','secret').request('POST','subscriptions',{})
            self.assertEqual(request.call_count,1)
            self.assertNotIn('private',str(error.exception))

    def test_all_paginates(self):
        provider=Razorpay('key','secret')
        with patch.object(provider,'request',side_effect=[{'items':[{'id':x} for x in range(100)]},{'items':[{'id':100}]}]) as request:
            self.assertEqual(len(list(provider.all('invoices',{'subscription_id':'sub_1'}))),101)
            self.assertIn('skip=100',request.call_args.args[1])
