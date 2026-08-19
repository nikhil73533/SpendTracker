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
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentDashboardTotalBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.example.spendtracker.data.local.dao.TransactionDao;
import dagger.hilt.android.AndroidEntryPoint;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

@AndroidEntryPoint
public class TotalSummaryFragment extends Fragment {

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
        binding.btnExportExcel.setOnClickListener(v -> exportToExcel());
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
        binding.tvComparedPercent.setText(data.comparedPercent + "%");
        binding.tvAccountExpenses.setText(formatAmountWithState(data.accountExpenses, masked));
        binding.tvCardExpenses.setText(formatAmountWithState(data.cardExpenses, masked));
        binding.tvTotalTransfers.setText(formatAmountWithState(data.transfers, masked));
    }

    private String formatAmountWithState(double amount, boolean masked) {
        if (masked) return "***";
        return String.format(Locale.getDefault(), "₹ %.0f", amount);
    }

    private void exportToExcel() {
        transactionViewModel.getAllTransactions().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<List<Transaction>>() {
            @Override
            public void onChanged(List<Transaction> transactions) {
                if (transactions == null || transactions.isEmpty()) {
                    Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show();
                    return;
                }
                transactionViewModel.getAllTransactions().removeObserver(this);
                
                try {
                    XSSFWorkbook workbook = new XSSFWorkbook();
                    Sheet sheet = workbook.createSheet("Transactions");

                    Row headerRow = sheet.createRow(0);
                    String[] headers = {"Date", "Category", "Description", "Amount", "Type", "Source", "Receiver/Sender", "UPI ID"};
                    for (int i = 0; i < headers.length; i++) {
                        headerRow.createCell(i).setCellValue(headers[i]);
                    }

                    int rowNum = 1;
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());

                    for (Transaction t : transactions) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(sdf.format(new Date(t.getDate())));
                        row.createCell(1).setCellValue(t.getCategory());
                        row.createCell(2).setCellValue(masked ? transactionViewModel.maskPII(t.getDescription()) : t.getDescription());
                        if (masked) row.createCell(3).setCellValue("***");
                        else row.createCell(3).setCellValue(t.getAmount());
                        row.createCell(4).setCellValue(t.getType());
                        row.createCell(5).setCellValue(t.getSource());
                        String contact = "INCOME".equals(t.getType()) ? t.getSender() : t.getReceiverName();
                        row.createCell(6).setCellValue(masked ? transactionViewModel.maskPII(contact) : contact);
                        row.createCell(7).setCellValue(masked ? transactionViewModel.maskPII(t.getUpiId()) : t.getUpiId());
                    }

                    File file = new File(requireContext().getExternalFilesDir(null), "SpendTracker_Export.xlsx");
                    FileOutputStream out = new FileOutputStream(file);
                    workbook.write(out);
                    out.close();
                    workbook.close();

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share Excel File"));

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
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
                @Override public boolean areItemsTheSame(@NonNull TransactionDao.CategorySum old, @NonNull TransactionDao.CategorySum newI) { return old.category.equals(newI.category); }
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
