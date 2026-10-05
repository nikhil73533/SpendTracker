#!/usr/bin/env python3
"""Execute the app's emitted SQL against SQLite and compare to independent raw-row totals.
Requires Python 3 and a JDK (JAVA_HOME or javac/java on PATH). No user database is opened.
"""
import base64
import math
import os
from pathlib import Path
import random
import shutil
import sqlite3
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/example/spendtracker'
DAY = 86_400_000


def emitted_sql():
    java_home = os.environ.get('JAVA_HOME')
    javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not javac or not java:
        raise SystemExit('Set JAVA_HOME to a JDK to verify the production SQL.')
    with tempfile.TemporaryDirectory(prefix='spend-summary-') as temp:
        root = Path(temp)
        files = {
            'androidx/sqlite/db/SupportSQLiteDatabase.java': 'package androidx.sqlite.db; public interface SupportSQLiteDatabase { void execSQL(String sql); }',
            'androidx/room/RoomDatabase.java': 'package androidx.room; public class RoomDatabase { public static class Callback { public void onCreate(androidx.sqlite.db.SupportSQLiteDatabase db) {} public void onOpen(androidx.sqlite.db.SupportSQLiteDatabase db) {} } }',
            'Export.java': '''import com.example.spendtracker.data.local.database.AnalyticsMaterialization;
import com.example.spendtracker.data.local.dao.AnalyticsSql;
public class Export {
  static void emit(String sql) { System.out.println(java.util.Base64.getEncoder().encodeToString(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
  public static void main(String[] args) {
    AnalyticsMaterialization.CALLBACK.onOpen(Export::emit);
    AnalyticsMaterialization.install(Export::emit);
    emit(AnalyticsSql.TYPE_FACTS); emit(AnalyticsSql.CATEGORY_FACTS); emit(AnalyticsSql.MERCHANT_FACTS); emit(AnalyticsSql.DASHBOARD_SUMMARY);
  }
}'''
        }
        for name, content in files.items():
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
        sources = [str(root / name) for name in files]
        sources += [str(JAVA / 'data/local/database/AnalyticsMaterialization.java'), str(JAVA / 'data/local/dao/AnalyticsSql.java')]
        subprocess.run([javac, '-d', str(root)] + sources, check=True)
        lines = subprocess.check_output([java, '-cp', str(root), 'Export'], text=True).splitlines()
        values = [base64.b64decode(line).decode() for line in lines]
        return values[:-4], dict(zip(['type', 'category', 'merchant'], values[-4:-1])), values[-1]


def database():
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE transactions (id INTEGER PRIMARY KEY, date INTEGER, amount REAL, type TEXT, category TEXT, receiverName TEXT, status TEXT, sourceTransactionId TEXT UNIQUE)')
    db.execute('CREATE INDEX tx_range ON transactions(status,date)')
    return db


def classify(kind, category):
    if kind == 'TRANSFER' or category is not None and 'transfer' in category.lower():
        return 'TRANSFER'
    return kind if category is not None and kind is not None else 'UNCLASSIFIED'


def expected(db, start, end, dimension):
    result = {}
    for date, amount, kind, category, merchant in db.execute("SELECT date,amount,type,category,receiverName FROM transactions WHERE status='ACTIVE' AND date BETWEEN ? AND ?", (start, end)):
        key = (classify(kind, category), {'type': '', 'category': category or '', 'merchant': merchant or ''}[dimension])
        total, count = result.get(key, (0, 0))
        result[key] = (total + amount, count + 1)
    return result


def actual(db, facts, start, end):
    return {(kind, bucket): (total, count) for kind, bucket, total, count in db.execute('SELECT type,bucket,SUM(amount),SUM(transactionCount) FROM ' + facts + ' GROUP BY type,bucket', {'start': start, 'end': end})}


def assert_equal(want, got):
    assert want.keys() == got.keys(), (want.keys(), got.keys())
    for key, (total, count) in want.items():
        assert math.isclose(total, got[key][0], rel_tol=1e-9, abs_tol=1e-6), (key, total, got[key])
        assert count == got[key][1], (key, count, got[key])


def verify():
    statements, facts, summary_sql = emitted_sql()
    db = database()
    rng = random.Random(7129)
    rows = [(i, rng.randrange(-DAY, 32 * DAY), rng.randrange(1, 200000) / 100,
             rng.choice(['INCOME', 'EXPENSE', 'TRANSFER', None]), rng.choice(['Food', 'Travel', 'Transfer', '', None]),
             rng.choice(['Cafe', 'Train', '', None]), rng.choice(['ACTIVE', 'ACTIVE', 'DELETED']), f'import-{i}') for i in range(400)]
    db.executemany('INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?)', rows)
    # Tests migration/backfill with populated history, then all incremental mutations.
    for sql in statements:
        db.execute(sql)
    def check(start=-DAY, end=40 * DAY):
        for dimension, sql in facts.items():
            assert_equal(expected(db, start, end, dimension), actual(db, sql, start, end))
    check()
    for i in range(150):
        row_id = rng.randrange(400)
        action = i % 5
        if action == 0:
            db.execute("UPDATE transactions SET category='Transfer', type='EXPENSE', date=?, amount=?, receiverName='Changed' WHERE id=?", (rng.randrange(35 * DAY), i + 0.25, row_id))
        elif action == 1:
            db.execute("UPDATE transactions SET status='DELETED' WHERE id=?", (row_id,))
        elif action == 2:
            db.execute("UPDATE transactions SET status='ACTIVE' WHERE id=?", (row_id,))
        elif action == 3:
            # REPLACE must remove old contribution without erasing peers in the bucket.
            db.execute('INSERT OR REPLACE INTO transactions VALUES (?,?,?,?,?,?,?,?)', (row_id, DAY, 3.5, 'EXPENSE', 'Food', 'Cafe', 'ACTIVE', f'import-{row_id}'))
        else:
            db.execute('DELETE FROM transactions WHERE id=?', (row_id,))
        if i % 10 == 0:
            check()
    db.execute('INSERT OR IGNORE INTO transactions VALUES (?,?,?,?,?,?,?,?)', (999, DAY, 99999, 'EXPENSE', 'Food', 'Cafe', 'ACTIVE', 'import-0'))
    check()
    # Exact endpoints, sub-day windows, local-midnight offset, epoch, long all-time range.
    ranges = [(0, DAY - 1), (1, DAY - 1), (0, DAY), (DAY, DAY), (DAY + 1, DAY + 20), (-DAY, -1),
              (DAY - 19_800_000, 20 * DAY - 19_800_001), (0, 2**63-1)]
    ranges += [tuple(sorted([rng.randrange(-DAY, 40 * DAY), rng.randrange(-DAY, 40 * DAY)])) for _ in range(150)]
    for start, end in ranges:
        check(start, end)
    db.commit()
    db.execute('BEGIN')
    db.execute('DELETE FROM transactions')
    db.rollback()
    check()
    plans = list(db.execute('EXPLAIN QUERY PLAN SELECT SUM(amount) FROM ' + facts['type'], {'start': DAY + 100, 'end': 30 * DAY + 200}))
    raw_searches = [p[3] for p in plans if 'transactions' in p[3]]
    assert len(raw_searches) == 2 and all('SEARCH' in p and 'date>' in p and 'date<' in p for p in raw_searches), plans
    db.execute("ALTER TABLE transactions ADD COLUMN direction TEXT DEFAULT 'UNKNOWN'")
    db.execute("ALTER TABLE transactions ADD COLUMN sourceType TEXT DEFAULT 'Account'")
    db.execute("UPDATE transactions SET direction='CREDIT' WHERE id % 3 = 0")
    for start, end in ranges:
        rows = list(db.execute(summary_sql, {'start': start, 'end': end}))
        categories = {(kind, category): (total, count) for kind, category, total, count, account in rows if kind != '__META'}
        want = {key: value for key, value in expected(db, start, end, 'category').items() if key[0] in ('INCOME', 'EXPENSE')}
        assert_equal(want, categories)
        meta = [row for row in rows if row[0] == '__META'][0]
        transfer = account_total = 0
        for amount, kind, category, direction in db.execute("SELECT amount,type,category,direction FROM transactions WHERE status='ACTIVE' AND date BETWEEN ? AND ?", (start,end)):
            if classify(kind, category) == 'TRANSFER':
                transfer += amount if direction == 'CREDIT' or direction == 'UNKNOWN' and kind == 'INCOME' else -amount
            if kind is not None and kind != 'TRANSFER' and category is not None and 'transfer' not in category.lower():
                account_total += amount
        assert math.isclose(meta[2], transfer, abs_tol=1e-6), (meta, transfer)
        assert math.isclose(meta[4], account_total, abs_tol=1e-6), (meta, account_total)
    print('PASS: migration backfill; insert/update/replace/delete/restore; rollback; 158 exact date ranges across 3 materializations; indexed boundary reads; combined dashboard summary with signed transfers.')
    db.close()

    bench = database()
    bench.executemany('INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?)',
                     [(i, (i % 365) * DAY + 1000, i % 500 + 0.25, 'EXPENSE' if i % 3 else 'INCOME', 'Food', 'Cafe', 'ACTIVE', str(i)) for i in range(50000)])
    for sql in statements:
        bench.execute(sql)
    bounds = {'start': 0, 'end': 366 * DAY - 1}
    raw = "SELECT type,SUM(amount),COUNT(*) FROM transactions WHERE status='ACTIVE' AND date BETWEEN :start AND :end GROUP BY type"
    cached = 'SELECT type,SUM(amount),SUM(transactionCount) FROM ' + facts['type'] + ' GROUP BY type'
    timings = []
    for sql in [raw, cached]:
        began = time.perf_counter()
        for _ in range(30): list(bench.execute(sql, bounds))
        timings.append((time.perf_counter() - began) * 1000 / 30)
    print(f'Synthetic SQLite benchmark (50,000 transactions, 30 reads): raw grouped totals {timings[0]:.2f} ms; materialized totals {timings[1]:.2f} ms. Not an Android latency measurement.')

if __name__ == '__main__':
    verify()
