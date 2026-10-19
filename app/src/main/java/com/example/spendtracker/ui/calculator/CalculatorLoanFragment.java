package com.example.spendtracker.ui.calculator;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentCalculatorLoanBinding;

public class CalculatorLoanFragment extends Fragment {

    private FragmentCalculatorLoanBinding binding;
    private CalculatorViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalculatorLoanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(CalculatorViewModel.class);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerCalculation();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        binding.etPrincipal.addTextChangedListener(watcher);
        binding.etInterest.addTextChangedListener(watcher);
        binding.etTenureYears.addTextChangedListener(watcher);
        binding.etTenureMonths.addTextChangedListener(watcher);
        binding.etDownPayment.addTextChangedListener(watcher);
        binding.etProcessingFee.addTextChangedListener(watcher);

        binding.chipGroupLoanType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            // Optional: change some defaults based on loan type if desired.
            triggerCalculation();
        });

        viewModel.getLoanResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                binding.tvResultEmi.setText(CalculationEngine.formatIndianCurrency(result.emi));
                binding.tvResultLoanAmount.setText(CalculationEngine.formatIndianCurrency(result.effectivePrincipal));
                binding.tvResultInterest.setText(CalculationEngine.formatIndianCurrency(result.totalInterest));
                binding.tvResultFee.setText(CalculationEngine.formatIndianCurrency(result.processingFeeAmount));
                binding.tvResultTotal.setText(CalculationEngine.formatIndianCurrency(result.totalRepayment));
            }
        });
    }

    private void triggerCalculation() {
        String p = binding.etPrincipal.getText() != null ? binding.etPrincipal.getText().toString() : "";
        String r = binding.etInterest.getText() != null ? binding.etInterest.getText().toString() : "";
        String y = binding.etTenureYears.getText() != null ? binding.etTenureYears.getText().toString() : "";
        String m = binding.etTenureMonths.getText() != null ? binding.etTenureMonths.getText().toString() : "";
        String fee = binding.etProcessingFee.getText() != null ? binding.etProcessingFee.getText().toString() : "";
        String dp = binding.etDownPayment.getText() != null ? binding.etDownPayment.getText().toString() : "";
        viewModel.calculateLoan(p, r, y, m, fee, dp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
