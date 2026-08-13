package com.example.prediction.domain.service;

import android.content.Context;
import com.example.prediction.data.local.PredictionDatabase;
import com.example.prediction.data.local.entity.MerchantStatsEntity;
import com.example.prediction.data.local.entity.PrototypeEntity;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.util.FeatureExtractor;
import com.example.prediction.util.PredictionLogger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PredictionService {
    private final PredictionDatabase db;
    private final FeatureExtractor featureExtractor;
    private final KNNPredictor knnPredictor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PredictionService(Context context) {
        this.db = PredictionDatabase.getDatabase(context);
        this.featureExtractor = new FeatureExtractor();
        this.knnPredictor = new KNNPredictor();
    }

    public KNNPredictor.PredictionResult predict(PredictionTransaction tx) {
        PredictionLogger.log("Predicting category for: " + tx.merchantName + " (Type: " + tx.type + ")");
        MerchantStatsEntity stats = db.merchantStatsDao().getStatsForMerchant(tx.merchantName);
        float[] features = featureExtractor.extractFeatures(tx, stats);
        
        // Use type-specific pipeline
        List<PrototypeEntity> prototypes = db.prototypeDao().getPrototypesByType(tx.type);
        if (prototypes.isEmpty()) {
            // Fallback to all if no specific ones yet (bootstrap)
            prototypes = db.prototypeDao().getAllPrototypes();
        }
        
        KNNPredictor.PredictionResult result = knnPredictor.predict(features, prototypes);
        PredictionLogger.log("Predicted: " + result.getCategory() + " (" + (int)(result.getConfidence() * 100) + "%)");
        return result;
    }

    public List<KNNPredictor.PredictionResult> batchPredict(List<PredictionTransaction> transactions) {
        List<KNNPredictor.PredictionResult> results = new ArrayList<>();
        // In-memory cache for prototypes by type to avoid repeated DB calls in batch
        Map<String, List<PrototypeEntity>> protoCache = new HashMap<>();
        
        for (PredictionTransaction tx : transactions) {
            if (!protoCache.containsKey(tx.type)) {
                List<PrototypeEntity> protos = db.prototypeDao().getPrototypesByType(tx.type);
                if (protos.isEmpty()) protos = db.prototypeDao().getAllPrototypes();
                protoCache.put(tx.type, protos);
            }
            
            MerchantStatsEntity stats = db.merchantStatsDao().getStatsForMerchant(tx.merchantName);
            float[] features = featureExtractor.extractFeatures(tx, stats);
            results.add(knnPredictor.predict(features, protoCache.get(tx.type)));
        }
        return results;
    }

    public void learn(PredictionTransaction tx, String actualCategory) {
        executor.execute(() -> {
            PredictionLogger.log("Learning from manual correction: " + actualCategory);
            
            // 1. Update Merchant Stats
            MerchantStatsEntity stats = db.merchantStatsDao().getStatsForMerchant(tx.merchantName);
            if (stats == null) {
                stats = new MerchantStatsEntity(tx.merchantName);
                stats.frequency = 1;
                stats.totalAmount = tx.amount;
                stats.averageAmount = tx.amount;
                stats.lastCategory = actualCategory;
                stats.preferredCategory = actualCategory;
                stats.lastTransactionDate = tx.timestamp;
                db.merchantStatsDao().insert(stats);
            } else {
                stats.frequency++;
                stats.totalAmount += tx.amount;
                stats.averageAmount = stats.totalAmount / stats.frequency;
                stats.lastCategory = actualCategory;
                stats.lastTransactionDate = tx.timestamp;
                // Simplified preferred category: just use the last one for now or track counts
                stats.preferredCategory = actualCategory; 
                db.merchantStatsDao().update(stats);
            }

            // 2. Create Prototype
            float[] features = featureExtractor.extractFeatures(tx, stats);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(tx.timestamp);
            
            PrototypeEntity proto = new PrototypeEntity(
                actualCategory,
                features,
                tx.merchantName,
                tx.upiId,
                tx.amount,
                tx.type,
                cal.get(Calendar.DAY_OF_WEEK),
                cal.get(Calendar.HOUR_OF_DAY)
            );
            db.prototypeDao().insert(proto);
            PredictionLogger.log("Model updated with new prototype and merchant stats");
        });
    }

    public void resetModel() {
        executor.execute(() -> {
            db.clearAllTables();
            PredictionLogger.log("ML Model reset: All prototypes and stats cleared.");
        });
    }
}
