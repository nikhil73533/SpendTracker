package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.spendtracker.databinding.FragmentDashboardMonthlyBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MonthlySummaryFragment extends Fragment {

    private FragmentDashboardMonthlyBinding binding;
    private DashboardViewModel viewModel;
    private MonthlySummaryAdapter monthlyAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardMonthlyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);

        setupRecyclerView();
        observeViewModel();
    }

    private void setupRecyclerView() {
        monthlyAdapter = new MonthlySummaryAdapter(amount -> viewModel.formatAmount(amount));
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(monthlyAdapter);
    }

    private void observeViewModel() {
        viewModel.getMonthlySummaries().observe(getViewLifecycleOwner(), summaries -> {
            monthlyAdapter.submitList(summaries);
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            monthlyAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
