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
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentCategoryDetailBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;
import com.example.spendtracker.ui.dashboard.GroupedTransactionAdapter;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@AndroidEntryPoint
public class CategoryDetailFragment extends Fragment {

    private FragmentCategoryDetailBinding binding;
    private DashboardViewModel dashboardViewModel;
    private TransactionViewModel transactionViewModel;
    private String categoryName;
    private String sourceTypeFilter; // non-null when filtering by sourceType
    private GroupedTransactionAdapter adapter;
    private java.util.List<String> incomeCategories = new java.util.ArrayList<>();
    private java.util.List<String> expenseCategories = new java.util.ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        if (getArguments() != null) {
            String raw = getArguments().getString("categoryName", "Other");
            if (raw.startsWith("__source__:")) {
                sourceTypeFilter = raw.substring("__source__:".length());
                categoryName = sourceTypeFilter; // display title
            } else {
                categoryName = raw;
                sourceTypeFilter = null;
            }
        }

        binding.tvCategoryTitle.setText(categoryName);
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        
        // Set icon based on category
        int iconRes = android.R.drawable.ic_menu_help;
        String lowerCat = categoryName.toLowerCase();
        if (lowerCat.contains("food")) iconRes = android.R.drawable.ic_menu_gallery;
        else if (lowerCat.contains("transport")) iconRes = android.R.drawable.ic_menu_directions;
        else if (lowerCat.contains("gift")) iconRes = android.R.drawable.btn_star_big_on;
        else if (lowerCat.contains("health")) iconRes = android.R.drawable.ic_menu_mylocation;
        binding.ivHeaderIcon.setImageResource(iconRes);

        setupRecyclerView();
        observeData();
    }

    private void setupRecyclerView() {
        adapter = new GroupedTransactionAdapter(new GroupedTransactionAdapter.OnTransactionClickListener() {
            @Override public void onEdit(Transaction t) {
                Bundle args = new Bundle();
                args.putInt("transactionId", t.getId());
                Navigation.findNavController(requireView()).navigate(R.id.transactionFormFragment, args);
            }
            @Override public void onDelete(Transaction t) { transactionViewModel.deleteTransaction(t); }
            @Override public void onCategoryChange(Transaction t, String c) { dashboardViewModel.updateTransactionCategory(t, c); }
            @Override public List<String> getCategoriesByType(String type) { 
                java.util.List<String> list = "INCOME".equals(type) ? incomeCategories : expenseCategories;
                if (list.isEmpty()) {
                    if ("INCOME".equals(type)) return java.util.Arrays.asList("Salary", "Allowance", "Bonus", "Other");
                    return java.util.Arrays.asList("Food", "Rent", "Travel", "Other");
                }
                return list;
            }
        }, new GroupedTransactionAdapter.DataFormatter() {
            @Override public String formatAmount(double amount) { return dashboardViewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return transactionViewModel.maskPII(value); }
        });
        binding.rvCategoryTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategoryTransactions.setAdapter(adapter);
    }

    private void observeData() {
        transactionViewModel.getIncomeCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) incomeCategories = list;
        });

        transactionViewModel.getExpenseCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) expenseCategories = list;
        });

        dashboardViewModel.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            if (transactions == null) return;

            List<Transaction> filtered = new ArrayList<>();
            double total = 0;
            for (Transaction t : transactions) {
                boolean matches = (sourceTypeFilter != null)
                    ? sourceTypeFilter.equalsIgnoreCase(t.getSourceType())
                    : categoryName.equals(t.getCategory());
                if (matches) {
                    filtered.add(t);
                    total += t.getAmount();
                }
            }

            binding.tvCategoryTotal.setText(dashboardViewModel.formatAmount(total));
            updateList(filtered);
            updateTrend(filtered);
        });

        dashboardViewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            adapter.notifyDataSetChanged();
            List<Transaction> transactions = dashboardViewModel.getTransactions().getValue();
            if (transactions != null) {
                double total = 0;
                for (Transaction t : transactions) {
                    boolean matches = (sourceTypeFilter != null)
                        ? sourceTypeFilter.equalsIgnoreCase(t.getSourceType())
                        : categoryName.equals(t.getCategory());
                    if (matches) total += t.getAmount();
                }
                binding.tvCategoryTotal.setText(dashboardViewModel.formatAmount(total));
            }
        });

        dashboardViewModel.getDateRange().observe(getViewLifecycleOwner(), range -> {
            if (range != null) {
                binding.tvDateLabel.setText(range.label.equals("All Time") ? "All Time" : 
                    new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(new Date(range.start)));
            }
        });
    }

    private void updateList(List<Transaction> transactions) {
        // Group by date logic
        Map<Long, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            long day = getStartOfDay(t.getDate());
            if (!groups.containsKey(day)) groups.put(day, new ArrayList<>());
            groups.get(day).add(t);
        }

        List<GroupedTransactionAdapter.ListItem> items = new ArrayList<>();
        for (Map.Entry<Long, List<Transaction>> entry : groups.entrySet()) {
            double income = 0, expense = 0;
            for (Transaction t : entry.getValue()) {
                if ("INCOME".equals(t.getType())) income += t.getAmount();
                else expense += t.getAmount();
            }
            items.add(new GroupedTransactionAdapter.HeaderItem(entry.getKey(), income, expense));
            for (Transaction t : entry.getValue()) items.add(new GroupedTransactionAdapter.TransactionItem(t));
        }
        adapter.submitList(items);
    }

    private void updateTrend(List<Transaction> transactions) {
        Map<String, Double> monthMap = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());
        
        // Show last 6-12 months? Let's just group existing ones.
        for (Transaction t : transactions) {
            String m = sdf.format(new Date(t.getDate()));
            monthMap.put(m, monthMap.getOrDefault(m, 0.0) + t.getAmount());
        }

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>(monthMap.keySet());
        Collections.reverse(labels); // Usually sorted by date DESC, so reverse for timeline
        
        int i = 0;
        for (String label : labels) {
            entries.add(new Entry(i++, monthMap.get(label).floatValue()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Monthly Spending");
        dataSet.setColor(Color.parseColor("#FF9800"));
        dataSet.setCircleColor(Color.WHITE);
        dataSet.setLineWidth(3f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(dataSet);
        binding.categoryLineChart.setData(data);
        binding.categoryLineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.categoryLineChart.getXAxis().setTextColor(Color.WHITE);
        binding.categoryLineChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        binding.categoryLineChart.getAxisLeft().setTextColor(Color.WHITE);
        binding.categoryLineChart.getAxisRight().setEnabled(false);
        binding.categoryLineChart.getDescription().setEnabled(false);
        binding.categoryLineChart.animateX(800);
        binding.categoryLineChart.invalidate();
    }

    private long getStartOfDay(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
