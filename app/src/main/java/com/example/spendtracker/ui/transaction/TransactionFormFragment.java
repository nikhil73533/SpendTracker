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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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

    /** Known Indian bank names for the Bank Name autocomplete. */
    private static final List<String> KNOWN_BANKS = Arrays.asList(
        "ICICI Bank", "HDFC Bank", "SBI", "Axis Bank", "Kotak Bank",
        "Yes Bank", "PNB", "Bank of Baroda", "Union Bank", "Canara Bank",
        "IDBI Bank", "IndusInd Bank", "Federal Bank", "RBL Bank",
        "AU Bank", "Bajaj Finance", "Paytm", "PhonePe", "Amazon Pay",
        "Airtel Payments Bank", "Jio Payments Bank", "OneCard", "Slice", "Navi"
    );

    /** Source-type options for the Account / Credit Card dropdown. */
    private static final List<String> SOURCE_TYPES = Arrays.asList(
        "Account", "Credit Card", "UPI", "Wallet", "Manual"
    );

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

        // Category suggestions
        viewModel.getCategoriesByType().observe(getViewLifecycleOwner(), this::updateCategoryAdapter);

        // Contact suggestions for Sender / Receiver
        viewModel.getUniqueContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts != null) {
                ArrayAdapter<String> contactAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, contacts);
                binding.etSender.setAdapter(contactAdapter);
                binding.etReceiver.setAdapter(contactAdapter);
            }
        });

        // Bank name autocomplete
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, KNOWN_BANKS);
        binding.etBankName.setAdapter(bankAdapter);

        // Account / Credit Card type dropdown
        ArrayAdapter<String> sourceTypeAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, SOURCE_TYPES);
        binding.etAccountInfo.setAdapter(sourceTypeAdapter);

        updateFormForType();

        binding.actvCategory.setOnItemClickListener((parent, v, position, id) -> {
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

    private void updateCategoryAdapter(List<String> categories) {
        java.util.LinkedHashSet<String> uniqueCategories = new java.util.LinkedHashSet<>();
        uniqueCategories.add("Transfer");
        if (categories != null) {
            uniqueCategories.addAll(categories);
        }

        if (uniqueCategories.size() <= 1) {
            if ("INCOME".equals(selectedType)) {
                uniqueCategories.addAll(Arrays.asList("Salary", "Allowance", "Bonus", "Petty Cash", "Other"));
            } else {
                uniqueCategories.addAll(Arrays.asList("Food", "Rent", "Travel", "Shopping", "Medical", "Other"));
            }
        }

        List<String> filtered = new ArrayList<>(uniqueCategories);

        if (!filtered.contains("Create New Category...")) {
            filtered.add("Create New Category...");
        }
        if (!filtered.contains("Manage Categories...")) {
            filtered.add("Manage Categories...");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, filtered);
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
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_category, null);
        
        android.widget.EditText etName = view.findViewById(R.id.et_category_name);
        android.widget.Spinner spinnerType = view.findViewById(R.id.spinner_type);
        android.widget.Spinner spinnerPeriod = view.findViewById(R.id.spinner_period);
        com.google.android.material.switchmaterial.SwitchMaterial switchUnlimited = view.findViewById(R.id.switch_unlimited);
        android.widget.LinearLayout layoutBudgetInputs = view.findViewById(R.id.layout_budget_inputs);
        android.widget.Spinner spinnerMaxRange = view.findViewById(R.id.spinner_max_range);
        android.widget.TextView tvSliderLabel = view.findViewById(R.id.tv_slider_label);
        com.google.android.material.slider.Slider sliderBudget = view.findViewById(R.id.slider_budget);
        android.widget.EditText etBudgetAmount = view.findViewById(R.id.et_budget_amount);
        android.widget.TextView tvBudgetSummary = view.findViewById(R.id.tv_budget_summary);
        com.google.android.material.switchmaterial.SwitchMaterial switchNotifications = view.findViewById(R.id.switch_notifications);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"EXPENSE", "INCOME"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);
        spinnerType.setSelection("INCOME".equalsIgnoreCase(selectedType) ? 1 : 0);

        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Weekly", "Monthly", "Annually"});
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriod.setAdapter(periodAdapter);

        ArrayAdapter<String> maxRangeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"0 - ₹ 10,000", "0 - ₹ 50,000", "0 - ₹ 1,000,000"});
        maxRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxRange.setAdapter(maxRangeAdapter);

        final boolean[] unlimited = new boolean[]{true, true, true};
        final double[] budgets = new double[]{0.0, 0.0, 0.0};

        Runnable updateSummary = () -> {
            String summaryText = String.format(Locale.getDefault(),
                    "Weekly: %s | Monthly: %s | Annually: %s",
                    unlimited[0] ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", budgets[0]),
                    unlimited[1] ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", budgets[1]),
                    unlimited[2] ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", budgets[2]));
            tvBudgetSummary.setText(summaryText);
        };

        Runnable updateMaxLimit = () -> {
            int pos = spinnerMaxRange.getSelectedItemPosition();
            float maxVal = (pos == 1) ? 50000f : ((pos == 2) ? 1000000f : 10000f);
            float step = (pos == 2) ? 1000f : ((pos == 1) ? 500f : 100f);

            sliderBudget.setValueTo(maxVal);
            sliderBudget.setStepSize(step);
            tvSliderLabel.setText(String.format(Locale.getDefault(), "Budget Bar (0 - ₹ %.0f)", maxVal));

            int currentPeriod = spinnerPeriod.getSelectedItemPosition();
            float val = (float) budgets[currentPeriod];
            if (val > maxVal) val = maxVal;
            if (val < 0) val = 0;
            sliderBudget.setValue(val);
        };

        Runnable bindPeriodData = () -> {
            int currentPeriod = spinnerPeriod.getSelectedItemPosition();
            boolean isUnlimited = unlimited[currentPeriod];
            switchUnlimited.setChecked(isUnlimited);
            layoutBudgetInputs.setVisibility(isUnlimited ? View.GONE : View.VISIBLE);

            if (!isUnlimited) {
                double currentVal = budgets[currentPeriod];
                if (currentVal > 50000) spinnerMaxRange.setSelection(2);
                else if (currentVal > 10000) spinnerMaxRange.setSelection(1);
                else spinnerMaxRange.setSelection(0);

                updateMaxLimit.run();
                etBudgetAmount.setText(currentVal > 0 ? String.format(Locale.getDefault(), "%.0f", currentVal) : "0");
            }
            updateSummary.run();
        };

        spinnerPeriod.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                bindPeriodData.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerMaxRange.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateMaxLimit.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        switchUnlimited.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int currentPeriod = spinnerPeriod.getSelectedItemPosition();
            unlimited[currentPeriod] = isChecked;
            layoutBudgetInputs.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            if (!isChecked && budgets[currentPeriod] == 0) {
                budgets[currentPeriod] = 1000.0;
                etBudgetAmount.setText("1000");
                sliderBudget.setValue(1000f);
            }
            updateSummary.run();
        });

        sliderBudget.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int currentPeriod = spinnerPeriod.getSelectedItemPosition();
                budgets[currentPeriod] = value;
                etBudgetAmount.setText(String.format(Locale.getDefault(), "%.0f", value));
                updateSummary.run();
            }
        });

        etBudgetAmount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    double val = Double.parseDouble(s.toString().trim());
                    int currentPeriod = spinnerPeriod.getSelectedItemPosition();
                    budgets[currentPeriod] = val;
                    if (val <= sliderBudget.getValueTo() && val >= sliderBudget.getValueFrom()) {
                        sliderBudget.setValue((float) val);
                    }
                    updateSummary.run();
                } catch (Exception ignored) {}
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        bindPeriodData.run();

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Create Category & Budget")
                .setView(view)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String type = (String) spinnerType.getSelectedItem();
                    if (!name.isEmpty()) {
                        com.example.spendtracker.data.local.entity.CategoryEntity catToSave =
                                new com.example.spendtracker.data.local.entity.CategoryEntity(
                                        0, name, "", false, type,
                                        unlimited[0], budgets[0],
                                        unlimited[1], budgets[1],
                                        unlimited[2], budgets[2],
                                        switchNotifications.isChecked()
                                );
                        viewModel.saveCategory(catToSave);
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
                
                String displayCategory = transaction.getCategoryName();
                if (transaction.getCategoryEmoji() != null && !transaction.getCategoryEmoji().isEmpty()) {
                    displayCategory = transaction.getCategoryEmoji() + " " + displayCategory;
                }
                binding.actvCategory.setText(displayCategory, false);
                
                binding.etDescription.setText(transaction.getDescription());
                binding.etSender.setText(transaction.getSender());
                binding.etReceiver.setText(transaction.getReceiverName());

                // Populate new fields
                String bankName = transaction.getBankName() != null ? transaction.getBankName() : "";
                binding.etBankName.setText(bankName, false);
                String sourceType = transaction.getSourceType() != null ? transaction.getSourceType() : "";
                binding.etAccountInfo.setText(sourceType, false);

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
        try {
            Navigation.findNavController(requireView()).navigate(R.id.action_transactionFormFragment_to_categoryManagementFragment);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Manage Categories", Toast.LENGTH_SHORT).show();
        }
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
        String categoryInput = "TRANSFER".equals(selectedType) ? "Transfer" : binding.actvCategory.getText().toString().trim();
        
        String categoryName = categoryInput;
        String categoryEmoji = "";
        if (categoryInput.length() >= 2 && Character.isSurrogate(categoryInput.charAt(0))) {
            categoryEmoji = categoryInput.substring(0, 2);
            categoryName = categoryInput.substring(2).trim();
        }

        String description = cleanText(binding.etDescription.getText().toString());
        String sender = cleanText(binding.etSender.getText().toString());
        String receiver = cleanText(binding.etReceiver.getText().toString());
        String bankName = cleanText(binding.etBankName.getText().toString());
        String accountInfo = cleanText(binding.etAccountInfo.getText().toString());
        String sourceType = accountInfo.isEmpty() ? "Manual" : accountInfo;
        String source = bankName.isEmpty() ? "Manual" : bankName + " (" + sourceType + ")";

        Transaction transaction;
        if (existingTransaction != null) {
            transaction = new Transaction(
                    transactionId,
                    amount,
                    categoryName,
                    categoryEmoji,
                    description,
                    selectedType,
                    calendar.getTimeInMillis(),
                    source,
                    sender,
                    existingTransaction.getUpiId(),
                    receiver,
                    bankName.isEmpty() ? existingTransaction.getBankName() : bankName,
                    sourceType,
                    binding.etFromAccount.getText().toString(),
                    binding.etToAccount.getText().toString(),
                    0.0
            );
        } else {
            transaction = new Transaction(
                    0,
                    amount,
                    categoryName,
                    categoryEmoji,
                    description,
                    selectedType,
                    calendar.getTimeInMillis(),
                    source,
                    sender,
                    "",
                    receiver,
                    bankName.isEmpty() ? "" : bankName,
                    sourceType,
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

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "").trim();
    }
}
