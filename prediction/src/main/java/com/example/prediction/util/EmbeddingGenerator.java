package com.example.prediction.util;

public class EmbeddingGenerator {
    private static final int VECTOR_SIZE = 64;

    public float[] generateEmbedding(String text) {
        float[] vector = new float[VECTOR_SIZE];
        if (text == null || text.isEmpty()) return vector;

        text = text.toLowerCase();
        // Simple character n-gram hashing for offline embedding
        for (int i = 0; i < text.length() - 1; i++) {
            String gram = text.substring(i, i + 2);
            int hash = Math.abs(gram.hashCode()) % VECTOR_SIZE;
            vector[hash] += 1.0f;
        }

        // Normalize
        float sum = 0;
        for (float v : vector) sum += v * v;
        float norm = (float) Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < VECTOR_SIZE; i++) vector[i] /= norm;
        }

        return vector;
    }
}
