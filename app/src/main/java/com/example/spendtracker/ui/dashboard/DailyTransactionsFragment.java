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
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentDashboardDailyBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AndroidEntryPoint
public class DailyTransactionsFragment extends Fragment {

    private FragmentDashboardDailyBinding binding;
    private DashboardViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private GroupedTransactionAdapter adapter;
    private List<String> incomeCategories = new ArrayList<>();
    private List<String> expenseCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardDailyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);
        transactionViewModel = new ViewModelProvider(requireParentFragment()).get(TransactionViewModel.class);

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
                List<String> result = new ArrayList<>();
                if (list.isEmpty()) {
                    result.add("Transfer");
                    if ("INCOME".equals(type)) {
                        result.addAll(Arrays.asList("Salary", "Allowance", "Bonus", "Petty Cash", "Gift", "Other"));
                    } else {
                        result.addAll(Arrays.asList("Food", "Rent", "Travel", "Shopping", "Medical", "Other"));
                    }
                } else {
                    if (!list.contains("Transfer")) {
                        result.add("Transfer");
                    }
                    result.addAll(list);
                }
                return result;
            }
        }, new GroupedTransactionAdapter.DataFormatter() {
            @Override public String formatAmount(double amount) { return viewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return transactionViewModel.maskPII(value); }
        });
        binding.rvTransactions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getGroupedTransactions().observe(getViewLifecycleOwner(), items -> {
            if (items == null || items.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.rvTransactions.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvTransactions.setVisibility(View.VISIBLE);
                adapter.submitList(items);
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            adapter.notifyDataSetChanged();
        });

        transactionViewModel.getIncomeCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) incomeCategories = list;
        });

        transactionViewModel.getExpenseCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) expenseCategories = list;
        });
    }

    /** Called by {@link DashboardFragment} after biometric auth to force re-render with actual values. */
    public void refreshAdapter() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
