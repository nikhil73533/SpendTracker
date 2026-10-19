package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.spendtracker.databinding.FragmentDashboardCalendarBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CalendarFragment extends Fragment {

    private FragmentDashboardCalendarBinding binding;
    private DashboardViewModel viewModel;
    private CalendarAdapter calendarAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);
        calendarAdapter = new CalendarAdapter(day -> {
            viewModel.setCalendarFilter(day.timestamp, new java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date(day.timestamp)));
            viewModel.selectTab(0);
        }, amount -> viewModel.formatAmount(amount));
        binding.rvCalendar.setHasFixedSize(true);
        binding.rvCalendar.setItemAnimator(null);
        binding.rvCalendar.setAdapter(calendarAdapter);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getCalendarDays().observe(getViewLifecycleOwner(), days -> {
            if (days != null) {
                // Keep the adapter and its recycled cells while the month changes.
                // Replacing the adapter forced a complete layout during each slide.
                calendarAdapter.submit(days);
            }
        });
        
        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (binding.rvCalendar.getAdapter() != null) {
                binding.rvCalendar.getAdapter().notifyDataSetChanged();
            }
        });
    }

    /** Called by {@link DashboardFragment} after biometric auth to force re-render with actual values. */
    public void refreshAdapter() {
        if (binding != null && binding.rvCalendar.getAdapter() != null) {
            binding.rvCalendar.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
