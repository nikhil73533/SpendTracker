package com.example.spendtracker.billing;

import java.util.concurrent.TimeUnit;

/** Immutable, locally cached entitlement metadata. It never contains a raw purchase token. */
public final class PremiumState {
    public enum Status { FREE, ACTIVE, LIFETIME, PENDING, EXPIRED, UNAVAILABLE }

    private static final long SUBSCRIPTION_OFFLINE_GRACE_MS = TimeUnit.DAYS.toMillis(3);

    public final Status status;
    public final String productId;
    public final PremiumPurchaseType purchaseType;
    public final String purchaseTokenHash;
    public final long lastVerifiedAt;
    public final long expiresAt;

    public PremiumState(Status status, String productId, PremiumPurchaseType purchaseType,
                        String purchaseTokenHash, long lastVerifiedAt, long expiresAt) {
        this.status = status;
        this.productId = productId == null ? "" : productId;
        this.purchaseType = purchaseType;
        this.purchaseTokenHash = purchaseTokenHash == null ? "" : purchaseTokenHash;
        this.lastVerifiedAt = lastVerifiedAt;
        this.expiresAt = expiresAt;
    }

    public static PremiumState free() {
        return new PremiumState(Status.FREE, "", null, "", 0L, 0L);
    }

    public boolean hasPremiumAccess(long now) {
        if (status == Status.LIFETIME) return true;
        if (status != Status.ACTIVE || purchaseType != PremiumPurchaseType.SUBSCRIPTION) return false;
        if (expiresAt > 0L) return now < expiresAt;
        return lastVerifiedAt > 0L && now - lastVerifiedAt <= SUBSCRIPTION_OFFLINE_GRACE_MS;
    }
}
