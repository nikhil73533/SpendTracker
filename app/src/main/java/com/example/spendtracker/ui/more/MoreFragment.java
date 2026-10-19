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
import com.example.spendtracker.feedback.FeedbackManager;
import com.example.spendtracker.sharing.ShareManager;
import com.example.spendtracker.util.OnlineIntentLauncher;
import com.example.spendtracker.util.PlayStoreManager;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class MoreFragment extends Fragment {

    private FragmentMoreBinding binding;

    @Inject
    com.example.spendtracker.ui.premium.FeatureGate featureGate;

    @Inject
    com.example.spendtracker.domain.repository.SecurityRepository securityRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        refreshProfile();
        binding.cardProfile.setOnClickListener(v -> com.example.spendtracker.ui.settings.ProfileEditor.show(requireContext(), this::refreshProfile));
        binding.cardLedger.setOnClickListener(v -> safeNavigate(view, R.id.accountsFragment));
        // These cards are navigation affordances.  Gating them here made the cards appear
        // unresponsive to free users, even though each destination can present its own
        // upgrade state where an action actually needs premium access.
        binding.cardAdvancedAnalytics.setOnClickListener(v ->
                safeNavigate(view, R.id.action_moreFragment_to_advancedAnalyticsFragment));
        binding.cardConfiguration.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_trashFragment));

        binding.cardCalcbox.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_calculatorFragment));
        binding.cardBackup.setOnClickListener(v ->
                safeNavigate(view, R.id.action_moreFragment_to_backupFragment));
        binding.cardFeedback.setOnClickListener(v -> new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Feedback")
                .setItems(new String[]{"Send feedback", "Report a problem", "Suggest a feature", "Rate the app"}, (dialog, which) -> {
                    if (which == 3) binding.cardRate.performClick();
                    else openFeedback(which == 0 ? FeedbackManager.Type.FEEDBACK : which == 1 ? FeedbackManager.Type.PROBLEM : FeedbackManager.Type.FEATURE);
                }).show());
        binding.cardReportProblem.setVisibility(View.GONE);
        binding.cardSuggestFeature.setVisibility(View.GONE);
        binding.cardOnlineHelp.setVisibility(View.GONE);
        binding.cardRate.setVisibility(View.GONE);
        binding.cardHelp.setOnClickListener(v -> new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Help").setItems(new String[]{"Help and FAQs", "Online help"}, (dialog, which) -> {
                    if (which == 0) safeNavigate(view, R.id.action_moreFragment_to_helpFragment);
                    else openOnlineHelp();
                }).show());
        binding.cardRecommend.setOnClickListener(v -> shareApp());
        binding.cardBillAlerts.setOnClickListener(v ->
                safeNavigate(view, R.id.action_moreFragment_to_billAlertsFragment));

        binding.cardBulkIngestion.setOnClickListener(v ->
                safeNavigate(view, R.id.action_moreFragment_to_pdfIngestionFragment));
        binding.cardPrivacyOffline.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_privacyOfflineFragment));
        binding.cardReportProblem.setOnClickListener(v -> openFeedback(FeedbackManager.Type.PROBLEM));
        binding.cardSuggestFeature.setOnClickListener(v -> openFeedback(FeedbackManager.Type.FEATURE));
        binding.cardOnlineHelp.setOnClickListener(v -> openOnlineHelp());
        binding.cardRate.setOnClickListener(v -> {
            if (!com.example.spendtracker.util.ConnectivityHelper.isOnline(requireContext())) {
                android.widget.Toast.makeText(requireContext(), R.string.internet_required, android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            PlayStoreManager.requestReview(requireActivity(),
                    () -> android.widget.Toast.makeText(requireContext(), R.string.store_unavailable, android.widget.Toast.LENGTH_LONG).show());
        });
        binding.cardPremium.setOnClickListener(v -> safeNavigate(view, R.id.action_moreFragment_to_paywallFragment));
    }

    private void refreshProfile() {
        if (binding == null) return;
        com.example.spendtracker.util.UserProfile profile = com.example.spendtracker.util.UserProfile.load(requireContext());
        binding.profileInitials.setText(profile.initials());
        binding.profileSummary.setText(profile.name.isEmpty() ? "Add your name, email and phone" :
                profile.name + (profile.email.isEmpty() ? "" : "\n" + profile.email) + (profile.phone.isEmpty() ? "" : "\n" + profile.phone));
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

    private void openFeedback(FeedbackManager.Type type) {
        showOnlineResult(FeedbackManager.open(requireContext(), type));
    }

    private void openOnlineHelp() {
        showOnlineResult(OnlineIntentLauncher.open(requireContext(), getString(R.string.help_center_url)));
    }

    private void shareApp() {
        if (!ShareManager.shareApp(requireContext())) {
            android.widget.Toast.makeText(requireContext(), R.string.share_unavailable, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void requireFeature(com.example.spendtracker.billing.PremiumFeature feature, Runnable allowed) {
        featureGate.require(feature, allowed,
                () -> safeNavigate(requireView(), R.id.action_moreFragment_to_paywallFragment));
    }

    private void showOnlineResult(OnlineIntentLauncher.Result result) {
        if (result == OnlineIntentLauncher.Result.OPENED) return;
        int message = result == OnlineIntentLauncher.Result.OFFLINE ? R.string.internet_required
                : result == OnlineIntentLauncher.Result.NO_HANDLER ? R.string.browser_unavailable
                : R.string.online_service_unavailable;
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
