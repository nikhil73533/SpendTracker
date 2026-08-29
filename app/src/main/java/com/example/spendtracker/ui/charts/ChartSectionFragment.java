package com.example.spendtracker.ui.charts;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentChartSectionBinding;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.DailyTrend;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;

public class ChartSectionFragment extends Fragment {

    private static final String ARG_TYPE = "transaction_type";
    private FragmentChartSectionBinding binding;
    private ChartsViewModel viewModel;
    private CategoryStatsAdapter statsAdapter;
    private CategoryStatsAdapter sourceStatsAdapter;
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    private String transactionType;

    public static ChartSectionFragment newInstance(String type) {
        ChartSectionFragment fragment = new ChartSectionFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            transactionType = getArguments().getString(ARG_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChartSectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(ChartsViewModel.class);

        setupRecyclerView();
        observeViewModel();
        updateSectionVisibility();
    }

    private void setupRecyclerView() {
        statsAdapter = new CategoryStatsAdapter(category -> navigateToCategoryDetail(category), amount -> viewModel.formatAmount(amount));
        binding.rvStats.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.rvStats.setAdapter(statsAdapter);

        sourceStatsAdapter = new CategoryStatsAdapter(sourceType -> navigateToSourceDetail(sourceType), amount -> viewModel.formatAmount(amount));
        binding.rvSourceStats.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.rvSourceStats.setAdapter(sourceStatsAdapter);
    }

    private void navigateToCategoryDetail(String category) {
        Bundle args = new Bundle();
        args.putString("categoryName", category);
        try {
            Navigation.findNavController(requireView()).navigate(R.id.action_chartsFragment_to_categoryDetailFragment, args);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(), "Coming soon: " + category, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToSourceDetail(String sourceType) {
        Bundle args = new Bundle();
        args.putString("categoryName", "__source__:" + sourceType);
        try {
            Navigation.findNavController(requireView()).navigate(R.id.action_chartsFragment_to_categoryDetailFragment, args);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(), "Transactions: " + sourceType, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void observeViewModel() {
        viewModel.getChartData().observe(getViewLifecycleOwner(), summary -> {
            updateUIWithData(summary);
            binding.pieChartMain.invalidate();
        });

        viewModel.getDailyTrends(transactionType).observe(getViewLifecycleOwner(), trends -> {
            setupLineChart(trends);
            binding.lineChart.invalidate();
        });

        viewModel.getWeekdayWeekendTotals(transactionType).observe(getViewLifecycleOwner(), data -> {
            setupBarChart(binding.barChartWeekend, data, "Weekend vs Weekday");
        });

        viewModel.getBankTotals(transactionType).observe(getViewLifecycleOwner(), data -> {
            setupBarChart(binding.barChartBanks, data, "Bank Totals");
        });

        viewModel.getSourceTypeTotals(transactionType).observe(getViewLifecycleOwner(), data -> {
            Map<String, Double> breakdown = mapSourceLabels(data);
            double total = calculateTotal(data);
            setupSourcePieChart(binding.pieChartSource, breakdown, total, ColorTemplate.VORDIPLOM_COLORS);
            updateSourceStatsList(breakdown, total, ColorTemplate.VORDIPLOM_COLORS);
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            Summary summary = viewModel.getChartData().getValue();
            if (summary != null) {
                updateUIWithData(summary);
                binding.pieChartMain.invalidate();
            }
            List<DailyTrend> trends = viewModel.getDailyTrends(transactionType).getValue();
            if (trends != null) {
                setupLineChart(trends);
                binding.lineChart.invalidate();
            }
        });
    }

    private void updateSectionVisibility() {
        boolean showingExpenses = "EXPENSE".equals(transactionType);
        int visibility = showingExpenses ? View.VISIBLE : View.GONE;
        
        binding.tvWeekendTitle.setVisibility(visibility);
        binding.barChartWeekend.setVisibility(visibility);
        binding.tvBanksTitle.setVisibility(visibility);
        binding.barChartBanks.setVisibility(visibility);
        binding.tvSourceTitle.setVisibility(visibility);
        binding.pieChartSource.setVisibility(visibility);
        binding.rvSourceStats.setVisibility(visibility);
    }

    private void updateUIWithData(Summary summary) {
        if (summary == null) return;
        
        boolean isExpense = "EXPENSE".equals(transactionType);
        Map<String, Double> breakdown = isExpense ? summary.getExpenseBreakdown() : summary.getIncomeBreakdown();
        double total = isExpense ? summary.getTotalExpense() : summary.getTotalIncome();
        int[] colors = isExpense ? ColorTemplate.COLORFUL_COLORS : ColorTemplate.JOYFUL_COLORS;

        setupPieChart(binding.pieChartMain, breakdown, total, colors);
        statsAdapter.submitList(createStatsList(breakdown, total, colors));
    }

    private List<CategoryStatsAdapter.CategoryStat> createStatsList(Map<String, Double> breakdown, double total, int[] baseColors) {
        List<CategoryStatsAdapter.CategoryStat> stats = new ArrayList<>();
        if (breakdown == null || total == 0) return stats;

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            int percentage = (int) Math.round((entry.getValue() / total) * 100);
            int color = baseColors[i % baseColors.length];
            stats.add(new CategoryStatsAdapter.CategoryStat(entry.getKey(), entry.getValue(), percentage, color));
        }
        return stats;
    }

    private void updateSourceStatsList(Map<String, Double> breakdown, double total, int[] baseColors) {
        sourceStatsAdapter.submitList(createStatsList(breakdown, total, baseColors));
    }

    private void setupPieChart(PieChart chart, Map<String, Double> breakdown, double total, int[] baseColors) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (breakdown != null && total > 0) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Double> entry = sorted.get(i);
                if (entry.getValue() <= 0) continue;
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
                colors.add(baseColors[i % baseColors.length]);
            }
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No data available");
            chart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(70f);
        dataSet.setValueLinePart1Length(0.6f);
        dataSet.setValueLinePart2Length(0.6f);
        dataSet.setValueLineVariableLength(true);
        dataSet.setValueLineColor(Color.LTGRAY);
        dataSet.setUsingSliceColorAsValueLineColor(true);
        dataSet.setValueTextColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(getMaskedPercentFormatter(chart));

        PieData pieData = new PieData(dataSet);
        chart.setData(pieData);
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(50f);
        chart.setTransparentCircleRadius(55f);
        chart.setEntryLabelColor(Color.LTGRAY);
        chart.setEntryLabelTextSize(11f);
        chart.setDrawEntryLabels(true);
        chart.setExtraOffsets(35, 10, 35, 10);

        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry) {
                    navigateToCategoryDetail(((PieEntry) e).getLabel());
                    chart.highlightValue(null);
                }
            }
            @Override public void onNothingSelected() {}
        });

        chart.animateY(1200);
        chart.invalidate();
    }

    private void setupSourcePieChart(PieChart chart, Map<String, Double> breakdown, double total, int[] baseColors) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (breakdown != null && total > 0) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Double> entry = sorted.get(i);
                if (entry.getValue() <= 0) continue;
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
                colors.add(baseColors[i % baseColors.length]);
            }
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No source data");
            chart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1Length(0.6f);
        dataSet.setValueLinePart2Length(0.6f);
        dataSet.setValueTextColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(getMaskedPercentFormatter(chart));

        PieData pieData = new PieData(dataSet);
        chart.setData(pieData);
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(50f);
        chart.setTransparentCircleRadius(55f);
        chart.setEntryLabelColor(Color.LTGRAY);
        chart.setEntryLabelTextSize(11f);
        chart.setDrawEntryLabels(true);
        chart.setExtraOffsets(35, 10, 35, 10);

        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry) {
                    navigateToSourceDetail(((PieEntry) e).getLabel());
                    chart.highlightValue(null);
                }
            }
            @Override public void onNothingSelected() {}
        });

        chart.animateY(1200);
        chart.invalidate();
    }

    private void setupLineChart(List<DailyTrend> trends) {
        if (trends == null) return;

        ChartsViewModel.Granularity g = viewModel.getGranularity().getValue();
        List<Entry> entries = new ArrayList<>();
        final List<String> xLabels = new ArrayList<>();
        
        SimpleDateFormat weekFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());

        for (int i = 0; i < trends.size(); i++) {
            DailyTrend d = trends.get(i);
            String label;
            if (g == ChartsViewModel.Granularity.WEEKLY) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(d.getTimestamp());
                String start = weekFormat.format(cal.getTime());
                cal.add(Calendar.DAY_OF_YEAR, 6);
                String end = weekFormat.format(cal.getTime());
                label = start + " - " + end;
            } else if (g == ChartsViewModel.Granularity.ANNUALLY) {
                label = new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date(d.getTimestamp()));
            } else {
                label = monthYearFormat.format(new Date(d.getTimestamp()));
            }
            xLabels.add(label);
            entries.add(new Entry(i, (float) d.getAmount()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Total Volume");
        dataSet.setColor("EXPENSE".equals(transactionType) ? Color.parseColor("#FF5252") : Color.parseColor("#2196F3"));
        dataSet.setCircleColor(Color.WHITE);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(3.5f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(!isPrivacyActive());
        dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        LineData lineData = new LineData(dataSet);
        binding.lineChart.setData(lineData);
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.getLegend().setEnabled(false);
        
        binding.lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        binding.lineChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        binding.lineChart.getXAxis().setDrawGridLines(false);
        binding.lineChart.getXAxis().setTextColor(Color.GRAY);
        binding.lineChart.getXAxis().setGranularity(1f);
        
        binding.lineChart.getAxisLeft().setTextColor(Color.GRAY);
        binding.lineChart.getAxisLeft().setGridColor(Color.DKGRAY);
        if (isPrivacyActive()) {
            binding.lineChart.getAxisLeft().setValueFormatter(getMaskedValueFormatter());
        }
        binding.lineChart.getAxisRight().setEnabled(false);
        
        binding.lineChart.animateX(1000);
        binding.lineChart.invalidate();
    }

    private void setupBarChart(BarChart chart, List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum> data, String label) {
        if (data == null || data.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No data available");
            chart.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new BarEntry(i, (float) data.get(i).total));
            xLabels.add(data.get(i).category);
        }

        BarDataSet dataSet = new BarDataSet(entries, label);
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(getMaskedValueFormatter());

        BarData barData = new BarData(dataSet);
        chart.setData(barData);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);

        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        chart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setTextColor(Color.GRAY);
        chart.getXAxis().setGranularity(1f);

        chart.getAxisLeft().setTextColor(Color.GRAY);
        chart.getAxisLeft().setGridColor(Color.DKGRAY);
        chart.getAxisRight().setEnabled(false);

        chart.animateY(1000);
        chart.invalidate();
    }

    private Map<String, Double> mapSourceLabels(List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum> data) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (data != null) {
            for (com.example.spendtracker.data.local.dao.TransactionDao.CategorySum sum : data) {
                String label = (sum.category == null || sum.category.isEmpty()) ? "Other" : sum.category;
                map.put(label, map.getOrDefault(label, 0.0) + sum.total);
            }
        }
        return map;
    }

    private double calculateTotal(List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum> data) {
        double total = 0;
        if (data != null) {
            for (com.example.spendtracker.data.local.dao.TransactionDao.CategorySum sum : data) {
                total += sum.total;
            }
        }
        return total;
    }

    private boolean isPrivacyActive() {
        return Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
    }

    private com.github.mikephil.charting.formatter.ValueFormatter getMaskedValueFormatter() {
        return new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return isPrivacyActive() ? "***" : String.format(Locale.getDefault(), "%.0f", value);
            }
        };
    }

    private com.github.mikephil.charting.formatter.ValueFormatter getMaskedPercentFormatter(PieChart chart) {
        return new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return isPrivacyActive() ? "***" : String.format(Locale.getDefault(), "%.1f%%", value);
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
