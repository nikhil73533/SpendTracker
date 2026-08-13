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
import com.example.spendtracker.databinding.FragmentDashboardBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@AndroidEntryPoint
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private GroupedTransactionAdapter adapter;
    private MonthlySummaryAdapter monthlyAdapter;
    private java.util.List<String> incomeCategories = new java.util.ArrayList<>();
    private java.util.List<String> expenseCategories = new java.util.ArrayList<>();
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        setupToolbar();
        setupTabLayout();
        setupRecyclerView();
        setupFab();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.btnPrevDate.setOnClickListener(v -> viewModel.movePrev());
        binding.btnNextDate.setOnClickListener(v -> viewModel.moveNext());
        
        binding.btnFavorite.setOnClickListener(v -> android.widget.Toast.makeText(requireContext(), "Added to favorites", android.widget.Toast.LENGTH_SHORT).show());
        binding.btnSearch.setOnClickListener(v -> android.widget.Toast.makeText(requireContext(), "Search clicked", android.widget.Toast.LENGTH_SHORT).show());
        binding.btnFilter.setOnClickListener(v -> android.widget.Toast.makeText(requireContext(), "Filter clicked", android.widget.Toast.LENGTH_SHORT).show());

        setupSwipeGestures();
    }

    private void setupSwipeGestures() {
        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(requireContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX > 0) {
                        viewModel.movePrev();
                    } else {
                        viewModel.moveNext();
                    }
                    return true;
                }
                return false;
            }
        });

        binding.rvCalendar.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.layoutTotalTab.setVisibility(View.GONE);
                binding.rvTransactions.setVisibility(View.GONE);
                binding.rvCalendar.setVisibility(View.GONE);
                binding.layoutCalendarHeader.setVisibility(View.GONE);
                binding.layoutEmptyState.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: // Daily
                        viewModel.setFilter(DashboardViewModel.FilterType.DAILY);
                        binding.rvTransactions.setVisibility(View.VISIBLE);
                        binding.rvTransactions.setAdapter(adapter);
                        break;
                    case 1: // Calendar
                        viewModel.setFilter(DashboardViewModel.FilterType.CALENDAR);
                        binding.rvCalendar.setVisibility(View.VISIBLE);
                        binding.layoutCalendarHeader.setVisibility(View.VISIBLE);
                        break;
                    case 2: // Monthly
                        viewModel.setFilter(DashboardViewModel.FilterType.MONTHLY);
                        binding.rvTransactions.setVisibility(View.VISIBLE);
                        binding.rvTransactions.setAdapter(monthlyAdapter);
                        break;
                    case 3: // Total
                        viewModel.setFilter(DashboardViewModel.FilterType.MONTHLY); // Total page context for current month
                        binding.layoutTotalTab.setVisibility(View.VISIBLE);
                        break;
                    case 4: // Notes
                        viewModel.setFilter(DashboardViewModel.FilterType.NOTE);
                        android.widget.Toast.makeText(requireContext(), "Notes section coming soon", android.widget.Toast.LENGTH_SHORT).show();
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Set initial selection to Daily
        if (binding.tabLayout.getTabCount() > 0) {
            binding.tabLayout.getTabAt(0).select();
        }
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
                showDeleteConfirmation(transaction);
            }

            @Override
            public void onCategoryChange(Transaction transaction, String newCategory) {
                viewModel.updateTransactionCategory(transaction, newCategory);
            }

            @Override
            public java.util.List<String> getCategoriesByType(String type) {
                java.util.List<String> list = "INCOME".equals(type) ? incomeCategories : expenseCategories;
                if (list.isEmpty()) {
                    // Fallback to defaults if DB is empty for this type
                    if ("INCOME".equals(type)) {
                        return java.util.Arrays.asList("Salary", "Allowance", "Bonus", "Petty Cash", "Gift", "Other");
                    } else {
                        return java.util.Arrays.asList("Food", "Rent", "Travel", "Shopping", "Medical", "Other");
                    }
                }
                return list;
            }
        });
        binding.rvTransactions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(adapter);

        monthlyAdapter = new MonthlySummaryAdapter();
    }

    private void showDeleteConfirmation(Transaction transaction) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    transactionViewModel.deleteTransaction(transaction);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupFab() {
        binding.fabAddTransaction.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigate(R.id.action_dashboardFragment_to_transactionFormFragment);
        });

        // Draggable FAB
        binding.fabContainer.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View view, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        view.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });

        // Long press reset
        binding.fabAddTransaction.setOnLongClickListener(v -> {
            startResetAnimation();
            return true;
        });

        binding.btnExportExcel.setOnClickListener(v -> exportToExcel());
    }

    private void startResetAnimation() {
        binding.fabProgress.setVisibility(View.VISIBLE);
        binding.tvFabPercent.setVisibility(View.VISIBLE);
        
        android.os.Handler handler = new android.os.Handler();
        final int[] progress = {0};
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (progress[0] <= 100) {
                    binding.fabProgress.setProgress(progress[0]);
                    binding.tvFabPercent.setText(progress[0] + "%");
                    progress[0] += 5;
                    handler.postDelayed(this, 50);
                } else {
                    viewModel.resetModel();
                    binding.fabProgress.setVisibility(View.GONE);
                    binding.tvFabPercent.setVisibility(View.GONE);
                    android.widget.Toast.makeText(requireContext(), "ML Model Reset Complete", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        };
        handler.post(runnable);
    }

    private void observeViewModel() {
        viewModel.getGroupedTransactions().observe(getViewLifecycleOwner(), items -> {
            if (binding.tabLayout.getSelectedTabPosition() == 2 || binding.tabLayout.getSelectedTabPosition() == 3) return;
            if (items == null || items.isEmpty()) {
                if (binding.tabLayout.getSelectedTabPosition() == 0) {
                    binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    binding.rvTransactions.setVisibility(View.GONE);
                }
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                if (binding.tabLayout.getSelectedTabPosition() == 0) {
                    binding.rvTransactions.setVisibility(View.VISIBLE);
                }
                adapter.submitList(items);
            }
        });

        viewModel.getMonthlySummaries().observe(getViewLifecycleOwner(), summaries -> {
            monthlyAdapter.submitList(summaries);
        });

        viewModel.getCalendarDays().observe(getViewLifecycleOwner(), days -> {
            if (days != null) {
                CalendarAdapter calendarAdapter = new CalendarAdapter(days, day -> {
                    // Switch tab first so setFilter(DAILY) runs, then override with specific date
                    binding.tabLayout.getTabAt(0).select(); 
                    viewModel.setCalendarFilter(day.timestamp, "Selected Date");
                });
                binding.rvCalendar.setAdapter(calendarAdapter);
            }
        });

        viewModel.getDateRange().observe(getViewLifecycleOwner(), range -> {
            if (range.start == 0) {
                binding.tvDashboardHeader.setText("All Time");
            } else {
                binding.tvDashboardHeader.setText(range.label);
            }
            binding.tvTotalRangeLabel.setText(new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(new Date(range.start)) + " ~ " + 
                                            new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(new Date(range.end)));
        });

        viewModel.getSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                binding.tvTotalIncome.setText(String.format(Locale.getDefault(), "₹%.2f", summary.getTotalIncome()));
                binding.tvTotalExpense.setText(String.format(Locale.getDefault(), "₹%.2f", summary.getTotalExpense()));
                binding.tvAccountTotal.setText(String.format(Locale.getDefault(), "₹%.2f", summary.getTotalAccountTransaction()));
            }
        });

        viewModel.getTotalPageData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                binding.tvComparedPercent.setText(data.comparedPercent + "%");
                binding.tvAccountExpenses.setText(String.format(Locale.getDefault(), "₹ %.2f", data.accountExpenses));
                binding.tvCardExpenses.setText(String.format(Locale.getDefault(), "₹ %.2f", data.cardExpenses));
                binding.tvTotalTransfers.setText(String.format(Locale.getDefault(), "₹ %.2f", data.transfers));
            }
        });

        viewModel.getCategories().observe(getViewLifecycleOwner(), list -> {
            // This is for all categories, but we want typed ones for the dropdown
            // To be more efficient, we could observe getCategoriesByType in VM
        });

        transactionViewModel.getIncomeCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) incomeCategories = list;
        });

        transactionViewModel.getExpenseCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) expenseCategories = list;
        });
    }

    private void exportToExcel() {
        // Fetch fresh data for export
        transactionViewModel.getAllTransactions().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<java.util.List<Transaction>>() {
            @Override
            public void onChanged(java.util.List<Transaction> transactions) {
                if (transactions == null) return;
                // Stop observing after getting data
                transactionViewModel.getAllTransactions().removeObserver(this);
                
                try {
                    org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                    org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Transactions");

                    // Header
                    org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                    String[] headers = {"Date", "Category", "Description", "Amount", "Type", "Source", "Receiver/Sender", "UPI ID"};
                    for (int i = 0; i < headers.length; i++) {
                        headerRow.createCell(i).setCellValue(headers[i]);
                    }

                    // Data
                    int rowNum = 1;
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    for (Transaction t : transactions) {
                        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(sdf.format(new Date(t.getDate())));
                        row.createCell(1).setCellValue(t.getCategory());
                        row.createCell(2).setCellValue(t.getDescription());
                        row.createCell(3).setCellValue(t.getAmount());
                        row.createCell(4).setCellValue(t.getType());
                        row.createCell(5).setCellValue(t.getSource());
                        row.createCell(6).setCellValue("INCOME".equals(t.getType()) ? t.getSender() : t.getReceiverName());
                        row.createCell(7).setCellValue(t.getUpiId());
                    }

                    String fileName = "SpendTracker_" + System.currentTimeMillis() + ".xlsx";
                    java.io.File file = new java.io.File(requireContext().getExternalFilesDir(null), fileName);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                    workbook.write(out);
                    out.close();
                    workbook.close();

                    android.widget.Toast.makeText(requireContext(), "Excel exported to: " + file.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
                    
                    // Share intent
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
                    intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(android.content.Intent.createChooser(intent, "Share Excel File"));

                } catch (Exception e) {
                    e.printStackTrace();
                    android.widget.Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
