package com.example.prediction.domain.service;

import com.example.prediction.data.local.entity.CategoryFeedbackEntity;
import com.example.prediction.domain.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class IncrementalCategoryModelTest {
    private static final List<String> EXPENSE = Arrays.asList("Food 🍔", "Transport 🚗", "Health 🏥", "Education 📚", "Shopping 🛍️", "Rent 🏠", "Other ✨", "Pets", "Work");
    private static final List<String> INCOME = Arrays.asList("Salary 💰", "Allowance 💸", "Bonus 🏅", "Gift 🎁", "Other (Inc) 🧧");
    private PredictionTransaction tx(String merchant, String type) { return new PredictionTransaction(merchant, "", 250, type, 100); }
    private CategoryFeedbackEntity sample(String id, String merchant, String type, String category, long time) {
        PredictionTransaction tx = tx(merchant, type);
        CategoryFeedbackEntity sample = new CategoryFeedbackEntity();
        sample.id = id; sample.merchant = CategoryText.merchant(tx); sample.tokens = CategoryText.features(tx);
        sample.type = type; sample.category = category; sample.updatedAt = time;
        return sample;
    }
    @Test public void transferCorrectionsArePredictableWithinTheirOriginalDirection() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        List<String> categories = Arrays.asList("Salary", "Food", "Transfer");
        model.put(sample("credit", "Family account", "INCOME", "Transfer", 1));
        assertEquals("Transfer", model.predict(tx("Family account", "INCOME"), categories).getCategory());
        assertFalse(model.predict(tx("Family account", "INCOME"), categories).needsUserConfirmation());
        assertTrue(model.predict(tx("Family account", "EXPENSE"), categories).needsUserConfirmation());
    }
    @Test public void firstCorrectionOverridesSeedAndOldMerchantMajority() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        assertEquals("Food 🍔", model.predict(tx("Zomato", "EXPENSE"), EXPENSE).getCategory());
        for (int i = 0; i < 10; i++) model.put(sample("old" + i, "Zomato", "EXPENSE", "Food 🍔", i));
        model.put(sample("new", "Zomato", "EXPENSE", "Work", 20));
        assertEquals("Work", model.predict(tx("Zomato Pvt Ltd", "EXPENSE"), EXPENSE).getCategory());
    }
    @Test public void repeatSaveReplacesOldLabelRatherThanCountingTwice() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        for (int i = 0; i < 100; i++) model.put(sample("tx1", "Chintu", "EXPENSE", "Food 🍔", i));
        model.put(sample("tx1", "Chintu", "EXPENSE", "Pets", 101));
        assertEquals(1, model.sampleCount());
        assertEquals("Pets", model.predict(tx("Chintu", "EXPENSE"), EXPENSE).getCategory());
        model.remove("tx1");
        assertTrue(model.predict(tx("Chintu", "EXPENSE"), EXPENSE).needsUserConfirmation());
    }
    @Test public void learnsUnseenMerchantFromTwoConfirmedTextExamples() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        PredictionTransaction unseen = tx("Happy paws grooming", "EXPENSE");
        assertTrue(model.predict(unseen, EXPENSE).needsUserConfirmation());
        model.put(sample("1", "Happy paws care", "EXPENSE", "Pets", 1));
        model.put(sample("2", "Happy paws grooming centre", "EXPENSE", "Pets", 2));
        IncrementalPredictionResult learned = model.predict(unseen, EXPENSE);
        assertEquals("Pets", learned.getCategory());
        assertFalse(learned.needsUserConfirmation());
    }
    @Test public void oneTrainingCategoryDoesNotMakeUnrelatedMerchantsCertain() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        model.put(sample("1", "Happy paws care", "EXPENSE", "Pets", 1));
        assertTrue(model.predict(tx("Happy motors", "EXPENSE"), EXPENSE).needsUserConfirmation());
        assertEquals("Uncategorized", model.predict(tx("Ramesh", "EXPENSE"), EXPENSE).getCategory());
    }
    @Test public void incomeAndExpenseNeverShareLabels() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        model.put(sample("1", "Acme", "INCOME", "Salary 💰", 1));
        assertEquals("Salary 💰", model.predict(tx("Acme", "INCOME"), INCOME).getCategory());
        assertEquals("Uncategorized", model.predict(tx("Acme", "EXPENSE"), EXPENSE).getCategory());
        assertEquals("Other (Inc) 🧧", model.predict(new PredictionTransaction("Amazon", "", 500, "INCOME", 1, "Refund"), INCOME).getCategory());
    }
    @Test public void blankMerchantsDoNotShareMemory() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        model.put(sample("1", "", "EXPENSE", "Food 🍔", 1));
        model.put(sample("2", "9999999999", "EXPENSE", "Food 🍔", 2));
        assertTrue(model.predict(tx("", "EXPENSE"), EXPENSE).needsUserConfirmation());
        assertTrue(model.predict(tx("UPI", "EXPENSE"), EXPENSE).needsUserConfirmation());
        model.put(sample("3", "Unknown Merchant", "EXPENSE", "Food 🍔", 3));
        assertTrue(model.predict(tx("Unknown Merchant", "EXPENSE"), EXPENSE).needsUserConfirmation());
    }
    @Test public void upiPayeeIsFallbackIdentityButProviderIsNotAFeature() {
        PredictionTransaction tx = new PredictionTransaction("", "swiggy.12345@okicici", 200, "EXPENSE", 1);
        assertEquals("Food 🍔", new IncrementalCategoryModel().predict(tx, EXPENSE).getCategory());
        assertFalse(CategoryText.features(tx).contains("okicici"));
        PredictionTransaction placeholder = new PredictionTransaction("Unknown Merchant", "swiggy@okicici", 200, "EXPENSE", 1);
        assertEquals("Food 🍔", new IncrementalCategoryModel().predict(placeholder, EXPENSE).getCategory());
        assertEquals("swiggy", CategoryText.merchant(placeholder));
    }
    @Test public void deletedCategoriesCannotBePredicted() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        model.put(sample("1", "Zomato", "EXPENSE", "Work", 1));
        assertEquals("Food 🍔", model.predict(tx("Zomato", "EXPENSE"), Collections.singletonList("Food 🍔")).getCategory());
        assertEquals("Uncategorized", model.predict(tx("Zomato", "EXPENSE"), Collections.emptyList()).getCategory());
    }
    @Test public void descriptionHasUsefulContextAndBoundedPrivateDataFeatures() {
        PredictionTransaction tx = new PredictionTransaction("Acme", "", 12000, "INCOME", 1,
                "INR 12,000 credited to A/C XX7788 SALARY NEFT REF 9898123322 balance 90000");
        assertEquals("Salary 💰", new IncrementalCategoryModel().predict(tx, INCOME).getCategory());
        String features = CategoryText.features(tx);
        assertFalse(features.matches(".*[0-9].*"));
        assertFalse(features.contains("neft"));
    }
    @Test public void conflictingHintsAndSubstringMatchesAbstain() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        assertTrue(model.predict(tx("School Hospital", "EXPENSE"), EXPENSE).needsUserConfirmation());
        assertEquals("Uncategorized", model.predict(tx("Scholar Manish", "EXPENSE"), EXPENSE).getCategory());
        assertEquals("Uncategorized", model.predict(tx("GIFTED CAPITAL", "INCOME"), INCOME).getCategory());
    }
    @Test public void transferBypassesLearningAndNullIsSafe() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        assertNull(model.predict(null, EXPENSE));
        assertEquals("Transfer", model.predict(tx("Zomato", "TRANSFER"), EXPENSE).getCategory());
    }
    @Test public void changingDirectionOfSameSampleRetractsIncomeTraining() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        model.put(sample("1", "Acme", "INCOME", "Salary 💰", 1));
        model.put(sample("1", "Acme", "EXPENSE", "Work", 2));
        assertEquals("Uncategorized", model.predict(tx("Acme", "INCOME"), INCOME).getCategory());
        assertEquals("Work", model.predict(tx("Acme", "EXPENSE"), EXPENSE).getCategory());
    }
    @Test public void devanagariMerchantLearnsAfterOneCorrection() {
        IncrementalCategoryModel model = new IncrementalCategoryModel();
        model.put(sample("1", "शर्मा भोजनालय", "EXPENSE", "Food 🍔", 1));
        assertEquals("Food 🍔", model.predict(tx("शर्मा भोजनालय", "EXPENSE"), EXPENSE).getCategory());
        assertTrue(model.predict(tx("कुमार वस्त्रालय", "EXPENSE"), EXPENSE).needsUserConfirmation());
    }
}
