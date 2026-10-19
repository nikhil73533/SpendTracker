package com.example.spendtracker.billing.autopay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Standard prepaid checkout: no mandate and no automatic renewal. */
public final class PaymentCheckoutOptions {
    private PaymentCheckoutOptions() { }
    public static void configure(JSONObject options, String orderId, long amount, String currency) throws JSONException {
        if (orderId == null || !orderId.startsWith("order_") || orderId.length() <= 6
                || amount <= 0 || !"INR".equals(currency)) throw new JSONException("Invalid payment order");
        options.remove("subscription_id");
        options.remove("recurring");
        options.put("order_id", orderId).put("amount", amount).put("currency", currency);
        options.put("config", new JSONObject().put("display", new JSONObject()
                .put("blocks", new JSONObject().put("upi", new JSONObject().put("name", "Pay with UPI")
                        .put("instruments", new JSONArray().put(new JSONObject().put("method", "upi")))))
                .put("sequence", new JSONArray().put("block.upi"))
                .put("preferences", new JSONObject().put("show_default_blocks", true))));
    }
}
