package com.example.spendtracker.ui.transaction;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentTransactionFormBinding;
import com.example.spendtracker.domain.model.Transaction;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

@AndroidEntryPoint
public class TransactionFormFragment extends Fragment {

    private FragmentTransactionFormBinding binding;
    private TransactionViewModel viewModel;
    private Transaction existingTransaction;
    private Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateTimeSdf = new SimpleDateFormat("dd/MM/yy (EEE) h:mm a", Locale.getDefault());
    private int transactionId = -1;
    private String selectedType = "EXPENSE";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        if (getArguments() != null) {
            transactionId = getArguments().getInt("transactionId", -1);
        }

        setupUI();
        if (transactionId != -1) {
            loadTransaction();
        }
    }

    private void setupUI() {
        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_type_income) {
                    selectedType = "INCOME";
                } else if (checkedId == R.id.btn_type_expense) {
                    selectedType = "EXPENSE";
                } else if (checkedId == R.id.btn_type_transfer) {
                    selectedType = "TRANSFER";
                }
                updateFormForType();
            }
        });

        // Observe categories once
        viewModel.getCategoriesByType().observe(getViewLifecycleOwner(), this::updateCategoryAdapter);

        updateFormForType();

        binding.actvCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            if ("Create New Category...".equals(selected)) {
                showCreateCategoryDialog();
            } else if ("Manage Categories...".equals(selected)) {
                showManageCategoriesDialog();
            }
        });

        updateDateTimeLabels();

        binding.btnPickDateTime.setOnClickListener(v -> showDateTimePicker());
        binding.btnSave.setOnClickListener(v -> saveTransaction());
        binding.btnContinue.setOnClickListener(v -> saveTransaction());
    }

    private void updateFormForType() {
        if ("TRANSFER".equals(selectedType)) {
            binding.tilCategory.setVisibility(View.GONE);
            binding.layoutTransferFields.setVisibility(View.VISIBLE);
            binding.btnFees.setVisibility(View.VISIBLE);
            binding.tilSender.setVisibility(View.GONE);
            binding.tilReceiver.setVisibility(View.GONE);
        } else {
            binding.tilCategory.setVisibility(View.VISIBLE);
            binding.layoutTransferFields.setVisibility(View.GONE);
            binding.btnFees.setVisibility(View.GONE);
            
            if ("INCOME".equals(selectedType)) {
                binding.tilSender.setVisibility(View.VISIBLE);
                binding.tilReceiver.setVisibility(View.GONE);
                binding.tilCategory.setHint("Income Category");
            } else {
                binding.tilSender.setVisibility(View.GONE);
                binding.tilReceiver.setVisibility(View.VISIBLE);
                binding.tilCategory.setHint("Category");
            }

            // Trigger category update via filter change
            viewModel.setCategoryTypeFilter(selectedType);
        }
    }

    private void updateCategoryAdapter(java.util.List<String> categories) {
        java.util.List<String> filtered = new java.util.ArrayList<>();
        if (categories != null) {
            for (String c : categories) {
                if (!"Transfer".equalsIgnoreCase(c)) filtered.add(c);
            }
        }
        
        // Add default categories if empty (initial bootstrap)
        if (filtered.isEmpty()) {
            if ("INCOME".equals(selectedType)) {
                filtered.addAll(java.util.Arrays.asList("Salary", "Allowance", "Bonus", "Petty Cash", "Other"));
            } else {
                filtered.addAll(java.util.Arrays.asList("Food", "Rent", "Travel", "Shopping", "Medical", "Other"));
            }
        }

        if (!filtered.contains("Create New Category...")) {
            filtered.add("Create New Category...");
        }
        if (!filtered.contains("Manage Categories...")) {
            filtered.add("Manage Categories...");
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, filtered);
        binding.actvCategory.setAdapter(adapter);
    }

    private void updateDateTimeLabels() {
        binding.btnPickDateTime.setText(dateTimeSdf.format(calendar.getTime()));
    }

    private void showDateTimePicker() {
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            
            new TimePickerDialog(requireContext(), (tView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                updateDateTimeLabels();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
            
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showCreateCategoryDialog() {
        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setHint("Category Name (e.g. MyCat 🐱)");
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Create Category")
                .setView(et)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = et.getText().toString();
                    if (!name.isEmpty()) {
                        viewModel.addCategory(name, selectedType);
                        binding.actvCategory.setText(name, false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadTransaction() {
        viewModel.getTransaction(transactionId).observe(getViewLifecycleOwner(), transaction -> {
            if (transaction != null) {
                this.existingTransaction = transaction;
                binding.etAmount.setText(String.valueOf(transaction.getAmount()));
                binding.actvCategory.setText(transaction.getCategory(), false);
                binding.etDescription.setText(transaction.getDescription());
                binding.etSender.setText(transaction.getSender());
                binding.etReceiver.setText(transaction.getReceiverName());
                
                selectedType = transaction.getType();
                if ("INCOME".equals(selectedType)) {
                    binding.toggleType.check(R.id.btn_type_income);
                } else if ("TRANSFER".equals(selectedType)) {
                    binding.toggleType.check(R.id.btn_type_transfer);
                    binding.etFromAccount.setText(transaction.getFromAccount());
                    binding.etToAccount.setText(transaction.getToAccount());
                } else {
                    binding.toggleType.check(R.id.btn_type_expense);
                }
                
                calendar.setTimeInMillis(transaction.getDate());
                updateDateTimeLabels();
                updateFormForType();
            }
        });
    }

    private void showManageCategoriesDialog() {
        viewModel.getCategoriesByType(selectedType).observe(getViewLifecycleOwner(), categories -> {
            if (categories == null) return;
            
            java.util.List<String> filtered = new java.util.ArrayList<>();
            for (String c : categories) if (!"Transfer".equalsIgnoreCase(c)) filtered.add(c);

            String[] items = filtered.toArray(new String[0]);
            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Manage " + selectedType.toLowerCase() + " Categories")
                    .setItems(items, (dialog, which) -> {
                        String selected = items[which];
                        showEditDeleteDialog(selected);
                    })
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    private void showEditDeleteDialog(String categoryName) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(categoryName)
                .setItems(new String[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showEditCategoryDialog(categoryName);
                    } else {
                        viewModel.deleteCategory(categoryName);
                    }
                })
                .show();
    }

    private void showEditCategoryDialog(String oldName) {
        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setText(oldName);
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Edit Category")
                .setView(et)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newName = et.getText().toString();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        viewModel.renameCategory(oldName, newName);
                        binding.actvCategory.setText(newName, false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveTransaction() {
        String amountStr = binding.etAmount.getText().toString();
        if (amountStr.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(amountStr);
        String category = "TRANSFER".equals(selectedType) ? "Transfer" : binding.actvCategory.getText().toString();
        String description = binding.etDescription.getText().toString();
        String sender = binding.etSender.getText().toString();
        String receiver = binding.etReceiver.getText().toString();

        Transaction transaction;
        if (existingTransaction != null) {
            transaction = new Transaction(
                    transactionId,
                    amount,
                    category,
                    description,
                    selectedType,
                    calendar.getTimeInMillis(),
                    existingTransaction.getSource(),
                    sender,
                    existingTransaction.getUpiId(),
                    receiver,
                    existingTransaction.getBankName(),
                    existingTransaction.getSourceType(),
                    binding.etFromAccount.getText().toString(),
                    binding.etToAccount.getText().toString(),
                    0.0
            );
        } else {
            transaction = new Transaction(
                    0,
                    amount,
                    category,
                    description,
                    selectedType,
                    calendar.getTimeInMillis(),
                    "Manual",
                    sender,
                    "",
                    receiver,
                    "",
                    "Manual",
                    binding.etFromAccount.getText().toString(),
                    binding.etToAccount.getText().toString(),
                    0.0
            );
        }

        if (transactionId == -1) {
            viewModel.addTransaction(transaction);
        } else {
            viewModel.updateTransaction(transaction);
        }

        Navigation.findNavController(requireView()).navigateUp();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
