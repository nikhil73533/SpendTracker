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
    private List<TransactionDao.AccountSummary> allAccounts = new ArrayList<>();

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
        
        setupSearch();

        adapter = new AccountsAdapter(account -> {
            viewModel.markAsRead(account.name);
            Bundle args = new Bundle();
            args.putString("accountId", account.name); 
            args.putString("accountName", account.name);
            Navigation.findNavController(requireView()).navigate(R.id.action_accountsFragment_to_accountHistoryFragment, args);
        }, new AccountsAdapter.AccountFormatter() {
            @Override public String formatAmount(double amount) { return viewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return viewModel.maskPII(value); }
        });
        binding.rvAccounts.setAdapter(adapter);

        viewModel.getUniqueAccounts().observe(getViewLifecycleOwner(), accounts -> {
            allAccounts = accounts;
            if (binding.etSearch.getText() != null) {
                filterAccounts(binding.etSearch.getText().toString());
            } else {
                filterAccounts("");
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            adapter.notifyDataSetChanged();
        });
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAccounts(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void filterAccounts(String query) {
        if (query == null || query.isEmpty()) {
            adapter.submitList(allAccounts);
            return;
        }
        List<TransactionDao.AccountSummary> filtered = new ArrayList<>();
        for (TransactionDao.AccountSummary account : allAccounts) {
            if (account.name.toLowerCase().contains(query.toLowerCase()) || 
                (account.upiId != null && account.upiId.toLowerCase().contains(query.toLowerCase()))) {
                filtered.add(account);
            }
        }
        adapter.submitList(filtered);
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
            android.widget.TextView tvName, tvUpiId, tvExpense, tvLastDate, tvUnread;
            private final AccountFormatter formatter;

            public ViewHolder(@NonNull android.view.View itemView, AccountFormatter formatter) {
                super(itemView);
                this.formatter = formatter;
                tvName = itemView.findViewById(R.id.tv_name);
                tvUpiId = itemView.findViewById(R.id.tv_upi_id);
                tvExpense = itemView.findViewById(R.id.tv_total_expense);
                tvLastDate = itemView.findViewById(R.id.tv_last_date);
                tvUnread = itemView.findViewById(R.id.tv_unread_count);
            }

            public void bind(TransactionDao.AccountSummary account, java.util.function.Consumer<TransactionDao.AccountSummary> listener) {
                tvName.setText(formatter.maskPII(account.name));
                tvUpiId.setText(formatter.maskPII(account.upiId));
                String dateLabel = formatLastDate(account.lastTransactionDate);
                tvLastDate.setText(dateLabel);
                tvExpense.setText(formatter.formatAmount(account.totalExpense));

                if (account.unreadCount > 0) {
                    tvUnread.setVisibility(View.VISIBLE);
                    tvUnread.setText(String.valueOf(account.unreadCount));
                } else {
                    tvUnread.setVisibility(View.GONE);
                }
                
                itemView.setOnClickListener(v -> listener.accept(account));
            }

            private String formatLastDate(long timestamp) {
                if (timestamp == 0) return "";
                java.util.Calendar today = java.util.Calendar.getInstance();
                java.util.Calendar target = java.util.Calendar.getInstance();
                target.setTimeInMillis(timestamp);

                if (today.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR) &&
                    today.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)) {
                    return "Today";
                }
                
                today.add(java.util.Calendar.DAY_OF_YEAR, -1);
                if (today.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR) &&
                    today.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)) {
                    return "Yesterday";
                }

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault());
                return sdf.format(new java.util.Date(timestamp));
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
