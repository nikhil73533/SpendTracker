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
import com.example.spendtracker.databinding.FragmentAccountHistoryBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class AccountHistoryFragment extends Fragment {

    private FragmentAccountHistoryBinding binding;
    private TransactionViewModel viewModel;
    private ChatAdapter adapter;
    private String accountId;
    private String accountName;
    private long currentMonthStart;
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        if (getArguments() != null) {
            accountId = getArguments().getString("accountId");
            accountName = getArguments().getString("accountName");
        }

        setupUI();
        observeData();
    }

    private void setupUI() {
        binding.tvAccountName.setText(accountName);
        binding.tvUpiId.setText(accountId);
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        currentMonthStart = cal.getTimeInMillis();
        updateDateLabel();

        binding.btnDateFilter.setOnClickListener(this::showMonthPicker);

        adapter = new ChatAdapter();
        binding.rvHistory.setAdapter(adapter);
    }

    private void updateDateLabel() {
        binding.tvDateLabel.setText(monthFormat.format(new Date(currentMonthStart)));
    }

    private void showMonthPicker(View v) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), v);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentMonthStart);
        
        // Show last 6 months
        for (int i = 0; i < 6; i++) {
            menu.getMenu().add(0, i, 0, monthFormat.format(cal.getTime()));
            cal.add(Calendar.MONTH, -1);
        }

        menu.setOnMenuItemClickListener(item -> {
            Calendar newCal = Calendar.getInstance();
            newCal.set(Calendar.DAY_OF_MONTH, 1);
            newCal.add(Calendar.MONTH, -item.getItemId());
            currentMonthStart = newCal.getTimeInMillis();
            updateDateLabel();
            loadHistory();
            return true;
        });
        menu.show();
    }

    private void observeData() {
        loadHistory();
    }

    private void loadHistory() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentMonthStart);
        long start = cal.getTimeInMillis();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long end = cal.getTimeInMillis();

        viewModel.getAccountHistory(accountId, start, end).observe(getViewLifecycleOwner(), transactions -> {
            adapter.submitList(transactions);
            double totalExpense = 0;
            for (Transaction t : transactions) {
                if ("EXPENSE".equals(t.getType())) totalExpense += t.getAmount();
            }
            binding.tvTotalExpense.setText(String.format(Locale.getDefault(), "₹ %.2f", totalExpense));
        });
    }

    private class ChatAdapter extends androidx.recyclerview.widget.ListAdapter<Transaction, ChatAdapter.ViewHolder> {
        private static final int TYPE_PAID = 0;
        private static final int TYPE_RECEIVED = 1;

        protected ChatAdapter() {
            super(new androidx.recyclerview.widget.DiffUtil.ItemCallback<Transaction>() {
                @Override
                public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
                    return oldItem.getId() == newItem.getId();
                }
                @Override
                public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
                    return oldItem.getAmount() == newItem.getAmount() && oldItem.getCategory().equals(newItem.getCategory());
                }
            });
        }

        @Override
        public int getItemViewType(int position) {
            return "INCOME".equals(getItem(position).getType()) ? TYPE_RECEIVED : TYPE_PAID;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == TYPE_PAID ? R.layout.item_chat_paid : R.layout.item_chat_received;
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction t = getItem(position);
            holder.tvAmount.setText(String.format(Locale.getDefault(), "₹ %.2f", t.getAmount()));
            holder.tvCategory.setText(t.getCategory());
            holder.tvTime.setText(timeFormat.format(new Date(t.getDate())));
            
            if ("INCOME".equals(t.getType())) {
                holder.tvStatus.setText("Received from " + accountName);
            } else {
                holder.tvStatus.setText("Paid to " + accountName);
            }
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvAmount, tvStatus, tvCategory, tvTime;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAmount = itemView.findViewById(R.id.tv_amount);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvCategory = itemView.findViewById(R.id.tv_category);
                tvTime = itemView.findViewById(R.id.tv_time);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
