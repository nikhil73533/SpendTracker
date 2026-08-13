package com.example.prediction.domain.model;

public class PredictionResult {
    private final String category;
    private final double confidence;

    public PredictionResult(String category, double confidence) {
        this.category = category;
        this.confidence = confidence;
    }

    public String getCategory() { return category; }
    public double getConfidence() { return confidence; }
}
