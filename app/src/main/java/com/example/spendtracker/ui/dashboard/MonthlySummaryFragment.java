package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.spendtracker.databinding.FragmentDashboardMonthlyBinding;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@AndroidEntryPoint
public class MonthlySummaryFragment extends Fragment {

    @Inject
    com.example.spendtracker.ui.premium.FeatureGate featureGate;

    private FragmentDashboardMonthlyBinding binding;
    private DashboardViewModel viewModel;
    private MonthlySummaryAdapter monthlyAdapter;
    private List<MonthlySummaryAdapter.MonthSummary> summaries = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardMonthlyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);

        binding.btnShareReceipt.setOnClickListener(v -> featureGate.require(
                com.example.spendtracker.billing.PremiumFeature.ADVANCED_REPORTS,
                this::shareMonthlySummaryReport, this::openPaywall));
        setupRecyclerView();
        observeViewModel();
    }

    private void setupRecyclerView() {
        monthlyAdapter = new MonthlySummaryAdapter(amount -> viewModel.formatAmount(amount));
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(monthlyAdapter);
    }

    private void observeViewModel() {
        viewModel.getMonthlySummaries().observe(getViewLifecycleOwner(), summaries -> {
            this.summaries = summaries == null ? Collections.emptyList() : summaries;
            monthlyAdapter.submitList(this.summaries);
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            monthlyAdapter.notifyDataSetChanged();
        });
    }

    /** The Monthly tab exports its visible month and week rows, never an arbitrary receipt. */
    private void shareMonthlySummaryReport() {
        requireReportAuthentication(() -> {
            if (summaries.isEmpty()) {
                Toast.makeText(requireContext(), "No monthly data to share", Toast.LENGTH_SHORT).show();
                return;
            }
            List<MonthlySummaryAdapter.MonthSummary> reportRows = new java.util.ArrayList<>(summaries);
            new Thread(() -> {
                try {
                    java.io.File report = com.example.spendtracker.util.PdfReportService
                            .generateMonthlySummaryReport(requireContext(), reportRows);
                    requireActivity().runOnUiThread(() -> {
                        try {
                            com.example.spendtracker.util.PdfReportService.share(
                                    requireContext(), report, "Monthly summary report", "Share monthly summary");
                        } catch (RuntimeException error) {
                            Toast.makeText(requireContext(), "No app available to share the report", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception error) {
                    android.util.Log.e("MonthlySummary", "Unable to create report", error);
                    if (isAdded()) requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Could not create monthly report", Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    /** Sensitive figures are never written to a shareable file while Privacy Mode is locked. */
    private void requireReportAuthentication(Runnable action) {
        if (!Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue())) {
            action.run();
            return;
        }
        com.example.spendtracker.util.BiometricHelper.authenticate(requireActivity(), new com.example.spendtracker.util.BiometricHelper.BiometricCallback() {
            @Override public void onSuccess() {
                viewModel.setPrivacyModeEnabled(false);
                action.run();
            }
            @Override public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), "Authentication required to share a report", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openPaywall() {
        if (isAdded()) androidx.navigation.Navigation.findNavController(requireView()).navigate(com.example.spendtracker.R.id.paywallFragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
