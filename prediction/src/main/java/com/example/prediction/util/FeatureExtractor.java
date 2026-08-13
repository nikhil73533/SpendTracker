package com.example.prediction.util;

import com.example.prediction.data.local.entity.MerchantStatsEntity;
import com.example.prediction.domain.model.PredictionTransaction;
import java.util.Calendar;

public class FeatureExtractor {
    private final EmbeddingGenerator embeddingGenerator;

    public FeatureExtractor() {
        this.embeddingGenerator = new EmbeddingGenerator();
    }

    public float[] extractFeatures(PredictionTransaction tx, MerchantStatsEntity stats) {
        float[] merchantVec = embeddingGenerator.generateEmbedding(tx.merchantName);
        float[] upiVec = embeddingGenerator.generateEmbedding(tx.upiId);

        // 64 + 64 + 10 = 138 dimensions
        float[] combined = new float[64 + 64 + 10];
        System.arraycopy(merchantVec, 0, combined, 0, 64);
        System.arraycopy(upiVec, 0, combined, 64, 64);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(tx.timestamp);
        
        // Basic features
        combined[128] = (float) Math.log1p(tx.amount) / 10.0f;
        combined[129] = "INCOME".equalsIgnoreCase(tx.type) ? 1.0f : 0.0f;
        combined[130] = (float) cal.get(Calendar.DAY_OF_WEEK) / 7.0f;
        combined[131] = (float) cal.get(Calendar.HOUR_OF_DAY) / 24.0f;

        // Enhanced features
        combined[132] = (float) Math.min(1.0f, tx.merchantName.length() / 30.0f); // Merchant length
        combined[133] = (float) cal.get(Calendar.DAY_OF_MONTH) / 31.0f; // Day of month
        
        int day = cal.get(Calendar.DAY_OF_WEEK);
        combined[134] = (day == Calendar.SATURDAY || day == Calendar.SUNDAY) ? 1.0f : 0.0f; // Weekend
        
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        combined[135] = (hour < 6) ? 0.0f : (hour < 12) ? 0.33f : (hour < 18) ? 0.66f : 1.0f; // Hour Bucket
        
        double amount = tx.amount;
        combined[136] = (amount < 100) ? 0.0f : (amount < 1000) ? 0.33f : (amount < 5000) ? 0.66f : 1.0f; // Amount Bucket

        if (stats != null) {
            combined[137] = (float) Math.log1p(stats.frequency) / 5.0f; // Merchant Frequency
        }

        return combined;
    }

    public float[] extractFeatures(PredictionTransaction tx) {
        return extractFeatures(tx, null);
    }
}
