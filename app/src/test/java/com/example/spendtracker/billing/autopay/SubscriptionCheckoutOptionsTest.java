package com.example.spendtracker.billing.autopay;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubscriptionCheckoutOptionsTest {
    @Test public void nativeMandateUsesSubscriptionAndKeepsEligibleFallbacks() throws Exception {
        JSONObject options = new JSONObject();
        SubscriptionCheckoutOptions.configure(options, "sub_serverReference");
        assertEquals("sub_serverReference", options.getString("subscription_id"));
        assertFalse(options.has("order_id"));
        assertFalse(options.has("amount")); // server plan owns recurring price
        JSONObject display = options.getJSONObject("config").getJSONObject("display");
        assertEquals("block.upi_autopay", display.getJSONArray("sequence").getString(0));
        assertEquals("upi", display.getJSONObject("blocks").getJSONObject("upi_autopay")
                .getJSONArray("instruments").getJSONObject(0).getString("method"));
        assertTrue(display.getJSONObject("preferences").getBoolean("show_default_blocks"));
    }
    @Test(expected = JSONException.class) public void ordinaryOrderCannotBecomeAMandate() throws Exception {
        SubscriptionCheckoutOptions.configure(new JSONObject(), "order_notAMandate");
    }
}
