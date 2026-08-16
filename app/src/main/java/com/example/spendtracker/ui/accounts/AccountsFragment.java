package com.example.spendtracker.ui.accounts;

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
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.databinding.FragmentAccountsBinding;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class AccountsFragment extends Fragment {

    private FragmentAccountsBinding binding;
    private TransactionViewModel viewModel;
    private AccountsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        
        adapter = new AccountsAdapter(account -> {
            Bundle args = new Bundle();
            args.putString("accountId", (account.upiId != null && !account.upiId.isEmpty()) ? account.upiId : account.name);
            args.putString("accountName", account.name);
            Navigation.findNavController(requireView()).navigate(R.id.action_accountsFragment_to_accountHistoryFragment, args);
        }, new AccountsAdapter.AccountFormatter() {
            @Override public String formatAmount(double amount) { return viewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return viewModel.maskPII(value); }
        });
        binding.rvAccounts.setAdapter(adapter);

        viewModel.getUniqueAccounts().observe(getViewLifecycleOwner(), accounts -> {
            adapter.submitList(accounts);
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            adapter.notifyDataSetChanged();
        });
    }

    private static class AccountsAdapter extends androidx.recyclerview.widget.ListAdapter<TransactionDao.AccountSummary, AccountsAdapter.ViewHolder> {
        public interface AccountFormatter {
            String formatAmount(double amount);
            String maskPII(String value);
        }
        private final java.util.function.Consumer<TransactionDao.AccountSummary> listener;
        private final AccountFormatter formatter;

        protected AccountsAdapter(java.util.function.Consumer<TransactionDao.AccountSummary> listener, AccountFormatter formatter) {
            super(new androidx.recyclerview.widget.DiffUtil.ItemCallback<TransactionDao.AccountSummary>() {
                @Override
                public boolean areItemsTheSame(@NonNull TransactionDao.AccountSummary oldItem, @NonNull TransactionDao.AccountSummary newItem) {
                    return (oldItem.upiId != null ? oldItem.upiId : oldItem.name).equals(newItem.upiId != null ? newItem.upiId : newItem.name);
                }
                @Override
                public boolean areContentsTheSame(@NonNull TransactionDao.AccountSummary oldItem, @NonNull TransactionDao.AccountSummary newItem) {
                    return Double.compare(oldItem.totalExpense, newItem.totalExpense) == 0 && oldItem.lastTransactionDate == newItem.lastTransactionDate;
                }
            });
            this.listener = listener;
            this.formatter = formatter;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.view.View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
            return new ViewHolder(view, formatter);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransactionDao.AccountSummary account = getItem(position);
            holder.bind(account, listener);
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvName, tvUpiId, tvExpense, tvLastDate;
            private final AccountFormatter formatter;

            public ViewHolder(@NonNull android.view.View itemView, AccountFormatter formatter) {
                super(itemView);
                this.formatter = formatter;
                tvName = itemView.findViewById(R.id.tv_name);
                tvUpiId = itemView.findViewById(R.id.tv_upi_id);
                tvExpense = itemView.findViewById(R.id.tv_total_expense);
                tvLastDate = itemView.findViewById(R.id.tv_last_date);
            }

            public void bind(TransactionDao.AccountSummary account, java.util.function.Consumer<TransactionDao.AccountSummary> listener) {
                tvName.setText(formatter.maskPII(account.name));
                tvUpiId.setText(formatter.maskPII(account.upiId));
                tvExpense.setText(formatter.formatAmount(account.totalExpense));
                
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault());
                tvLastDate.setText("Last: " + sdf.format(new java.util.Date(account.lastTransactionDate)));
                
                itemView.setOnClickListener(v -> listener.accept(account));
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
