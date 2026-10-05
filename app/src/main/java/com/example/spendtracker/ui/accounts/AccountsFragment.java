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
        }, this::shareAccount, new AccountsAdapter.AccountFormatter() {
            @Override public String formatAmount(double amount) { return viewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return viewModel.maskPII(value); }
        });
        binding.rvAccounts.setAdapter(adapter);

        viewModel.getUniqueAccounts().observe(getViewLifecycleOwner(), accounts -> {
            allAccounts = accounts == null ? new ArrayList<>() : accounts;
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

    private void shareAccount(TransactionDao.AccountSummary account) {
        androidx.lifecycle.LiveData<List<com.example.spendtracker.domain.model.Transaction>> history = viewModel.getAccountHistory(account.name, 0, Long.MAX_VALUE);
        history.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<List<com.example.spendtracker.domain.model.Transaction>>() {
            @Override public void onChanged(List<com.example.spendtracker.domain.model.Transaction> transactions) {
                history.removeObserver(this);
                com.example.spendtracker.util.AccountReport.share(AccountsFragment.this, account.name, transactions,
                        Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()));
            }
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

    private static class AccountsAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<AccountsAdapter.ViewHolder> {
        interface AccountFormatter { String formatAmount(double amount); String maskPII(String value); }
        private final java.util.function.Consumer<TransactionDao.AccountSummary> listener, share;
        private final AccountFormatter formatter;
        private final List<Object> rows = new ArrayList<>();
        AccountsAdapter(java.util.function.Consumer<TransactionDao.AccountSummary> listener,
                java.util.function.Consumer<TransactionDao.AccountSummary> share, AccountFormatter formatter) {
            this.listener=listener; this.share=share; this.formatter=formatter;
        }
        void submitList(List<TransactionDao.AccountSummary> accounts) {
            rows.clear(); String last="";
            java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault());
            if(accounts!=null) for(TransactionDao.AccountSummary account:accounts) {
                String day=date.format(new java.util.Date(account.lastTransactionDate));
                if(!day.equals(last)) { rows.add(day); last=day; }
                rows.add(account);
            }
            notifyDataSetChanged();
        }
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int position) { return rows.get(position) instanceof String?0:1; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
            if(type==0) {
                android.widget.TextView text=new android.widget.TextView(parent.getContext());
                text.setPadding(24,20,24,12); text.setTextSize(14); text.setTypeface(null,android.graphics.Typeface.BOLD);
                text.setTextColor(parent.getContext().getColor(R.color.text_secondary));
                text.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,-2)); return new ViewHolder(text);
            }
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account,parent,false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder,int position) {
            Object row=rows.get(position);
            if(row instanceof String) { ((android.widget.TextView)holder.itemView).setText((String)row); return; }
            TransactionDao.AccountSummary account=(TransactionDao.AccountSummary)row;
            set(holder,R.id.tv_name,formatter.maskPII(account.name));
            set(holder,R.id.tv_upi_id,formatter.maskPII(account.upiId));
            set(holder,R.id.tv_category_count,account.categoryCount+" categories");
            set(holder,R.id.tv_last_date,"");
            set(holder, R.id.tv_total_credited, formatter.formatAmount(account.totalIncome));
            set(holder, R.id.tv_total_debited, formatter.formatAmount(account.totalExpense));
            set(holder, R.id.tv_total_transfer, formatter.formatAmount(account.totalTransfer));
            android.widget.TextView unread=holder.itemView.findViewById(R.id.tv_unread_count);
            unread.setVisibility(account.unreadCount>0?View.VISIBLE:View.GONE); unread.setText(String.valueOf(account.unreadCount));
            holder.itemView.setOnClickListener(v -> listener.accept(account));
            holder.itemView.findViewById(R.id.share_account).setOnClickListener(v -> share.accept(account));
        }
        private void set(ViewHolder holder,int id,String text) { ((android.widget.TextView)holder.itemView.findViewById(id)).setText(text); }
        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder { ViewHolder(View view) { super(view); } }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
