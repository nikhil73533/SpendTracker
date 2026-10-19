package com.example.spendtracker.billing.autopay;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubscriptionCatalogTest {
    private JSONObject item(String id, int interval, long amount) throws Exception {
        return new JSONObject().put("id", id).put("title", id).put("currency", "INR")
                .put("period", id.equals("yearly") ? "yearly" : "monthly")
                .put("interval", interval).put("amount", amount).put("catalog_version", 1);
    }
    private JSONObject catalog() throws Exception {
        return new JSONObject().put("version", "2").put("checkout_enabled", true).put("plans", new JSONArray()
                .put(item("yearly", 1, 49900)).put(item("monthly", 1, 9900))
                .put(item("six_monthly", 6, 29900)).put(item("quarterly", 3, 14900)));
    }
    @Test public void allFourPeriodsSurviveParsingInDurationOrder() throws Exception {
        SubscriptionCatalog result = SubscriptionCatalog.parse(catalog());
        assertEquals(4, result.plans.size());
        assertEquals("monthly", result.plans.get(0).productId);
        assertEquals("quarterly", result.plans.get(1).productId);
        assertEquals("six_monthly", result.plans.get(2).productId);
        assertEquals("yearly", result.plans.get(3).productId);
        assertTrue(result.checkoutEnabled);
        assertTrue(result.plans.get(2).period.contains("Prepaid for 6 months"));
        assertEquals("1", result.versionFor("monthly")); // another plan's update must not invalidate this plan
    }
    @Test public void disabledCheckoutStillShowsAllPublishedPrices() throws Exception {
        SubscriptionCatalog result = SubscriptionCatalog.parse(catalog().put("checkout_enabled", false));
        assertEquals(4, result.plans.size());
        assertFalse(result.checkoutEnabled);
    }
    @Test public void monthlyOnlyCatalogCannotStartCheckout() throws Exception {
        SubscriptionCatalog result = SubscriptionCatalog.parse(catalog().put("plans",
                new JSONArray().put(item("monthly", 1, 9900))));
        assertEquals(1, result.plans.size());
        assertFalse(result.complete);
        assertFalse(result.checkoutEnabled);
    }
    @Test(expected = JSONException.class) public void duplicatePeriodsAreRejected() throws Exception {
        JSONObject value = catalog();
        value.getJSONArray("plans").put(item("monthly", 1, 9900));
        SubscriptionCatalog.parse(value);
    }
    @Test(expected = JSONException.class) public void incorrectPeriodCannotChargeAsAnotherPeriod() throws Exception {
        JSONObject value = catalog();
        value.getJSONArray("plans").getJSONObject(2).put("interval", 1);
        SubscriptionCatalog.parse(value);
    }
    @Test(expected = JSONException.class) public void incorrectCurrencyIsRejected() throws Exception {
        JSONObject value = catalog();
        value.getJSONArray("plans").getJSONObject(0).put("currency", "USD");
        SubscriptionCatalog.parse(value);
    }
    @Test public void olderBackendGlobalVersionStillWorks() throws Exception {
        JSONObject value = catalog();
        value.getJSONArray("plans").getJSONObject(0).remove("catalog_version");
        assertEquals("2", SubscriptionCatalog.parse(value).versionFor("yearly"));
    }
}
