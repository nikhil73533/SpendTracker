package com.example.spendtracker.billing;

import android.app.Activity;
import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Owns the sole Play Billing client and never persists ProductDetails or purchase tokens. */
@Singleton
public class BillingManager implements PurchasesUpdatedListener {
    public enum State { IDLE, CONNECTING, READY, UNAVAILABLE, CONFIGURATION_MISSING }

    public interface Listener {
        void onVerifiedPurchase(BillingProductConfig.ProductSpec spec, String purchaseToken);
        void onPendingPurchase();
        void onPurchaseFailure(int responseCode);
        void onRestoreFinished(boolean succeeded, boolean hasEntitlement);
    }

    private final Context context;
    private final PurchaseVerifier verifier;
    private final BillingClient client;
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final MutableLiveData<List<PremiumPlan>> plans = new MutableLiveData<>(Collections.emptyList());
    private final Map<String, ProductDetails> details = new LinkedHashMap<>();
    private final Map<String, PremiumPlan> visiblePlans = new LinkedHashMap<>();
    private volatile Listener listener;
    private volatile boolean connecting;

    @Inject
    public BillingManager(@ApplicationContext Context context, PurchaseVerifier verifier) {
        this.context = context.getApplicationContext();
        this.verifier = verifier;
        PendingPurchasesParams pending = PendingPurchasesParams.newBuilder().enableOneTimeProducts().build();
        client = BillingClient.newBuilder(this.context)
                .setListener(this)
                .enablePendingPurchases(pending)
                .enableAutoServiceReconnection()
                .build();
    }

    public LiveData<State> getState() { return state; }
    public LiveData<List<PremiumPlan>> getPlans() { return plans; }
    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isStoreConfigured() { return !BillingProductConfig.configuredProducts(context).isEmpty(); }

    public void connect() {
        if (!isStoreConfigured()) {
            state.postValue(State.CONFIGURATION_MISSING);
            return;
        }
        if (client.isReady()) {
            state.postValue(State.READY);
            queryProducts();
            refreshPurchases();
            return;
        }
        if (connecting) return;
        connecting = true;
        state.postValue(State.CONNECTING);
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                connecting = false;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    state.postValue(State.READY);
                    queryProducts();
                    refreshPurchases();
                } else {
                    state.postValue(State.UNAVAILABLE);
                }
            }

            @Override public void onBillingServiceDisconnected() {
                connecting = false;
                state.postValue(State.UNAVAILABLE);
            }
        });
    }

    public void queryProducts() {
        List<BillingProductConfig.ProductSpec> configured = BillingProductConfig.configuredProducts(context);
        if (configured.isEmpty()) {
            details.clear();
            visiblePlans.clear();
            plans.postValue(Collections.emptyList());
            state.postValue(State.CONFIGURATION_MISSING);
            return;
        }
        if (!client.isReady()) {
            connect();
            return;
        }
        synchronized (details) {
            details.clear();
            visiblePlans.clear();
        }
        queryProducts(configured, BillingClient.ProductType.SUBS);
        queryProducts(configured, BillingClient.ProductType.INAPP);
    }

    private void queryProducts(List<BillingProductConfig.ProductSpec> configured, String type) {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        for (BillingProductConfig.ProductSpec spec : configured) {
            if (type.equals(spec.productType)) {
                products.add(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(spec.productId).setProductType(spec.productType).build());
            }
        }
        if (products.isEmpty()) return;
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(products).build();
        client.queryProductDetailsAsync(params, (result, response) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            for (ProductDetails product : response.getProductDetailsList()) {
                BillingProductConfig.ProductSpec spec = BillingProductConfig.find(context, product.getProductId());
                PremiumPlan plan = toPlan(product, spec);
                if (plan != null) {
                    synchronized (details) {
                        details.put(product.getProductId(), product);
                        visiblePlans.put(plan.productId, plan);
                    }
                }
            }
            synchronized (details) {
                plans.postValue(new ArrayList<>(visiblePlans.values()));
            }
        });
    }

    private PremiumPlan toPlan(ProductDetails product, BillingProductConfig.ProductSpec spec) {
        if (spec == null) return null;
        if (PremiumPurchaseType.LIFETIME == spec.purchaseType) {
            List<ProductDetails.OneTimePurchaseOfferDetails> offers = product.getOneTimePurchaseOfferDetailsList();
            if (offers == null || offers.isEmpty()) return null;
            ProductDetails.OneTimePurchaseOfferDetails offer = offers.get(0);
            return new PremiumPlan(spec.productId, context.getString(spec.titleRes), offer.getFormattedPrice(),
                    context.getString(com.example.spendtracker.R.string.premium_once), offer.getOfferToken());
        }
        List<ProductDetails.SubscriptionOfferDetails> offers = product.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty() || offers.get(0).getPricingPhases().getPricingPhaseList().isEmpty()) return null;
        ProductDetails.PricingPhase phase = offers.get(0).getPricingPhases().getPricingPhaseList().get(0);
        return new PremiumPlan(spec.productId, context.getString(spec.titleRes), phase.getFormattedPrice(),
                formatBillingPeriod(phase.getBillingPeriod()), offers.get(0).getOfferToken());
    }

    private String formatBillingPeriod(String billingPeriod) {
        if ("P1M".equals(billingPeriod)) return context.getString(com.example.spendtracker.R.string.premium_per_month);
        if ("P1Y".equals(billingPeriod)) return context.getString(com.example.spendtracker.R.string.premium_per_year);
        return billingPeriod;
    }

    public void launchPurchase(Activity activity, PremiumPlan plan) {
        ProductDetails product;
        synchronized (details) { product = details.get(plan.productId); }
        if (product == null || !client.isReady()) {
            Listener callback = listener;
            if (callback != null) callback.onPurchaseFailure(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE);
            return;
        }
        BillingFlowParams.ProductDetailsParams detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).setOfferToken(plan.offerToken).build();
        BillingResult result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(detailsParams)).build());
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Listener callback = listener;
            if (callback != null) callback.onPurchaseFailure(result.getResponseCode());
        }
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        Listener callback = listener;
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            if (callback != null) callback.onPurchaseFailure(result.getResponseCode());
            return;
        }
        if (purchases == null) return;
        for (Purchase purchase : purchases) processPurchase(purchase);
    }

    public void refreshPurchases() {
        if (BillingProductConfig.configuredProducts(context).isEmpty()) return;
        if (!client.isReady()) {
            connect();
            return;
        }
        AtomicInteger outstanding = new AtomicInteger(2);
        AtomicBoolean success = new AtomicBoolean(true);
        AtomicBoolean entitlement = new AtomicBoolean(false);
        refreshPurchases(BillingClient.ProductType.SUBS, outstanding, success, entitlement);
        refreshPurchases(BillingClient.ProductType.INAPP, outstanding, success, entitlement);
    }

    private void refreshPurchases(String productType, AtomicInteger outstanding, AtomicBoolean success,
                                  AtomicBoolean entitlement) {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(productType).build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                success.set(false);
            } else {
                for (Purchase purchase : purchases) {
                    entitlement.set(processPurchase(purchase) || entitlement.get());
                }
            }
            if (outstanding.decrementAndGet() == 0) {
                Listener callback = listener;
                if (callback != null) callback.onRestoreFinished(success.get(), entitlement.get());
            }
        });
    }

    private boolean processPurchase(Purchase purchase) {
        BillingProductConfig.ProductSpec spec = null;
        for (String productId : purchase.getProducts()) {
            spec = BillingProductConfig.find(context, productId);
            if (spec != null) break;
        }
        if (spec == null) return false;
        Listener callback = listener;
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            if (callback != null) callback.onPendingPurchase();
            return false;
        }
        if (!verifier.isVerified(purchase, spec)) return false;
        if (callback != null) callback.onVerifiedPurchase(spec, purchase.getPurchaseToken());
        if (!purchase.isAcknowledged()) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken()).build(), ignored -> { });
        }
        return true;
    }
}
