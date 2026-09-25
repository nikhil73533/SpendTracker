package com.example.spendtracker.ui.premium;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.spendtracker.billing.PremiumFeature;
import com.example.spendtracker.billing.PremiumRepository;
import org.junit.Test;

public class FeatureGateTest {
    @Test public void preservesExistingFeaturesUntilStoreIsConfigured() {
        PremiumRepository repository = mock(PremiumRepository.class);
        when(repository.isStoreConfigured()).thenReturn(false);
        int[] result = {0};
        new FeatureGate(repository).require(PremiumFeature.PDF_IMPORT, () -> result[0] = 1, () -> result[0] = 2);
        assertEquals(1, result[0]);
    }

    @Test public void routesConfiguredFreeUserToLock() {
        PremiumRepository repository = mock(PremiumRepository.class);
        when(repository.isStoreConfigured()).thenReturn(true);
        when(repository.hasAccess(PremiumFeature.PDF_IMPORT)).thenReturn(false);
        int[] result = {0};
        new FeatureGate(repository).require(PremiumFeature.PDF_IMPORT, () -> result[0] = 1, () -> result[0] = 2);
        assertEquals(2, result[0]);
    }
}
