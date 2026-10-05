package com.example.spendtracker.billing.autopay;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.spendtracker.BuildConfig;
import com.example.spendtracker.MainActivity;
import com.example.spendtracker.R;
import com.example.spendtracker.billing.PremiumFeature;
import com.example.spendtracker.billing.PremiumRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class AutoPayDeviceTest {
    private void assertIsolated() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.junit.Assume.assumeTrue(BuildConfig.AUTOPAY_ENABLED);
        assertTrue("Tests must use isolated package", context.getPackageName().contains("verification"));
        assertFalse(new AutoPayManager(context).configured());
        for (String permission : new String[]{"android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.POST_NOTIFICATIONS"}) {
            try {
                android.os.ParcelFileDescriptor command = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommand("pm grant " + context.getPackageName() + " " + permission);
                try (java.io.InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)) {
                    while (stream.read() != -1) { }
                }
            } catch (Exception ignored) { }
        }
    }
    @Test public void missingConfigurationCannotUnlockFeatures() {
        assertIsolated();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("premiumRepository");
                    field.setAccessible(true);
                    PremiumRepository repository = (PremiumRepository) field.get(activity);
                    assertTrue("Unconfigured direct build must not bypass feature gates", repository.isStoreConfigured());
                    assertFalse(repository.hasAccess(PremiumFeature.PDF_IMPORT));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        }
    }
    @Test public void checkoutWithoutCredentialsShowsSetupMessageAndSurvivesRecreation() {
        assertIsolated();
        try (ActivityScenario<AutoPayActivity> scenario = ActivityScenario.launch(AutoPayActivity.class)) {
            onView(withText("AutoPay setup is not complete yet.")).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withText("AutoPay setup is not complete yet.")).check(matches(isDisplayed()));
        }
    }
    @Test public void paywallWithoutCredentialsDisablesPurchaseAndRestore() {
        assertIsolated();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                host.getNavController().navigate(R.id.paywallFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.premium_continue).isEnabled());
                assertFalse(activity.findViewById(R.id.premium_restore).isEnabled());
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.autopay_cancel).getVisibility());
            });
        }
    }
}
