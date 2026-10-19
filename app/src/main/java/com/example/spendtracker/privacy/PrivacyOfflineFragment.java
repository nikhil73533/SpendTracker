package com.example.spendtracker.privacy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;

public class PrivacyOfflineFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                                   @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_privacy_offline, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((com.google.android.material.appbar.MaterialToolbar) view.findViewById(R.id.privacy_toolbar))
                .setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        if (com.example.spendtracker.BuildConfig.FREE_PLAY_BUILD) {
            View policy = view.findViewById(R.id.open_privacy_policy);
            policy.setVisibility(View.VISIBLE);
            policy.setOnClickListener(v -> {
                android.webkit.WebView content = new android.webkit.WebView(requireContext());
                content.getSettings().setJavaScriptEnabled(false);
                content.loadUrl("file:///android_asset/privacy-policy-play.html");
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Privacy Policy").setView(content)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> content.destroy()).show();
            });
        }
    }
}
