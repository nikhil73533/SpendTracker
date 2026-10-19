package com.example.spendtracker.billing;

import com.android.billingclient.api.Purchase;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LocalPurchaseVerifier implements PurchaseVerifier {
    @Inject public LocalPurchaseVerifier() { }

    @Override public boolean isVerified(Purchase purchase, BillingProductConfig.ProductSpec spec) {
        return purchase != null && spec != null
                && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                && purchase.getProducts().contains(spec.productId);
    }
}
