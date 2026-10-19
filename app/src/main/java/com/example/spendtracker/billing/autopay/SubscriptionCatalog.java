package com.example.spendtracker.billing.autopay;

import com.example.spendtracker.billing.PremiumPlan;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Server-owned prices with deterministic period ordering and a separate purchase gate. */
public final class SubscriptionCatalog {
    private static final String[] IDS = {"monthly", "quarterly", "six_monthly", "yearly"};
    private static final int[] INTERVALS = {1, 3, 6, 1};
    public final List<PremiumPlan> plans;
    public final boolean complete;
    public final boolean checkoutEnabled;
    private final Map<String, String> versions;

    private SubscriptionCatalog(List<PremiumPlan> plans, Map<String, String> versions, boolean enabled) {
        this.plans = Collections.unmodifiableList(plans);
        this.versions = versions;
        complete = plans.size() == IDS.length;
        checkoutEnabled = enabled && complete;
    }

    public String versionFor(String id) { return versions.get(id); }

    public static SubscriptionCatalog parse(JSONObject catalog) throws JSONException {
        String version = catalog.getString("version");
        JSONArray items = catalog.getJSONArray("plans");
        Map<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.getString("id");
            if (byId.put(id, item) != null) throw new JSONException("Duplicate billing period");
        }
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        currency.setCurrency(Currency.getInstance("INR"));
        List<PremiumPlan> plans = new ArrayList<>();
        Map<String, String> versions = new HashMap<>();
        for (int i = 0; i < IDS.length; i++) {
            JSONObject item = byId.get(IDS[i]);
            if (item == null) continue;
            if (!"INR".equals(item.getString("currency")) || item.getLong("amount") <= 0
                    || item.getInt("interval") != INTERVALS[i]
                    || !item.getString("period").equals(i == 3 ? "yearly" : "monthly")
                    || item.getString("title").trim().isEmpty()) {
                throw new JSONException("Invalid billing period or price");
            }
            String period = i == 3 ? "1 year" : i == 0 ? "1 month" : INTERVALS[i] + " months";
            plans.add(new PremiumPlan(IDS[i], item.getString("title"),
                    currency.format(item.getLong("amount") / 100.0), "Prepaid for " + period + ". No automatic renewal.", ""));
            versions.put(IDS[i], item.has("catalog_version") ? String.valueOf(item.getLong("catalog_version")) : version);
        }
        return new SubscriptionCatalog(plans, versions, catalog.optBoolean("checkout_enabled"));
    }
}
