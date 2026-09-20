package com.example.prediction.domain.service;

import com.example.prediction.domain.model.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class ColdStartBenchmarkTest {
    @Test public void coldStartClearsSixtyPercentOnDocumentedDevelopmentCorpus() throws Exception {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        List<String> expense = Arrays.asList("Food 🍔", "Transport 🚗", "Health 🏥", "Education 📚", "Shopping 🛍️", "Rent 🏠", "Other ✨");
        List<String> income = Arrays.asList("Salary 💰", "Allowance 💸", "Bonus 🏅", "Gift 🎁", "Other (Inc) 🧧");
        int total = 0, correct = 0, assigned = 0, baselineCorrect = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/category_cold_start.tsv"), StandardCharsets.UTF_8))) {
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.startsWith("#") || row.isBlank()) continue;
                String[] fields = row.split("\\|", -1);
                PredictionTransaction tx = new PredictionTransaction(fields[1], "", 500, fields[0], 0, fields[2]);
                IncrementalPredictionResult result = model.predict(tx, "INCOME".equals(fields[0]) ? income : expense);
                total++;
                if (!result.needsUserConfirmation()) {
                    assigned++;
                    if (CategoryText.normalize(result.getCategory()).equals(CategoryText.normalize(fields[3]))) correct++;
                }
                // Previous empty model returned Other/Other Income, always marked for review.
                if (fields[3].equals("INCOME".equals(fields[0]) ? "Other Inc" : "Other")) baselineCorrect++;
            }
        }
        System.out.printf(Locale.ROOT, "Cold start: %d/%d correct (%.1f%%); assigned %d; precision %.1f%%; legacy top-label %d/%d; legacy auto-assignment 0%%.%n",
                correct, total, 100.0 * correct / total, assigned, 100.0 * correct / assigned, baselineCorrect, total);
        assertTrue("Include ambiguous cases and multiple directions", total >= 70);
        assertTrue("Abstentions count as misses in the 60% goal", correct / (double) total >= .60);
        assertTrue("Avoid raising coverage through wrong assignments", correct / (double) assigned >= .90);
        assertEquals("Inference must never train on its own labels", 0, model.sampleCount());
    }
}
