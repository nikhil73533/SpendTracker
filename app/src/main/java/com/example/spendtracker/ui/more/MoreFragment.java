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
        
        binding.cardLedger.setOnClickListener(v -> 
            Navigation.findNavController(view).navigate(R.id.accountsFragment)
        );

        binding.cardAdvancedAnalytics.setOnClickListener(v -> 
            Navigation.findNavController(view).navigate(R.id.chartsFragment)
        );

        binding.cardConfiguration.setOnClickListener(v -> 
            Navigation.findNavController(view).navigate(R.id.action_moreFragment_to_trashFragment)
        );
        binding.cardMessageCaching.setOnClickListener(v -> showComingSoon("Message Caching"));
        binding.cardCalcbox.setOnClickListener(v -> 
            Navigation.findNavController(view).navigate(R.id.action_moreFragment_to_calculatorFragment)
        );
        binding.cardBackup.setOnClickListener(v -> 
            Navigation.findNavController(view).navigate(R.id.action_moreFragment_to_backupFragment)
        );
        binding.cardFeedback.setOnClickListener(v -> showComingSoon("Feedback"));
        binding.cardHelp.setOnClickListener(v -> showComingSoon("Help"));
        binding.cardRecommend.setOnClickListener(v -> showComingSoon("Recommend"));
        binding.cardBillAlerts.setOnClickListener(v -> 
            Navigation.findNavController(view).navigate(R.id.action_moreFragment_to_billAlertsFragment)
        );
        binding.cardBulkIngestion.setOnClickListener(v -> showComingSoon("Bulk Ingestion"));
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
