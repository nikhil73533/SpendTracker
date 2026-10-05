package com.example.spendtracker.ui.premium;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.billing.BillingManager;
import com.example.spendtracker.databinding.FragmentPaywallBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PaywallFragment extends Fragment {
    private FragmentPaywallBinding binding;
    private PremiumPlanAdapter adapter;
    @javax.inject.Inject com.example.spendtracker.billing.autopay.AutoPayManager autoPay;
    private boolean autoPayEnabled() { return com.example.spendtracker.BuildConfig.AUTOPAY_ENABLED; }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                                   @Nullable Bundle savedInstanceState) {
        binding = FragmentPaywallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PaywallViewModel viewModel = new ViewModelProvider(this).get(PaywallViewModel.class);
        adapter = new PremiumPlanAdapter(() -> {
            if (autoPayEnabled()) renderAutoPay();
            else if (binding != null) binding.premiumContinue.setEnabled(adapter != null && adapter.selected() != null);
        });
        binding.premiumPlans.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.premiumPlans.setAdapter(adapter);
        binding.premiumClose.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.premiumContinue.setOnClickListener(v -> {
            if (autoPayEnabled() && adapter.selected() == null) { autoPay.launch(requireActivity(), "status", null); return; }
            if (adapter.selected() != null) viewModel.purchase(requireActivity(), adapter.selected());
        });
        binding.premiumRestore.setOnClickListener(v -> {
            if (autoPayEnabled()) { autoPay.launch(requireActivity(), "status", null); return; }
            viewModel.restore();
            Toast.makeText(requireContext(), R.string.premium_loading, Toast.LENGTH_SHORT).show();
        });
        binding.premiumLater.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        viewModel.getPlans().observe(getViewLifecycleOwner(), adapter::submit);
        viewModel.getBillingState().observe(getViewLifecycleOwner(), state -> renderState(state));
        viewModel.getPurchaseEvent().observe(getViewLifecycleOwner(), event -> showPurchaseEvent(event));
        if (autoPayEnabled()) {
            binding.premiumRestore.setText(R.string.autopay_status);
            binding.autopayCancel.setOnClickListener(v -> autoPay.launch(requireActivity(), "cancel", null));
            autoPay.message().observe(getViewLifecycleOwner(), text -> renderAutoPay());
            autoPay.busy().observe(getViewLifecycleOwner(), value -> renderAutoPay());
            autoPay.cancellable().observe(getViewLifecycleOwner(), value -> renderAutoPay());
        }
        viewModel.refresh();
    }

    private void renderState(BillingManager.State state) {
        if (binding == null) return;
        if (autoPayEnabled()) { renderAutoPay(); return; }
        boolean loading = state == BillingManager.State.CONNECTING || state == BillingManager.State.IDLE;
        binding.premiumProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.premiumStatus.setVisibility(state == BillingManager.State.READY ? View.GONE : View.VISIBLE);
        if (state == BillingManager.State.CONFIGURATION_MISSING) {
            binding.premiumStatus.setText(R.string.premium_unavailable);
        } else if (state == BillingManager.State.UNAVAILABLE) {
            binding.premiumStatus.setText(R.string.internet_required);
        } else {
            binding.premiumStatus.setText(R.string.premium_loading);
        }
    }

    private void renderAutoPay() {
        if (binding == null) return;
        boolean busy = Boolean.TRUE.equals(autoPay.busy().getValue());
        binding.premiumProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.premiumStatus.setVisibility(View.VISIBLE);
        binding.premiumStatus.setText(autoPay.configured() ? autoPay.message().getValue() : "AutoPay setup is not complete yet.");
        binding.premiumContinue.setEnabled(autoPay.configured() && !busy);
        binding.premiumContinue.setText(adapter != null && adapter.selected() != null ? R.string.autopay_continue : R.string.autopay_sign_in);
        binding.premiumRestore.setEnabled(autoPay.configured() && !busy);
        binding.autopayCancel.setVisibility(Boolean.TRUE.equals(autoPay.cancellable().getValue()) ? View.VISIBLE : View.GONE);
        binding.autopayCancel.setEnabled(!busy);
        org.json.JSONObject catalog = autoPay.catalog();
        if (catalog != null) {
            android.text.SpannableStringBuilder links = new android.text.SpannableStringBuilder("Auto-renewing subscription. No free trial. ");
            addBillingLink(links, "Terms", catalog.optString("terms_url"));
            addBillingLink(links, "Privacy", catalog.optString("privacy_url"));
            addBillingLink(links, "Support", catalog.optString("support_url"));
            binding.autopayTerms.setText(links);
            binding.autopayTerms.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            binding.autopayTerms.setVisibility(View.VISIBLE);
        }
    }
    private void addBillingLink(android.text.SpannableStringBuilder text, String label, String url) {
        if (!url.startsWith("https://")) return;
        int start = text.length(); text.append(label);
        text.setSpan(new android.text.style.URLSpan(url), start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("  ");
    }

    private void showPurchaseEvent(com.example.spendtracker.billing.PurchaseEvent event) {
        if (event == null || event == com.example.spendtracker.billing.PurchaseEvent.NONE || !isAdded()) return;
        int message = R.string.premium_purchase_failed;
        if (event == com.example.spendtracker.billing.PurchaseEvent.PENDING) message = R.string.premium_pending;
        else if (event == com.example.spendtracker.billing.PurchaseEvent.CANCELLED) message = R.string.premium_purchase_cancelled;
        else if (event == com.example.spendtracker.billing.PurchaseEvent.RESTORED) message = R.string.premium_restore_complete;
        else if (event == com.example.spendtracker.billing.PurchaseEvent.PURCHASED) message = R.string.premium_purchase_complete;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
