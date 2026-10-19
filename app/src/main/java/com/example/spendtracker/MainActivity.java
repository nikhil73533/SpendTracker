package com.example.spendtracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.spendtracker.databinding.ActivityMainBinding;
import com.example.spendtracker.util.DataInitializer;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 100;
    private ActivityMainBinding binding;
    private long lastDashboardTap;

    @Inject
    DataInitializer dataInitializer;

    @Inject
    com.example.spendtracker.domain.repository.SecurityRepository securityRepository;

    @Inject
    com.example.spendtracker.billing.PremiumRepository premiumRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        // Checkpoint the encrypted database without exposing recovery details in logs.
        try {
            byte[] pass = securityRepository.getDatabasePassphrase();
            java.io.File dbFile = getDatabasePath("spend_tracker_db");
            if (dbFile.exists()) {
                net.zetetic.database.sqlcipher.SQLiteDatabase db = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), pass, null, null);
                db.rawExecSQL("PRAGMA wal_checkpoint(FULL);");
                db.close();
            }
        } catch (Exception ignored) { }

        dataInitializer.initializeData();
        setupNavigation();
        checkPermissions();

    }

    private void checkPermissions() {
        String[] permissions = android.os.Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS};
        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            android.content.SharedPreferences disclosure = getSharedPreferences("sms_disclosure", MODE_PRIVATE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                    && !disclosure.getBoolean("shown", false)) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.sms_disclosure_title)
                        .setMessage(R.string.sms_disclosure_message)
                        .setPositiveButton(R.string.sms_enable, (dialog, which) -> {
                            disclosure.edit().putBoolean("shown", true).putBoolean("accepted", true).apply();
                            checkPermissions();
                        })
                        .setNegativeButton(R.string.sms_manual, (dialog, which) ->
                                disclosure.edit().putBoolean("shown", true).putBoolean("accepted", false).apply())
                        .show();
                return;
            }
            if (disclosure.getBoolean("shown", false) && !disclosure.getBoolean("accepted", false)) return;
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                getSharedPreferences("bill_ui", MODE_PRIVATE).edit().putBoolean("permission_requested", true).apply();
            ActivityCompat.requestPermissions(this, permissions, SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        com.example.spendtracker.data.sms.BillReminderWorker.checkNow(this);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                com.example.spendtracker.data.sms.SmsTrackingWorker.recoverInbox(this);
            }
        }
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
            
            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                lastDashboardTap = item.getItemId() == R.id.dashboardFragment
                        ? android.os.SystemClock.uptimeMillis() : 0;
                navController.popBackStack(item.getItemId(), false);
                return NavigationUI.onNavDestinationSelected(item, navController);
            });
            binding.bottomNavigation.setOnItemReselectedListener(item -> {
                if (item.getItemId() != R.id.dashboardFragment) return;
                long now = android.os.SystemClock.uptimeMillis();
                if (lastDashboardTap != 0
                        && now - lastDashboardTap <= android.view.ViewConfiguration.getDoubleTapTimeout()) {
                    androidx.fragment.app.Fragment current = navHostFragment
                            .getChildFragmentManager().getPrimaryNavigationFragment();
                    if (current instanceof com.example.spendtracker.ui.dashboard.DashboardFragment) {
                        ((com.example.spendtracker.ui.dashboard.DashboardFragment) current).showDaily();
                    }
                    lastDashboardTap = 0;
                } else {
                    lastDashboardTap = now;
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        premiumRepository.refreshEntitlement();
        com.example.spendtracker.data.sms.SmsTrackingWorker.recoverInbox(this);
    }

}
