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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            
            // Fix for stale navigation state when re-selecting charts
            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.chartsFragment) {
                    navController.popBackStack(R.id.chartsFragment, false);
                }
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
