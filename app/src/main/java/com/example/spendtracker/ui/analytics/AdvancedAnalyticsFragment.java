package com.example.spendtracker.ui.analytics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.databinding.FragmentAdvancedAnalyticsBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class AdvancedAnalyticsFragment extends Fragment {

    private FragmentAdvancedAnalyticsBinding binding;
    private AdvancedAnalyticsViewModel viewModel;
    private AnalyticsSectionAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAdvancedAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AdvancedAnalyticsViewModel.class);

        adapter = new AnalyticsSectionAdapter();
        binding.rvAnalyticsSections.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        binding.rvAnalyticsSections.setAdapter(adapter);

        setupChart();
        setupFilters();
        observeViewModel();
    }

    private void setupFilters() {
        binding.chipGroupGranularity.setOnCheckedChangeListener((group, checkedId) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            long now = System.currentTimeMillis();
            
            if (checkedId == binding.chipDaily.getId()) {
                viewModel.setStartDate(cal.getTimeInMillis());
                binding.btnDateFilter.setText("Today");
            } else if (checkedId == binding.chipWeekly.getId()) {
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                viewModel.setStartDate(cal.getTimeInMillis());
                binding.btnDateFilter.setText("This Week");
            } else if (checkedId == binding.chipMonthly.getId()) {
                cal.set(Calendar.DAY_OF_MONTH, 1);
                viewModel.setStartDate(cal.getTimeInMillis());
                binding.btnDateFilter.setText("This Month");
            }
            viewModel.setEndDate(now);
        });
    }

    private void setupChart() {
        binding.trendChart.getDescription().setEnabled(false);
        binding.trendChart.getLegend().setEnabled(false);
        binding.trendChart.setDrawGridBackground(false);
        binding.trendChart.setTouchEnabled(true);
        binding.trendChart.setDragEnabled(true);
        binding.trendChart.setScaleEnabled(false);

        XAxis xAxis = binding.trendChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(getResources().getColor(android.R.color.darker_gray, getContext().getTheme()));

        binding.trendChart.getAxisRight().setEnabled(false);
        binding.trendChart.getAxisLeft().setDrawGridLines(true);
        binding.trendChart.getAxisLeft().setTextColor(getResources().getColor(android.R.color.darker_gray, getContext().getTheme()));
    }

    private void observeViewModel() {
        viewModel.getFinancialOverview().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                binding.tvTotalIncome.setText(formatAmount(summary.getTotalIncome()));
                binding.tvTotalExpense.setText(formatAmount(summary.getTotalExpense()));
                binding.tvNetBalance.setText(formatAmount(summary.getNetBalance()));
                binding.tvTotalTransfer.setText(formatAmount(summary.getTotalTransfer()));
            }
        });

        viewModel.getMonthlyExpenseTrend().observe(getViewLifecycleOwner(), trend -> {
            if (trend != null && !trend.isEmpty()) {
                List<Entry> entries = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                
                for (int i = 0; i < trend.size(); i++) {
                    entries.add(new Entry(i, (float) trend.get(i).getValue()));
                    labels.add(trend.get(i).getLabel());
                }

                LineDataSet dataSet = new LineDataSet(entries, "Expense");
                dataSet.setColor(getResources().getColor(android.R.color.holo_red_light, getContext().getTheme()));
                dataSet.setDrawCircles(true);
                dataSet.setCircleColor(getResources().getColor(android.R.color.holo_red_light, getContext().getTheme()));
                dataSet.setDrawValues(false);
                dataSet.setLineWidth(2f);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Smooth curves

                LineData lineData = new LineData(dataSet);
                binding.trendChart.setData(lineData);
                binding.trendChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                binding.trendChart.invalidate(); // refresh
            } else {
                binding.trendChart.clear();
            }
        });

        viewModel.getExpenseCategoryAnalytics().observe(getViewLifecycleOwner(), categories -> {
            List<AnalyticsSectionAdapter.SectionItem> items = new ArrayList<>();
            if (categories != null && !categories.isEmpty()) {
                items.add(new AnalyticsSectionAdapter.HeaderItem("Top Expense Categories"));
                for (com.example.spendtracker.domain.model.analytics.CategoryAnalytics cat : categories) {
                    items.add(new AnalyticsSectionAdapter.CardItem(
                            cat.getCategoryName(), 
                            formatAmount(cat.getTotalAmount()) + " (" + cat.getTransactionCount() + " txns)"
                    ));
                }
            }
            adapter.submitList(items);
        });
    }

    private String formatAmount(double amount) {
        return String.format(Locale.getDefault(), "₹ %.0f", amount);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
