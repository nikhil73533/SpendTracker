package com.example.spendtracker.ui.premium;

import android.app.Activity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.billing.BillingManager;
import com.example.spendtracker.billing.PremiumPlan;
import com.example.spendtracker.billing.PremiumRepository;
import com.example.spendtracker.billing.PurchaseEvent;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class PaywallViewModel extends ViewModel {
    private final PremiumRepository premiumRepository;

    @Inject public PaywallViewModel(PremiumRepository premiumRepository) {
        this.premiumRepository = premiumRepository;
    }

    public LiveData<List<PremiumPlan>> getPlans() { return premiumRepository.getPlans(); }
    public LiveData<BillingManager.State> getBillingState() { return premiumRepository.getBillingState(); }
    public LiveData<PurchaseEvent> getPurchaseEvent() { return premiumRepository.getPurchaseEvent(); }
    public void refresh() { premiumRepository.refreshEntitlement(); }
    public void restore() { premiumRepository.restorePurchases(); }
    public void purchase(Activity activity, PremiumPlan plan) { premiumRepository.purchase(activity, plan); }
}
