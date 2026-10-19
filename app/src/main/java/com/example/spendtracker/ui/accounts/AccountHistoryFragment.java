package com.example.spendtracker.ui.accounts;

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
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentAccountHistoryBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@AndroidEntryPoint
public class AccountHistoryFragment extends Fragment {

    private FragmentAccountHistoryBinding binding;
    private TransactionViewModel viewModel;
    private ChatAdapter adapter;
    private List<Transaction> current = new ArrayList<>();
    private String accountId;
    private String accountName;
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());

    private enum Granularity { WEEKLY, MONTHLY, ANNUAL }
    private Granularity currentGranularity = Granularity.WEEKLY;

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
        binding.tvAccountName.setText(viewModel.maskPII(accountName));
        binding.tvUpiId.setText(viewModel.maskPII(accountId));
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.btnGranularity.setOnClickListener(v -> showGranularityMenu());
        binding.btnGranularity.setText(currentGranularity.name());

        adapter = new ChatAdapter();
        binding.rvHistory.setAdapter(adapter);
        
        binding.shareAccount.setOnClickListener(v -> com.example.spendtracker.util.AccountReport.share(this, accountName, current,
                Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue())));
        setupLineChart();
    }

    private void showGranularityMenu() {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), binding.btnGranularity);
        for (Granularity g : Granularity.values()) {
            menu.getMenu().add(g.name());
        }
        menu.setOnMenuItemClickListener(item -> {
            currentGranularity = Granularity.valueOf(item.getTitle().toString());
            binding.btnGranularity.setText(currentGranularity.name());
            updateTrendChart();
            return true;
        });
        menu.show();
    }

    private void observeData() {
        viewModel.getUniqueAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null) return;
            for (com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary account : accounts) {
                if (java.util.Objects.equals(account.name, accountId)) {
                    binding.tvMerchantCategories.setText(getResources().getQuantityString(R.plurals.merchant_category_count,
                            account.categoryCount, account.categoryCount) + " · All time\n"
                            + (account.categoryNames == null ? "" : account.categoryNames.replace(",", ", ")));
                    break;
                }
            }
        });
        // Fetch all history for this account
        viewModel.getAccountHistory(accountId, 0, Long.MAX_VALUE).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null) {
                current = new ArrayList<>(transactions);
                processAndSubmitList(new ArrayList<>(transactions));
                updateTrendChart(transactions);
                
                com.example.spendtracker.ui.charts.FlowSummary.render(binding.getRoot(), current, viewModel::formatAmount);
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.tvAccountName.setText(viewModel.maskPII(accountName));
            binding.tvUpiId.setText(viewModel.maskPII(accountId));
            adapter.notifyDataSetChanged();
            com.example.spendtracker.ui.charts.FlowSummary.render(binding.getRoot(), current, viewModel::formatAmount);
            updateTrendChart(); // Redraw chart to mask values if needed
        });
    }

    private void processAndSubmitList(List<Transaction> transactions) {
        List<ChatListItem> items = new ArrayList<>();
        if (transactions == null || transactions.isEmpty()) {
            adapter.setListItems(items);
            return;
        }

        // Sort descending (latest first for chat-like top-down history or latest at bottom?)
        // WhatsApp has latest at bottom. Chronological order.
        Collections.sort(transactions, (a, b) -> {
            int cmp = Long.compare(a.getDate(), b.getDate());
            return cmp != 0 ? cmp : Integer.compare(a.getId(), b.getId());
        });

        String lastDate = "";
        for (Transaction t : transactions) {
            String dateStr = getFormattedDate(t.getDate());
            if (!dateStr.equals(lastDate)) {
                items.add(new ChatListItem(dateStr));
                lastDate = dateStr;
            }
            items.add(new ChatListItem(t));
        }
        adapter.setListItems(items);
        binding.rvHistory.scrollToPosition(items.size() - 1);
    }

    private String getFormattedDate(long timestamp) {
        Calendar today = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);

        if (today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "TODAY";
        }
        
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "YESTERDAY";
        }

        return dateFormat.format(new Date(timestamp));
    }

    private void setupLineChart() {
        LineChart chart = binding.trendChart;
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.parseColor("#33FFFFFF"));
        chart.getAxisRight().setEnabled(false);
    }

    private void updateTrendChart() {
        updateTrendChart(current);
    }

    private void updateTrendChart(List<Transaction> transactions) {
        com.example.spendtracker.ui.charts.TransactionFlowChart.render(binding.trendChart, transactions,
                currentGranularity.name(), Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()));
    }

    private static class ChatListItem {
        static final int TYPE_SEPARATOR = 0;
        static final int TYPE_TRANSACTION = 1;
        
        int type;
        String dateLabel;
        Transaction transaction;

        ChatListItem(String dateLabel) {
            this.type = TYPE_SEPARATOR;
            this.dateLabel = dateLabel;
        }

        ChatListItem(Transaction transaction) {
            this.type = TYPE_TRANSACTION;
            this.transaction = transaction;
        }
    }

    private class ChatAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
        private final List<ChatListItem> items = new ArrayList<>();

        void setListItems(List<ChatListItem> newList) {
            items.clear();
            items.addAll(newList);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            ChatListItem item = items.get(position);
            if (item.type == ChatListItem.TYPE_SEPARATOR) return 2;
            return com.example.spendtracker.util.TransferDirection.isIncoming(item.transaction) ? 1 : 0;
        }

        @NonNull
        @Override
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 2) {
                return new SeparatorViewHolder(inflater.inflate(R.layout.item_chat_date_separator, parent, false));
            }
            int layout = viewType == 0 ? R.layout.item_chat_paid : R.layout.item_chat_received;
            return new TransactionViewHolder(inflater.inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
            ChatListItem item = items.get(position);
            if (holder instanceof SeparatorViewHolder) {
                ((SeparatorViewHolder) holder).tvDate.setText(item.dateLabel);
            } else if (holder instanceof TransactionViewHolder) {
                Transaction t = item.transaction;
                TransactionViewHolder vh = (TransactionViewHolder) holder;
                vh.tvAmount.setText(viewModel.formatAmount(t.getAmount()));
                vh.tvCategory.setText(t.getCategory());
                vh.tvTime.setText(timeFormat.format(new Date(t.getDate())));
                
                vh.itemView.setOnClickListener(v -> {
                    Bundle args = new Bundle(); args.putInt("transactionId", t.getId());
                    Navigation.findNavController(v).navigate(R.id.transactionFormFragment, args);
                });
                if (com.example.spendtracker.util.TransferDirection.isTransfer(t)) {
                    String owner = com.example.spendtracker.util.UserProfile.load(requireContext()).name;
                    vh.tvStatus.setText("Transfer · " + viewModel.maskPII(com.example.spendtracker.util.TransferDirection.from(t, owner)) + " → "
                            + viewModel.maskPII(com.example.spendtracker.util.TransferDirection.to(t, owner)));
                } else if ("INCOME".equals(t.getType())) {
                    vh.tvStatus.setText("Received from " + viewModel.maskPII(accountName));
                } else {
                    vh.tvStatus.setText("Paid to " + viewModel.maskPII(accountName));
                }
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class SeparatorViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvDate;
            SeparatorViewHolder(View v) { super(v); tvDate = v.findViewById(R.id.tv_date); }
        }

        static class TransactionViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvAmount, tvStatus, tvCategory, tvTime;
            TransactionViewHolder(View v) {
                super(v);
                tvAmount = v.findViewById(R.id.tv_amount);
                tvStatus = v.findViewById(R.id.tv_status);
                tvCategory = v.findViewById(R.id.tv_category);
                tvTime = v.findViewById(R.id.tv_time);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
