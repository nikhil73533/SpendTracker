package com.example.prediction.domain.service;

import com.example.prediction.data.local.entity.CategoryFeedbackEntity;
import com.example.prediction.domain.model.*;
import java.util.*;

/**
 * Native online multinomial Naive Bayes (binary word counts, uniform class prior),
 * backed by explicit corrections and a latest-confirmed merchant preference.
 * Updates subtract the previous sample before adding its replacement. No retraining pass.
 * Confidence values are conservative decision scores, not calibrated probabilities.
 * The owner serializes all access.
 */
public final class IncrementalCategoryModel {
    private final Map<String, CategoryFeedbackEntity> samples = new HashMap<>();
    private final Map<String, Map<String, Integer>> counts = new HashMap<>();
    private final Map<String, Integer> totals = new HashMap<>();
    private final Map<String, Integer> documents = new HashMap<>();
    private final Map<String, Map<String, Integer>> vocabularyByType = new HashMap<>();
    private final Map<String, NavigableSet<CategoryFeedbackEntity>> merchants = new HashMap<>();

    public void put(CategoryFeedbackEntity sample) {
        remove(sample.id);
        samples.put(sample.id, sample);
        adjust(sample, 1);
        if (!sample.merchant.isEmpty()) merchants.computeIfAbsent(sample.type + "|" + sample.merchant,
                k -> new TreeSet<>(Comparator.comparingLong((CategoryFeedbackEntity f) -> f.updatedAt)
                        .thenComparing(f -> f.id))).add(sample);
    }
    public void remove(String id) {
        CategoryFeedbackEntity old = samples.remove(id);
        if (old == null) return;
        adjust(old, -1);
        NavigableSet<CategoryFeedbackEntity> history = merchants.get(old.type + "|" + old.merchant);
        if (history != null) {
            history.remove(old);
            if (history.isEmpty()) merchants.remove(old.type + "|" + old.merchant);
        }
    }
    private void adjust(CategoryFeedbackEntity sample, int delta) {
        String key = sample.type + "|" + sample.category;
        Map<String, Integer> words = counts.computeIfAbsent(key, k -> new HashMap<>());
        Map<String, Integer> vocabulary = vocabularyByType.computeIfAbsent(sample.type, k -> new HashMap<>());
        for (String token : CategoryText.tokens(sample.tokens)) {
            int count = words.getOrDefault(token, 0) + delta;
            if (count <= 0) words.remove(token); else words.put(token, count);
            totals.put(key, totals.getOrDefault(key, 0) + delta);
            int uses = vocabulary.getOrDefault(token, 0) + delta;
            if (uses <= 0) vocabulary.remove(token); else vocabulary.put(token, uses);
        }
        int size = documents.getOrDefault(key, 0) + delta;
        if (size <= 0) { counts.remove(key); totals.remove(key); documents.remove(key); }
        else documents.put(key, size);
    }
    public int sampleCount() { return samples.size(); }

    public IncrementalPredictionResult predict(PredictionTransaction tx, Collection<String> categories) {
        if (tx == null) return null;
        String type = CategoryText.type(tx.type);
        if ("TRANSFER".equals(type)) return result("Transfer", 1, IncrementalPredictionResult.Source.TRANSFER_RULE);
        if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) return unknown();
        List<String> allowed = new ArrayList<>();
        for (String category : categories) {
            String clean = CategoryText.normalize(category);
            if (!clean.isEmpty() && !clean.equals("uncategorized") && !clean.equals("pending")) allowed.add(category);
        }
        Collections.sort(allowed);
        String merchant = CategoryText.merchant(tx);
        NavigableSet<CategoryFeedbackEntity> memory = merchants.get(type + "|" + merchant);
        CategoryFeedbackEntity latest = null;
        if (!merchant.isEmpty() && memory != null) for (CategoryFeedbackEntity sample : memory.descendingSet()) {
            if (allowed.contains(sample.category)) { latest = sample; break; }
        }
        if (latest != null) return result(latest.category, .94, IncrementalPredictionResult.Source.MERCHANT_HISTORY);

        // Useful description context (e.g. "Amazon refund") is available to rules and learning.
        Map<String, Double> seeds = ColdStartCategories.scores(
                tx.merchantName + " " + tx.description + " " + CategoryText.features(tx), type, allowed);
        Map<String, Double> learned = tokenScores(CategoryText.tokens(CategoryText.features(tx)), type, allowed);
        if (!learned.isEmpty()) {
            Map.Entry<String, Double> best = best(learned);
            if (best.getValue() >= .70) return new IncrementalPredictionResult(best.getKey(), best.getValue(),
                    IncrementalPredictionResult.Source.TOKEN_MATCH, learned, false);
        }
        if (seeds.size() == 1) return result(seeds.keySet().iterator().next(), .82, IncrementalPredictionResult.Source.TOKEN_MATCH);
        // Conflicting hints and uninformative names are reviewable, never a majority-category guess.
        if (!seeds.isEmpty()) {
            seeds.replaceAll((k, v) -> 1.0 / seeds.size());
            return new IncrementalPredictionResult("Uncategorized", 0, IncrementalPredictionResult.Source.TOKEN_MATCH, seeds, true);
        }
        if (!learned.isEmpty()) {
            Map.Entry<String, Double> best = best(learned);
            return new IncrementalPredictionResult(best.getKey(), best.getValue(), IncrementalPredictionResult.Source.TOKEN_MATCH, learned, true);
        }
        return unknown();
    }
    private Map<String, Double> tokenScores(Set<String> input, String type, List<String> allowed) {
        Map<String, Integer> vocabulary = vocabularyByType.getOrDefault(type, Collections.emptyMap());
        List<String> trained = new ArrayList<>();
        for (String category : allowed) {
            Map<String, Integer> words = counts.get(type + "|" + category);
            if (words != null && !words.isEmpty()) trained.add(category);
        }
        Set<String> overlap = new HashSet<>(input);
        overlap.retainAll(vocabulary.keySet());
        Map<String, Double> scores = new TreeMap<>();
        if (overlap.isEmpty()) return scores;
        double max = -Double.MAX_VALUE;
        for (String category : trained) {
            String key = type + "|" + category;
            double log = 0;
            for (String token : overlap) log += Math.log((counts.get(key).getOrDefault(token, 0) + 1.0)
                    / (totals.get(key) + vocabulary.size()));
            scores.put(category, log);
            max = Math.max(max, log);
        }
        double sum = 0;
        for (String category : trained) { double p = Math.exp(scores.get(category) - max); scores.put(category, p); sum += p; }
        for (String category : trained) {
            String key = type + "|" + category;
            long hits = overlap.stream().filter(t -> counts.get(key).containsKey(t)).count();
            // Require multiple independent confirmed transactions for cross-merchant generalization.
            double support = Math.min(1, documents.get(key) / 2.0);
            double coverage = hits / (double) Math.max(1, input.size());
            scores.put(category, Math.min(.90, scores.get(category) / sum) * support * Math.sqrt(coverage));
        }
        return scores;
    }
    private static Map.Entry<String, Double> best(Map<String, Double> scores) {
        // TreeMap iteration gives reproducible tie breaking.
        Map.Entry<String, Double> top = null;
        for (Map.Entry<String, Double> e : scores.entrySet()) if (top == null || e.getValue() > top.getValue()) top = e;
        return top;
    }
    private static IncrementalPredictionResult result(String category, double confidence, IncrementalPredictionResult.Source source) {
        return new IncrementalPredictionResult(category, confidence, source, Collections.singletonMap(category, confidence), confidence < .70);
    }
    private static IncrementalPredictionResult unknown() {
        return new IncrementalPredictionResult("Uncategorized", 0, IncrementalPredictionResult.Source.GLOBAL_PRIOR, Collections.emptyMap(), true);
    }
}
