package com.example.spendtracker.ui.charts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentChartsBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
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
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return ChartSectionFragment.newInstance(position == 0 ? "INCOME" : "EXPENSE");
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        };
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabChartType, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Income" : "Expenses");
        }).attach();

        binding.tabChartType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    binding.tabChartType.setSelectedTabIndicatorColor(requireContext().getColor(R.color.expense_red));
                } else {
                    binding.tabChartType.setSelectedTabIndicatorColor(requireContext().getColor(R.color.income_blue));
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Set initial tab to Expenses
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
            TabLayout.Tab incomeTab = binding.tabChartType.getTabAt(0);
            if (incomeTab != null) incomeTab.setText("Income " + viewModel.formatAmount(summary.getTotalIncome()));
            
            TabLayout.Tab expenseTab = binding.tabChartType.getTabAt(1);
            if (expenseTab != null) expenseTab.setText("Expenses " + viewModel.formatAmount(summary.getTotalExpense()));
        });
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
            label = startDate + " – " + endDate;
        } else {
            label = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.getTime());
        }
        binding.tvChartRange.setText(label);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
