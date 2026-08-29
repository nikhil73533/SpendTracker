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
 */
public class IncrementalPredictionService {

    private static final String TAG = "IncrementalPredSvc";
    private static final double CONFIRM_THRESHOLD = 0.70;
    private static final int MERCHANT_MIN_SAMPLES = 3;
    private static final double MERCHANT_HIGH_CONFIDENCE = 0.85;
    private static final double W_MERCHANT = 0.80;
    private static final double W_GLOBAL   = 0.20;
    private static final double LAPLACE_ALPHA = 0.5;

    private final PredictionDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public IncrementalPredictionService(Context context) {
        this.db = PredictionDatabase.getDatabase(context);
    }

    public TransactionFeatures extractFeatures(PredictionTransaction tx) {
        if (tx == null) return null;
        if ("TRANSFER".equalsIgnoreCase(tx.type)) return null;

        String merchantKey = MerchantNormalizer.normalize(tx.merchantName);
        String txType = normalizeType(tx.type);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(tx.timestamp);
        int hour      = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean weekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        int timeBucket = TransactionFeatures.timeBucketFor(hour);

        // Load merchant stats filtered by transaction type (separate income/expense pipelines)
        List<MerchantCategoryStatsEntity> merchantStats =
            db.merchantCategoryStatsDao().getStatsForMerchantByType(merchantKey, txType);

        Map<String, Double> merchantProbs = categoryProbabilitiesFromStats(merchantStats);
        int merchantTotal = merchantStats.stream().mapToInt(e -> e.count).sum();
        int merchantCatCount = merchantStats.size();

        // Load global stats filtered by transaction type
        List<GlobalCategoryStatsEntity> globalStats = db.globalCategoryStatsDao().getAllByType(txType);
        Map<String, Double> globalProbs = globalProbabilities(globalStats);

        return new TransactionFeatures(
            tx.amount,
            Math.log1p(tx.amount),
            hour,
            dayOfWeek,
            weekend,
            timeBucket,
            merchantKey,
            "",
            tx.type,
            tx.type,
            merchantTotal,
            merchantCatCount,
            merchantProbs,
            new HashMap<>(),
            globalProbs
        );
    }

    public IncrementalPredictionResult predict(PredictionTransaction tx) {
        if (tx == null) return null;

        if ("TRANSFER".equalsIgnoreCase(tx.type)) {
            return new IncrementalPredictionResult(
                "Transfer", 1.0,
                IncrementalPredictionResult.Source.TRANSFER_RULE,
                Collections.emptyMap(), false);
        }

        String merchantKey = MerchantNormalizer.normalize(tx.merchantName);
        String txType = normalizeType(tx.type);
        PredictionLogger.log(TAG + ": predicting for merchant='" + merchantKey + "' type=" + txType);

        // Use type-filtered merchant stats (separate income/expense pipelines)
        List<MerchantCategoryStatsEntity> merchantStats =
            db.merchantCategoryStatsDao().getStatsForMerchantByType(merchantKey, txType);

        int merchantTotal = merchantStats.stream().mapToInt(e -> e.count).sum();
        Map<String, Double> merchantProbs = categoryProbabilitiesFromStats(merchantStats);

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

        // Rule 3: weighted ensemble (merchant + global prior) — type-filtered
        List<GlobalCategoryStatsEntity> globalStats = db.globalCategoryStatsDao().getAllByType(txType);
        Map<String, Double> globalProbs = globalProbabilities(globalStats);

        Map<String, Double> ensemble = ensembleScore(merchantProbs, globalProbs);

        if (ensemble.isEmpty()) {
            // Completely cold start: no data at all
            String defaultCat = "INCOME".equalsIgnoreCase(txType) ? "Other Income" : "Other";
            PredictionLogger.log(TAG + ": cold start (" + txType + "), returning default=" + defaultCat);
            return new IncrementalPredictionResult(
                defaultCat, 0.0,
                IncrementalPredictionResult.Source.GLOBAL_PRIOR,
                Collections.emptyMap(), true);
        }

        Map.Entry<String, Double> top = argMax(ensemble);
        boolean needsConfirm = (top == null || top.getValue() < CONFIRM_THRESHOLD);

        PredictionLogger.log(TAG + ": ensemble (" + txType + ") → " + (top != null ? top.getKey() : "null")
            + " conf=" + (top != null ? top.getValue() : 0));

        return new IncrementalPredictionResult(
            top != null ? top.getKey() : "Other",
            top != null ? top.getValue() : 0.0,
            IncrementalPredictionResult.Source.ENSEMBLE,
            ensemble,
            needsConfirm);
    }

    public void learn(PredictionTransaction tx, String category) {
        if (tx == null || category == null || category.trim().isEmpty()) return;
        if ("Transfer".equalsIgnoreCase(category)) return;

        executor.execute(() -> {
            String merchantKey = MerchantNormalizer.normalize(tx.merchantName);
            String txType = normalizeType(tx.type);
            String compositeId = merchantKey + "|" + txType + "|" + category;
            long now = System.currentTimeMillis();

            // 1. Update merchant_category_stats (type-scoped)
            MerchantCategoryStatsEntity existing = db.merchantCategoryStatsDao().getById(compositeId);
            if (existing == null) {
                db.merchantCategoryStatsDao().insert(
                    new MerchantCategoryStatsEntity(merchantKey, category, txType, 1, now));
            } else {
                existing.count++;
                existing.lastSeenMs = now;
                db.merchantCategoryStatsDao().update(existing);
            }

            // 2. Update global_category_stats (type-scoped)
            String globalId = txType + "|" + category;
            GlobalCategoryStatsEntity global = db.globalCategoryStatsDao().getById(globalId);
            if (global == null) {
                db.globalCategoryStatsDao().insert(new GlobalCategoryStatsEntity(category, txType, 1));
            } else {
                global.count++;
                db.globalCategoryStatsDao().update(global);
            }

            PredictionLogger.log(TAG + ": learned merchant='" + merchantKey + "' (" + txType + ") → " + category);
        });
    }

    public void learnAsync(PredictionTransaction tx, String category) {
        executor.execute(() -> learn(tx, category));
    }

    public void resetAllData() {
        db.merchantCategoryStatsDao().deleteAll();
        db.globalCategoryStatsDao().deleteAll();
        db.prototypeDao().deleteAll();
        db.merchantStatsDao().deleteAll();
        PredictionLogger.log(TAG + ": all model data cleared");
    }

    private Map<String, Double> categoryProbabilitiesFromStats(List<MerchantCategoryStatsEntity> stats) {
        Map<String, Double> probs = new HashMap<>();
        if (stats == null || stats.isEmpty()) return probs;

        double total = stats.stream().mapToInt(e -> e.count).sum() + LAPLACE_ALPHA * stats.size();
        for (MerchantCategoryStatsEntity e : stats) {
            probs.put(e.category, (e.count + LAPLACE_ALPHA) / total);
        }
        return probs;
    }

    private Map<String, Double> globalProbabilities(List<GlobalCategoryStatsEntity> stats) {
        Map<String, Double> probs = new HashMap<>();
        if (stats == null || stats.isEmpty()) return probs;

        double total = stats.stream().mapToInt(e -> e.count).sum() + LAPLACE_ALPHA * stats.size();
        for (GlobalCategoryStatsEntity e : stats) {
            probs.put(e.category, (e.count + LAPLACE_ALPHA) / total);
        }
        return probs;
    }

    private Map<String, Double> ensembleScore(
            Map<String, Double> merchantProbs, Map<String, Double> globalProbs) {

        Map<String, Double> result = new HashMap<>();
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

    private static <K> Map.Entry<K, Double> argMax(Map<K, Double> map) {
        return map.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
    }

    /** Normalizes transaction type to upper-case INCOME or EXPENSE. */
    private static String normalizeType(String type) {
        if (type == null) return "EXPENSE";
        String upper = type.trim().toUpperCase();
        return "INCOME".equals(upper) ? "INCOME" : "EXPENSE";
    }
}
