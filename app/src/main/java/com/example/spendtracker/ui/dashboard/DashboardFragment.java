package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
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
        setupSearch();
        setupViewPager();
        setupFab();
        setupNotificationBell();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.btnPrevDate.setOnClickListener(v -> viewModel.movePrev());
        binding.btnNextDate.setOnClickListener(v -> viewModel.moveNext());

        // Search icon toggles search bar visibility
        binding.btnSearch.setOnClickListener(v -> {
            boolean isVisible = binding.layoutSearch.getVisibility() == View.VISIBLE;
            binding.layoutSearch.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            if (!isVisible) {
                binding.etSearch.requestFocus();
            } else {
                binding.etSearch.setText("");
            }
        });
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
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
                case 4: tab.setText(getString(R.string.tab_transaction_group)); break;
            }
        }).attach();

        binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                // FAB is only relevant on the Daily tab; hide on all other tabs to prevent overlap
                binding.fabContainer.setVisibility(position == 0 ? View.VISIBLE : View.GONE);

                switch (position) {
                    case 0:
                        // If the user tapped a specific calendar day, do NOT override the
                        // single-day date filter with the full-month DAILY filter.
                        if (!viewModel.isCalendarDaySelected()) {
                            viewModel.setFilter(DashboardViewModel.FilterType.DAILY);
                        }
                        break;
                    case 1: viewModel.setFilter(DashboardViewModel.FilterType.CALENDAR); break;
                    case 2:
                    case 3: viewModel.setFilter(DashboardViewModel.FilterType.MONTHLY); break;
                    case 4: viewModel.setFilter(DashboardViewModel.FilterType.TRANSACTION_GROUP); break;
                }
                // Clear the single-day guard when user leaves the Daily tab
                if (position != 0) {
                    viewModel.clearCalendarDaySelected();
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
                    // Force immediate re-render of all navigation summary widgets so that
                    // actual (unmasked) values are displayed right after authentication.
                    refreshNavigationWidgets();
                }

                @Override
                public void onError(String error) {
                    android.widget.Toast.makeText(requireContext(), "Auth: " + error, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        });
    }

    /**
     * Sets up the notification bell icon that shows suspicious transaction count
     * and navigates to the full suspicious transactions list on click.
     */
    private void setupNotificationBell() {
        binding.btnNotifications.setOnClickListener(v -> {
            Navigation.findNavController(requireView())
                .navigate(R.id.action_dashboardFragment_to_suspiciousTransactionsFragment);
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

        // Combined observer: re-render whenever summary data OR privacy mode changes.
        // Using two separate observers sharing the same update logic prevents stale values.
        viewModel.getSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
                updateSummaryUI(summary, masked);
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            // Re-fetch summary from the LiveData to guarantee we always show the latest data.
            Summary summary = viewModel.getSummary().getValue();
            if (summary != null) {
                updateSummaryUI(summary, enabled);
            } else {
                // Summary not yet emitted – refresh the widgets once it arrives.
                viewModel.getSummary().observe(getViewLifecycleOwner(), s -> {
                    if (s != null) updateSummaryUI(s, Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()));
                });
            }
            // Also notify child-fragment adapters that data visibility changed.
            notifyChildAdapters();
        });

        viewModel.getSelectedTab().observe(getViewLifecycleOwner(), index -> {
            if (binding.viewPager.getCurrentItem() != index) {
                binding.viewPager.setCurrentItem(index, true);
            }
        });

        // ── Suspicious Transaction Notification Badge ────────────────
        viewModel.getSuspiciousTransactions().observe(getViewLifecycleOwner(), suspiciousList -> {
            if (suspiciousList != null && !suspiciousList.isEmpty()) {
                binding.tvNotificationBadge.setVisibility(View.VISIBLE);
                binding.tvNotificationBadge.setText(String.valueOf(Math.min(suspiciousList.size(), 99)));
            } else {
                binding.tvNotificationBadge.setVisibility(View.GONE);
            }
        });
    }

    private void updateSummaryUI(Summary summary, boolean masked) {
        binding.tvTotalIncome.setText(formatAmountWithState(summary.getTotalIncome(), masked));
        binding.tvTotalExpense.setText(formatAmountWithState(summary.getTotalExpense(), masked));
        binding.tvTotalTransfer.setText(formatAmountWithState(summary.getTotalTransfer(), masked));
        // Total = Income - Expense (net balance, excluding transfers)
        binding.tvAccountTotal.setText(formatAmountWithState(summary.getNetBalance(), masked));
    }

    private String formatAmountWithState(double amount, boolean masked) {
        if (masked) return "***";
        return String.format(Locale.getDefault(), "₹ %.0f", amount);
    }

    /**
     * Forces all three navigation summary widgets to display the latest actual values
     * immediately after a successful biometric authentication.
     */
    private void refreshNavigationWidgets() {
        if (binding == null) return;
        Summary summary = viewModel.getSummary().getValue();
        if (summary != null) {
            updateSummaryUI(summary, false);   // privacy is now disabled
        }
        notifyChildAdapters();
    }

    /**
     * Calls notifyDataSetChanged on every visible child fragment that holds a
     * RecyclerView adapter, so cached/masked values are immediately replaced.
     */
    private void notifyChildAdapters() {
        if (binding == null) return;
        // Iterate all child fragments managed by the ViewPager
        for (androidx.fragment.app.Fragment child : getChildFragmentManager().getFragments()) {
            if (child instanceof DailyTransactionsFragment) {
                ((DailyTransactionsFragment) child).refreshAdapter();
            } else if (child instanceof CalendarFragment) {
                ((CalendarFragment) child).refreshAdapter();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
