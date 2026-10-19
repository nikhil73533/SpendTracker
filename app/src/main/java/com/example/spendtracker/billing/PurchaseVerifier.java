package com.example.spendtracker.billing;

import com.android.billingclient.api.Purchase;

/** Seam for replacing local checks with server-side Play Developer API verification later. */
public interface PurchaseVerifier {
    boolean isVerified(Purchase purchase, BillingProductConfig.ProductSpec spec);
}
