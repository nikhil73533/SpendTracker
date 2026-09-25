package com.example.spendtracker.billing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Central feature entitlement API consumed by UI gates, not individual premium booleans. */
@Singleton
public class PremiumRepository implements BillingManager.Listener {
    private final EntitlementCache cache;
    private final BillingManager billingManager;
    private final MutableLiveData<PremiumState> premiumState;
    private final MutableLiveData<PurchaseEvent> purchaseEvent = new MutableLiveData<>(PurchaseEvent.NONE);

    @Inject
    public PremiumRepository(EntitlementCache cache, BillingManager billingManager) {
        this.cache = cache;
        this.billingManager = billingManager;
        PremiumState initial = cache.read();
        if (!initial.hasPremiumAccess(System.currentTimeMillis()) && initial.status == PremiumState.Status.ACTIVE) {
            initial = new PremiumState(PremiumState.Status.EXPIRED, initial.productId, initial.purchaseType,
                    initial.purchaseTokenHash, initial.lastVerifiedAt, initial.expiresAt);
        }
        premiumState = new MutableLiveData<>(initial);
        billingManager.setListener(this);
    }

    public LiveData<PremiumState> getPremiumState() { return premiumState; }
    public LiveData<BillingManager.State> getBillingState() { return billingManager.getState(); }
    public LiveData<List<PremiumPlan>> getPlans() { return billingManager.getPlans(); }
    public LiveData<PurchaseEvent> getPurchaseEvent() { return purchaseEvent; }

    public boolean hasAccess(PremiumFeature feature) {
        PremiumState current = premiumState.getValue();
        return current != null && current.hasPremiumAccess(System.currentTimeMillis());
    }

    /** Before the owner configures Play products, preserve all existing local functionality. */
    public boolean isStoreConfigured() { return billingManager.isStoreConfigured(); }

    public void refreshEntitlement() { billingManager.connect(); }
    public void restorePurchases() { billingManager.refreshPurchases(); }
    public void purchase(android.app.Activity activity, PremiumPlan plan) { billingManager.launchPurchase(activity, plan); }

    @Override public void onVerifiedPurchase(BillingProductConfig.ProductSpec spec, String purchaseToken) {
        PremiumState.Status status = spec.purchaseType == PremiumPurchaseType.LIFETIME
                ? PremiumState.Status.LIFETIME : PremiumState.Status.ACTIVE;
        PremiumState updated = new PremiumState(status, spec.productId, spec.purchaseType,
                EntitlementCache.hashToken(purchaseToken), System.currentTimeMillis(), 0L);
        cache.write(updated);
        premiumState.postValue(updated);
        purchaseEvent.postValue(PurchaseEvent.PURCHASED);
    }

    @Override public void onPendingPurchase() {
        PremiumState current = premiumState.getValue();
        if (current != null && current.hasPremiumAccess(System.currentTimeMillis())) return;
        premiumState.postValue(new PremiumState(PremiumState.Status.PENDING,
                current == null ? "" : current.productId, current == null ? null : current.purchaseType,
                current == null ? "" : current.purchaseTokenHash, System.currentTimeMillis(), 0L));
        purchaseEvent.postValue(PurchaseEvent.PENDING);
    }

    @Override public void onPurchaseFailure(int responseCode) {
        purchaseEvent.postValue(responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED
                ? PurchaseEvent.CANCELLED : PurchaseEvent.FAILED);
    }

    @Override public void onRestoreFinished(boolean succeeded, boolean hasEntitlement) {
        if (!succeeded) return;
        if (!hasEntitlement) {
            cache.clear();
            premiumState.postValue(PremiumState.free());
        }
        purchaseEvent.postValue(PurchaseEvent.RESTORED);
    }
}
