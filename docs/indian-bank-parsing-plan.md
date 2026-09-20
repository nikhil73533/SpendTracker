# Indian bank SMS and statement parsing: implementation and evaluation plan

2026-09-18 category prediction update: see [native prediction review, implementation and benchmark](category-prediction-update.md). The current first-use category target is 60%; the 76.3% synthetic development result does not satisfy the real-data measurement work below. SMS detection/field-extraction targets are unchanged.

Updated 2026-09-17. Scope: incoming bank SMS classification/extraction and PDF counterparty names. This supplements `bank-statement-extraction.md`; historical `.artifacts` plans address older UI/backup/transfer work, not a measured SMS accuracy benchmark.

## Current state and audit

Already implemented: a modular SMS pipeline with ten bank configuration files; on-device PDF text/OCR with positioned table cells; strict statement amounts/dates/directions; editable/deletable import preview; Daily swipe and batch deletion. Preserve that work.

Remaining defects identified in source and targeted synthetic probes:

- `SMSReceiver` parses individual multipart PDUs instead of assembling the message.
- The detector's keyword score admits OTP purchases and future mandate debits. Pending events have no non-persisting result status.
- Amount extraction takes the first currency value: a balance-first probe returned 9,000 instead of the 500 debit.
- Ordinary NEFT transfers become `TRANSFER` without evidence that both accounts belong to the user; a refund to a debit card becomes `EXPENSE`.
- A completed refund mentioning an earlier failure is rejected; action/state must be interpreted together.
- Currencyless SBI-style `debited by 1046.0` fails extraction. HDFC's ISO-date/colon-time loses its printed time.
- Production JSON patterns are bypassed by the current modular JVM tests. Configuration dates are loaded but ignored; numeric reference IDs and VPAs are conflated.
- PDF name extraction handles `UPI/DR/reference/name` but not ICICI `UPI/reference/UPI/name-or-VPA/bank`. First-token fallbacks return `UPI`, `MMT`, `VPS`, or routing IDs. Cleaning truncates names and removes non-ASCII letters. All bank row parsers share this downstream name extraction.
- Prediction uses merchant history/global priors and can return a low-confidence category. The SMS receiver logs `needsUserConfirmation` but still assigns it. Category prediction is not the same task as extracting printed fields.

These are targeted counterexamples, not a representative baseline accuracy score. The previous 232 passing unit tests likewise do not establish real-world accuracy.

## Research and format inventory

Bank alerts include non-ledger events: ICICI documents debit/credit and cheque-return alerts; HDFC documents transaction and balance notifications. Treat a bank sender as context, not proof of a posted transaction. Sources: [ICICI alerts](https://www.icici.bank.in/personal-banking/ways-to-bank/mobile-banking/alerts), [HDFC InstaAlerts](https://www.hdfc.bank.in/nri-banking/ways-to-bank/instaalerts-sms-email).

First-hand, user-contributed examples show SBI currencyless `debited by`, ICICI's spend plus available-limit suffix, HDFC `Amt Sent`, `Used`, date-without-year, and `yyyy-MM-dd:HH:mm:ss`. These are observations, not an authoritative or exhaustive template specification. Source: [redacted SMS contributions](https://www.reddit.com/r/CreditCardsIndia/comments/1fpmhk4/help_me_build_an_expense_tracker_share_your_sms/).

Implementation format matrix (placeholders below are generalized, not copied private messages):

| Family | Shape to support | Main ambiguity |
| --- | --- | --- |
| ICICI account | Acct MASK debited AMOUNT; BENEFICIARY credited | Credit verb refers to the recipient |
| ICICI card | AMOUNT spent using Card MASK on DATE on MERCHANT; available limit | Limit is not the payment amount |
| SBI UPI | A/C MASK debited by AMOUNT on date ddMMMyy trf to NAME Refno ID | Currency prefix may be absent |
| HDFC | Amt Sent / Used / Spent AMOUNT … To/At NAME On DATE | Missing year; colon before time |
| Axis/Kotak/other accounts | AMOUNT has been debited/credited from/to A/c MASK | Owner vs counterparty account |
| AU/SBI narration | UPI/DR-or-CR/REFERENCE/NAME/BANK | Preserve delimiter and field roles |
| ICICI statement | UPI/REFERENCE/UPI/VPA/BANK; MMT/IMPS/REF/NAME/BANK; VPS/NAME/… | Handle VPA as handle, not invented personal name |
| HDFC/Axis statement | UPI-NAME-REF-VPA; NEFT/route/REF/NAME | Skip routing/reference tokens |
| Non-posted | OTP, balance, bill due, collect request, mandate notice, failure, pending | No fabricated expense/income |
| Reversal | Completed credit/refund referring to earlier debit/failure | A reversal is a distinct confirmed event |

DLT header suffixes must be preserved and parsed separately from the bank header. A transactional telecom category can mean a banking OTP, not money movement: [MSG91 sender-ID documentation](https://msg91.com/help/dlt-registration-in-india/suffix-in-sms-sender-id).

Failed/pending payments can coexist with a debit and later reversal. Keep lifecycle semantics distinct from ledger direction; don't silently discard or double-count confirmed reversal events. [NPCI UPI FAQ](https://www.npci.org.in/what-we-do/upi/faqs).

Candidate evaluation data: [FinEE dataset card](https://huggingface.co/datasets/Ranjit0034/finee-dataset) reports Apache-2.0 licensing, 2,419 anonymized real ICICI samples and predominantly synthetic remaining data. Audit provenance, privacy, duplicates and labels before reuse; do not treat its random synthetic test split as a real-bank accuracy benchmark. [Indian spam/ham collection](https://github.com/junioralive/india-spam-sms-classification) reports MIT licensing and over 2,000 messages, but spam/ham labels must be re-annotated: legitimate OTPs and balance alerts are not ledger transactions. No private inbox or statements are uploaded to external services.

## Implementation order

### 1. Safety and event semantics

- Add shared preprocessing for safety footers, authorization requests, future/conditional clauses, and completed actions.
- Return non-persisting statuses for failed/pending messages. Recognize completed refunds before historical failure words; retain raw SMS for provenance.
- Determine direction from the account action, not card-product nouns or a blanket debit-wins rule. Only explicit own-account transfers become `TRANSFER`.
- Assemble multipart messages before parsing; don't stop processing unrelated messages because one is rejected.
- Preserve existing public parser API and old user data. No automatic reparse or migration of historical transactions in this change.

### 2. Field extraction and production configurations

- Rank all monetary candidates by nearby action and field labels; reject balances/limits/dues as payment amounts, invalid grouping, zero, overflow and unresolved competing amounts. Use decimal parsing, not arbitrary numeric tokens.
- Support numeric/textual/ISO dates, compact dates, AM/PM and explicit time separators. Preserve timestamp provenance; absent time must not masquerade as printed time. Yearless dates use message arrival year with a bounded year-boundary rule.
- Extract UTR/RRN/UPI reference and VPA separately; retain configuration date captures and account masks.
- Add injectable configuration loading for tests using the real JSON assets. Validate/compile patterns and test configuration+generic precedence; scope risky patterns to the identified issuer.

### 3. Statement counterparty extraction

- Introduce a independently testable narration parser shared where appropriate with SMS. Keep table amounts/dates untouched.
- Recognize UPI variants, MMT/IMPS, NEFT/RTGS, VPS/POS/ECOM; skip channel, direction, routing, reference and bank tokens using token roles.
- Preserve Unicode names, wrapped name segments and punctuation. Never infer a legal name from a VPA: retain the handle as a lower-confidence display fallback with provenance.
- No first-token/channel-as-name fallback. Unknown names remain empty and review shows narration/unknown status. Preserve sender/receiver direction.
- Verify ICICI, SBI, HDFC, Axis and AU fixtures plus malformed/unknown variants, missing names and names containing bank-like words. Supplied screenshots are layout evidence, not a sufficient OCR benchmark.

### 4. Category confidence and consistency

- Use corrected counterparty evidence for prediction. Do not silently assign a category when the predictor requests confirmation; keep a neutral category.
- Preserve metadata when applying category predictions, including reference, direction and timestamp precision.
- Future improvement: explicit category taxonomy, user-confirmed merchant aliases, a calibrated local classifier only if deterministic/history baselines fail. Do not train on unconfirmed model guesses.

### 5. Representative benchmark and release gate

- Build a consented, de-identified corpus targeting 10,000 real SMS across ICICI, HDFC, SBI, Axis, Kotak, AU, PNB, Yes, IndusInd, IDFC and long-tail banks. Include at least 40% non-posted examples, uncommon transaction channels, multiple amounts, noisy formatting, Hindi/regional-language strata and unknown senders. Synthetic examples augment training/regression only.
- Label posted vs non-posted, lifecycle, direction, amount/currency, date/time precision, bank, account mask, reference, VPA, counterparty span and spending category. Missing fields are explicitly null, not hallucinated labels. Two annotators adjudicate ambiguity; retain provenance/license and template family.
- Split 60/20/20 grouped by contributor/account/template family, with an additional newer-date/unseen-template evaluation. Do not let substitutions from one template leak across splits. Freeze test labels before tuning. Report sparse bank/language strata as unverified.
- Statement benchmark: consented originals and scans, including at least 40 ICICI statements across layouts plus other banks, matched row/counterparty ground truth; split by document/account, not page. Measure text-only name parsing separately from end-to-end OCR so OCR loss isn't hidden.
- Classification release target: at least 90% accuracy **and** macro-F1, with posted-transaction precision at least 95%. Report confusion matrix, recall, per-bank/language/channel results and 95% confidence intervals.
- Field release target: at least 90% pooled normalized exact-match accuracy on present fields and at least 85% for each supported field, targeting 90%. Require amount/direction at least 95%. Report counterparty normalized exact match and token F1 separately; handle fallback is not a correct personal name.
- Category target: at least 85% top-1 accuracy, targeting 90%, and macro-F1 on human-labeled supported categories. Report `Unknown`/abstention coverage and cold-start separately; exclude no failures silently. User-personal categories cannot be inferred reliably from every P2P payment.
- End-to-end scoring counts missed transactions and missing required fields as failures, not just successfully parsed rows. Report auto-accept precision and coverage together to prevent gaming accuracy by rejecting difficult inputs.
- Existing unit tests, new adversarial fixtures, real asset configuration tests, offline debug build and later consented device tests are necessary but not sufficient. Publish measured scores only after the frozen real-data benchmark runs. No accuracy guarantee is currently established.

## Delivery checklist

- [x] Review existing implementation and source-backed format research.
- [x] Reproduce targeted SMS failures and identify statement name-parser defects.
- [x] Implement safety/amount/direction/date/reference fixes and configuration tests.
- [x] Implement narration/counterparty parser and integrate preview metadata.
- [x] Assemble multipart SMS and respect category-confidence decisions.
- [x] Run regression tests and build; document remaining limitations.
- [ ] Acquire and label representative real-data corpus with consent.
- [ ] Measure the 90% classification / 85–90% extraction and category targets.

The last two tasks require representative data and annotation; they cannot be replaced by a passing synthetic unit-test suite.

## Implemented behavior and remaining limitations

The active SMSParsingService now uses event-aware guards and contextual monetary candidates. Real production JSON assets are exercised by the regression tests, including ICICI and currencyless SBI patterns. Explicit printed times are retained; date-only SMS keep arrival ordering but carry DATE_ONLY so that time is not shown as printed. A missing date is labeled SMS_RECEIVED, and Daily displays its time as received time. Ordinary transfers without ownership evidence no longer disappear from expense totals. Existing historical data is not rewritten.

CounterpartyExtractor replaces the first-token fallbacks in PDF ingestion, preserves Unicode/full names, and identifies VPA display fallbacks separately from actual name fields in the JSON. Preview labels handles and missing names. Income SMS now show the counterparty rather than the SMS header in Daily. Category assignment preserves extraction metadata and retains neutral categories when confirmation is requested.

Still pending beyond this implementation:

- The consented real-data benchmark, human category labels, and measured/calibrated accuracy targets above.
- Robust multilingual SMS interpretation and additional unknown bank narration families; current changes focus on English SMS and the supplied statement layouts, with Unicode name preservation.
- Persisted field-level provenance/account and original SMS-header metadata in a dedicated schema, plus lifecycle reconciliation/deduplication across pending, completed, SMS and statement records. Numeric references are now separately retained, but the legacy upiId field still falls back to a reference for old consumers.
- Category taxonomy/alias improvements and model calibration driven by the benchmark, not by unconfirmed predictions.
- Actual affected original PDFs and physical-device verification; screenshot/constructed-row tests do not establish scan-level OCR accuracy.

The old monolithic SMSParser remains for compatibility; the production receiver uses the modular service. Some earlier transfer tests were clarified to state explicit ownership, and unowned transfers now test as outgoing payments rather than self-transfers.

Verification on 2026-09-17: all 248 JVM tests passed (zero failures/errors), including the actual ten bank asset configurations, synthetic adversarial SMS, multipart assembly and ICICI statement JSON name/handle integration. The normal debug APK built successfully. Lint reported two existing NotificationPermission errors in AlertParsingService and BudgetNotificationHelper; no new lint errors were reported. No device UI tests, installation, commit or push were performed for this change. These test results are not the requested real-world accuracy percentages.
