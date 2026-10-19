# Analytics refresh and materialized summaries

Implemented on 28 September 2026. Room schema version 18 adds three persistent SQLite summary tables:

- `analytics_daily_type`: UTC day and effective transaction type, with amount sum and count.
- `analytics_daily_category`: the same totals per category.
- `analytics_daily_merchant`: the same totals per merchant.

SQLite has no native materialized-view refresh mechanism. Transactional triggers maintain these stored aggregates on insert, relevant update, hard delete, soft delete and restore. REPLACE enables recursive delete triggers so displaced rows are subtracted. Trigger insertions use `NOT EXISTS`, preventing the caller's REPLACE conflict policy from replacing an existing summary bucket. Unrelated updates do not refresh summaries.

Migration 17→18 backfills existing active history. Both main and cloned databases register the migration and callback; fresh databases get the same triggers. No transaction data is removed. Transfers, including legacy transfer categories, remain separate from income and expense.

Totals/counts/averages, category summaries and merchant totals/counts use the materializations. The advanced financial overview replaces eight individual queries with one grouped query. The main dashboard summary also replaces eight independently emitting queries with one consistent snapshot, including category averages and signed transfer/account metadata. That metadata still reads raw rows because direction and funding source are not dimensions of these summaries. Row-level anomaly detection, detailed history and local-calendar trend calculations still read original transactions where those details are required; this change does not claim every query has disappeared.

Range queries combine complete UTC days from the materializations with at most two indexed raw boundary ranges. This preserves exact timestamps and local-midnight ranges without persisting a device timezone. Queries remain correct after a timezone change. Financial totals keep the existing inclusive DAO contract; the advanced screen converts its exclusive date-picker end appropriately. Its half-open category-frequency service converts the end once before calling the DAO.

Analytics queries now execute only while observed and refresh when transactions change. Superseded background requests cannot publish over newer results. The date-range filter updates atomically, removing duplicate work caused by separate start/end updates. The unused monthly-trend observer was removed.

## Verification

Run `scripts/verify-analytics-materialization.py` with `JAVA_HOME` set to a JDK. It compiles the production SQL generator, executes its actual SQL in a temporary SQLite database, and compares results to independent raw-row calculations. It covers migration backfill, replace/import conflicts, category/type/date/merchant edits, soft delete/restore, hard delete, transaction rollback, null classifications, 158 exact date ranges, and index use for both boundary reads.

Initial synthetic benchmark: 50,000 transactions, 30 reads, about **133.48 ms for raw grouped totals versus 2.31 ms for materialized totals** on this desktop. This measures the aggregate query, not full-screen Android latency. Results vary by machine, dataset and cache state. Three summaries add storage and write work; evaluate large imports and encrypted-database performance on a device before release.

`AnalyticsMaterializationTest` also covers Room schema migration and mutation behavior on Android. The isolated device-test configuration includes it. Device execution requires an authorized emulator or phone.
