package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentSpendingChartBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Read-only detail for the same rolling week shown by the dashboard strip. */
public class SpendingChartFragment extends BottomSheetDialogFragment {
    private FragmentSpendingChartBinding binding;
    private DashboardViewModel viewModel;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentSpendingChartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);
        binding.spendingChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override public void onValueSelected(com.github.mikephil.charting.data.Entry entry,
                    com.github.mikephil.charting.highlight.Highlight highlight) {
                java.util.List<Long> dates = new java.util.ArrayList<>(DashboardChartSeries.spendingWeek(
                        viewModel.getAllTransactions().getValue(), System.currentTimeMillis()).keySet());
                int index = Math.round(entry.getX());
                if (index >= 0 && index < dates.size()) binding.spendingChartSelection.setText(
                    new java.text.SimpleDateFormat("EEE, d MMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date(dates.get(index)))
                    + " · " + DashboardChartRenderer.currency(entry.getY()));
            }
            @Override public void onNothingSelected() { }
        });
        binding.btnCloseSpendingChart.setOnClickListener(v -> dismiss());
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), ignored -> render());
        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), ignored -> render());
    }

    private void render() {
        if (binding == null) return;
        binding.spendingChart.highlightValues(null);
        java.util.Map<Long, Double> week = DashboardChartSeries.spendingWeek(viewModel.getAllTransactions().getValue(), System.currentTimeMillis());
        java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault());
        java.util.List<Long> days = new java.util.ArrayList<>(week.keySet());
        String range = date.format(new java.util.Date(days.get(0))) + " – " + date.format(new java.util.Date(days.get(6)));
        binding.spendingChartSelection.setText(Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue())
                ? getString(R.string.chart_private) : range + " · " + (java.util.Collections.max(week.values()) == 0d
                    ? "No spending recorded" : "Tap a point for its amount"));
        DashboardChartRenderer.render(binding.spendingChart,
                DashboardChartSeries.spendingWeek(viewModel.getAllTransactions().getValue(), System.currentTimeMillis()),
                Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()), getString(R.string.spending_chart_title));
    }

    @Override public void onResume() { super.onResume(); render(); }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
