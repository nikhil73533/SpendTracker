package com.example.spendtracker.billing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import java.util.concurrent.TimeUnit;

public class PremiumStateTest {
    @Test public void lifetimeEntitlementIsAvailableOffline() {
        PremiumState state = new PremiumState(PremiumState.Status.LIFETIME, "lifetime",
                PremiumPurchaseType.LIFETIME, "hash", 1L, 0L);
        assertTrue(state.hasPremiumAccess(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650)));
    }

    @Test public void subscriptionUsesBoundedOfflineGracePeriod() {
        long now = System.currentTimeMillis();
        PremiumState state = new PremiumState(PremiumState.Status.ACTIVE, "monthly",
                PremiumPurchaseType.SUBSCRIPTION, "hash", now, 0L);
        assertTrue(state.hasPremiumAccess(now + TimeUnit.HOURS.toMillis(72) - 1));
        assertFalse(state.hasPremiumAccess(now + TimeUnit.HOURS.toMillis(72) + 1));
    }

    @Test public void pendingAndExpiredPurchasesNeverUnlockPremium() {
        assertFalse(new PremiumState(PremiumState.Status.PENDING, "monthly", PremiumPurchaseType.SUBSCRIPTION,
                "hash", System.currentTimeMillis(), 0L).hasPremiumAccess(System.currentTimeMillis()));
        assertFalse(new PremiumState(PremiumState.Status.EXPIRED, "monthly", PremiumPurchaseType.SUBSCRIPTION,
                "hash", System.currentTimeMillis(), 0L).hasPremiumAccess(System.currentTimeMillis()));
    }
}
