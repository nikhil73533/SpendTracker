package com.example.spendtracker.billing.autopay;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PaymentCheckoutOptionsTest {
    @Test public void usesOrderAndKeepsUpiAndDefaultMethods() throws Exception {
        JSONObject options = new JSONObject().put("subscription_id", "sub_old").put("recurring", "1");
        PaymentCheckoutOptions.configure(options, "order_123", 19900, "INR");
        assertEquals("order_123", options.getString("order_id"));
        assertFalse(options.has("subscription_id")); assertFalse(options.has("recurring"));
        JSONObject display = options.getJSONObject("config").getJSONObject("display");
        assertTrue(display.getJSONObject("preferences").getBoolean("show_default_blocks"));
        assertEquals("upi", display.getJSONObject("blocks").getJSONObject("upi").getJSONArray("instruments").getJSONObject(0).getString("method"));
    }
    @Test(expected=org.json.JSONException.class) public void rejectsSubscriptionReference() throws Exception {
        PaymentCheckoutOptions.configure(new JSONObject(), "sub_123", 19900, "INR");
    }
}
