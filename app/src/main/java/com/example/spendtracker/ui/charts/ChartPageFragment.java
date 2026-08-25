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
import com.example.spendtracker.databinding.FragmentChartPageBinding;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.DailyTrend;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.LineChart;
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
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;

@AndroidEntryPoint
public class ChartPageFragment extends Fragment {

    private static final String ARG_IS_EXPENSE = "is_expense";
    private FragmentChartPageBinding binding;
    private ChartsViewModel viewModel;
    private CategoryStatsAdapter statsAdapter;
    private CategoryStatsAdapter sourceStatsAdapter;
    private boolean isExpense;
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

    public static ChartPageFragment newInstance(boolean isExpense) {
        ChartPageFragment fragment = new ChartPageFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_EXPENSE, isExpense);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isExpense = getArguments().getBoolean(ARG_IS_EXPENSE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChartPageBinding.inflate(inflater, container, false);
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

    private void updateSectionVisibility() {
        int visibility = isExpense ? View.VISIBLE : View.GONE;
        binding.tvWeekendTitle.setVisibility(visibility);
        binding.barChartWeekend.setVisibility(visibility);
        binding.tvBanksTitle.setVisibility(visibility);
        binding.barChartBanks.setVisibility(visibility);
        binding.tvSourceTitle.setVisibility(visibility);
        binding.pieChartSource.setVisibility(visibility);
        binding.rvSourceStats.setVisibility(visibility);
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
        } catch (Exception e) {}
    }

    private void navigateToSourceDetail(String sourceType) {
        Bundle args = new Bundle();
        args.putString("categoryName", "__source__:" + sourceType);
        try {
            Navigation.findNavController(requireView()).navigate(R.id.action_chartsFragment_to_categoryDetailFragment, args);
        } catch (Exception e) {}
    }

    private void observeViewModel() {
        viewModel.getChartData().observe(getViewLifecycleOwner(), summary -> {
            boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
            updateUIWithData(summary, masked);
        });

        viewModel.getDailyTrends().observe(getViewLifecycleOwner(), trends -> {
            boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
            setupLineChart(trends, masked);
        });

        if (isExpense) {
            viewModel.getWeekdayWeekendTotals().observe(getViewLifecycleOwner(), data -> {
                boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
                setupBarChart(binding.barChartWeekend, data, "Weekend vs Weekday", masked);
            });

            viewModel.getBankTotals().observe(getViewLifecycleOwner(), data -> {
                boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
                setupBarChart(binding.barChartBanks, data, "Bank Totals", masked);
            });

            viewModel.getSourceTypeTotals().observe(getViewLifecycleOwner(), data -> {
                boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
                updateSourceUI(data, masked);
            });
        }

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            Summary summary = viewModel.getChartData().getValue();
            if (summary != null) updateUIWithData(summary, enabled);
            List<DailyTrend> trends = viewModel.getDailyTrends().getValue();
            if (trends != null) setupLineChart(trends, enabled);
            
            if (isExpense) {
                updateSourceUI(viewModel.getSourceTypeTotals().getValue(), enabled);
                setupBarChart(binding.barChartWeekend, viewModel.getWeekdayWeekendTotals().getValue(), "Weekend vs Weekday", enabled);
                setupBarChart(binding.barChartBanks, viewModel.getBankTotals().getValue(), "Bank Totals", enabled);
            }
        });
    }

    private void updateUIWithData(Summary summary, boolean masked) {
        if (summary == null) return;
        Map<String, Double> breakdown = isExpense ? summary.getExpenseBreakdown() : summary.getIncomeBreakdown();
        double total = isExpense ? summary.getTotalExpense() : summary.getTotalIncome();
        int[] colors = isExpense ? ColorTemplate.COLORFUL_COLORS : ColorTemplate.JOYFUL_COLORS;

        setupPieChart(binding.pieChartMain, breakdown, total, colors, masked);
        updateStatsList(breakdown, total, colors, masked);
    }

    private void updateSourceUI(List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum> data, boolean masked) {
        if (data == null) return;
        Map<String, Double> breakdown = mapSourceLabels(data);
        double total = calculateTotal(data);
        setupSourcePieChart(binding.pieChartSource, breakdown, total, ColorTemplate.VORDIPLOM_COLORS, masked);
        sourceStatsAdapter.submitList(createStatsList(breakdown, total, ColorTemplate.VORDIPLOM_COLORS, masked));
    }

    private void updateStatsList(Map<String, Double> breakdown, double total, int[] baseColors, boolean masked) {
        statsAdapter.submitList(createStatsList(breakdown, total, baseColors, masked));
    }

    private List<CategoryStatsAdapter.CategoryStat> createStatsList(Map<String, Double> breakdown, double total, int[] baseColors, boolean masked) {
        List<CategoryStatsAdapter.CategoryStat> stats = new ArrayList<>();
        if (breakdown == null || total == 0) return stats;

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            int percentage = (int) Math.round((entry.getValue() / total) * 100);
            int color = baseColors[i % baseColors.length];
            String label = masked ? "Category " + (i+1) : entry.getKey();
            stats.add(new CategoryStatsAdapter.CategoryStat(label, entry.getValue(), percentage, color));
        }
        return stats;
    }

    private void setupPieChart(PieChart chart, Map<String, Double> breakdown, double total, int[] baseColors, boolean masked) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (breakdown != null && total > 0) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Double> entry = sorted.get(i);
                if (entry.getValue() <= 0) continue;
                String label = masked ? "Src " + (i + 1) : entry.getKey();
                entries.add(new PieEntry(entry.getValue().floatValue(), label));
                colors.add(baseColors[i % baseColors.length]);
            }
        }
        configurePieChart(chart, entries, colors, masked);
    }

    private void setupSourcePieChart(PieChart chart, Map<String, Double> breakdown, double total, int[] baseColors, boolean masked) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (breakdown != null && total > 0) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Double> entry = sorted.get(i);
                if (entry.getValue() <= 0) continue;
                String label = masked ? "Src " + (i + 1) : entry.getKey();
                entries.add(new PieEntry(entry.getValue().floatValue(), label));
                colors.add(baseColors[i % baseColors.length]);
            }
        }
        configurePieChart(chart, entries, colors, masked);
    }

    private void configurePieChart(PieChart chart, List<PieEntry> entries, List<Integer> colors, boolean masked) {
        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No data");
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
        dataSet.setDrawValues(true);
        dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.PercentFormatter(chart));

        chart.setData(new PieData(dataSet));
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(50f);
        chart.setDrawEntryLabels(true);
        chart.setEntryLabelColor(Color.LTGRAY);
        chart.animateY(1000);
        chart.invalidate();
    }

    private void setupLineChart(List<DailyTrend> trends, boolean masked) {
        if (trends == null || binding.lineChart == null) return;
        List<Entry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        for (int i = 0; i < trends.size(); i++) {
            entries.add(new Entry(i, (float) trends.get(i).getAmount()));
            xLabels.add(masked ? "T" + (i+1) : monthYearFormat.format(new Date(trends.get(i).getTimestamp())));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Trend");
        dataSet.setColor(isExpense ? Color.RED : Color.BLUE);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        
        binding.lineChart.setData(new LineData(dataSet));
        binding.lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        binding.lineChart.getAxisLeft().setDrawLabels(true);
        if (masked) {
            binding.lineChart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) { return "***"; }
            });
        } else {
            binding.lineChart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.DefaultAxisValueFormatter(0));
        }
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.invalidate();
    }

    private void setupBarChart(BarChart chart, List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum> data, String label, boolean masked) {
        if (data == null || data.isEmpty() || chart == null) return;
        List<BarEntry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new BarEntry(i, (float) data.get(i).total));
            xLabels.add(masked ? "L" + (i+1) : data.get(i).category);
        }
        BarDataSet dataSet = new BarDataSet(entries, label);
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setDrawValues(!masked);
        chart.setData(new BarData(dataSet));
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        chart.getAxisLeft().setDrawLabels(true);
        if (masked) {
            chart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) { return "***"; }
            });
        } else {
            chart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.DefaultAxisValueFormatter(0));
        }
        chart.getDescription().setEnabled(false);
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
        if (data != null) for (com.example.spendtracker.data.local.dao.TransactionDao.CategorySum sum : data) total += sum.total;
        return total;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
