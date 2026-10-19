package com.example.spendtracker.billing.autopay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** UPI is preferred; Razorpay owns merchant/bank/app eligibility and mandate authorization. */
public final class SubscriptionCheckoutOptions {
    private SubscriptionCheckoutOptions() { }

    public static void configure(JSONObject options, String subscriptionId) throws JSONException {
        if (subscriptionId == null || !subscriptionId.startsWith("sub_") || subscriptionId.length() <= 4) {
            throw new JSONException("Missing server subscription reference");
        }
        options.put("subscription_id", subscriptionId);
        options.put("recurring", "1");
        options.put("config", new JSONObject().put("display", new JSONObject()
                .put("blocks", new JSONObject().put("upi_autopay", new JSONObject()
                        .put("name", "UPI AutoPay")
                        .put("instruments", new JSONArray().put(new JSONObject().put("method", "upi")))))
                .put("sequence", new JSONArray().put("block.upi_autopay"))
                .put("preferences", new JSONObject().put("show_default_blocks", true))));
    }
}
