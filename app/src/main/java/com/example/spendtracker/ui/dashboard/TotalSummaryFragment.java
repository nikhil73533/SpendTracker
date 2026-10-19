package com.example.spendtracker.ui.dashboard;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentDashboardTotalBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.example.spendtracker.data.local.dao.TransactionDao;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

@AndroidEntryPoint
public class TotalSummaryFragment extends Fragment {

    @Inject
    com.example.spendtracker.ui.premium.FeatureGate featureGate;

    @Inject com.example.spendtracker.domain.repository.TransactionRepository transactionRepository;

    private FragmentDashboardTotalBinding binding;
    private DashboardViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private BankTotalAdapter bankAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardTotalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);
        transactionViewModel = new ViewModelProvider(requireParentFragment()).get(TransactionViewModel.class);

        setupRecyclerView();
        binding.btnExportExcel.setOnClickListener(v -> requireFeature(com.example.spendtracker.billing.PremiumFeature.EXCEL_EXPORT, this::exportToExcel));
        binding.btnShareReceipt.setOnClickListener(v -> requireFeature(com.example.spendtracker.billing.PremiumFeature.ADVANCED_REPORTS,
                () -> requireReportAuthentication(this::shareExpenseAccountReport)));
        binding.btnDownloadPdf.setOnClickListener(v -> requireFeature(com.example.spendtracker.billing.PremiumFeature.ADVANCED_REPORTS,
                () -> requireReportAuthentication(this::downloadOverallReport)));
        observeViewModel();
    }

    private void setupRecyclerView() {
        bankAdapter = new BankTotalAdapter(amount -> viewModel.formatAmount(amount), bank -> {
            Bundle args = new Bundle();
            args.putString("bankName", bank);
            androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.bankDetailFragment, args);
        });
        binding.rvBankTotals.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.rvBankTotals.setAdapter(bankAdapter);
    }

    private void observeViewModel() {
        viewModel.getDateRange().observe(getViewLifecycleOwner(), range -> {
            binding.tvTotalRangeLabel.setText(new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(new Date(range.start)) + " ~ " + 
                                            new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(new Date(range.end)));
        });

        viewModel.getTotalPageData().observe(getViewLifecycleOwner(), data -> {
            updateUI(data, Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()));
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            DashboardViewModel.TotalPageData data = viewModel.getTotalPageData().getValue();
            if (data != null) updateUI(data, enabled);
            bankAdapter.notifyDataSetChanged();
        });

        viewModel.getBankTotals().observe(getViewLifecycleOwner(), totals -> {
            bankAdapter.submitList(totals);
        });
    }

    private void updateUI(DashboardViewModel.TotalPageData data, boolean masked) {
        binding.tvComparedPercent.setText(masked ? "***" : data.comparedPercent + "%");
        binding.tvAccountExpenses.setText(formatAmountWithState(data.accountExpenses, masked));
        binding.tvCardExpenses.setText(formatAmountWithState(data.cardExpenses, masked));
        binding.tvTransferIncoming.setText(formatAmountWithState(data.transferIncoming, masked));
        binding.tvTransferOutgoing.setText(formatAmountWithState(data.transferOutgoing, masked));
        binding.tvTotalTransfers.setText(formatAmountWithState(data.transfers, masked));
        binding.tvTotalIncome.setText(formatAmountWithState(data.income, masked));
    }

    private String formatAmountWithState(double amount, boolean masked) {
        if (masked) return "***";
        return String.format(Locale.getDefault(), "₹ %.0f", amount);
    }

    private void exportToExcel() {
        final android.content.Context context = requireContext().getApplicationContext();
        final boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
        final LiveData<List<Transaction>> source = transactionViewModel.getTransactions();
        source.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<List<Transaction>>() {
            @Override public void onChanged(List<Transaction> transactions) {
                source.removeObserver(this);
                if (transactions == null || transactions.isEmpty()) {
                    Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show(); return;
                }
                new Thread(() -> {
                    try {
                        File directory = context.getExternalFilesDir("exports");
                        if (directory == null || (!directory.exists() && !directory.mkdirs()))
                            throw new java.io.IOException("Export storage is unavailable");
                        File file = File.createTempFile("SpendTracker_", ".xlsx", directory);
                        try (FileOutputStream output = new FileOutputStream(file)) {
                            com.example.spendtracker.util.ExcelExportWriter.write(output, transactionRepository.getTransactionsSync(), masked, java.time.ZoneId.systemDefault());
                        }
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (!isAdded() || binding == null) return;
                            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                            Intent intent = new Intent(Intent.ACTION_SEND).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setClipData(android.content.ClipData.newRawUri("SpendTracker export", uri));
                            startActivity(Intent.createChooser(intent, "Share Excel File"));
                        });
                    } catch (Exception error) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                Toast.makeText(context, "Excel export failed. Please try again.", Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        });
    }

    /** Shares only expense-account activity from the current Total tab date range. */
    private void shareExpenseAccountReport() {
        android.content.Context context = requireContext().getApplicationContext();
        collectReportTransactions("No expense account transactions to share for selected period", (transactions, dateRangeLabel) ->
                new Thread(() -> {
                    try {
                        File pdfFile = com.example.spendtracker.util.PdfReportService.generateExpenseAccountReport(
                                context, transactions, dateRangeLabel);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (!isAdded() || binding == null) return;
                            try {
                                com.example.spendtracker.util.PdfReportService.share(
                                        requireContext(), pdfFile, "Expense account report", "Share expense account report");
                            } catch (RuntimeException error) {
                                Toast.makeText(requireContext(), "No app available to share the report", Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (IllegalArgumentException e) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) Toast.makeText(requireContext(),
                                    "No expense account transactions to share for selected period", Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception e) {
                        android.util.Log.e("TotalSummary", "Unable to create expense account report", e);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) Toast.makeText(requireContext(),
                                    "Could not create expense account report", Toast.LENGTH_LONG).show();
                        });
                    }
                }).start());
    }

    /** Saves an overall income, expense and transfer review for the current date range. */
    private void downloadOverallReport() {
        DashboardViewModel.TotalPageData data = viewModel.getTotalPageData().getValue();
        collectReportTransactions("No transactions to report for selected period", (transactions, dateRangeLabel) ->
                new Thread(() -> {
                    try {
                        File pdfFile = com.example.spendtracker.util.PdfReportService.generateReport(
                                requireContext(), data, transactions, dateRangeLabel);
                        requireActivity().runOnUiThread(() -> {
                            try {
                                com.example.spendtracker.util.PdfReportService.open(
                                        requireContext(), pdfFile, "Open overall review PDF");
                                Toast.makeText(requireContext(), "Overall review PDF saved", Toast.LENGTH_SHORT).show();
                            } catch (RuntimeException error) {
                                Toast.makeText(requireContext(), "PDF saved, but no viewer is available", Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        android.util.Log.e("TotalSummary", "Unable to create overall report", e);
                        if (isAdded()) requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "PDF export failed", Toast.LENGTH_LONG).show());
                    }
                }).start());
    }

    private interface ReportTransactionConsumer {
        void accept(List<Transaction> transactions, String dateRangeLabel);
    }

    private void collectReportTransactions(String emptyMessage, ReportTransactionConsumer consumer) {
        DashboardViewModel.DateRange range = viewModel.getDateRange().getValue();

        String label = "All Time";
        if (range != null && range.start > 0 && range.end < Long.MAX_VALUE) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            label = sdf.format(new Date(range.start)) + " – " + sdf.format(new Date(range.end));
        }

        final String dateRangeLabel = label;

        LiveData<List<Transaction>> txnsLive = (range != null && range.start > 0 && range.end < Long.MAX_VALUE)
                ? transactionViewModel.getTransactionsInRange(range.start, range.end)
                : transactionViewModel.getTransactions();

        txnsLive.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<List<Transaction>>() {
            @Override
            public void onChanged(List<Transaction> transactions) {
                txnsLive.removeObserver(this);
                if (transactions == null || transactions.isEmpty()) {
                    Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
                consumer.accept(transactions, dateRangeLabel);
            }
        });
    }

    /** Avoids creating an unprotected file from a locked Privacy Mode session. */
    private void requireReportAuthentication(Runnable action) {
        if (!Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue())) {
            action.run();
            return;
        }
        com.example.spendtracker.util.BiometricHelper.authenticate(requireActivity(), new com.example.spendtracker.util.BiometricHelper.BiometricCallback() {
            @Override public void onSuccess() {
                viewModel.setPrivacyModeEnabled(false);
                action.run();
            }
            @Override public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), "Authentication required to create a report", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requireFeature(com.example.spendtracker.billing.PremiumFeature feature, Runnable allowed) {
        featureGate.require(feature, allowed, () -> {
            if (isAdded()) androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.paywallFragment);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class BankTotalAdapter extends androidx.recyclerview.widget.ListAdapter<TransactionDao.CategorySum, BankTotalAdapter.ViewHolder> {
        public interface Formatter { String format(double amount); }
        public interface OnBankClickListener { void onBankClick(String bank); }
        private final Formatter formatter;
        private final OnBankClickListener listener;

        protected BankTotalAdapter(Formatter formatter, OnBankClickListener listener) {
            super(new androidx.recyclerview.widget.DiffUtil.ItemCallback<TransactionDao.CategorySum>() {
                @Override public boolean areItemsTheSame(@NonNull TransactionDao.CategorySum old, @NonNull TransactionDao.CategorySum newI) { return java.util.Objects.equals(old.category, newI.category); }
                @Override public boolean areContentsTheSame(@NonNull TransactionDao.CategorySum old, @NonNull TransactionDao.CategorySum newI) { return Double.compare(old.total, newI.total) == 0; }
            });
            this.formatter = formatter;
            this.listener = listener;
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bank_total, parent, false);
            return new ViewHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransactionDao.CategorySum item = getItem(position);
            holder.tvName.setText(item.category);
            holder.tvAmount.setText(formatter.format(item.total));
            holder.itemView.setOnClickListener(v -> listener.onBankClick(item.category));
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvName, tvAmount;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_bank_name);
                tvAmount = v.findViewById(R.id.tv_bank_amount);
            }
        }
    }
}
