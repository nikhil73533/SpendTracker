package com.example.prediction.domain.service;

import com.example.prediction.domain.model.TransactionFeatures;
import com.example.prediction.domain.model.IncrementalPredictionResult;

/**
 * Interface for the online/incremental learning model.
 * Implementations must support per-sample learning without full retraining.
 */
public interface OnlineLearningModel {

    /**
     * Predict the category for the given features.
     * Returns the best matching category with a confidence score.
     */
    IncrementalPredictionResult predict(TransactionFeatures features);

    /**
     * Update the model with a confirmed correct label.
     * This is called ONLY after explicit user confirmation.
     *
     * @param features extracted features for the transaction
     * @param category the user-confirmed correct category
     */
    void learn(TransactionFeatures features, String category);

    /** Persist model state to local storage. */
    void save();

    /** Load previously saved model state from local storage. */
    void load();
}
