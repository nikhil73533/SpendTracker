package com.example.spendtracker.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bill Alerts Fragment — scans existing parsed transactions to detect recurring
 * bills and subscriptions. Does NOT modify SMS parsing logic.
 *
 * Detection algorithm:
 * 1. Groups EXPENSE transactions by receiver/payee name
 * 2. Identifies receivers with 2+ transactions in different months
 * 3. Estimates frequency (monthly/weekly) and average amount
 * 4. Presents detected bills with estimated next due date
 */
@AndroidEntryPoint
public class BillAlertsFragment extends Fragment {

    private TransactionViewModel viewModel;
    private RecyclerView rvBillAlerts;
    private View layoutEmptyState;
    private TextView tvAlertsHeader;
    private BillAlertAdapter adapter;

    @javax.inject.Inject
    com.example.spendtracker.data.sms.AlertParsingService alertParsingService;

    // Custom keyword UI
    private com.google.android.material.textfield.TextInputEditText etKeywordInput;
    private com.google.android.material.chip.ChipGroup chipGroupKeywords;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bill_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        view.findViewById(R.id.toolbar).setOnClickListener(v ->
            Navigation.findNavController(v).navigateUp());
        ((com.google.android.material.appbar.MaterialToolbar) view.findViewById(R.id.toolbar))
            .setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        rvBillAlerts = view.findViewById(R.id.rv_bill_alerts);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        tvAlertsHeader = view.findViewById(R.id.tv_alerts_header);

        adapter = new BillAlertAdapter();
        rvBillAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvBillAlerts.setAdapter(adapter);

        view.findViewById(R.id.btn_scan_bills).setOnClickListener(v -> scanForBills());

        // ── Custom Alert Keyword Section ──────────────────────────────────
        etKeywordInput = view.findViewById(R.id.et_keyword_input);
        chipGroupKeywords = view.findViewById(R.id.chip_group_keywords);

        View btnAddKeyword = view.findViewById(R.id.btn_add_keyword);
        if (btnAddKeyword != null && etKeywordInput != null) {
            btnAddKeyword.setOnClickListener(v -> {
                String keyword = etKeywordInput.getText() != null ? etKeywordInput.getText().toString().trim() : "";
                if (!keyword.isEmpty()) {
                    alertParsingService.addCustomKeyword(keyword);
                    etKeywordInput.setText("");
                    refreshKeywordChips();
                    Toast.makeText(requireContext(), "Alert keyword added: \"" + keyword + "\"", Toast.LENGTH_SHORT).show();
                }
            });
        }

        refreshKeywordChips();

        // Show empty state initially
        layoutEmptyState.setVisibility(View.VISIBLE);
    }

    /**
     * Refreshes the ChipGroup to display all user-defined alert keywords.
     * Each chip has a close icon to allow removal.
     */
    private void refreshKeywordChips() {
        if (chipGroupKeywords == null) return;
        chipGroupKeywords.removeAllViews();
        java.util.Set<String> keywords = alertParsingService.getCustomKeywords();
        for (String keyword : keywords) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setText(keyword);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                alertParsingService.removeCustomKeyword(keyword);
                refreshKeywordChips();
            });
            chipGroupKeywords.addView(chip);
        }
    }

    private void scanForBills() {
        viewModel.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            if (transactions == null || transactions.isEmpty()) {
                showEmpty();
                return;
            }

            List<BillAlert> detectedBills = detectRecurringBills(transactions);
            if (detectedBills.isEmpty()) {
                showEmpty();
                Toast.makeText(requireContext(), "No recurring bills detected in your transactions", Toast.LENGTH_SHORT).show();
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                tvAlertsHeader.setVisibility(View.VISIBLE);
                rvBillAlerts.setVisibility(View.VISIBLE);
                adapter.submitList(detectedBills);
            }
        });
    }

    private void showEmpty() {
        layoutEmptyState.setVisibility(View.VISIBLE);
        rvBillAlerts.setVisibility(View.GONE);
        tvAlertsHeader.setVisibility(View.GONE);
    }

    /**
     * Detects recurring bills by grouping expense transactions by payee
     * and identifying those with regular monthly/weekly patterns.
     */
    private List<BillAlert> detectRecurringBills(List<Transaction> transactions) {
        // Group expenses by receiverName
        Map<String, List<Transaction>> byPayee = new HashMap<>();
        for (Transaction t : transactions) {
            if (!"EXPENSE".equals(t.getType())) continue;
            String payee = t.getReceiverName();
            if (payee == null || payee.trim().isEmpty()) continue;
            payee = payee.trim();
            if (!byPayee.containsKey(payee)) {
                byPayee.put(payee, new ArrayList<>());
            }
            byPayee.get(payee).add(t);
        }

        List<BillAlert> alerts = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        for (Map.Entry<String, List<Transaction>> entry : byPayee.entrySet()) {
            List<Transaction> txns = entry.getValue();
            if (txns.size() < 2) continue; // Need at least 2 transactions

            // Check if they span different months
            java.util.Set<String> months = new java.util.HashSet<>();
            double totalAmount = 0;
            long latestDate = 0;
            String category = "";
            for (Transaction t : txns) {
                cal.setTimeInMillis(t.getDate());
                months.add(cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH));
                totalAmount += t.getAmount();
                if (t.getDate() > latestDate) {
                    latestDate = t.getDate();
                    category = t.getCategory() != null ? t.getCategory() : "";
                }
            }

            if (months.size() < 2) continue; // Must span at least 2 different months

            double avgAmount = totalAmount / txns.size();
            // Estimate frequency: if avg gap between txns is ~28-32 days, it's monthly
            String frequency = "Monthly";
            long avgGap = computeAverageGap(txns);
            if (avgGap < 10 * 24 * 60 * 60 * 1000L) {
                frequency = "Weekly";
            } else if (avgGap > 80 * 24 * 60 * 60 * 1000L) {
                frequency = "Quarterly";
            }

            // Estimate next due date
            long nextDue = latestDate + avgGap;

            alerts.add(new BillAlert(entry.getKey(), category, avgAmount, frequency, nextDue, txns.size()));
        }

        // Sort by next due (soonest first)
        alerts.sort((a, b) -> Long.compare(a.nextDue, b.nextDue));
        return alerts;
    }

    private long computeAverageGap(List<Transaction> txns) {
        if (txns.size() < 2) return 30L * 24 * 60 * 60 * 1000; // default monthly
        List<Long> dates = new ArrayList<>();
        for (Transaction t : txns) dates.add(t.getDate());
        dates.sort(Long::compare);
        long totalGap = 0;
        for (int i = 1; i < dates.size(); i++) {
            totalGap += dates.get(i) - dates.get(i - 1);
        }
        return totalGap / (dates.size() - 1);
    }

    // ── Data class ───────────────────────────────────────────────────────────

    static class BillAlert {
        String payeeName;
        String category;
        double averageAmount;
        String frequency;
        long nextDue;
        int occurrences;

        BillAlert(String payeeName, String category, double averageAmount, String frequency, long nextDue, int occurrences) {
            this.payeeName = payeeName;
            this.category = category;
            this.averageAmount = averageAmount;
            this.frequency = frequency;
            this.nextDue = nextDue;
            this.occurrences = occurrences;
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    static class BillAlertAdapter extends RecyclerView.Adapter<BillAlertAdapter.ViewHolder> {
        private List<BillAlert> items = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        void submitList(List<BillAlert> list) {
            this.items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bill_alert, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BillAlert alert = items.get(position);
            holder.tvName.setText(alert.payeeName);
            holder.tvCategory.setText(alert.category + " • " + alert.occurrences + " transactions");
            holder.tvAmount.setText(String.format(Locale.getDefault(), "≈ ₹ %.0f", alert.averageAmount));
            holder.tvFrequency.setText(alert.frequency);
            holder.tvNextDue.setText("Next: " + sdf.format(new Date(alert.nextDue)));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvCategory, tvAmount, tvFrequency, tvNextDue;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_bill_name);
                tvCategory = itemView.findViewById(R.id.tv_bill_category);
                tvAmount = itemView.findViewById(R.id.tv_bill_amount);
                tvFrequency = itemView.findViewById(R.id.tv_bill_frequency);
                tvNextDue = itemView.findViewById(R.id.tv_bill_next_due);
            }
        }
    }
}
