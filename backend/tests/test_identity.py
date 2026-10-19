"""Exercise real Google JWT verification with locally signed tokens and local certificates."""
import json
import time
import unittest
from datetime import datetime, timedelta, timezone
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from google.auth import crypt, jwt
from billing import Error, GoogleIdentity


class IdentityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Local test')])
        cert = x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key()).serial_number(1).not_valid_before(datetime.now(timezone.utc)-timedelta(days=1)).not_valid_after(datetime.now(timezone.utc)+timedelta(days=1)).sign(key, hashes.SHA256())
        cls.signer = crypt.RSASigner.from_string(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()), key_id='test-key')
        cls.certs = {'test-key': cert.public_bytes(serialization.Encoding.PEM).decode()}

    def setUp(self):
        self.identity = GoogleIdentity('expected-client')
        class Response:
            status = 200
            data = json.dumps(IdentityTests.certs).encode()
        self.identity.request = lambda *args, **kwargs: Response()

    def token(self, **changes):
        now = int(time.time())
        claims = {'iss':'https://accounts.google.com','aud':'expected-client','sub':'123',
                  'iat':now-10,'exp':now+1000,'email_verified':True}
        claims.update(changes)
        return jwt.encode(self.signer,claims).decode()

    def test_valid_signed_google_identity_uses_stable_subject(self):
        self.assertEqual(self.identity(self.token()),'google:123')

    def test_wrong_audience_issuer_expiry_and_unverified_email_rejected(self):
        for claims in [{'aud':'attacker-client'},{'iss':'https://attacker.invalid'}, {'exp':int(time.time())-100},
                       {'email_verified':False},{'sub':''},{'iat':int(time.time())+1000}]:
            with self.subTest(claims=claims), self.assertRaises(Error) as error:
                self.identity(self.token(**claims))
            self.assertEqual(error.exception.status,401)

    def test_forged_payload_and_malformed_token_rejected(self):
        good=self.token()
        import base64
        parts=good.split('.')
        claims=json.loads(base64.urlsafe_b64decode(parts[1]+'=='))
        claims['sub']='attacker'
        parts[1]=base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip('=')
        for token in ['.'.join(parts),'not-a-token','']:
            with self.assertRaises(Error): self.identity(token)
