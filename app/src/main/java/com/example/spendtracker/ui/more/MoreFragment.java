package com.example.spendtracker.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentMoreBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MoreFragment extends Fragment {

    private FragmentMoreBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.cardLedger.setOnClickListener(v -> safeNavigate(view, R.id.accountsFragment));
        binding.cardAdvancedAnalytics.setOnClickListener(v -> safeNavigate(view, R.id.chartsFragment));
        binding.cardConfiguration.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_trashFragment));
        binding.cardMessageCaching.setOnClickListener(v -> showComingSoon("Message Caching"));
        binding.cardCalcbox.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_calculatorFragment));
        binding.cardBackup.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_backupFragment));
        binding.cardFeedback.setOnClickListener(v -> showComingSoon("Feedback"));
        binding.cardHelp.setOnClickListener(v -> showComingSoon("Help"));
        binding.cardRecommend.setOnClickListener(v -> showComingSoon("Recommend"));
        binding.cardBillAlerts.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_billAlertsFragment));
        binding.cardRepeatedAlerts.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_repeatedAlertsFragment));
        binding.cardBulkIngestion.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_pdfIngestionFragment));
    }

    private void safeNavigate(View view, int actionId) {
        try {
            androidx.navigation.NavController navController = Navigation.findNavController(view);
            if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() == R.id.moreFragment) {
                navController.navigate(actionId);
            }
        } catch (IllegalArgumentException e) {
            // Ignore fast double-click crashes
        }
    }

    private void showComingSoon(String feature) {
        android.widget.Toast.makeText(requireContext(), getString(R.string.coming_soon, feature), android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
