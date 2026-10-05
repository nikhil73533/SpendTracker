"""Explicit offline operator resolution of a creation attempt; never creates a mandate."""
import argparse
import os
import sqlite3
from billing import Razorpay


def main():
    parser = argparse.ArgumentParser(description='Stop the billing service before using this command.')
    parser.add_argument('attempt_id')
    parser.add_argument('--confirm-no-provider-subscription', action='store_true', required=True)
    args = parser.parse_args()
    provider = Razorpay(os.environ['RAZORPAY_KEY_ID'], os.environ['RAZORPAY_KEY_SECRET'])
    db = sqlite3.connect(os.environ['BILLING_DATABASE'])
    db.row_factory = sqlite3.Row
    db.execute('BEGIN IMMEDIATE')
    try:
        row = db.execute('SELECT * FROM attempts WHERE id=?', (args.attempt_id,)).fetchone()
        if row is None or row['status'] != 'creating' or row['subscription']:
            raise RuntimeError('Only an unresolved, unattached creation can be rejected')
        for sub in provider.all('subscriptions', {'from': row['created'] - 60}):
            if isinstance(sub.get('notes'), dict) and sub['notes'].get('attempt_id') == row['id']:
                raise RuntimeError('Provider subscription exists; run reconciliation instead')
        db.execute("UPDATE attempts SET status='rejected' WHERE id=?", (args.attempt_id,))
        db.commit()
        print('Creation reservation rejected after explicit operator confirmation.')
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == '__main__':
    main()
