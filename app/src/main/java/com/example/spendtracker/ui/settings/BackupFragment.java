package com.example.spendtracker.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentBackupBinding;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@AndroidEntryPoint
public class BackupFragment extends Fragment {

    private FragmentBackupBinding binding;
    private BackupViewModel viewModel;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    viewModel.handleSignInResult(result.getData());
                } else {
                    Toast.makeText(requireContext(), "Google Sign-In cancelled", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBackupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BackupViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        viewModel.getLastBackupTime().observe(getViewLifecycleOwner(), time -> {
            if (time == 0) {
                binding.tvLastBackupDate.setText(R.string.label_never);
            } else {
                binding.tvLastBackupDate.setText(sdf.format(new Date(time)));
            }
        });

        viewModel.getIsAutoBackupEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.switchAutoBackup.setChecked(enabled);
        });

        binding.switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setAutoBackupEnabled(isChecked);
            Toast.makeText(requireContext(), isChecked ? R.string.msg_auto_backup_enabled : R.string.msg_auto_backup_disabled, Toast.LENGTH_SHORT).show();
        });

        binding.btnBackupNow.setOnClickListener(v -> {
            viewModel.backupNow();
            Toast.makeText(requireContext(), R.string.msg_backup_success, Toast.LENGTH_SHORT).show();
        });

        binding.btnRestore.setOnClickListener(v -> {
            boolean success = viewModel.restoreNow();
            if (success) {
                Toast.makeText(requireContext(), R.string.msg_restore_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), getString(R.string.msg_restore_failed, "Backup file not found"), Toast.LENGTH_SHORT).show();
            }
        });

        // Google Drive Connect/Disconnect
        viewModel.getDriveAccountEmail().observe(getViewLifecycleOwner(), email -> {
            if (email != null && !email.isEmpty()) {
                binding.btnConnectDrive.setText(getString(R.string.label_disconnect_drive));
                binding.btnConnectDrive.setOnClickListener(v -> {
                    viewModel.disconnectDrive(requireContext());
                    Toast.makeText(requireContext(), "Google Drive disconnected", Toast.LENGTH_SHORT).show();
                });
            } else {
                binding.btnConnectDrive.setText(getString(R.string.label_connect_drive));
                binding.btnConnectDrive.setOnClickListener(v -> {
                    Intent signInIntent = viewModel.getGoogleSignInIntent(requireContext());
                    googleSignInLauncher.launch(signInIntent);
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
