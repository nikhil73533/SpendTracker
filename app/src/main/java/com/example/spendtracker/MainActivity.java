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

    private static MainActivity instance;
    private static final int SMS_PERMISSION_CODE = 100;
    private ActivityMainBinding binding;

    @Inject
    DataInitializer dataInitializer;

    @Inject
    com.example.spendtracker.domain.repository.SecurityRepository securityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // EMERGENCY CHECKPOINT
        try {
            byte[] pass = securityRepository.getDatabasePassphrase();
            java.io.File dbFile = getDatabasePath("spend_tracker_db");
            if (dbFile.exists()) {
                android.util.Log.e("RECOVERY_CORE", "Attempting Startup Checkpoint...");
                // pass is raw byte[] from securityRepository.getDatabasePassphrase()
                // Use the absolute path string to match the byte[] overload
                net.sqlcipher.database.SQLiteDatabase db = net.sqlcipher.database.SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), pass, null);
                db.rawExecSQL("PRAGMA wal_checkpoint(FULL);");
                db.close();
                android.util.Log.e("RECOVERY_CORE", "Startup Checkpoint Complete.");
            }
        } catch (Exception e) {
            android.util.Log.e("RECOVERY_CORE", "Startup Checkpoint Error: " + e.getMessage());
        }

        // EMERGENCY DIAGNOSTICS
        try {
            java.io.File cacheDir = getCacheDir();
            java.io.File backupZip = new java.io.File(cacheDir, "backup.zip");
            if (backupZip.exists()) {
                android.util.Log.e("RECOVERY_CORE", "BACKUP ZIP FOUND: " + backupZip.length() + " bytes");
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(backupZip))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        android.util.Log.e("RECOVERY_CORE", "ZIP ENTRY: " + entry.getName() + " Size: " + entry.getSize());
                    }
                }
            }
        } catch (Exception e) {}

        dataInitializer.initializeData();
        setupNavigation();
        checkPermissions();

        com.example.prediction.util.PredictionLogger.setCallback(MainActivity::logModeling);
    }

    private void checkPermissions() {
        String[] permissions = {Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS};
        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
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
                navController.popBackStack(item.getItemId(), false);
                return NavigationUI.onNavDestinationSelected(item, navController);
            });
        }
    }

    public static void logModeling(String message) {
        if (instance != null) {
            instance.runOnUiThread(() -> {
                if (instance.binding != null && instance.binding.auditStrip != null) {
                    android.widget.TextView tv = (android.widget.TextView) instance.binding.auditStrip.getChildAt(0);
                    tv.setText(message);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy() ;
        if (instance == this) instance = null;
    }
}
