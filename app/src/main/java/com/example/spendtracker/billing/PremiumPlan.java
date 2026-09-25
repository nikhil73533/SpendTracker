package com.example.spendtracker.billing;

/** Display-only product data derived from fresh ProductDetails responses. */
public final class PremiumPlan {
    public final String productId;
    public final String title;
    public final String price;
    public final String period;
    public final String offerToken;

    public PremiumPlan(String productId, String title, String price, String period, String offerToken) {
        this.productId = productId;
        this.title = title;
        this.price = price;
        this.period = period;
        this.offerToken = offerToken;
    }
}
