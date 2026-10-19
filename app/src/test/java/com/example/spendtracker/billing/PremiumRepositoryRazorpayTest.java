package com.example.spendtracker.billing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.spendtracker.billing.autopay.AutoPayManager;
import dagger.Lazy;
import org.junit.Test;

public class PremiumRepositoryRazorpayTest {
    @Test
    public void mainBuildUsesRazorpayWithoutInitializingPlayBilling() {
        EntitlementCache cache = mock(EntitlementCache.class);
        BillingManager playBilling = mock(BillingManager.class);
        AutoPayManager razorpay = mock(AutoPayManager.class);
        when(cache.read()).thenReturn(PremiumState.free());
        Lazy<BillingManager> lazyPlayBilling = () -> playBilling;

        PremiumRepository repository = new PremiumRepository(cache, lazyPlayBilling, razorpay);
        repository.getPlans();
        repository.refreshEntitlement();

        verify(razorpay).plans();
        verify(razorpay).refresh();
        verifyNoInteractions(playBilling);
    }
}
