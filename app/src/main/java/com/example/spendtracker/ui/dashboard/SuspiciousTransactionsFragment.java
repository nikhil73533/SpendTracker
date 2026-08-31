package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Notifications Area — Suspicious Transactions.
 * Displays all transactions where the ML model predicted a category with
 * confidence below the configurable threshold, grouped by date using the
 * same structure as the Daily transaction view.
 */
@AndroidEntryPoint
public class SuspiciousTransactionsFragment extends Fragment {

    private DashboardViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private GroupedTransactionAdapter adapter;
    private RecyclerView rvTransactions;
    private View layoutEmptyState;
    private TextView tvHeader;
    private List<String> incomeCategories = new ArrayList<>();
    private List<String> expenseCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_suspicious_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        rvTransactions = view.findViewById(R.id.rv_suspicious_transactions);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        tvHeader = view.findViewById(R.id.tv_suspicious_header);

        setupRecyclerView();
        observeViewModel();
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
                viewModel.updateTransactionCategory(transaction, newCategory);
            }

            @Override
            public List<String> getCategoriesByType(String type) {
                List<String> list = "INCOME".equals(type) ? incomeCategories : expenseCategories;
                if (list.isEmpty()) {
                    if ("INCOME".equals(type)) {
                        return Arrays.asList("Salary", "Allowance", "Bonus", "Petty Cash", "Gift", "Other");
                    } else {
                        return Arrays.asList("Food", "Rent", "Travel", "Shopping", "Medical", "Other");
                    }
                }
                return list;
            }
        }, new GroupedTransactionAdapter.DataFormatter() {
            @Override public String formatAmount(double amount) { return viewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return transactionViewModel.maskPII(value); }
        });
        rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTransactions.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getGroupedSuspiciousTransactions().observe(getViewLifecycleOwner(), items -> {
            if (items == null || items.isEmpty()) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                rvTransactions.setVisibility(View.GONE);
                tvHeader.setText("No suspicious transactions detected");
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                rvTransactions.setVisibility(View.VISIBLE);
                // Count actual transaction items (not headers)
                int count = 0;
                for (GroupedTransactionAdapter.ListItem item : items) {
                    if (item instanceof GroupedTransactionAdapter.TransactionItem) count++;
                }
                tvHeader.setText(count + " Suspicious Transactions");
                adapter.submitList(items);
            }
        });

        transactionViewModel.getIncomeCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) incomeCategories = list;
        });

        transactionViewModel.getExpenseCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) expenseCategories = list;
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
