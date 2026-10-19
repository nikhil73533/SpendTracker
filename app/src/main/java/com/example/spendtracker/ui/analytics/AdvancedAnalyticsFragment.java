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
import com.example.spendtracker.domain.model.analytics.SpendingConcentration;
import com.example.spendtracker.domain.model.analytics.LargeTransactionSummary;
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
    private List<MerchantAnalytics> currentRecurringMerchants = new ArrayList<>();
    private List<CategoryAnalytics> currentCategoryGrowth = new ArrayList<>();
    private List<AnomalyTransaction> currentAnomalies = new ArrayList<>();
    private SpendingConcentration currentConcentration;
    private LargeTransactionSummary currentLargeTransactions;
    private BehaviorAnalytics currentTimeOfDay;
    private BehaviorAnalytics currentDayOfWeek;
    private BehaviorAnalytics currentWeekdayWeekend;
    private ForecastResult currentVelocity;
    private String selectedPeriod = "Monthly";
    private com.example.spendtracker.domain.model.analytics.SavingsAnalysis currentSavings;

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
        initialiseScreen();
    }

    private void initialiseScreen() {
        adapter = new AnalyticsSectionAdapter();
        binding.rvAnalyticsSections.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        binding.rvAnalyticsSections.setAdapter(adapter);

        setupChart();
        setupFilters();
        binding.predictionChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry entry, com.github.mikephil.charting.highlight.Highlight highlight) {
                openPredictions(Math.round(entry.getX()));
                binding.predictionChart.highlightValues(null);
            }
            @Override public void onNothingSelected() { }
        });
        binding.btnReviewPredictions.setOnClickListener(v -> openPredictions(2));
        binding.btnPrivacy.setOnClickListener(v -> {
            if (!isLocked()) { viewModel.setPrivacyModeEnabled(true); return; }
            com.example.spendtracker.util.BiometricHelper.authenticate(requireActivity(),
                    new com.example.spendtracker.util.BiometricHelper.BiometricCallback() {
                        @Override public void onSuccess() { viewModel.setPrivacyModeEnabled(false); }
                        @Override public void onError(String error) { }
                    });
        });
        observeViewModel();
        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), locked -> {
            binding.btnPrivacy.setText(isLocked() ? "Show values" : "Hide values");
            if (isLocked()) maskScreen();
            else {
                for (com.github.mikephil.charting.charts.Chart<?> chart : new com.github.mikephil.charting.charts.Chart<?>[]{
                        binding.trendChart, binding.volumeChart, binding.categoryFrequencyChart, binding.predictionChart})
                    chart.setNoDataText("No transactions in this period");
                for (Runnable render : privateRenderers) render.run();
            }
        });
    }

    private final List<Runnable> privateRenderers = new ArrayList<>();
    private boolean isLocked() { return !Boolean.FALSE.equals(viewModel.isPrivacyModeEnabled().getValue()); }
    private <T> void observePrivate(androidx.lifecycle.LiveData<T> source, androidx.lifecycle.Observer<T> renderer) {
        privateRenderers.add(() -> { if (source.getValue() != null) renderer.onChanged(source.getValue()); });
        source.observe(getViewLifecycleOwner(), value -> { if (!isLocked()) renderer.onChanged(value); });
    }
    private void maskScreen() {
        for (android.widget.TextView value : new android.widget.TextView[]{binding.tvTotalIncome,
                binding.tvTotalExpense, binding.tvNetBalance, binding.tvTotalTransfer, binding.tvAverageDailyTransactions,
                binding.tvSelectedPeriodTotal, binding.predictionCount, binding.predictionAccuracy, binding.predictionReview})
            value.setText("***");
        for (com.github.mikephil.charting.charts.Chart<?> chart : new com.github.mikephil.charting.charts.Chart<?>[]{
                binding.trendChart, binding.volumeChart, binding.categoryFrequencyChart, binding.predictionChart}) {
            chart.clear(); chart.setNoDataText("Values hidden"); chart.invalidate();
        }
        adapter.submitList(java.util.Collections.singletonList(new AnalyticsSectionAdapter.CardItem("Private analytics", "***")));
    }

    private void openPredictions(int outcome) {
        if (isLocked()) return;
        if (outcome < 0 || outcome > 2) return;
        Bundle args = new Bundle(); args.putInt("outcome", outcome);
        androidx.navigation.Navigation.findNavController(requireView()).navigate(com.example.spendtracker.R.id.predictionTransactionsFragment, args);
    }

    private void setupFilters() {
        AnalyticsGranularity retained = viewModel.getGranularity().getValue();
        binding.chipGroupGranularity.check(retained == AnalyticsGranularity.DAY ? binding.chipDaily.getId()
                : retained == AnalyticsGranularity.WEEK ? binding.chipWeekly.getId()
                : retained == AnalyticsGranularity.YEAR ? binding.chipAnnual.getId() : binding.chipMonthly.getId());
        binding.chipGroupGranularity.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.chipDaily.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.DAY);
                selectedPeriod = "Daily";
            } else if (checkedId == binding.chipWeekly.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.WEEK);
                selectedPeriod = "Weekly";
            } else if (checkedId == binding.chipMonthly.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.MONTH);
                selectedPeriod = "Monthly";
            } else if (checkedId == binding.chipAnnual.getId()) {
                viewModel.setGranularity(AnalyticsGranularity.YEAR);
                selectedPeriod = "Yearly";
            }
        });
        viewModel.getGranularity().observe(getViewLifecycleOwner(), value -> {
            selectedPeriod = value == AnalyticsGranularity.DAY ? "Daily" : value == AnalyticsGranularity.WEEK
                    ? "Weekly" : value == AnalyticsGranularity.YEAR ? "Yearly" : "Monthly";
            binding.tvSelectedPeriodLabel.setText(selectedPeriod + " transactions");
            binding.tvMoneyFlowTitle.setText("Money in and out · " + selectedPeriod);
            renderInsightSections();
        });

        java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        binding.btnDateFilter.setText(date.format(new java.util.Date(viewModel.getStartDate())) + " – "
                + date.format(new java.util.Date(viewModel.getEndDate() - 1)));
        binding.btnDateFilter.setOnClickListener(v -> showDateRangePicker());
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        viewModel.getDateRange().observe(getViewLifecycleOwner(), ignored -> {
            if (binding == null) return;
            currentSavings = null;
            currentCategoryGrowth = new ArrayList<>();
            renderInsightSections();
            binding.btnDateFilter.setText(date.format(new java.util.Date(viewModel.getStartDate())) + " – "
                    + date.format(new java.util.Date(viewModel.getEndDate() - 1)));
        });
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select analytics date range")
                .setSelection(Pair.create(utcPickerDate(viewModel.getStartDate()), utcPickerDate(viewModel.getEndDate() - 1)))
                .build();
        picker.addOnPositiveButtonClickListener(range -> {
            if (binding == null || range == null || range.first == null || range.second == null) return;
            // MaterialDatePicker returns UTC date labels; convert them to local day boundaries.
            Calendar localEnd = Calendar.getInstance();
            localEnd.setTimeInMillis(localPickerDate(range.second));
            localEnd.add(Calendar.DAY_OF_MONTH, 1);
            viewModel.setDateRange(localPickerDate(range.first), localEnd.getTimeInMillis());
            binding.btnDateFilter.setText(picker.getHeaderText());
        });
        picker.show(getParentFragmentManager(), "analytics_date_range");
    }

    private static long utcPickerDate(long localMillis) {
        Calendar local = Calendar.getInstance(); local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private static long localPickerDate(long utcMillis) {
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance(); local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
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
        chart.getLegend().setTextColor(requireContext().getColor(com.example.spendtracker.R.color.dashboard_text_secondary));
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), com.example.spendtracker.R.color.dashboard_text_secondary));

        chart.getAxisRight().setEnabled(false);
        chart.setNoDataText("No transactions in this period");
        chart.setNoDataTextColor(requireContext().getColor(com.example.spendtracker.R.color.dashboard_text_secondary));
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setGridColor(requireContext().getColor(com.example.spendtracker.R.color.dashboard_dark_outline));
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), com.example.spendtracker.R.color.dashboard_text_secondary));
    }

    private void observeViewModel() {
        observePrivate(viewModel.getSavingsAnalysis(), value -> {
            currentSavings = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getPredictionInsights(), stats -> {
            binding.predictionCount.setText(stats.predicted + " predictions\n" + stats.evaluated + " reviewed");
            binding.predictionAccuracy.setText(stats.evaluated == 0 ? "Not enough data yet"
                    : String.format(Locale.getDefault(), "%.1f%% accuracy", stats.accuracy()));
            binding.predictionReview.setText(stats.evaluated == 0
                    ? (stats.predicted - stats.evaluated) + " awaiting review · retained / corrected unavailable"
                    : String.format(Locale.getDefault(), "%.1f%% retained · %.1f%% corrected · %d awaiting review",
                        stats.accuracy(), stats.correctedPercent(), stats.predicted - stats.evaluated));
            configureCartesianChart(binding.predictionChart, false);
            binding.predictionChart.setNoDataTextColor(requireContext().getColor(com.example.spendtracker.R.color.dashboard_text_secondary));
            binding.predictionChart.setNoDataText("No tracked predictions yet");
            if (stats.predicted == 0) { binding.predictionChart.clear(); return; }
            List<BarEntry> entries = new ArrayList<>();
            entries.add(new BarEntry(0, stats.retained));
            entries.add(new BarEntry(1, stats.evaluated - stats.retained));
            entries.add(new BarEntry(2, stats.predicted - stats.evaluated));
            BarDataSet set = new BarDataSet(entries, "Prediction outcomes");
            set.setColors(requireContext().getColor(com.example.spendtracker.R.color.dashboard_income_blue),
                    requireContext().getColor(com.example.spendtracker.R.color.dashboard_expense_red),
                    requireContext().getColor(com.example.spendtracker.R.color.dashboard_brand_transfer));
            set.setValueTextColor(requireContext().getColor(com.example.spendtracker.R.color.dashboard_text_primary));
            set.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) { return String.valueOf(Math.round(value)); }
            });
            binding.predictionChart.getAxisLeft().setAxisMinimum(0);
            binding.predictionChart.getAxisLeft().setGranularity(1f);
            binding.predictionChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(new String[]{"Retained", "Corrected", "Unreviewed"}));
            binding.predictionChart.getXAxis().setLabelCount(3);
            binding.predictionChart.setData(new BarData(set));
            binding.predictionChart.setFitBars(true);
            binding.predictionChart.notifyDataSetChanged();
            binding.predictionChart.invalidate();
        });
        observePrivate(viewModel.getFinancialOverview(), summary -> {
            if (summary != null) {
                binding.tvTotalIncome.setText(formatAmount(summary.getTotalIncome()));
                binding.tvTotalExpense.setText(formatAmount(summary.getTotalExpense()));
                binding.tvNetBalance.setText(formatAmount(summary.getNetBalance()));
                binding.tvTotalTransfer.setText(formatAmount(summary.getTotalTransfer()));
                binding.tvAverageDailyTransactions.setText(String.format(Locale.getDefault(), "%.1f / day",
                        summary.getAverageDailyTransactions()));
            }
        });

        observePrivate(viewModel.getIncomeExpenseTrend(), trend -> {
            if (trend != null && !trend.isEmpty()) {
                List<Entry> incomeEntries = new ArrayList<>();
                List<Entry> expenseEntries = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < trend.size(); i++) {
                    incomeEntries.add(new Entry(i, (float) trend.get(i).getValue()));
                    expenseEntries.add(new Entry(i, (float) trend.get(i).getSecondaryValue()));
                    labels.add(trend.get(i).getLabel());
                }
                LineDataSet incomeSet = createLineSet(incomeEntries, "Credits", com.example.spendtracker.R.color.dashboard_income_blue);
                LineDataSet expenseSet = createLineSet(expenseEntries, "Debits", com.example.spendtracker.R.color.dashboard_expense_red);
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

        observePrivate(viewModel.getTransactionFrequency(), points -> {
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
            dataSet.setColor(ContextCompat.getColor(requireContext(), com.example.spendtracker.R.color.dashboard_income_blue));
            dataSet.setDrawValues(false);
            binding.volumeChart.setData(new BarData(dataSet));
            binding.volumeChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            binding.volumeChart.invalidate();
        });

        observePrivate(viewModel.getTransactionVolume(), volume -> {
            if (volume == null) return;
            binding.tvSelectedPeriodTotal.setText(String.format(Locale.getDefault(), "%d", volume.getTotalCount()));
            binding.tvSelectedPeriodLabel.setText(selectedPeriod + " transactions");
        });

        observePrivate(viewModel.getExpenseCategoryAnalytics(), categories -> {
            currentCategories = categories == null ? new ArrayList<>() : new ArrayList<>(categories);
            renderCategoryFrequency(categories);
            renderInsightSections();
        });
        observePrivate(viewModel.getFinancialInsights(), insights -> {
            currentInsights = insights == null ? new ArrayList<>() : new ArrayList<>(insights);
            renderInsightSections();
        });
        observePrivate(viewModel.getMonthOverMonthComparison(), comparison -> {
            currentComparison = comparison;
            renderInsightSections();
        });
        observePrivate(viewModel.getRollingExpenseAverage(), rollingAverage -> {
            currentRollingAverage = rollingAverage;
            renderInsightSections();
        });
        observePrivate(viewModel.getDayNightAnalytics(), value -> {
            currentDayNight = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getSpendingForecast(), value -> {
            currentForecast = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getMerchantAnalytics(), value -> {
            currentMerchants = value == null ? new ArrayList<>() : new ArrayList<>(value);
            renderInsightSections();
        });
        observePrivate(viewModel.getRecurringMerchants(), value -> {
            currentRecurringMerchants = value == null ? new ArrayList<>() : new ArrayList<>(value);
            renderInsightSections();
        });
        observePrivate(viewModel.getCategoryGrowth(), value -> {
            currentCategoryGrowth = value == null ? new ArrayList<>() : new ArrayList<>(value);
            renderInsightSections();
        });
        observePrivate(viewModel.getSpendingConcentration(), value -> {
            currentConcentration = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getLargeTransactionAnalysis(), value -> {
            currentLargeTransactions = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getTimeOfDayAnalytics(), value -> {
            currentTimeOfDay = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getDayOfWeekAnalytics(), value -> {
            currentDayOfWeek = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getWeekdayWeekendAnalytics(), value -> {
            currentWeekdayWeekend = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getSpendingVelocity(), value -> {
            currentVelocity = value;
            renderInsightSections();
        });
        observePrivate(viewModel.getUnusualTransactions(), value -> {
            currentAnomalies = value == null ? new ArrayList<>() : new ArrayList<>(value);
            renderInsightSections();
        });
    }

    private LineDataSet createLineSet(List<Entry> entries, String label, int color) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(ContextCompat.getColor(requireContext(), color));
        set.setCircleColor(ContextCompat.getColor(requireContext(), color));
        // A trend should read as a continuous in/out line, not disconnected dots.
        set.setDrawCircles(entries.size() == 1);
        set.setDrawValues(false);
        set.setLineWidth(2.5f);
        set.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
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
        dataSet.setColor(ContextCompat.getColor(requireContext(), com.example.spendtracker.R.color.dashboard_brand_primary));
        dataSet.setDrawValues(false);
        binding.categoryFrequencyChart.setData(new BarData(dataSet));
        binding.categoryFrequencyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.categoryFrequencyChart.invalidate();
    }

    private void renderInsightSections() {
        if (binding == null || adapter == null) return;
        if (isLocked()) { maskScreen(); return; }
        List<AnalyticsSectionAdapter.SectionItem> items = new ArrayList<>();
        if (currentSavings != null) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Savings insights"));
            if (!currentSavings.hasCurrentCashFlow()) {
                items.add(new AnalyticsSectionAdapter.CardItem("No recorded income or expenses", "Add income and expenses to see savings insights for this period."));
            } else {
                double surplus = currentSavings.current.getNetBalance();
                items.add(new AnalyticsSectionAdapter.CardItem(surplus < 0 ? "Recorded deficit" : "Recorded surplus",
                        formatAmount(Math.abs(surplus)) + (surplus < 0 ? " more spent than earned." : " left after expenses.")
                                + " Transfers excluded; this is not your bank savings balance."));
                items.add(new AnalyticsSectionAdapter.CardItem("Savings rate", currentSavings.hasSavingsRate()
                        ? String.format(Locale.getDefault(), "%.1f%% of recorded income", currentSavings.savingsRate())
                        : "Unavailable — no positive income recorded in this period."));
                if (currentSavings.hasSavingsRate()) {
                    items.add(new AnalyticsSectionAdapter.CardItem("Income spent", String.format(Locale.getDefault(),
                            "%.1f%% of recorded income went to expenses", currentSavings.expenseRatio())));
                }
                if (currentSavings.hasPreviousCashFlow()) {
                    double change = currentSavings.surplusChange();
                    items.add(new AnalyticsSectionAdapter.CardItem("Surplus change", formatAmount(Math.abs(change))
                            + (change >= 0 ? " higher" : " lower") + " than " + comparisonDates()));
                    if (currentSavings.hasRateComparison()) {
                        items.add(new AnalyticsSectionAdapter.CardItem("Savings rate change", String.format(Locale.getDefault(),
                                "%+.1f percentage points vs %s", currentSavings.rateChangePoints(), comparisonDates())));
                    }
                } else {
                    items.add(new AnalyticsSectionAdapter.CardItem("Savings comparison", "No recorded income or expenses in " + comparisonDates()));
                }
                if (currentSavings.current.getTotalExpense() > 0) {
                    items.add(new AnalyticsSectionAdapter.CardItem("What if you spent 10% less?",
                            formatAmount(currentSavings.current.getTotalExpense() * 0.1)
                                    + " more could remain this period, with income unchanged. Illustration only."));
                }
            }
        }
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
        addBehaviorPeak(items, "Time patterns", "Highest-spend time", currentTimeOfDay);
        addBehaviorPeak(items, "Weekly patterns", "Highest-spend day", currentDayOfWeek);
        addBehaviorPeak(items, "Weekday vs weekend", "Higher-spend period", currentWeekdayWeekend);
        if (currentVelocity != null) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Pace comparison"));
            items.add(new AnalyticsSectionAdapter.CardItem("Compared with last month",
                    formatAmount(currentVelocity.getDailyRate()) + " per day · " + currentVelocity.getVelocityInsight()));
        }
        if (currentConcentration != null && !currentConcentration.getTopContributors().isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Spending concentration"));
            SpendingConcentration.Contributor top = currentConcentration.getTopContributors().get(0);
            items.add(new AnalyticsSectionAdapter.CardItem(top.getName(), String.format(Locale.getDefault(),
                    "%s · %.1f%% of expenses", formatAmount(top.getAmount()), top.getPercentage())));
        }
        if (!currentCategoryGrowth.isEmpty()) {
            items.add(new AnalyticsSectionAdapter.HeaderItem(selectedPeriod + " category trends"));
            for (CategoryAnalytics growth : currentCategoryGrowth) {
                items.add(new AnalyticsSectionAdapter.CardItem(growth.getCategoryName(), String.format(Locale.getDefault(),
                        "%s · %s", formatAmount(growth.getTotalAmount()), growth.hasGrowthData()
                                ? String.format(Locale.getDefault(), "%+.1f%% vs %s", growth.getGrowthPercent(), comparisonDates())
                                : "No prior spending — percentage unavailable")));
            }
        }
        if (!currentRecurringMerchants.isEmpty()) {
            MerchantAnalytics merchant = currentRecurringMerchants.get(0);
            items.add(new AnalyticsSectionAdapter.HeaderItem("Recurring payments"));
            items.add(new AnalyticsSectionAdapter.CardItem(merchant.getMerchantName(), merchant.getTransactionCount()
                    + " payments recorded"));
        }
        if (currentLargeTransactions != null && currentLargeTransactions.getCount() > 0) {
            items.add(new AnalyticsSectionAdapter.HeaderItem("Large expenses"));
            items.add(new AnalyticsSectionAdapter.CardItem(currentLargeTransactions.getCount() + " above "
                    + formatAmount(currentLargeTransactions.getThreshold()), formatAmount(currentLargeTransactions.getTotalAmount())
                    + " total in this window"));
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

    private void addBehaviorPeak(List<AnalyticsSectionAdapter.SectionItem> items, String heading,
                                 String label, BehaviorAnalytics analytics) {
        if (analytics == null || analytics.getHighestSpendingSegment() == null) return;
        BehaviorAnalytics.Segment peak = analytics.getHighestSpendingSegment();
        if (peak.getTransactionCount() == 0) return;
        items.add(new AnalyticsSectionAdapter.HeaderItem(heading));
        items.add(new AnalyticsSectionAdapter.CardItem(label, peak.getLabel() + " · "
                + formatAmount(peak.getTotalAmount()) + " across " + peak.getTransactionCount() + " payments"));
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

    private String comparisonDates() {
        com.example.spendtracker.domain.model.analytics.ComparisonWindow window =
                com.example.spendtracker.domain.model.analytics.ComparisonWindow.previous(
                        viewModel.getStartDate(), viewModel.getEndDate(), viewModel.getGranularity().getValue());
        java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        return date.format(new java.util.Date(window.start)) + " – " + date.format(new java.util.Date(window.end - 1));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        privateRenderers.clear();
        binding = null;
    }
}
