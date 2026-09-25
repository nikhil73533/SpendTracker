package com.example.spendtracker.ui.premium;

import com.example.spendtracker.billing.PremiumFeature;
import com.example.spendtracker.billing.PremiumRepository;
import javax.inject.Inject;

/** Reusable decision point that keeps entitlement checks out of fragment business logic. */
public class FeatureGate {
    private final PremiumRepository premiumRepository;

    @Inject public FeatureGate(PremiumRepository premiumRepository) {
        this.premiumRepository = premiumRepository;
    }

    public void require(PremiumFeature feature, Runnable allowed, Runnable locked) {
        if (!premiumRepository.isStoreConfigured() || premiumRepository.hasAccess(feature)) allowed.run();
        else locked.run();
    }
}
