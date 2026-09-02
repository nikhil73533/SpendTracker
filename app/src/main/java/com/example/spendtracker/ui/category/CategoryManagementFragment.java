package com.example.spendtracker.ui.category;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.databinding.FragmentCategoryManagementBinding;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment managing category creation, budget range configurations (weekly, monthly, annual),
 * and notification preferences with dedicated Expense/Income tabs.
 */
@AndroidEntryPoint
public class CategoryManagementFragment extends Fragment {

    private FragmentCategoryManagementBinding binding;
    private TransactionViewModel viewModel;
    private CategoryAdapter adapter;
    private List<CategoryEntity> allCategoryEntities = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryManagementBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        setupRecyclerView();
        setupToolbar();
        setupTabLayout();
        setupFab();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void setupTabLayout() {
        binding.tabLayoutType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean isExpense = tab.getPosition() == 0;
                binding.tabLayoutType.setSelectedTabIndicatorColor(requireContext().getColor(isExpense ? R.color.expense_red : R.color.income_blue));
                filterCategoryList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onEdit(CategoryEntity category) {
                showCategoryDialog(category);
            }

            @Override
            public void onDelete(CategoryEntity category) {
                showDeleteConfirmation(category);
            }
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabAddCategory.setOnClickListener(v -> showCategoryDialog(null));
    }

    private void observeViewModel() {
        viewModel.getCategoryEntities().observe(getViewLifecycleOwner(), entities -> {
            if (entities != null) {
                allCategoryEntities = entities;
                filterCategoryList();
            }
        });
    }

    private void filterCategoryList() {
        if (binding == null) return;
        boolean isExpense = binding.tabLayoutType.getSelectedTabPosition() == 0;
        String activeType = isExpense ? "EXPENSE" : "INCOME";
        List<CategoryEntity> filtered = new ArrayList<>();
        for (CategoryEntity c : allCategoryEntities) {
            if (activeType.equalsIgnoreCase(c.type)) {
                filtered.add(c);
            }
        }
        adapter.submitList(filtered);
    }

    /**
     * Shows a dialog to create a new category or edit an existing category's budget ranges & settings.
     */
    private void showCategoryDialog(@Nullable CategoryEntity existing) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_category, null);
        
        EditText etName = view.findViewById(R.id.et_category_name);
        Spinner spinnerType = view.findViewById(R.id.spinner_type);
        Spinner spinnerPeriod = view.findViewById(R.id.spinner_period);
        SwitchMaterial switchUnlimited = view.findViewById(R.id.switch_unlimited);
        LinearLayout layoutBudgetInputs = view.findViewById(R.id.layout_budget_inputs);
        Spinner spinnerMaxRange = view.findViewById(R.id.spinner_max_range);
        TextView tvSliderLabel = view.findViewById(R.id.tv_slider_label);
        Slider sliderBudget = view.findViewById(R.id.slider_budget);
        EditText etBudgetAmount = view.findViewById(R.id.et_budget_amount);
        TextView tvBudgetSummary = view.findViewById(R.id.tv_budget_summary);
        SwitchMaterial switchNotifications = view.findViewById(R.id.switch_notifications);

        // 1. Setup Type Spinner
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"EXPENSE", "INCOME"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        // 2. Setup Period Spinner
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Weekly", "Monthly", "Annually"});
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriod.setAdapter(periodAdapter);

        // 3. Setup Max Range Spinner
        ArrayAdapter<String> maxRangeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"0 - ₹ 10,000", "0 - ₹ 50,000", "0 - ₹ 1,000,000"});
        maxRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxRange.setAdapter(maxRangeAdapter);

        // State arrays for 3 periods: [0]=Weekly, [1]=Monthly, [2]=Annually
        final boolean[] unlimited = new boolean[]{
                existing != null ? existing.unlimitedWeekly : true,
                existing != null ? existing.unlimitedMonthly : true,
                existing != null ? existing.unlimitedAnnually : true
        };
        final double[] budgets = new double[]{
                existing != null ? existing.weeklyBudget : 0.0,
                existing != null ? existing.monthlyBudget : 0.0,
                existing != null ? existing.annuallyBudget : 0.0
        };

        if (existing != null) {
            etName.setText(existing.name);
            spinnerType.setSelection("INCOME".equalsIgnoreCase(existing.type) ? 1 : 0);
            switchNotifications.setChecked(existing.notificationsEnabled);
        } else {
            // Default type based on currently selected tab
            int currentTabPos = binding.tabLayoutType.getSelectedTabPosition();
            spinnerType.setSelection(currentTabPos == 1 ? 1 : 0);
        }

        // Helper to refresh budget summary text
        Runnable updateSummary = () -> {
            String summaryText = String.format(Locale.getDefault(),
                    "Weekly: %s | Monthly: %s | Annually: %s",
                    unlimited[0] ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", budgets[0]),
                    unlimited[1] ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", budgets[1]),
                    unlimited[2] ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", budgets[2]));
            tvBudgetSummary.setText(summaryText);
        };

        // Helper to set slider max limit from spinner position
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

        // Helper to bind current period settings into UI controls
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

        // Event Listeners
        spinnerPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bindPeriodData.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerMaxRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateMaxLimit.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
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

        etBudgetAmount.addTextChangedListener(new TextWatcher() {
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
            @Override public void afterTextChanged(Editable s) {}
        });

        // Initialize state
        bindPeriodData.run();

        new AlertDialog.Builder(requireContext())
                .setTitle(existing != null ? "Edit Category & Budget" : "Add Category")
                .setView(view)
                .setPositiveButton(existing != null ? "Save" : "Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String type = (String) spinnerType.getSelectedItem();
                    if (!name.isEmpty()) {
                        CategoryEntity catToSave = new CategoryEntity(
                                existing != null ? existing.id : 0,
                                name,
                                existing != null ? existing.icon : "",
                                existing != null ? existing.isDefault : false,
                                type,
                                unlimited[0], budgets[0],
                                unlimited[1], budgets[1],
                                unlimited[2], budgets[2],
                                switchNotifications.isChecked()
                        );
                        viewModel.saveCategory(catToSave);
                    } else {
                        Toast.makeText(requireContext(), "Please enter category name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation(CategoryEntity category) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete category: " + category.name + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteCategory(category.name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private List<CategoryEntity> categories = new ArrayList<>();
        private final OnCategoryClickListener listener;

        interface OnCategoryClickListener {
            void onEdit(CategoryEntity category);
            void onDelete(CategoryEntity category);
        }

        CategoryAdapter(OnCategoryClickListener listener) {
            this.listener = listener;
        }

        void submitList(List<CategoryEntity> list) {
            this.categories = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryEntity cat = categories.get(position);
            holder.tvText1.setText(String.format(Locale.getDefault(), "%s (%s)", cat.name, cat.type));

            String wStr = cat.unlimitedWeekly ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", cat.weeklyBudget);
            String mStr = cat.unlimitedMonthly ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", cat.monthlyBudget);
            String aStr = cat.unlimitedAnnually ? "Unlimited" : String.format(Locale.getDefault(), "₹%.0f", cat.annuallyBudget);
            String notifStr = cat.notificationsEnabled ? "🔔 On" : "🔕 Off";

            holder.tvText2.setText(String.format(Locale.getDefault(), "Weekly: %s | Monthly: %s | Annual: %s | Notif: %s", wStr, mStr, aStr, notifStr));

            holder.itemView.setOnClickListener(v -> listener.onEdit(cat));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onDelete(cat);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvText1, tvText2;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvText1 = itemView.findViewById(android.R.id.text1);
                tvText2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
