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
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentDashboardBinding;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.google.android.material.tabs.TabLayoutMediator;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@AndroidEntryPoint
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private TransactionViewModel transactionViewModel;

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
        setupViewPager();
        setupFab();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.btnPrevDate.setOnClickListener(v -> viewModel.movePrev());
        binding.btnNextDate.setOnClickListener(v -> viewModel.moveNext());
        
        binding.btnFavorite.setOnClickListener(v -> android.widget.Toast.makeText(requireContext(), "Added to favorites", android.widget.Toast.LENGTH_SHORT).show());
        binding.btnSearch.setOnClickListener(v -> android.widget.Toast.makeText(requireContext(), "Search clicked", android.widget.Toast.LENGTH_SHORT).show());
        binding.btnFilter.setOnClickListener(v -> android.widget.Toast.makeText(requireContext(), "Filter clicked", android.widget.Toast.LENGTH_SHORT).show());
    }

    private void setupViewPager() {
        DashboardPagerAdapter adapter = new DashboardPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Daily"); break;
                case 1: tab.setText("Calendar"); break;
                case 2: tab.setText("Monthly"); break;
                case 3: tab.setText("Total"); break;
                case 4: tab.setText("Notes"); break;
            }
        }).attach();

        binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0: viewModel.setFilter(DashboardViewModel.FilterType.DAILY); break;
                    case 1: viewModel.setFilter(DashboardViewModel.FilterType.CALENDAR); break;
                    case 2:
                    case 3: viewModel.setFilter(DashboardViewModel.FilterType.MONTHLY); break;
                    case 4: viewModel.setFilter(DashboardViewModel.FilterType.NOTE); break;
                }
            }
        });
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

        // Long press for privacy mode
        binding.fabAddTransaction.setOnLongClickListener(v -> {
            com.example.spendtracker.util.BiometricHelper.authenticate(requireActivity(), new com.example.spendtracker.util.BiometricHelper.BiometricCallback() {
                @Override
                public void onSuccess() {
                    viewModel.setPrivacyModeEnabled(false);
                }

                @Override
                public void onError(String error) {
                    android.widget.Toast.makeText(requireContext(), "Auth: " + error, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        });
    }

    private void observeViewModel() {
        viewModel.getDateRange().observe(getViewLifecycleOwner(), range -> {
            if (range.start == 0) {
                binding.tvDashboardHeader.setText("All Time");
            } else {
                binding.tvDashboardHeader.setText(range.label);
            }
        });

        viewModel.getSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                updateSummaryUI(summary, Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()));
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            Summary summary = viewModel.getSummary().getValue();
            if (summary != null) updateSummaryUI(summary, enabled);
        });

        viewModel.getSelectedTab().observe(getViewLifecycleOwner(), index -> {
            if (binding.viewPager.getCurrentItem() != index) {
                binding.viewPager.setCurrentItem(index, true);
            }
        });
    }

    private void updateSummaryUI(Summary summary, boolean masked) {
        binding.tvTotalIncome.setText(formatAmountWithState(summary.getTotalIncome(), masked));
        binding.tvTotalExpense.setText(formatAmountWithState(summary.getTotalExpense(), masked));
        binding.tvAccountTotal.setText(formatAmountWithState(summary.getTotalAccountTransaction(), masked));
    }

    private String formatAmountWithState(double amount, boolean masked) {
        if (masked) return "***";
        return String.format(Locale.getDefault(), "₹ %.0f", amount);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
