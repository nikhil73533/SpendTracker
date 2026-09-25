# Bank statement extraction and deletion

The import pipeline runs locally using PDFBox for embedded text and the bundled ML Kit Latin recognizer for scanned pages and statement images (JPG, PNG, WEBP, HEIC, and HEIF).

Password-protected PDFs pause the batch and ask for that statement's password. The password is used only to unlock the selected file for the current extraction; it is never saved. If OCR is needed, a decrypted cache copy is created only for rendering and is deleted before extraction returns.

For the current SMS/name-parsing implementation checklist and measured-accuracy evaluation plan, see [Indian bank parsing plan](indian-bank-parsing-plan.md). The 2026-09-17 update replaces statement channel/reference-as-name fallbacks with token-role extraction and explicitly labels VPA-only and missing-name previews.

## Extraction

- Both engines retain word rectangles. StatementTableLayout recognizes date, value date, narration, reference, debit/withdrawal, credit/deposit, amount/type, and balance headers.
- Word coordinates assign cells to columns, preserving empty debit/credit cells. Dates anchor transactions; gaps between printed lines separate wrapped rows, including top-aligned and vertically centered dates. Repeated headers refresh the column layout and continuation pages reuse it.
- Explicit columns determine direction before narration. A running balance or reference number is never a substitute for an unreadable amount in a recognized table. Date parsing is strict and day-first; an explicitly printed time is preserved, while missing times stay DATE_ONLY.
- Indian lakh/crore and international thousands separators are supported. Common O/0 and I/l/1 corrections apply only to numeric cells. Conflicting debit/credit cells and unreadable amounts are rejected.
- OCR renders against white at up to 4x scale with an eight-million-pixel ceiling. A contrast retry is triggered by missing rows, unreadable descriptions, or inconsistent running balances. Extraction selection compares validated row count, balance consistency, and counterparty evidence, retaining embedded text on equal quality. Preprocessing uses a scanline buffer to limit memory.
- For mixed PDFs, extraction is selected separately per page. Equal-quality structured results favor embedded text. Unreadable or empty pages produce a preview warning; the user still reviews the candidates before import.
- Layouts without recognized headers use the existing text parser and balance reconciliation. Unresolved directions are omitted with a preview warning rather than being assumed to be expenses. These fallback layouts and poor/rotated scans still require careful review; the tests do not establish an accuracy percentage on real bank scans.

ML Kit rendering guidance: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## Deletion

- Preview Delete removes the chosen candidate by object identity, including when list positions change while its dialog is open. Nothing has been saved yet. Empty selection no longer discards the preview.
- In Daily, swipe left-to-right to move a transaction to Trash. A single deletion offers Undo.
- Long-press starts checkbox selection. Select all includes every transaction in the current Daily filter, excluding date headers. Deletion requires confirmation and uses one atomic database transaction with bounded SQL batches; the cloned database receives the same IDs.
- Back or Cancel exits selection. Selection follows transaction IDs, is pruned when filters/data change, and is restored after configuration changes. Deleted rows remain restorable through Trash.

## Verification

Run ./gradlew :app:testDebugUnitTest :app:assembleDebug.

To run the device checks without touching an existing installation, unlock the connected device and run:

    ./gradlew -I scripts/android-verification.init.gradle :app:connectedDebugAndroidTest

This uses the separate package com.example.spendtracker.verification, synthetic PDF/image data, a debug-only UI host, and an in-memory database. The UI cases require an unlocked screen; the OCR/database cases do not. Rebuild normally without the init script when producing the regular app APK.

Regression fixtures cover ICICI/HDFC/SBI/Axis/AU/HSBC layouts, word-box reconstruction, blank columns, wrapped narration, strict dates and amounts, mixed digital/scanned pages, printed times, preview removal, selection state, and batches above SQLite's parameter limit.

The remarks-table regression reproduces the reported ICICI layout with ten transactions, stacked headers, a centered remarks heading, names above UPI details, an ACH credit, and an ATM withdrawal. Column edges adapt to body text so names do not fall into an empty cheque column and references do not enter debit cells. Printed name headings take precedence over UPI handles; ACH/NACH narrations also expose named counterparties. The same rules apply across banks rather than using an ICICI-specific parser. Missing times remain unknown. Balance audits never alter printed amounts or direction, and rejected dated candidates remain included in the preview's found count.

Verification on 2026-09-19: all 315 JVM tests passed. Four OCR integration cases passed on the connected Samsung SM-A146B: digital and scanned SBI tables plus digital and scanned ten-row remarks tables. The optional original-screenshot case was skipped because the temporary attachment file was no longer present. The scanned-name assertions require all expected letters in order while allowing OCR-inserted spaces; digital names require exact spacing. An observed `UPI/` → `UPV` or `UP/` error is repaired only when the same name appears both before and after the marker and a handle is present. Spaces inside slash-delimited handles are normalized only when the full result is a valid handle. This verification uses recreated statements, not a measured accuracy benchmark across real bank documents.

Gradle's offline connected-test runner was missing a cached UTP dependency, so the compiled isolated APKs were installed with adb and the OCR class was run directly with `am instrument -w -e class com.example.spendtracker.ui.pdfimport.StatementOcrIntegrationTest com.example.spendtracker.verification.test/androidx.test.runner.AndroidJUnitRunner`.

Verification on 2026-09-16: all 232 JVM tests passed and the normal debug APK built successfully. Four real-device tests passed using synthetic digital/scanned statements and an in-memory Room database. Preview/selection UI checks could not complete on the locked device; further device UI checks were omitted at the user's request. The isolated verification apps were removed without changing the normal app's data. Lint remains blocked by two pre-existing notification-permission errors in AlertParsingService and BudgetNotificationHelper.

On-device checks: import a digital statement and a scanned statement; verify amounts/directions/dates against the originals; remove the first and last preview rows; swipe a Daily transaction and Undo; long-press, deselect entries, Select all, confirm deletion, and restore from Trash. Check rightward swipes while the dashboard's tab pager is present.
