package com.example.spendtracker.ui.settings;

import android.os.Bundle;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentBackupBinding;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.*;

@AndroidEntryPoint
public class BackupFragment extends Fragment {
    private FragmentBackupBinding binding;
    private BackupViewModel viewModel;
    private boolean updatingSwitch;
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) passwordDialog(true, password -> viewModel.configure(uri, password));
            });
    private final ActivityResultLauncher<String[]> restorePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) passwordDialog(false, password -> viewModel.restore(uri, password));
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentBackupBinding.inflate(inflater, container, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(BackupViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        viewModel.getLastBackupTime().observe(getViewLifecycleOwner(), time -> binding.tvLastBackupDate.setText(time == 0
                ? getString(R.string.label_never) : new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date(time))));
        viewModel.getIsAutoBackupEnabled().observe(getViewLifecycleOwner(), value -> {
            updatingSwitch = true; binding.switchAutoBackup.setChecked(value); updatingSwitch = false;
        });
        binding.switchAutoBackup.setOnCheckedChangeListener((button, enabled) -> {
            if (!updatingSwitch) viewModel.setAutoBackupEnabled(enabled);
        });
        binding.btnBackupNow.setOnClickListener(v -> {
            if (viewModel.hasFolder()) viewModel.backupNow(); else folderPicker.launch(null);
        });
        binding.btnChooseFolder.setOnClickListener(v -> folderPicker.launch(null));
        binding.btnRestore.setOnClickListener(v -> new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Restore saved records?")
                .setMessage("Choose a .pinch backup file and enter its password. Restoring replaces the current records and learning model with that saved copy. Save a backup of current records first if you need them.")
                .setPositiveButton("Choose backup", (dialog, which) -> restorePicker.launch(new String[]{"*/*"}))
                .setNegativeButton("Cancel", null).show());
        binding.btnConnectDrive.setOnClickListener(v -> {
            try { startActivity(new android.content.Intent("android.settings.BACKUP_SETTINGS")); }
            catch (android.content.ActivityNotFoundException error) { startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS)); }
        });
        viewModel.getBackupStatus().observe(getViewLifecycleOwner(), status -> binding.tvBackupMessage.setText(status));
        viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
            binding.btnBackupNow.setEnabled(!busy); binding.btnRestore.setEnabled(!busy);
            binding.btnChooseFolder.setEnabled(!busy); binding.switchAutoBackup.setEnabled(!busy);
        });
        viewModel.getRestoreReady().observe(getViewLifecycleOwner(), ready -> {
            if (ready) new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Backup checked")
                    .setMessage("The backup is ready. Close TrackMint now, then reopen it to finish restoring your records.")
                    .setCancelable(false).setPositiveButton("Close app", (dialog, which) -> {
                        requireActivity().finishAffinity();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }).show();
        });
    }
    private interface PasswordAction { void run(char[] password); }
    private void passwordDialog(boolean creating, PasswordAction action) {
        LinearLayout fields = new LinearLayout(requireContext()); fields.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density); fields.setPadding(pad, 0, pad, 0);
        TextView explanation = new TextView(requireContext());
        explanation.setText(creating ? "Use at least 10 characters and keep this password somewhere safe. It protects your phone backup and cannot be reset. A protected copy is saved in this installation to make scheduled backups." : "Enter the password used when this backup was created.");
        fields.addView(explanation);
        EditText password = new EditText(requireContext()); password.setHint("Backup password");
        password.setInputType(129); fields.addView(password);
        EditText confirmation = new EditText(requireContext()); confirmation.setHint("Repeat password"); confirmation.setInputType(129);
        if (creating) fields.addView(confirmation);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(creating ? "Protect phone backups" : "Unlock backup").setView(fields)
                .setPositiveButton(creating ? "Save backup" : "Restore", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            String text = password.getText().toString();
            if (creating && (text.length() < 10 || !text.contentEquals(confirmation.getText()))) {
                password.setError("Use at least 10 characters and matching passwords"); return;
            }
            char[] secret = text.toCharArray(); password.setText(""); confirmation.setText("");
            dialog.dismiss(); action.run(secret);
        }));
        dialog.show();
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
