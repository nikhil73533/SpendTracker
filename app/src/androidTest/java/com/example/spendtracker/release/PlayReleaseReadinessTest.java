package com.example.spendtracker.release;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.navigation.fragment.NavHostFragment;
import com.example.spendtracker.MainActivity;
import com.example.spendtracker.R;
import com.example.spendtracker.billing.*;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.di.MainDatabase;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.android.EntryPointAccessors;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayReleaseReadinessTest {
    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies {
        @MainDatabase SpendTrackerDatabase database();
        PremiumRepository premium();
    }
    @Test public void freeLaunchAndScreenshots() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Only run screenshot seeding on an explicitly disposable emulator.
        org.junit.Assume.assumeTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("Emulator") || android.os.Build.MODEL.contains("sdk_gphone"));
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("seedDemoData")));
        assertEquals("com.pinchtrack.app", context.getPackageName());
        assertTrue(com.example.spendtracker.BuildConfig.FREE_PLAY_BUILD);
        assertFalse(com.example.spendtracker.BuildConfig.AUTOPAY_ENABLED);
        context.getSharedPreferences("sms_disclosure", 0).edit().putBoolean("shown", true)
                .putBoolean("accepted", false).commit();
        Dependencies deps = EntryPointAccessors.fromApplication(context, Dependencies.class);
        assertTrue(deps.premium().hasAccess(PremiumFeature.ADVANCED_ANALYTICS));
        assertFalse(deps.premium().isStoreConfigured());
        SpendTrackerDatabase db = deps.database();
        try (android.database.Cursor rows = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM transactions")) {
            rows.moveToFirst(); assertEquals("Demo seeding requires an empty ledger", 0, rows.getInt(0));
        }
        String[] categories = {"Food", "Shopping", "Transport", "Entertainment", "Bills"};
        String[] descriptions = {"Lunch at the cafe", "Weekly groceries", "Metro commute", "Movie night", "Internet bill"};
        double[] amounts = {280, 1450, 90, 450, 799};
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < 20; i++) {
            TransactionEntity row = new TransactionEntity();
            row.amount = amounts[i % 5]; row.category = categories[i % 5]; row.description = descriptions[i % 5];
            row.type = "EXPENSE"; row.direction = "DEBIT"; row.bankName = "Sample Bank";
            row.receiverName = descriptions[i % 5]; row.source = "MANUAL"; row.sourceType = "MANUAL";
            Calendar date = (Calendar) today.clone(); date.add(Calendar.DAY_OF_MONTH, -(i / 3));
            row.date = date.getTimeInMillis(); db.transactionDao().insertTransaction(row);
        }
        TransactionEntity salary = new TransactionEntity(); salary.amount = 45000; salary.type = "INCOME";
        salary.direction = "CREDIT"; salary.category = "Salary"; salary.description = "Monthly salary";
        salary.sender = "Sample employer"; salary.date = today.getTimeInMillis(); salary.source = "MANUAL";
        db.transactionDao().insertTransaction(salary);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle(); capture(context, "01-dashboard.png");
            navigate(scenario, R.id.advancedAnalyticsFragment); settle(); capture(context, "02-analytics.png");
            navigate(scenario, R.id.paywallFragment); settle();
            scenario.onActivity(activity -> {
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.premium_continue).getVisibility());
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.premium_restore).getVisibility());
                assertTrue(activity.findViewById(R.id.premium_free).isEnabled());
                activity.findViewById(R.id.premium_free).performClick();
            });
            settle();
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                assertNotEquals(R.id.paywallFragment, host.getNavController().getCurrentDestination().getId());
            });
            navigate(scenario, R.id.moreFragment); settle(); capture(context, "03-tools.png");
            navigate(scenario, R.id.privacyOfflineFragment); settle(); capture(context, "04-privacy.png");
        }
    }
    private void navigate(ActivityScenario<MainActivity> scenario, int id) {
        scenario.onActivity(activity -> {
            NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            host.getNavController().navigate(id);
        });
    }
    private void settle() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(1500);
    }
    private void capture(Context context, String name) throws Exception {
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File directory = new File(context.getExternalFilesDir(null), "store-screenshots");
        directory.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(directory, name))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        screenshot.recycle();
    }
}
