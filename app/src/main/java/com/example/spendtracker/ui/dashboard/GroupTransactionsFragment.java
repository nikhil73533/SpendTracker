package com.example.spendtracker.ui.dashboard;

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
import com.example.spendtracker.databinding.FragmentGroupTransactionsBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.text.SimpleDateFormat;

@AndroidEntryPoint
public class GroupTransactionsFragment extends Fragment {

    private FragmentGroupTransactionsBinding binding;
    private TransactionGroupViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private GroupedTransactionAdapter adapter;
    private int groupId;
    private List<Transaction> groupTransactions = Collections.emptyList();
    private List<String> incomeCategories = Collections.emptyList();
    private List<String> expenseCategories = Collections.emptyList();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getInt("groupId", -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupTransactionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionGroupViewModel.class);
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        setupToolbar();
        setupRecyclerView();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        viewModel.getGroupById(groupId).observe(getViewLifecycleOwner(), group -> {
            if (group == null) return;
            binding.tvGroupTitle.setText(group.name);
            SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            binding.tvGroupRange.setText(date.format(new Date(group.startDate)) + " - " + date.format(new Date(group.endDate)));
        });
    }

    private void setupRecyclerView() {
        adapter = new GroupedTransactionAdapter(new GroupedTransactionAdapter.OnTransactionClickListener() {
            @Override
            public void onEdit(Transaction transaction) {
                Bundle args = new Bundle();
                args.putInt("transactionId", transaction.getId());
                Navigation.findNavController(requireView()).navigate(R.id.transactionFormFragment, args);
            }

            @Override
            public void onDelete(Transaction transaction) {
                transactionViewModel.deleteTransaction(transaction);
            }

            @Override
            public void onCategoryChange(Transaction transaction, String newCategory) {
                transaction.setCategory(newCategory);
                transactionViewModel.updateTransaction(transaction);
            }

            @Override
            public List<String> getCategoriesByType(String type) {
                return "INCOME".equals(type) ? incomeCategories : expenseCategories;
            }
        }, new GroupedTransactionAdapter.DataFormatter() {
            @Override public String formatAmount(double amount) { return transactionViewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return transactionViewModel.maskPII(value); }
        });
        binding.rvGroupTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGroupTransactions.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getGroupedTransactionsForGroup(groupId).observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
        });
        viewModel.getTransactionsForGroup(groupId).observe(getViewLifecycleOwner(), transactions -> {
            groupTransactions = transactions == null ? Collections.emptyList() : transactions;
            updateLocalWidgets();
            updateTrend();
        });
        transactionViewModel.getIncomeCategories().observe(getViewLifecycleOwner(), categories -> {
            incomeCategories = categories == null ? Collections.emptyList() : categories;
        });
        transactionViewModel.getExpenseCategories().observe(getViewLifecycleOwner(), categories -> {
            expenseCategories = categories == null ? Collections.emptyList() : categories;
        });
        transactionViewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), hidden -> {
            adapter.notifyDataSetChanged();
            updateLocalWidgets();
            updateTrend();
        });
    }

    /** Mirrors the category detail's local metrics, calculated from this group alone. */
    private void updateLocalWidgets() {
        double income = 0;
        double expense = 0;
        double transfers = 0;
        for (Transaction transaction : groupTransactions) {
            if (isTransfer(transaction)) transfers += transaction.getAmount();
            else if ("INCOME".equalsIgnoreCase(transaction.getType())) income += transaction.getAmount();
            else expense += transaction.getAmount();
        }
        binding.tvGroupTotal.setText(transactionViewModel.formatAmount(income - expense));
        binding.tvGroupIncome.setText(transactionViewModel.formatAmount(income));
        binding.tvGroupExpense.setText(transactionViewModel.formatAmount(expense));
        binding.tvGroupTransfers.setText(transactionViewModel.formatAmount(transfers));
    }

    private void updateTrend() {
        boolean locked = Boolean.TRUE.equals(transactionViewModel.isPrivacyModeEnabled().getValue());
        binding.groupLineChart.setVisibility(locked ? View.INVISIBLE : View.VISIBLE);
        if (locked || groupTransactions.isEmpty()) {
            binding.groupLineChart.clear();
            binding.groupLineChart.invalidate();
            return;
        }
        Map<Long, Double> dailyActivity = new TreeMap<>();
        for (Transaction transaction : groupTransactions) {
            long day = startOfDay(transaction.getDate());
            dailyActivity.put(day, dailyActivity.getOrDefault(day, 0.0) + transaction.getAmount());
        }
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        SimpleDateFormat date = new SimpleDateFormat("dd MMM", Locale.getDefault());
        int index = 0;
        for (Map.Entry<Long, Double> entry : dailyActivity.entrySet()) {
            entries.add(new Entry(index++, entry.getValue().floatValue()));
            labels.add(date.format(new Date(entry.getKey())));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Daily activity");
        dataSet.setColor(android.graphics.Color.parseColor("#FF9800"));
        dataSet.setCircleColor(android.graphics.Color.WHITE);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        binding.groupLineChart.setData(new LineData(dataSet));
        binding.groupLineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.groupLineChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        binding.groupLineChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        binding.groupLineChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        binding.groupLineChart.getAxisRight().setEnabled(false);
        binding.groupLineChart.getDescription().setEnabled(false);
        binding.groupLineChart.invalidate();
    }

    private boolean isTransfer(Transaction transaction) {
        return "TRANSFER".equalsIgnoreCase(transaction.getType())
                || transaction.getCategory().toLowerCase(Locale.ROOT).contains("transfer");
    }

    private long startOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
