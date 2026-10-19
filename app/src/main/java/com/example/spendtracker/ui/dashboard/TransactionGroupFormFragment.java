package com.example.spendtracker.ui.dashboard;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentTransactionGroupFormBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class TransactionGroupFormFragment extends BottomSheetDialogFragment {

    private static final String ARG_GROUP_ID = "group_id";
    private FragmentTransactionGroupFormBinding binding;
    private TransactionGroupViewModel viewModel;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private long selectedStartDate = 0;
    private long selectedEndDate = 0;
    private int editGroupId = -1;

    public static TransactionGroupFormFragment newInstance(int groupId) {
        TransactionGroupFormFragment f = new TransactionGroupFormFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_GROUP_ID, groupId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionGroupFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionGroupViewModel.class);

        if (getArguments() != null) {
            editGroupId = getArguments().getInt(ARG_GROUP_ID, -1);
        }

        setupDatePickers();
        loadCategories();

        if (editGroupId > 0) {
            binding.tvFormTitle.setText(R.string.title_edit_group);
            binding.btnDelete.setVisibility(View.VISIBLE);
            loadGroupData();
        }

        binding.btnSave.setOnClickListener(v -> saveGroup());
        binding.btnDelete.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.btn_delete_group)
                .setMessage("Are you sure you want to delete this group?")
                .setPositiveButton("Delete", (d, w) -> {
                    viewModel.deleteGroup(editGroupId);
                    Toast.makeText(requireContext(), R.string.msg_group_deleted, Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void setupDatePickers() {
        binding.etStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.tilStartDate.setEndIconOnClickListener(v -> showDatePicker(true));
        binding.etEndDate.setOnClickListener(v -> showDatePicker(false));
        binding.tilEndDate.setEndIconOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        if (isStart && selectedStartDate > 0) cal.setTimeInMillis(selectedStartDate);
        else if (!isStart && selectedEndDate > 0) cal.setTimeInMillis(selectedEndDate);

        new DatePickerDialog(requireContext(), (dp, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            if (isStart) {
                selectedStartDate = selected.getTimeInMillis();
                binding.etStartDate.setText(sdf.format(new Date(selectedStartDate)));
            } else {
                selected.set(Calendar.HOUR_OF_DAY, 23);
                selected.set(Calendar.MINUTE, 59);
                selected.set(Calendar.SECOND, 59);
                selectedEndDate = selected.getTimeInMillis();
                binding.etEndDate.setText(sdf.format(new Date(selectedEndDate)));
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadCategories() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            binding.chipGroupCategories.removeAllViews();
            if (categories != null) {
                for (String cat : categories) {
                    addCategoryChip(cat);
                }
            }
            // Add the "+" chip for creating new categories
            addCreateCategoryChip();
        });
    }

    private void addCategoryChip(String cat) {
        Chip chip = new Chip(requireContext());
        chip.setText(cat);
        chip.setCheckable(true);
        chip.setChipBackgroundColorResource(R.color.dark_surface);
        chip.setTextColor(getResources().getColor(R.color.white, null));
        chip.setTag(cat);
        chip.setCloseIconVisible(true);
        chip.setCloseIconTintResource(R.color.expense_red);

        // Long press to rename
        chip.setOnLongClickListener(v -> {
            showRenameCategoryDialog(cat);
            return true;
        });

        // Close icon to delete
        chip.setOnCloseIconClickListener(v -> {
            showDeleteCategoryConfirmation(cat);
        });

        binding.chipGroupCategories.addView(chip);
    }

    private void addCreateCategoryChip() {
        Chip addChip = new Chip(requireContext());
        addChip.setText("+ Add");
        addChip.setCheckable(false);
        addChip.setChipBackgroundColorResource(R.color.income_blue);
        addChip.setTextColor(getResources().getColor(R.color.white, null));
        addChip.setChipStrokeWidth(0);
        addChip.setOnClickListener(v -> showAddCategoryDialog());
        binding.chipGroupCategories.addView(addChip);
    }

    private void showAddCategoryDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_category, null);

        android.widget.EditText etName = dialogView.findViewById(R.id.et_category_name);
        android.widget.Spinner spinnerType = dialogView.findViewById(R.id.spinner_type);
        android.widget.Spinner spinnerPeriod = dialogView.findViewById(R.id.spinner_period);
        com.google.android.material.switchmaterial.SwitchMaterial switchUnlimited = dialogView.findViewById(R.id.switch_unlimited);
        android.widget.LinearLayout layoutBudgetInputs = dialogView.findViewById(R.id.layout_budget_inputs);
        android.widget.Spinner spinnerMaxRange = dialogView.findViewById(R.id.spinner_max_range);
        android.widget.TextView tvSliderLabel = dialogView.findViewById(R.id.tv_slider_label);
        com.google.android.material.slider.Slider sliderBudget = dialogView.findViewById(R.id.slider_budget);
        android.widget.EditText etBudgetAmount = dialogView.findViewById(R.id.et_budget_amount);
        android.widget.TextView tvBudgetSummary = dialogView.findViewById(R.id.tv_budget_summary);
        com.google.android.material.switchmaterial.SwitchMaterial switchNotifications = dialogView.findViewById(R.id.switch_notifications);

        android.widget.ArrayAdapter<String> typeAdapter = new android.widget.ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, new String[]{"EXPENSE", "INCOME"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        android.widget.ArrayAdapter<String> periodAdapter = new android.widget.ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, new String[]{"Weekly", "Monthly", "Annually"});
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriod.setAdapter(periodAdapter);

        android.widget.ArrayAdapter<String> maxRangeAdapter = new android.widget.ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, new String[]{"0 - ₹ 10,000", "0 - ₹ 50,000", "0 - ₹ 1,000,000"});
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
            .setTitle("Add Category & Budget")
            .setView(dialogView)
            .setPositiveButton("Add", (dialog, which) -> {
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
                    Toast.makeText(requireContext(), "Category '" + name + "' added", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showRenameCategoryDialog(String oldName) {
        android.widget.EditText etNewName = new android.widget.EditText(requireContext());
        etNewName.setText(oldName);
        etNewName.setSelectAllOnFocus(true);

        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Rename Category")
            .setView(etNewName)
            .setPositiveButton("Rename", (dialog, which) -> {
                String newName = etNewName.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(oldName)) {
                    viewModel.renameCategory(oldName, newName);
                    Toast.makeText(requireContext(), "Renamed '" + oldName + "' → '" + newName + "'", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showDeleteCategoryConfirmation(String categoryName) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Delete category '" + categoryName + "'? Transactions won't be deleted.")
            .setPositiveButton("Delete", (d, w) -> {
                viewModel.deleteCategory(categoryName);
                Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void loadGroupData() {
        viewModel.getGroupById(editGroupId).observe(getViewLifecycleOwner(), group -> {
            if (group == null) return;
            binding.etGroupName.setText(group.name);
            selectedStartDate = group.startDate;
            selectedEndDate = group.endDate;
            binding.etStartDate.setText(sdf.format(new Date(group.startDate)));
            binding.etEndDate.setText(sdf.format(new Date(group.endDate)));
        });

        viewModel.getGroupCategories(editGroupId).observe(getViewLifecycleOwner(), selectedCats -> {
            if (selectedCats == null) return;
            for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
                View child = binding.chipGroupCategories.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    chip.setChecked(selectedCats.contains(chip.getTag()));
                }
            }
        });
    }

    private void saveGroup() {
        String name = binding.etGroupName.getText() != null ? binding.etGroupName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.tilGroupName.setError(getString(R.string.error_group_name_required));
            return;
        }
        binding.tilGroupName.setError(null);

        if (selectedStartDate == 0) {
            binding.tilStartDate.setError(getString(R.string.error_start_date_required));
            return;
        }
        binding.tilStartDate.setError(null);

        if (selectedEndDate == 0) {
            binding.tilEndDate.setError(getString(R.string.error_end_date_required));
            return;
        }
        binding.tilEndDate.setError(null);

        if (selectedEndDate < selectedStartDate) {
            binding.tilEndDate.setError(getString(R.string.error_end_before_start));
            return;
        }

        List<String> selectedCategories = new ArrayList<>();
        for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
            View child = binding.chipGroupCategories.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                selectedCategories.add(((Chip) child).getTag().toString());
            }
        }

        if (selectedCategories.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_categories_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (editGroupId > 0) {
            viewModel.updateGroup(editGroupId, name, selectedStartDate, selectedEndDate, selectedCategories);
            Toast.makeText(requireContext(), R.string.msg_group_updated, Toast.LENGTH_SHORT).show();
        } else {
            viewModel.createGroup(name, selectedStartDate, selectedEndDate, selectedCategories);
            Toast.makeText(requireContext(), R.string.msg_group_created, Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
