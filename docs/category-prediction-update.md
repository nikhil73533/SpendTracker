# Native category prediction review and implementation

Updated 2026-09-18. The current request sets a 60% first-use category accuracy target, followed by learning from explicit corrections. The development benchmark clears that gate; real-customer accuracy remains unmeasured.

## Review findings

- The active `IncrementalPredictionService` used merchant counts and global category frequencies. There was no first-use vocabulary or text classifier. With no history it returned Other/Other Income at zero confidence; with history it could assign the most frequent category to an unrelated merchant.
- `OnlineLearningModel` was only an interface. The older `PredictionService`/KNN implementation has no active app callers. Its distance model and fallback across transaction types are not the production path. Legacy classes remain for compatibility.
- Learning had no transaction identity. Repeated edits accumulated contradictory observations. Separate executors delayed visibility, and `learnAsync` queued another asynchronous call.
- Dashboard corrections always used the receiver and original direction. Statement income records often contain only the sender. Manual form saves did not train the model.
- Predictions were not constrained to current typed categories, and category renames/deletions did not update prediction memory.

## Implemented

- First-use whole-word/phrase signals for Indian merchants and transaction purposes. Signals map to existing category names, including emoji labels and Health/Medical or Transport/Travel aliases. Conflicting signals require review; no categories are silently created.
- Native Java incremental multinomial Naive Bayes using unique word counts, separate income/expense models and uniform class priors. Token overlap, support and coverage gate assignments. Scores are conservative decision scores, **not calibrated probabilities**.
- Latest-confirmed merchant preference takes effect after one correction. Multiple confirmed examples can generalize to a new merchant through shared words. Empty names/generic placeholders do not establish shared unknown-merchant preferences. Unicode names are retained.
- One persisted training record per transaction. Re-editing subtracts the old counts and adds the replacement. Transfer/uncategorized changes retract the prior sample. Numbered custom categories stay distinct; same-millisecond corrections have strict ordering.
- Shared process model loads once and updates affected counters and an ordered merchant index. Predictions do not reread training history from disk. Text input is bounded to 2,048 characters and 48 unique words. Updates avoid a complete retraining pass. RAM/startup cost remains proportional to retained feedback; no device speed/memory result is claimed.
- Explicit Daily/suspicious-row corrections, manual edits and additions train after the transaction is saved on the repository worker. SMS predictions, PDF guesses and automatic refinement never train on their own output. Import confirmation alone does not confirm each category.
- SMS, statements and refinement share the model and current category registry. Income uses sender/payer; expense uses receiver/payee; narration supplies context. Corrections/refinement preserve statement reference/import metadata.
- Prediction database v5 adds feedback through a non-destructive 4→5 migration. Existing v4 merchant corrections remain available as merchant-only preferences. New feedback overrides them. Rename/delete updates saved labels; reset clears personalization while retaining first-use hints. The pre-existing destructive fallback for versions 1–3 remains.
- Daily/dashboard notification artwork reduced from 24dp to 18dp within its existing 40dp button; count badge reduced from 18dp to 14dp.

The model choice follows the incremental sufficient-count approach in the [official Naive Bayes documentation](https://scikit-learn.org/1.1/modules/naive_bayes.html#out-of-core-naive-bayes-model-fitting). This app uses local Java, with no Python, cloud service or new ML runtime dependency.

## Evaluation

`prediction/src/test/resources/category_cold_start.tsv` contains 76 hand-labeled synthetic development examples, including income, expenses and ambiguous personal payees. It was authored alongside the rules and is **not an independent held-out benchmark**. The 60% gate counts abstentions as misses. A separate precision gate prevents passing through arbitrary assignments.

| Metric | Result |
| --- | --- |
| Correct automatic first-use assignments / all examples | 58/76 (76.3%) |
| Automatically assigned | 58/76 |
| Correct / assigned | 58/58 (100%, this fixture only) |
| Needs review | 18/76 |
| Previous empty model top-label matches, ignoring review flag | 10/76 (13.2%) |
| Previous empty model automatic assignments | 0/76 |

Regression tests cover one-correction adaptation, unseen-merchant word generalization, repeated edits/retraction, type isolation, placeholders, conflicting hints, numbered categories, Devanagari names, persistence reload, failed writes, rename/delete, reset and v4 preferences. Persistence tests use a mocked DAO store, not an on-device Room upgrade.

Validation command:

2026-09-18 verification: 320 JVM tests passed (26 added), zero failures/errors. Debug APK assembly and lint passed. Lint reported zero errors, with 419 app warnings and 11 prediction-module warnings. No device installation or UI test was performed for this update.

```sh
JAVA_HOME='/home/mockingj/Downloads/android-studio-quail2-patch1-linux/android-studio/jbr' ./gradlew --offline :prediction:testDebugUnitTest :app:testDebugUnitTest assembleDebug lintDebug
```

For real 60% acceptance, freeze a consented labeled corpus before further tuning, split by merchant/template family, count all examples including abstentions, and evaluate a separate chronological correction stream. Report per-category/direction accuracy, precision and coverage. Personalized labels, P2P names and multi-purpose merchants may still need correction. Device UI, migration execution and learning latency have not been measured in this change.
