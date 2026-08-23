package com.example.spendtracker.ui.calculator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.databinding.FragmentCalculatorNormalBinding;

public class CalculatorNormalFragment extends Fragment {

    private FragmentCalculatorNormalBinding binding;
    private CalculatorViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalculatorNormalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(CalculatorViewModel.class);

        setupButtons();

        viewModel.getExpression().observe(getViewLifecycleOwner(), expr -> {
            binding.tvExpression.setText(expr);
        });

        viewModel.getNormalResult().observe(getViewLifecycleOwner(), res -> {
            binding.tvResult.setText(res);
        });
    }

    private void setupButtons() {
        View.OnClickListener listener = v -> {
            if (v instanceof com.google.android.material.button.MaterialButton) {
                String text = ((com.google.android.material.button.MaterialButton) v).getText().toString();
                viewModel.appendExpression(text);
            }
        };

        binding.btn0.setOnClickListener(listener);
        binding.btn1.setOnClickListener(listener);
        binding.btn2.setOnClickListener(listener);
        binding.btn3.setOnClickListener(listener);
        binding.btn4.setOnClickListener(listener);
        binding.btn5.setOnClickListener(listener);
        binding.btn6.setOnClickListener(listener);
        binding.btn7.setOnClickListener(listener);
        binding.btn8.setOnClickListener(listener);
        binding.btn9.setOnClickListener(listener);
        
        binding.btnPlus.setOnClickListener(listener);
        binding.btnMinus.setOnClickListener(listener);
        binding.btnMultiply.setOnClickListener(listener);
        binding.btnDivide.setOnClickListener(listener);
        binding.btnPercent.setOnClickListener(listener);
        binding.btnDot.setOnClickListener(listener);
        binding.btnParenOpen.setOnClickListener(listener);
        binding.btnParenClose.setOnClickListener(listener);

        binding.btnClear.setOnClickListener(v -> viewModel.clearExpression());
        
        binding.btnEquals.setOnClickListener(v -> viewModel.evaluateExpression());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
