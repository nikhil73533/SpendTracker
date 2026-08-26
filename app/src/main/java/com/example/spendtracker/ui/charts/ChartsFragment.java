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
import com.example.spendtracker.databinding.FragmentChartsBinding;
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
import com.google.android.material.tabs.TabLayout;
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
public class ChartsFragment extends Fragment {

    private FragmentChartsBinding binding;
    private ChartsViewModel viewModel;
    private CategoryStatsAdapter statsAdapter;
    private CategoryStatsAdapter sourceStatsAdapter;
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    private boolean showingExpenses = true;
    private android.view.GestureDetector gestureDetector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChartsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ChartsViewModel.class);

        setupToolbar();
        setupTabLayout();
        setupRecyclerView();
        setupSwipeNavigation();
        observeViewModel();
    }

    private void setupSwipeNavigation() {
        // Gesture detector for the chart content area: swipe left/right to switch Income/Expense tabs
        gestureDetector = new android.view.GestureDetector(requireContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(@Nullable android.view.MotionEvent e1, @NonNull android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return false;
                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();
                if (Math.abs(deltaX) > Math.abs(deltaY)
                        && Math.abs(deltaX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (deltaX > 0) {
                        // Swipe right → switch to Income tab (index 0)
                        if (showingExpenses) {
                            binding.tabChartType.getTabAt(0).select();
                        } else {
                            // Already on Income, swipe right → previous period
                            viewModel.movePrev();
                        }
                    } else {
                        // Swipe left → switch to Expense tab (index 1)
                        if (!showingExpenses) {
                            binding.tabChartType.getTabAt(1).select();
                        } else {
                            // Already on Expense, swipe left → next period
                            viewModel.moveNext();
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        // Attach swipe listener to the NestedScrollView content only (not charts)
        // We intercept at the root but allow scroll views to handle vertical scrolling
        binding.getRoot().setOnTouchListener((v, event) -> {
            boolean consumed = gestureDetector.onTouchEvent(event);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP && !consumed) {
                v.performClick();
            }
            return false; // Return false so scroll still works
        });
    }

    private void setupToolbar() {
        binding.btnPrevMonth.setOnClickListener(v -> viewModel.movePrev());
        binding.btnNextMonth.setOnClickListener(v -> viewModel.moveNext());
        
        binding.btnGranularity.setOnClickListener(this::showGranularityMenu);
    }

    private void setupTabLayout() {
        binding.tabChartType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingExpenses = tab.getPosition() == 1;
                if (showingExpenses) {
                    binding.tabChartType.setSelectedTabIndicatorColor(requireContext().getColor(R.color.expense_red));
                } else {
                    binding.tabChartType.setSelectedTabIndicatorColor(requireContext().getColor(R.color.income_blue));
                }
                viewModel.setTransactionType(showingExpenses ? "EXPENSE" : "INCOME");
                updateSectionVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        // Initial state: Expenses
        binding.tabChartType.getTabAt(1).select();
    }

    private void updateSectionVisibility() {
        int visibility = showingExpenses ? View.VISIBLE : View.GONE;
        binding.tvTrendsTitle.setVisibility(View.VISIBLE); // Trends are kept for both
        binding.lineChart.setVisibility(View.VISIBLE);
        
        binding.tvWeekendTitle.setVisibility(visibility);
        binding.barChartWeekend.setVisibility(visibility);
        binding.tvBanksTitle.setVisibility(visibility);
        binding.barChartBanks.setVisibility(visibility);
        binding.tvSourceTitle.setVisibility(visibility);
        binding.pieChartSource.setVisibility(visibility);
        binding.rvSourceStats.setVisibility(visibility);
    }

    private void showGranularityMenu(View v) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), v);
        menu.getMenu().add("Weekly");
        menu.getMenu().add("Monthly");
        menu.getMenu().add("Annually");
        
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            binding.btnGranularity.setText(title);
            if ("Weekly".equals(title)) viewModel.setGranularity(ChartsViewModel.Granularity.WEEKLY);
            else if ("Annually".equals(title)) viewModel.setGranularity(ChartsViewModel.Granularity.ANNUALLY);
            else viewModel.setGranularity(ChartsViewModel.Granularity.MONTHLY);
            return true;
        });
        menu.show();
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
        // Reuse categoryDetailFragment with a special "__source__:" prefix to distinguish source filter
        Bundle args = new Bundle();
        args.putString("categoryName", "__source__:" + sourceType);
        try {
            Navigation.findNavController(requireView()).navigate(R.id.action_chartsFragment_to_categoryDetailFragment, args);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(), "Transactions: " + sourceType, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void observeViewModel() {
        viewModel.getCurrentMonthStart().observe(getViewLifecycleOwner(), start -> {
            updateHeaderLabel();
        });

        viewModel.getGranularity().observe(getViewLifecycleOwner(), g -> {
            binding.tvTrendsTitle.setText(g.name().substring(0, 1) + g.name().substring(1).toLowerCase() + " Trends");
            updateHeaderLabel();
        });

        viewModel.getChartData().observe(getViewLifecycleOwner(), summary -> {
            updateUIWithData(summary);
            binding.pieChartMain.invalidate();
        });

        viewModel.getDailyTrends().observe(getViewLifecycleOwner(), trends -> {
            setupLineChart(trends);
            binding.lineChart.invalidate();
        });

        viewModel.getWeekdayWeekendTotals().observe(getViewLifecycleOwner(), data -> {
            setupBarChart(binding.barChartWeekend, data, "Weekend vs Weekday");
        });

        viewModel.getBankTotals().observe(getViewLifecycleOwner(), data -> {
            setupBarChart(binding.barChartBanks, data, "Bank Totals");
        });

        viewModel.getSourceTypeTotals().observe(getViewLifecycleOwner(), data -> {
            Map<String, Double> breakdown = mapSourceLabels(data);
            double total = calculateTotal(data);
            setupSourcePieChart(binding.pieChartSource, breakdown, total, ColorTemplate.VORDIPLOM_COLORS);
            updateSourceStatsList(breakdown, total, ColorTemplate.VORDIPLOM_COLORS);
        });

        updateSectionVisibility();

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            // Re-render all charts with current data (formatters handle masking)
            Summary summary = viewModel.getChartData().getValue();
            if (summary != null) {
                updateUIWithData(summary);
                binding.pieChartMain.invalidate();
            }
            List<com.example.spendtracker.domain.model.DailyTrend> trends = viewModel.getDailyTrends().getValue();
            if (trends != null) {
                setupLineChart(trends);
                binding.lineChart.invalidate();
            }
        });
    }

    /** Returns true when biometric privacy mode is active and sensitive values should be masked. */
    private boolean isPrivacyActive() {
        return Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
    }

    /** Returns a ValueFormatter that masks values with *** when privacy mode is active. */
    private com.github.mikephil.charting.formatter.ValueFormatter getMaskedValueFormatter() {
        return new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return isPrivacyActive() ? "***" : String.format(Locale.getDefault(), "%.0f", value);
            }
        };
    }

    /** Returns a PercentFormatter that masks values with *** when privacy mode is active. */
    private com.github.mikephil.charting.formatter.ValueFormatter getMaskedPercentFormatter(PieChart chart) {
        return new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return isPrivacyActive() ? "***" : String.format(Locale.getDefault(), "%.1f%%", value);
            }
        };
    }

    private void updateHeaderLabel() {
        Long start = viewModel.getCurrentMonthStart().getValue();
        ChartsViewModel.Granularity g = viewModel.getGranularity().getValue();
        if (start == null || g == null) return;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        
        String label;
        if (g == ChartsViewModel.Granularity.ANNUALLY) {
            label = new SimpleDateFormat("yyyy", Locale.getDefault()).format(cal.getTime());
        } else if (g == ChartsViewModel.Granularity.WEEKLY) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
            String startDate = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 6);
            String endDate = sdf.format(cal.getTime());
            label = startDate + " – " + endDate; // Using en-dash as per requirement example
        } else {
            label = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.getTime());
        }
        binding.tvChartRange.setText(label);
    }

    private void updateUIWithData(Summary summary) {
        if (summary == null) return;
        
        Map<String, Double> breakdown = showingExpenses ? summary.getExpenseBreakdown() : summary.getIncomeBreakdown();
        double total = showingExpenses ? summary.getTotalExpense() : summary.getTotalIncome();
        int[] colors = showingExpenses ? ColorTemplate.COLORFUL_COLORS : ColorTemplate.JOYFUL_COLORS;

        // Update Tab Text with Total (masked when privacy mode is on)
        TabLayout.Tab expenseTab = binding.tabChartType.getTabAt(1);
        if (expenseTab != null) expenseTab.setText("Expenses " + viewModel.formatAmount(summary.getTotalExpense()));
        
        TabLayout.Tab incomeTab = binding.tabChartType.getTabAt(0);
        if (incomeTab != null) incomeTab.setText("Income " + viewModel.formatAmount(summary.getTotalIncome()));

        setupPieChart(binding.pieChartMain, breakdown, total, colors);
        updateStatsList(breakdown, total, colors);
    }

    private void updateStatsList(Map<String, Double> breakdown, double total, int[] baseColors) {
        statsAdapter.submitList(createStatsList(breakdown, total, baseColors));
    }

    private void updateSourceStatsList(Map<String, Double> breakdown, double total, int[] baseColors) {
        sourceStatsAdapter.submitList(createStatsList(breakdown, total, baseColors));
    }

    private List<CategoryStatsAdapter.CategoryStat> createStatsList(Map<String, Double> breakdown, double total, int[] baseColors) {
        List<CategoryStatsAdapter.CategoryStat> stats = new ArrayList<>();
        if (breakdown == null || total == 0) {
            return stats;
        }

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

    /** Source pie chart with sourceType-based navigation on click. */
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
        chart.setHighlightPerTapEnabled(true);

        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry) {
                    navigateToSourceDetail(((PieEntry) e).getLabel());
                    chart.highlightValue(null);
                }
            }
            @Override
            public void onNothingSelected() {}
        });

        chart.animateY(1200);
        chart.invalidate();
    }

    private void setupPieChart(PieChart chart, Map<String, Double> breakdown, double total, int[] baseColors) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (breakdown != null && total > 0) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(breakdown.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Double> entry = sorted.get(i);
                // Requirement: Do NOT display labels for categories having 0%
                if (entry.getValue() <= 0) continue;
                
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
                colors.add(baseColors[i % baseColors.length]);
            }
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No data for " + (showingExpenses ? "expenses" : "income"));
            chart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f); 
        
        // Requirement: Rework label positioning so labels are distributed sufficiently far apart
        // and change connection-line angle/position
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
        chart.setDragDecelerationFrictionCoef(0.95f);
        chart.setRotationEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setRotationAngle(0); 

        // Requirement 9: Chart category navigation
        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry) {
                    navigateToCategoryDetail(((PieEntry) e).getLabel());
                    chart.highlightValue(null); // Clear highlight to prevent stale state on return
                }
            }

            @Override
            public void onNothingSelected() {}
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
        dataSet.setColor(showingExpenses ? Color.parseColor("#FF5252") : Color.parseColor("#2196F3"));
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
                // Preserve the actual sourceType value as-is so navigation can filter by it
                String label = (sum.category == null || sum.category.isEmpty()) ? "Other" : sum.category;
                map.put(label, map.getOrDefault(label, 0.0) + sum.total);
            }
        }
        return map;
    }

    private Map<String, Double> convertToMap(List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum> data) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (data != null) {
            for (com.example.spendtracker.data.local.dao.TransactionDao.CategorySum sum : data) {
                map.put(sum.category, sum.total);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
