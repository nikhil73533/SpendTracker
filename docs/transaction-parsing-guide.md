# How SpendTracker reads transaction messages

## 1. What steps detect a transaction?

1. Join the parts of a long SMS and retain the original text and receipt time.
2. Normalize spacing. Separate future-tense notices and security footers from completed transaction text.
3. Reject OTP/authorization requests, failed or pending payments, and payment-due notices. Require both a completed money action and an amount candidate.
4. Identify the bank from the SMS sender first, then from the message text.
5. Try the bank’s known message patterns. If none fits, use the shared extractors.
6. Extract the amount, credit/debit direction, date, account, counterparty and reference. Dates, account numbers and references are marked separately so their digits cannot become an amount.
7. Validate the fields. Ambiguous or invalid amounts are rejected; missing optional information can produce a partial result.
8. Predict a category and save through the repository, which checks duplicates. Category prediction failure must not discard an otherwise valid parsed transaction. Observed database changes refresh the transaction lists and totals.

## 2. How do the regex patterns work?

A regex describes a recognizable text structure. These are simplified examples; the code includes additional boundaries and validation.

| Pattern | Meaning |
|---|---|
| `(?i)\bcredited\b` | Match “credited” without case sensitivity, as a whole word. |
| `(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)` | Find a currency prefix, optional spaces, then capture an amount with optional commas and decimals. |
| `\d{1,2}-[A-Za-z]{3}-\d{2,4}` | Locate dates such as `01-Oct-26`; strict date parsing checks whether the date actually exists. |
| `NEFT-[A-Z]+-([A-Z0-9]+)-(.+?)` | Separate the routing token, reference and counterparty in a structured NEFT narration. The real pattern also stops before the available balance. |

`\b` means a word boundary; `\s*` means optional whitespace; `\d` means a digit. Parentheses capture a field. `(?:...)` groups alternatives without capturing them. `+` means one or more repetitions; `?` means optional, or makes a capture stop at the earliest valid boundary.

The parser scores amount candidates by their relationship to the transaction action. It excludes balances, credit limits, fees, dates and reference/account spans, rather than selecting the first number. A credit card product name does not establish incoming money: the account action establishes direction.

For the reported SMS, the expected record is:

- **Income:** ₹10, with credit direction.
- **Date:** 1 October 2026. The SMS prints no transaction time, so the result is marked date-only.
- **Bank:** ICICI; **sender:** CEL EBA.
- **Reference:** CHO1297600205.
- **Excluded from the amount:** account XX110, the date, reference and ₹20 available balance.

Converting this record to Transfer fills **CEL EBA → ICICI**. An outgoing payment instead uses **the user’s bank/name → the parsed receiver**. Explicitly edited endpoints are retained.

## 3. Why use this approach?

Bank alerts have repeated structures. Local patterns are fast, explainable, work offline, and can be covered by regression tests using redacted messages. Shared extractors cover formatting variations; strict checks reduce the risk of treating a balance or reference as money.

A trained entity-recognition model can help suggest dates, names and numeric roles, but should supplement these checks. The implementation includes a deterministic numeric-role pass; it is **not a trained NER model**. A separate GLiNER evaluation script is included for experimentation. GLiNER is not enabled in the Android app; its runtime, model size, accuracy and device performance need validation before adoption. See the [official GLiNER project](https://github.com/urchade/GLiNER).

GLiNER trial result: the library loaded, but the model download was stopped when available disk space fell to about 415 MiB. No inference or accuracy measurement was completed. The script can be rerun in an environment with sufficient storage.
