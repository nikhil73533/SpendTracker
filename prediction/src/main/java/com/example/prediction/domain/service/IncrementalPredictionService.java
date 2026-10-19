package com.example.prediction.domain.service;

import android.content.Context;
import com.example.prediction.data.local.PredictionDatabase;
import com.example.prediction.data.local.entity.CategoryFeedbackEntity;
import com.example.prediction.data.local.entity.MerchantCategoryStatsEntity;
import com.example.prediction.domain.model.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** Shared offline model. Call predict/learn/reset from a worker thread (Room disk access). */
public class IncrementalPredictionService {
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static IncrementalCategoryModel model;
    private static PredictionDatabase modelDatabase;
    private static Map<String, MerchantCategoryStatsEntity> legacyMemory;
    private static long lastFeedbackTime;
    private final PredictionDatabase db;
    private final Function<String, List<String>> categoryProvider;
    private static final List<String> EXPENSE = Arrays.asList("Food", "Transport", "Education", "Health", "Shopping", "Rent", "Other");
    private static final List<String> INCOME = Arrays.asList("Salary", "Gift", "Allowance", "Bonus", "Other Income");

    public IncrementalPredictionService(Context context) {
        this(context, type -> "INCOME".equals(type) ? INCOME : EXPENSE);
    }
    public IncrementalPredictionService(Context context, Function<String, List<String>> categoryProvider) {
        this(PredictionDatabase.getDatabase(context), categoryProvider);
    }
    IncrementalPredictionService(PredictionDatabase database, Function<String, List<String>> categoryProvider) {
        this.db = database;
        this.categoryProvider = categoryProvider;
    }
    private void load() {
        if (model != null && modelDatabase == db) return;
        IncrementalCategoryModel loaded = new IncrementalCategoryModel();
        long latestTime = 0;
        for (CategoryFeedbackEntity feedback : db.categoryFeedbackDao().getAll()) {
            loaded.put(feedback);
            latestTime = Math.max(latestTime, feedback.updatedAt);
        }
        legacyMemory = new HashMap<>();
        for (MerchantCategoryStatsEntity row : db.merchantCategoryStatsDao().getAll()) {
            String key = row.transactionType + "|" + row.merchantKey;
            MerchantCategoryStatsEntity previous = legacyMemory.get(key);
            if (row.count > 0 && (previous == null || row.lastSeenMs > previous.lastSeenMs)) legacyMemory.put(key, row);
        }
        modelDatabase = db;
        model = loaded;
        lastFeedbackTime = latestTime;
    }
    public IncrementalPredictionResult predict(PredictionTransaction tx) {
        if (tx == null) return null;
        // Read application categories outside LOCK to avoid lock inversion with category mutations.
        List<String> allowed = categoryProvider.apply(CategoryText.type(tx.type));
        synchronized (LOCK) {
            load();
            IncrementalPredictionResult result = model.predict(tx, allowed);
            // Keep existing v4 merchant corrections usable without treating them as token training.
            if (result.getSource() != IncrementalPredictionResult.Source.MERCHANT_HISTORY
                    && !"TRANSFER".equals(CategoryText.type(tx.type))) {
                String key = com.example.prediction.util.MerchantNormalizer.normalize(tx.merchantName);
                if (!CategoryText.merchant(tx).isEmpty()) {
                    MerchantCategoryStatsEntity latest = legacyMemory.get(CategoryText.type(tx.type) + "|" + key);
                    if (latest != null && allowed.contains(latest.category)) return new IncrementalPredictionResult(latest.category, .85,
                            IncrementalPredictionResult.Source.MERCHANT_HISTORY,
                            Collections.singletonMap(latest.category, .85), false);
                }
            }
            return result;
        }
    }
    /** Stable transaction ID makes repeat saves idempotent and corrections replaceable. */
    public void learn(String transactionId, PredictionTransaction tx, String category) {
        if (transactionId == null || transactionId.isBlank() || tx == null) return;
        String type = CategoryText.type(tx.type);
        List<String> allowed = categoryProvider.apply(type);
        String resolved = category == null ? null : ColdStartCategories.resolve(category, allowed);
        synchronized (LOCK) {
            load();
            if (resolved == null || "TRANSFER".equals(type) || "Uncategorized".equalsIgnoreCase(resolved)) {
                db.categoryFeedbackDao().delete(transactionId);
                model.remove(transactionId);
                return;
            }
            CategoryFeedbackEntity feedback = new CategoryFeedbackEntity();
            feedback.id = transactionId;
            feedback.type = type;
            feedback.category = resolved;
            feedback.merchant = CategoryText.merchant(tx);
            feedback.tokens = CategoryText.features(tx);
            feedback.updatedAt = Math.max(System.currentTimeMillis(), lastFeedbackTime + 1);
            db.categoryFeedbackDao().put(feedback); // Persist first; failed writes cannot change memory.
            lastFeedbackTime = feedback.updatedAt;
            model.put(feedback);
        }
    }
    /** Compatibility entry point; app corrections should always supply their database ID. */
    public void learn(PredictionTransaction tx, String category) {
        if (tx != null) learn("legacy-call:" + tx.timestamp + ":" + tx.amount + ":" +
                CategoryText.type(tx.type) + ":" + CategoryText.merchant(tx), tx, category);
    }
    public void learnAsync(PredictionTransaction tx, String category) {
        EXECUTOR.execute(() -> learn(tx, category));
    }
    public void renameCategory(String type, String oldName, String newName) {
        synchronized (LOCK) {
            load();
            db.runInTransaction(() -> {
                for (CategoryFeedbackEntity feedback : db.categoryFeedbackDao().getAll()) {
                    if (feedback.type.equals(type) && feedback.category.equals(oldName)) {
                        if (newName == null) db.categoryFeedbackDao().delete(feedback.id);
                        else { feedback.category = newName; db.categoryFeedbackDao().put(feedback); }
                    }
                }
                // Legacy rows cannot be updated in place because their primary key includes the name.
                List<MerchantCategoryStatsEntity> legacy = db.merchantCategoryStatsDao().getAll();
                db.merchantCategoryStatsDao().deleteAll();
                for (MerchantCategoryStatsEntity row : legacy) {
                    if (row.transactionType.equals(type) && row.category.equals(oldName)) {
                        if (newName == null) continue;
                        row.category = newName;
                        row.id = row.merchantKey + "|" + type + "|" + newName;
                    }
                    db.merchantCategoryStatsDao().insert(row);
                }
            });
            model = null;
            load();
        }
    }
    public void resetAllData() {
        synchronized (LOCK) {
            db.runInTransaction(() -> {
                db.categoryFeedbackDao().deleteAll();
                db.merchantCategoryStatsDao().deleteAll();
                db.globalCategoryStatsDao().deleteAll();
                db.prototypeDao().deleteAll();
                db.merchantStatsDao().deleteAll();
            });
            model = new IncrementalCategoryModel();
            modelDatabase = db;
            legacyMemory = new HashMap<>();
            lastFeedbackTime = 0;
        }
    }
}
