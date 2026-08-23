package com.example.spendtracker.ui.calculator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentCalculatorBinding;
import com.google.android.material.tabs.TabLayoutMediator;

public class CalculatorFragment extends Fragment {

    private FragmentCalculatorBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCalculatorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.toolbar.setNavigationOnClickListener(v -> 
            Navigation.findNavController(view).navigateUp()
        );

        binding.viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new CalculatorNormalFragment();
                    case 1: return new CalculatorEmiFragment();
                    case 2: return new CalculatorLoanFragment();
                    default: return new CalculatorNormalFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(getString(R.string.tab_normal)); break;
                case 1: tab.setText(getString(R.string.tab_emi)); break;
                case 2: tab.setText(getString(R.string.tab_loan)); break;
            }
        }).attach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
