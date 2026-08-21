package com.example.prediction.domain.service;

import android.content.Context;
import com.example.prediction.data.local.PredictionDatabase;
import com.example.prediction.data.local.entity.GlobalCategoryStatsEntity;
import com.example.prediction.data.local.entity.MerchantCategoryStatsEntity;
import com.example.prediction.domain.model.IncrementalPredictionResult;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.domain.model.TransactionFeatures;
import com.example.prediction.util.MerchantNormalizer;
import com.example.prediction.util.PredictionLogger;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Incremental, fully-offline transaction categorization service.
 *
 * <h3>Prediction Ensemble (in priority order)</h3>
 * <ol>
 *   <li><b>Transfer rule</b> – type == TRANSFER → always "Transfer", no ML needed.</li>
 *   <li><b>Merchant memory</b> – if merchantKey seen ≥ 3 times and P(best_cat|merchant) ≥ 0.85,
 *       return directly (high-confidence fast path).</li>
 *   <li><b>Weighted ensemble</b> – combine P(cat|merchant) + P(cat|global) with learned weights.</li>
 *   <li><b>Global prior fallback</b> – when merchant is new/cold, use the global distribution.</li>
 * </ol>
 *
 * <h3>Learning (incremental, per-sample)</h3>
 * <ul>
 *   <li>User correction → atomically updates merchant_category_stats and global_category_stats.</li>
 *   <li>OTP messages are rejected upstream by SMSParser; this service never sees them.</li>
 * </ul>
 */
public class IncrementalPredictionService {

    private static final String TAG = "IncrementalPredSvc";

    // Confidence threshold below which we request user confirmation
    private static final double CONFIRM_THRESHOLD = 0.70;

    // Merchant fast-path: require at least this many samples and this probability
    private static final int MERCHANT_MIN_SAMPLES = 3;
    private static final double MERCHANT_HIGH_CONFIDENCE = 0.85;

    // Ensemble weights (must sum to 1.0)
    private static final double W_MERCHANT = 0.80;
    private static final double W_GLOBAL   = 0.20;

    // Laplace smoothing pseudo-count
    private static final double LAPLACE_ALPHA = 0.5;

    private final PredictionDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public IncrementalPredictionService(Context context) {
        this.db = PredictionDatabase.getDatabase(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature Extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts a {@link TransactionFeatures} from a raw {@link PredictionTransaction}.
     * Must be called on a background thread (reads from DB).
     */
    public TransactionFeatures extractFeatures(PredictionTransaction tx) {
        if (tx == null) return null;
        if ("TRANSFER".equalsIgnoreCase(tx.type)) return null; // No features needed

        String merchantKey = MerchantNormalizer.normalize(tx.merchantName);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(tx.timestamp);
        int hour      = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean weekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        int timeBucket = TransactionFeatures.timeBucketFor(hour);

        // Load merchant stats from DB
        List<MerchantCategoryStatsEntity> merchantStats =
            db.merchantCategoryStatsDao().getStatsForMerchant(merchantKey);

        Map<String, Double> merchantProbs = categoryProbabilitiesFromStats(merchantStats);
        int merchantTotal = merchantStats.stream().mapToInt(e -> e.count).sum();
        int merchantCatCount = merchantStats.size();

        // Load global stats from DB
        List<GlobalCategoryStatsEntity> globalStats = db.globalCategoryStatsDao().getAll();
        Map<String, Double> globalProbs = globalProbabilities(globalStats);

        return new TransactionFeatures(
            tx.amount,
            Math.log1p(tx.amount),
            hour,
            dayOfWeek,
            weekend,
            timeBucket,
            merchantKey,
            "", // bankName not on PredictionTransaction; use "" for now
            tx.type,
            tx.type,
            merchantTotal,
            merchantCatCount,
            merchantProbs,
            new HashMap<>(), // time-context probabilities reserved for future
            globalProbs
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prediction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Predicts the best category for a transaction.
     * Returns null only for TRANSFER transactions (caller assigns "Transfer" directly).
     * Must be called on a background thread.
     */
    public IncrementalPredictionResult predict(PredictionTransaction tx) {
        if (tx == null) return null;

        // Rule 1: TRANSFER → no prediction needed
        if ("TRANSFER".equalsIgnoreCase(tx.type)) {
            return new IncrementalPredictionResult(
                "Transfer", 1.0,
                IncrementalPredictionResult.Source.TRANSFER_RULE,
                Collections.emptyMap(), false);
        }

        String merchantKey = MerchantNormalizer.normalize(tx.merchantName);
        PredictionLogger.log(TAG + ": predicting for merchant='" + merchantKey + "' type=" + tx.type);

        List<MerchantCategoryStatsEntity> merchantStats =
            db.merchantCategoryStatsDao().getStatsForMerchant(merchantKey);

        int merchantTotal = merchantStats.stream().mapToInt(e -> e.count).sum();
        Map<String, Double> merchantProbs = categoryProbabilitiesFromStats(merchantStats);

        // Rule 2: merchant fast-path (high confidence memorized mapping)
        if (merchantTotal >= MERCHANT_MIN_SAMPLES && !merchantProbs.isEmpty()) {
            Map.Entry<String, Double> best = argMax(merchantProbs);
            if (best != null && best.getValue() >= MERCHANT_HIGH_CONFIDENCE) {
                PredictionLogger.log(TAG + ": merchant fast-path → " + best.getKey() + " (" + best.getValue() + ")");
                return new IncrementalPredictionResult(
                    best.getKey(), best.getValue(),
                    IncrementalPredictionResult.Source.MERCHANT_HISTORY,
                    merchantProbs,
                    best.getValue() < CONFIRM_THRESHOLD);
            }
        }

        // Rule 3: weighted ensemble (merchant + global prior)
        List<GlobalCategoryStatsEntity> globalStats = db.globalCategoryStatsDao().getAll();
        Map<String, Double> globalProbs = globalProbabilities(globalStats);

        Map<String, Double> ensemble = ensembleScore(merchantProbs, globalProbs);

        if (ensemble.isEmpty()) {
            // Completely cold start: no data at all
            String defaultCat = "INCOME".equalsIgnoreCase(tx.type) ? "Other Income" : "Other";
            PredictionLogger.log(TAG + ": cold start, returning default=" + defaultCat);
            return new IncrementalPredictionResult(
                defaultCat, 0.0,
                IncrementalPredictionResult.Source.GLOBAL_PRIOR,
                Collections.emptyMap(), true);
        }

        Map.Entry<String, Double> top = argMax(ensemble);
        boolean needsConfirm = (top == null || top.getValue() < CONFIRM_THRESHOLD);

        PredictionLogger.log(TAG + ": ensemble → " + (top != null ? top.getKey() : "null")
            + " conf=" + (top != null ? top.getValue() : 0));

        return new IncrementalPredictionResult(
            top != null ? top.getKey() : "Other",
            top != null ? top.getValue() : 0.0,
            IncrementalPredictionResult.Source.ENSEMBLE,
            ensemble,
            needsConfirm);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Learning (called from background thread)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Incremental update triggered by a user-confirmed category correction.
     * All DB writes happen asynchronously on the single executor thread.
     *
     * @param tx       the transaction that was corrected
     * @param category the user-confirmed category
     */
    public void learn(PredictionTransaction tx, String category) {
        if (tx == null || category == null || category.isBlank()) return;
        if ("Transfer".equalsIgnoreCase(category)) return; // No learning for transfers

        executor.execute(() -> {
            String merchantKey = MerchantNormalizer.normalize(tx.merchantName);
            String compositeId = merchantKey + "|" + category;
            long now = System.currentTimeMillis();

            // 1. Update merchant_category_stats
            MerchantCategoryStatsEntity existing = db.merchantCategoryStatsDao().getById(compositeId);
            if (existing == null) {
                db.merchantCategoryStatsDao().insert(
                    new MerchantCategoryStatsEntity(merchantKey, category, 1, now));
            } else {
                existing.count++;
                existing.lastSeenMs = now;
                db.merchantCategoryStatsDao().update(existing);
            }

            // 2. Update global_category_stats
            GlobalCategoryStatsEntity global = db.globalCategoryStatsDao().getByCategory(category);
            if (global == null) {
                db.globalCategoryStatsDao().insert(new GlobalCategoryStatsEntity(category, 1));
            } else {
                global.count++;
                db.globalCategoryStatsDao().update(global);
            }

            PredictionLogger.log(TAG + ": learned merchant='" + merchantKey + "' → " + category);
        });
    }

    /** Convenience: enqueue learning on the background executor (safe to call from main thread). */
    public void learnAsync(PredictionTransaction tx, String category) {
        executor.execute(() -> learn(tx, category));
    }

    /** Clears all incremental learning state (merchant stats, global stats, prototypes). */
    public void resetAllData() {
        db.merchantCategoryStatsDao().deleteAll();
        db.globalCategoryStatsDao().deleteAll();
        db.prototypeDao().deleteAll();
        db.merchantStatsDao().deleteAll();
        PredictionLogger.log(TAG + ": all model data cleared");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Compute P(category|merchant) from raw counts using Laplace smoothing. */
    private Map<String, Double> categoryProbabilitiesFromStats(List<MerchantCategoryStatsEntity> stats) {
        Map<String, Double> probs = new HashMap<>();
        if (stats == null || stats.isEmpty()) return probs;

        double total = stats.stream().mapToInt(e -> e.count).sum() + LAPLACE_ALPHA * stats.size();
        for (MerchantCategoryStatsEntity e : stats) {
            probs.put(e.category, (e.count + LAPLACE_ALPHA) / total);
        }
        return probs;
    }

    /** Compute P(category) from global counts using Laplace smoothing. */
    private Map<String, Double> globalProbabilities(List<GlobalCategoryStatsEntity> stats) {
        Map<String, Double> probs = new HashMap<>();
        if (stats == null || stats.isEmpty()) return probs;

        double total = stats.stream().mapToInt(e -> e.count).sum() + LAPLACE_ALPHA * stats.size();
        for (GlobalCategoryStatsEntity e : stats) {
            probs.put(e.category, (e.count + LAPLACE_ALPHA) / total);
        }
        return probs;
    }

    /** Weighted sum of merchant and global probability maps. */
    private Map<String, Double> ensembleScore(
            Map<String, Double> merchantProbs, Map<String, Double> globalProbs) {

        Map<String, Double> result = new HashMap<>();

        // Collect all known categories
        for (String cat : merchantProbs.keySet()) result.put(cat, 0.0);
        for (String cat : globalProbs.keySet())   result.put(cat, 0.0);

        for (String cat : result.keySet()) {
            double m = merchantProbs.getOrDefault(cat, 0.0);
            double g = globalProbs.getOrDefault(cat, 0.0);
            double w_m = merchantProbs.isEmpty() ? 0.0 : W_MERCHANT;
            double w_g = merchantProbs.isEmpty() ? 1.0 : W_GLOBAL;
            result.put(cat, w_m * m + w_g * g);
        }
        return result;
    }

    /** Returns the entry with the highest value, or null if the map is empty. */
    private static <K> Map.Entry<K, Double> argMax(Map<K, Double> map) {
        return map.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
    }
}
