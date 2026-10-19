# Notifications, bill reminders, and category management

## Audit and implementation status

- [x] Diagnose missing notifications: manifest and permission flow lacked Android 13+ POST_NOTIFICATIONS; bill messages had no durable due-date scheduling.
- [x] Central permission/channel checks, actionable notification taps, and a settings entry point reporting blocked bill notifications.
- [x] Replace transaction-frequency estimates with bill-message detection, independent of posted-transaction ingestion.
- [x] Parse Indian day-first dates, named months, two/four-digit years, ISO dates, relative dates, and labeled amounts. Missing/ambiguous fields require review.
- [x] Incoming SMS persistence completes inside the receiver's existing background work. Bill failures cannot prevent transaction parsing.
- [x] Review pasted messages and optionally scan the last 90 days of SMS locally. No message contents leave the device.
- [x] Persist due date and delivery state; WorkManager checks every 15 minutes, with a catch-up check on opening the screen or adding a bill.
- [x] Reminders target 9 AM local time two calendar days before due, the day before, and the due date. A late arrival gets at most one catch-up reminder that day. No notifications after the due date.
- [x] Countdown and progress bar, review/edit, mark paid, and confirmed delete. Paid/deleted bills stop reminders and clear their notification.
- [x] Database 13→14 migration registered for both encrypted databases. Legacy unfiltered SMS-pattern rows are retained as resolved history; scan SMS to re-detect actual bills.
- [x] Category save/rename/delete is type-aware and atomic. Deletion preserves transactions under the same type's Uncategorized category. Shared group-category links survive when another type still uses that name.
- [x] Guard duplicate/blank names, invalid budgets, and system categories. Existing category type is read-only to avoid reclassifying transactions. Decimal budgets use a continuous slider without rounding saved input to whole rupees.
- [x] Budget alerts use Expense categories, current periods only, and one successful delivery per category/period/limit; blocked delivery does not consume that marker.

## Deliberate limits

- Background reminders are inexact, not alarm-clock guarantees. Android battery restrictions/Doze/force-stop can defer or prevent execution. Permission/channel blocking is visible in the bill screen.
- Deduplication is conservative: same sender, whitespace-normalized message and due date. Account digits are retained. Differently worded reminders may require manual deletion rather than risking merging two different bills.
- Payment-confirmation messages are not automatically matched to/used to resolve bills. Mark paid explicitly.
- Unrecognized billers and missing dates/amounts need user review. No measured universal bank-message accuracy claim is made.
- Existing category names shared by Income/Expense must be renamed/deleted from their typed Manage Categories tab, not ambiguous legacy name-only dialogs.

## Verification

Verified on 18 September 2026 with offline Gradle: testDebugUnitTest, assembleDebug, and lintDebug all succeeded. All 294 JVM tests passed (46 added in this update); lint reported no errors. Existing warnings remain.

Regression coverage includes bill detection/amount/date boundaries, persistence ordering, account separation, paid-bill deduplication, calendar-day/DST scheduling, progress bounds, category validation and typed mutations. Device UI, actual notification delivery, and an on-device encrypted-database upgrade are not claimed verified.

Android references:

- [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Persistent work scheduling and periodic-work limits](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
