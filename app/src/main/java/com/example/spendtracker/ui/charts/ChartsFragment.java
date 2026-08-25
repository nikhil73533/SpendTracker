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
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

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
        setupViewPager();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.btnPrevMonth.setOnClickListener(v -> viewModel.movePrev());
        binding.btnNextMonth.setOnClickListener(v -> viewModel.moveNext());
        binding.btnGranularity.setOnClickListener(this::showGranularityMenu);
    }

    private void setupViewPager() {
        ChartPagerAdapter adapter = new ChartPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new com.google.android.material.tabs.TabLayoutMediator(binding.tabChartType, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Income" : "Expenses");
        }).attach();

        binding.tabChartType.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                boolean showingExpenses = tab.getPosition() == 1;
                binding.tabChartType.setSelectedTabIndicatorColor(requireContext().getColor(
                    showingExpenses ? R.color.expense_red : R.color.income_blue));
                viewModel.setTransactionType(showingExpenses ? "EXPENSE" : "INCOME");
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        // Default to Expenses
        binding.viewPager.setCurrentItem(1, false);
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

    private void observeViewModel() {
        viewModel.getCurrentMonthStart().observe(getViewLifecycleOwner(), start -> updateHeaderLabel());
        viewModel.getGranularity().observe(getViewLifecycleOwner(), g -> updateHeaderLabel());

        viewModel.getChartData().observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) return;
            boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
            updateTabTotals(summary, masked);
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), masked -> {
            Summary summary = viewModel.getChartData().getValue();
            if (summary != null) updateTabTotals(summary, masked);
        });
    }

    private void updateTabTotals(Summary summary, boolean masked) {
        com.google.android.material.tabs.TabLayout.Tab incomeTab = binding.tabChartType.getTabAt(0);
        if (incomeTab != null) {
            String val = masked ? "***" : viewModel.formatAmount(summary.getTotalIncome());
            incomeTab.setText("Income " + val);
        }
        com.google.android.material.tabs.TabLayout.Tab expenseTab = binding.tabChartType.getTabAt(1);
        if (expenseTab != null) {
            String val = masked ? "***" : viewModel.formatAmount(summary.getTotalExpense());
            expenseTab.setText("Expenses " + val);
        }
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
            label = startDate + " – " + sdf.format(cal.getTime());
        } else {
            label = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.getTime());
        }
        binding.tvChartRange.setText(label);
    }

    private static class ChartPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        public ChartPagerAdapter(@NonNull Fragment fragment) { super(fragment); }
        @NonNull @Override public Fragment createFragment(int position) {
            return ChartPageFragment.newInstance(position == 1);
        }
        @Override public int getItemCount() { return 2; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
