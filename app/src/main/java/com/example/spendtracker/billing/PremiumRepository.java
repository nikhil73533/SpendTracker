package com.example.spendtracker.billing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import dagger.Lazy;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Central feature entitlement API consumed by UI gates, not individual premium booleans. */
@Singleton
public class PremiumRepository implements BillingManager.Listener {
    private final com.example.spendtracker.billing.autopay.AutoPayManager autoPay;
    private final EntitlementCache cache;
    private final Lazy<BillingManager> playBilling;
    private boolean playBillingListenerAttached;
    private final MutableLiveData<PremiumState> premiumState;
    private final MutableLiveData<PurchaseEvent> purchaseEvent = new MutableLiveData<>(PurchaseEvent.NONE);

    @Inject
    public PremiumRepository(EntitlementCache cache, Lazy<BillingManager> playBilling,
                             com.example.spendtracker.billing.autopay.AutoPayManager autoPay) {
        this.autoPay = autoPay;
        this.cache = cache;
        this.playBilling = playBilling;
        PremiumState initial = cache.read();
        if (!initial.hasPremiumAccess(System.currentTimeMillis()) && initial.status == PremiumState.Status.ACTIVE) {
            initial = new PremiumState(PremiumState.Status.EXPIRED, initial.productId, initial.purchaseType,
                    initial.purchaseTokenHash, initial.lastVerifiedAt, initial.expiresAt);
        }
        premiumState = new MutableLiveData<>(initial);
    }

    /** The direct-distribution build never calls this, so Play Billing is not initialized. */
    private synchronized BillingManager playBilling() {
        BillingManager manager = playBilling.get();
        if (!playBillingListenerAttached) {
            manager.setListener(this);
            playBillingListenerAttached = true;
        }
        return manager;
    }

    public LiveData<PremiumState> getPremiumState() { if (freePlay()) return new MutableLiveData<>(PremiumState.free()); return useAutoPay() ? autoPay.premium() : premiumState; }
    public LiveData<BillingManager.State> getBillingState() { if (freePlay()) return new MutableLiveData<>(BillingManager.State.READY); return useAutoPay() ? autoPay.state() : playBilling().getState(); }
    public LiveData<List<PremiumPlan>> getPlans() { if (freePlay()) return new MutableLiveData<>(java.util.Collections.emptyList()); return useAutoPay() ? autoPay.plans() : playBilling().getPlans(); }
    public LiveData<PurchaseEvent> getPurchaseEvent() { if (freePlay()) return purchaseEvent; return useAutoPay() ? autoPay.events() : purchaseEvent; }

    public boolean hasAccess(PremiumFeature feature) {
        if (freePlay()) return true;
        if (useAutoPay()) return autoPay.hasAccess();
        PremiumState current = premiumState.getValue();
        return current != null && current.hasPremiumAccess(System.currentTimeMillis());
    }

    public boolean isStoreConfigured() { if (freePlay()) return false; return useAutoPay() || playBilling().isStoreConfigured(); }

    private boolean freePlay() { return com.example.spendtracker.BuildConfig.FREE_PLAY_BUILD; }
    private boolean useAutoPay() { return com.example.spendtracker.BuildConfig.AUTOPAY_ENABLED; }
    public void refreshEntitlement() { if (freePlay()) return; if (useAutoPay()) autoPay.refresh(); else playBilling().connect(); }
    public void restorePurchases() { if (freePlay()) return; if (useAutoPay()) autoPay.refresh(); else playBilling().refreshPurchases(); }
    public void purchase(android.app.Activity activity, PremiumPlan plan) {
        if (freePlay()) return;
        if (useAutoPay()) autoPay.launch(activity, "buy", plan.productId);
        else playBilling().launchPurchase(activity, plan);
    }

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
