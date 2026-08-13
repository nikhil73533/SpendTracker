package com.example.prediction.domain.service;

import com.example.prediction.data.local.entity.PrototypeEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class KNNPredictor {
    private static final int K = 3;

    public PredictionResult predict(float[] features, List<PrototypeEntity> prototypes) {
        if (prototypes == null || prototypes.isEmpty()) {
            return new PredictionResult("Uncategorized", 0.0f);
        }

        PriorityQueue<Neighbor> pq = new PriorityQueue<>((a, b) -> Float.compare(b.distance, a.distance));

        for (PrototypeEntity proto : prototypes) {
            float dist = euclideanDistance(features, proto.vector);
            pq.offer(new Neighbor(proto.category, dist));
            if (pq.size() > K) pq.poll();
        }

        // Vote
        Map<String, Float> votes = new HashMap<>();
        float minDistance = Float.MAX_VALUE;
        String bestCategory = "Uncategorized";

        for (Neighbor n : pq) {
            votes.put(n.category, votes.getOrDefault(n.category, 0.0f) + (1.0f / (n.distance + 0.001f)));
        }

        float maxVote = -1;
        for (Map.Entry<String, Float> entry : votes.entrySet()) {
            if (entry.getValue() > maxVote) {
                maxVote = entry.getValue();
                bestCategory = entry.getKey();
            }
        }

        // Confidence score based on inverse distance
        float confidence = Math.min(1.0f, maxVote / 10.0f); // Heuristic

        return new PredictionResult(bestCategory, confidence);
    }

    private float euclideanDistance(float[] v1, float[] v2) {
        float sum = 0;
        for (int i = 0; i < v1.length; i++) {
            float diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    public static class PredictionResult {
        private final String category;
        private final float confidence;

        public PredictionResult(String category, float confidence) {
            this.category = category;
            this.confidence = confidence;
        }

        public String getCategory() { return category; }
        public float getConfidence() { return confidence; }
    }

    private static class Neighbor {
        final String category;
        final float distance;

        Neighbor(String category, float distance) {
            this.category = category;
            this.distance = distance;
        }
    }
}
