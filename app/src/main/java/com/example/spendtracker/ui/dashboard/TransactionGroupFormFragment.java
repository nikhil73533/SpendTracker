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
        android.view.View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_category, null);
        android.widget.EditText etName = dialogView.findViewById(R.id.et_category_name);
        android.widget.Spinner spinner = dialogView.findViewById(R.id.spinner_type);

        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, new String[]{"EXPENSE", "INCOME"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        new android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_category)
            .setView(dialogView)
            .setPositiveButton("Add", (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String type = (String) spinner.getSelectedItem();
                if (!name.isEmpty()) {
                    viewModel.addCategory(name, type);
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
