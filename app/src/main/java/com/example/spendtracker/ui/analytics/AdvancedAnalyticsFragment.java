package com.example.spendtracker.ui.analytics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import com.example.spendtracker.databinding.FragmentAdvancedAnalyticsBinding;
import com.example.spendtracker.domain.model.analytics.AnalyticsGranularity;
import com.example.spendtracker.domain.model.analytics.CategoryAnalytics;
import com.example.spendtracker.domain.model.analytics.FinancialInsight;
import com.example.spendtracker.domain.model.analytics.MonthlyComparison;
import com.example.spendtracker.domain.model.analytics.RollingAverage;
import com.example.spendtracker.domain.model.analytics.BehaviorAnalytics;
import com.example.spendtracker.domain.model.analytics.ForecastResult;
import com.example.spendtracker.domain.model.analytics.MerchantAnalytics;
import com.example.spendtracker.domain.model.analytics.AnomalyTransaction;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
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
    private List<CategoryAnalytics> currentCategories = new ArrayList<>();
    private List<FinancialInsight> currentInsights = new ArrayList<>();
    private MonthlyComparison currentComparison;
    private RollingAverage currentRollingAverage;
    private BehaviorAnalytics currentDayNight;
    private ForecastResult currentForecast;
    private List<MerchantAnalytics> currentMerchants = new ArrayList<>();
    private List<AnomalyTransaction> currentAnomalies = new ArrayList<>();

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
            if (checkedId == binding.chipDaily.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.DAY);
            } else if (checkedId == binding.chipWeekly.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.WEEK);
            } else if (checkedId == binding.chipMonthly.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.MONTH);
            } else if (checkedId == binding.chipAnnual.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.YEAR);
            }
        });

        binding.btnDateFilter.setOnClickListener(v -> showDateRangePicker());
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void showDateRangePicker() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select analytics date range")
                .setSelection(Pair.create(cal.getTimeInMillis(), System.currentTimeMillis()))
                .build();
        picker.addOnPositiveButtonClickListener(range -> {
            if (range == null || range.first == null || range.second == null) return;
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(range.second);
            end.add(Calendar.DAY_OF_MONTH, 1);
            viewModel.setStartDate(range.first);
            viewModel.setEndDate(end.getTimeInMillis());
            binding.btnDateFilter.setText(picker.getHeaderText());
        });
        picker.show(getParentFragmentManager(), "analytics_date_range");
    }

    private void setupChart() {
        configureCartesianChart(binding.trendChart, true);
        configureCartesianChart(binding.volumeChart, false);
        configureCartesianChart(binding.categoryFrequencyChart, false);
        binding.categoryFrequencyChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    private void configureCartesianChart(com.github.mikephil.charting.charts.BarLineChartBase<?> chart, boolean showLegend) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(showLegend);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));

        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
    }

    private void observeViewModel() {
        viewModel.getFinancialOverview().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                binding.tvTotalIncome.setText(formatAmount(summary.getTotalIncome()));
                binding.tvTotalExpense.setText(formatAmount(summary.getTotalExpense()));
                binding.tvNetBalance.setText(formatAmount(summary.getNetBalance()));
                binding.tvTotalTransfer.setText(formatAmount(summary.getTotalTransfer()));
                binding.tvAverageDailyTransactions.setText(String.format(Locale.getDefault(), "%.1f / day",
                        summary.getAverageDailyTransactions()));
            }
        });

        viewModel.getMonthlyExpenseTrend().observe(getViewLifecycleOwner(), trend -> {
            // This legacy stream is retained for compatibility; the cash-flow chart below
            // uses the richer income/expense series.
        });

        viewModel.getIncomeExpenseTrend().observe(getViewLifecycleOwner(), trend -> {
            if (trend != null && !trend.isEmpty()) {
                List<Entry> incomeEntries = new ArrayList<>();
                List<Entry> expenseEntries = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < trend.size(); i++) {
                    incomeEntries.add(new Entry(i, (float) trend.get(i).getValue()));
                    expenseEntries.add(new Entry(i, (float) trend.get(i).getSecondaryValue()));
                    labels.add(trend.get(i).getLabel());
                }
                LineDataSet incomeSet = createLineSet(incomeEntries, "Credits", android.R.color.holo_blue_light);
                LineDataSet expenseSet = createLineSet(expenseEntries, "Debits", android.R.color.holo_red_light);
                List<ILineDataSet> sets = new ArrayList<>();
                sets.add(incomeSet);
                sets.add(expenseSet);
                LineData lineData = new LineData(sets);
                binding.trendChart.setData(lineData);
                binding.trendChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                binding.trendChart.invalidate(); // refresh
            } else {
                binding.trendChart.clear();
            }
        });

        viewModel.getTransactionFrequency().observe(getViewLifecycleOwner(), points -> {
            if (points == null || points.isEmpty()) {
                binding.volumeChart.clear();
                binding.volumeChart.invalidate();
                return;
            }
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                entries.add(new BarEntry(i, (float) points.get(i).getValue()));
                labels.add(points.get(i).getLabel());
            }
            BarDataSet dataSet = new BarDataSet(entries, "Transactions");
            dataSet.setColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light));
            dataSet.setDrawValues(false);
            binding.volumeChart.setData(new BarData(dataSet));
            binding.volumeChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            binding.volumeChart.invalidate();
        });

        viewModel.getExpenseCategoryAnalytics().observe(getViewLifecycleOwner(), categories -> {
            currentCategories = categories == null ? new ArrayList<>() : new ArrayList<>(categories);
            renderCategoryFrequency(categories);
            renderInsightSections();
        });
        viewModel.getFinancialInsights().observe(getViewLifecycleOwner(), insights -> {
            currentInsights = insights == null ? new ArrayList<>() : new ArrayList<>(insights);
            renderInsightSections();
        });
        viewModel.getMonthOverMonthComparison().observe(getViewLifecycleOwner(), comparison -> {
            currentComparison = comparison;
            renderInsightSections();
        });
        viewModel.getRollingExpenseAverage().observe(getViewLifecycleOwner(), rollingAverage -> {
            currentRollingAverage = rollingAverage;
            renderInsightSections();
        });
        viewModel.getDayNightAnalytics().observe(getViewLifecycleOwner(), value -> {
            currentDayNight = value;
            renderInsightSections();
        });
        viewModel.getSpendingForecast().observe(getViewLifecycleOwner(), value -> {
            currentForecast = value;
            renderInsightSections();
        });
        viewModel.getMerchantAnalytics().observe(getViewLifecycleOwner(), value -> {
            currentMerchants = value == null ? new ArrayList<>() : new ArrayList<>(value);
            renderInsightSections();
        });
        viewModel.getUnusualTransactions().observe(getViewLifecycleOwner(), value -> {
            currentAnomalies = value == null ? new ArrayList<>() : new ArrayList<>(value);
            renderInsightSections();
        });
    }

    private LineDataSet createLineSet(List<Entry> entries, String label, int color) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(ContextCompat.getColor(requireContext(), color));
        set.setCircleColor(ContextCompat.getColor(requireContext(), color));
        set.setDrawCircles(true);
        set.setDrawValues(false);
        set.setLineWidth(2f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        return set;
    }

    private void renderCategoryFrequency(List<CategoryAnalytics> categories) {
        if (categories == null || categories.isEmpty()) {
            binding.categoryFrequencyChart.clear();
            binding.categoryFrequencyChart.invalidate();
            return;
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int limit = Math.min(categories.size(), 10);
        for (int i = 0; i < limit; i++) {
            CategoryAnalytics category = categories.get(i);
            entries.add(new BarEntry(i, category.getTransactionCount()));
            labels.add(category.getCategoryName());
        }
        BarDataSet dataSet = new BarDataSet(entries, "Transactions");
        dataSet.setColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light));
        dataSet.setDrawValues(false);
        binding.categoryFrequencyChart.setData(new BarData(dataSet));
        binding.categoryFrequencyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.categoryFrequencyChart.invalidate();
    }

    private void renderInsightSections() {
        List<AnalyticsSectionAdapter.SectionItem> items = new ArrayList<>();
        if (!currentCategories.isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Top Expense Categories"));
            int limit = Math.min(currentCategories.size(), 5);
            for (int i = 0; i < limit; i++) {
                CategoryAnalytics category = currentCategories.get(i);
                items.add(new AnalyticsSectionAdapter.CardItem(category.getCategoryName(),
                        formatAmount(category.getTotalAmount()) + " (" + category.getTransactionCount() + " transactions)"));
            }
        }
        if (currentComparison != null && currentComparison.hasPreviousData()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Period Comparison"));
            items.add(new AnalyticsSectionAdapter.CardItem("Expense change",
                    String.format(Locale.getDefault(), "%+.1f%%", currentComparison.getExpenseChangePercent())));
            items.add(new AnalyticsSectionAdapter.CardItem("Income change",
                    String.format(Locale.getDefault(), "%+.1f%%", currentComparison.getIncomeChangePercent())));
        }
        if (currentRollingAverage != null && currentRollingAverage.hasSufficientData()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Rolling Average"));
            items.add(new AnalyticsSectionAdapter.CardItem(currentRollingAverage.getMonths() + "-month average",
                    formatAmount(currentRollingAverage.getAverageMonthlySpending())));
        }
        if (currentForecast != null) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Spending Pace"));
            items.add(new AnalyticsSectionAdapter.CardItem("Projected spending", formatAmount(currentForecast.getProjectedTotal())
                    + " over " + currentForecast.getTotalDaysInPeriod() + " days"));
            items.add(new AnalyticsSectionAdapter.CardItem("Daily pace", formatAmount(currentForecast.getDailyRate())
                    + " · " + currentForecast.getVelocityInsight()));
        }
        if (currentDayNight != null && currentDayNight.getInsight() != null && !currentDayNight.getInsight().isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Spending Habits"));
            items.add(new AnalyticsSectionAdapter.CardItem("When you spend", currentDayNight.getInsight()));
        }
        if (!currentMerchants.isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Top Merchants"));
            int limit = Math.min(3, currentMerchants.size());
            for (int i = 0; i < limit; i++) {
                MerchantAnalytics merchant = currentMerchants.get(i);
                items.add(new AnalyticsSectionAdapter.CardItem(merchant.getMerchantName(),
                        formatAmount(merchant.getTotalAmount()) + " across " + merchant.getTransactionCount() + " payments"));
            }
        }
        if (!currentAnomalies.isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Review Activity"));
            items.add(new AnalyticsSectionAdapter.CardItem("Unusual transactions", currentAnomalies.size()
                    + " transaction" + (currentAnomalies.size() == 1 ? " needs" : "s need")
                    + " review · Tap to view", this::showUnusualTransactions));
        }
        if (!currentInsights.isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Insights"));
            for (FinancialInsight insight : currentInsights) {
                items.add(new AnalyticsSectionAdapter.CardItem(insight.getType().name().replace('_', ' '), insight.getMessage()));
            }
        }
        adapter.submitList(items);
    }

    /** Shows every anomaly for the selected period and lets the user open its transaction. */
    private void showUnusualTransactions() {
        if (currentAnomalies.isEmpty() || !isAdded()) return;
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        list.setPadding(padding, padding / 2, padding, padding / 2);
        final androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];

        for (AnomalyTransaction anomaly : currentAnomalies) {
            android.widget.TextView row = new android.widget.TextView(requireContext());
            String merchant = anomaly.getMerchantName() == null || anomaly.getMerchantName().trim().isEmpty()
                    ? anomaly.getCategory() : anomaly.getMerchantName();
            String date = new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    .format(new java.util.Date(anomaly.getDate()));
            row.setText(merchant + " · " + formatAmount(anomaly.getAmount()) + "\n"
                    + anomaly.getCategory() + " · " + date + " · "
                    + String.format(Locale.getDefault(), "%.1fx the usual amount", anomaly.getDeviationMultiple()));
            row.setTextSize(15f);
            row.setPadding(0, padding / 2, 0, padding / 2);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putInt("transactionId", anomaly.getTransactionId());
                if (dialog[0] != null) dialog[0].dismiss();
                androidx.navigation.Navigation.findNavController(requireView())
                        .navigate(com.example.spendtracker.R.id.transactionFormFragment, args);
            });
            list.addView(row);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        scroll.addView(list);
        dialog[0] = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Unusual transactions")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .create();
        dialog[0].show();
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
