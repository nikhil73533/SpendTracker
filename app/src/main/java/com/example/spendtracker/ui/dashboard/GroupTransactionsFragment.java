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
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class GroupTransactionsFragment extends Fragment {

    private FragmentGroupTransactionsBinding binding;
    private TransactionGroupViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private GroupedTransactionAdapter adapter;
    private int groupId;

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
            if (group != null) binding.tvGroupTitle.setText(group.name);
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
                // For simplicity, we reuse the dashboard logic if possible, 
                // but here we just show a toast or implement it if needed.
            }

            @Override
            public List<String> getCategoriesByType(String type) {
                return new ArrayList<>(); // Or fetch from transactionViewModel
            }
        }, new GroupedTransactionAdapter.DataFormatter() {
            @Override public String formatAmount(double amount) { 
                return String.format("₹ %.0f", amount); // Simplified
            }
            @Override public String maskPII(String value) { return value; } // No masking here for simplicity
        });
        binding.rvGroupTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGroupTransactions.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getGroupedTransactionsForGroup(groupId).observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
