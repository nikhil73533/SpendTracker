package com.example.spendtracker.billing;

import android.content.Context;
import android.text.TextUtils;
import com.android.billingclient.api.BillingClient;
import com.example.spendtracker.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Central place for Play Console product IDs; blank resources intentionally mean unconfigured. */
public final class BillingProductConfig {
    public static final class ProductSpec {
        public final String productId;
        public final String productType;
        public final PremiumPurchaseType purchaseType;
        public final int titleRes;

        private ProductSpec(String productId, String productType, PremiumPurchaseType purchaseType, int titleRes) {
            this.productId = productId;
            this.productType = productType;
            this.purchaseType = purchaseType;
            this.titleRes = titleRes;
        }
    }

    private BillingProductConfig() { }

    public static List<ProductSpec> configuredProducts(Context context) {
        List<ProductSpec> products = new ArrayList<>();
        addIfConfigured(products, context.getString(R.string.billing_product_monthly), BillingClient.ProductType.SUBS,
                PremiumPurchaseType.SUBSCRIPTION, R.string.premium_monthly);
        addIfConfigured(products, context.getString(R.string.billing_product_yearly), BillingClient.ProductType.SUBS,
                PremiumPurchaseType.SUBSCRIPTION, R.string.premium_yearly);
        addIfConfigured(products, context.getString(R.string.billing_product_lifetime), BillingClient.ProductType.INAPP,
                PremiumPurchaseType.LIFETIME, R.string.premium_lifetime);
        return Collections.unmodifiableList(products);
    }

    private static void addIfConfigured(List<ProductSpec> products, String id, String type,
                                        PremiumPurchaseType purchaseType, int titleRes) {
        if (!TextUtils.isEmpty(id) && !id.startsWith("REPLACE_")) {
            products.add(new ProductSpec(id.trim(), type, purchaseType, titleRes));
        }
    }

    public static ProductSpec find(Context context, String productId) {
        for (ProductSpec spec : configuredProducts(context)) {
            if (spec.productId.equals(productId)) return spec;
        }
        return null;
    }
}
