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
    @Test public void allFourPlansAreReachableAndSelectionSurvivesRefresh() {
        assertIsolated();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                host.getNavController().navigate(R.id.paywallFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            java.util.List<com.example.spendtracker.billing.PremiumPlan> plans = java.util.Arrays.asList(
                    new com.example.spendtracker.billing.PremiumPlan("monthly", "Monthly", "₹99", "Every month", ""),
                    new com.example.spendtracker.billing.PremiumPlan("quarterly", "Quarterly", "₹149", "Every 3 months", ""),
                    new com.example.spendtracker.billing.PremiumPlan("six_monthly", "Six-monthly", "₹299", "Every 6 months", ""),
                    new com.example.spendtracker.billing.PremiumPlan("yearly", "Yearly", "₹499", "Every year", ""));
            scenario.onActivity(activity -> {
                androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                androidx.fragment.app.Fragment paywall = host.getChildFragmentManager().getPrimaryNavigationFragment();
                try {
                    java.lang.reflect.Field field = com.example.spendtracker.ui.premium.PaywallFragment.class.getDeclaredField("autoPay");
                    field.setAccessible(true);
                    AutoPayManager manager = (AutoPayManager) field.get(paywall);
                    ((androidx.lifecycle.MutableLiveData<java.util.List<com.example.spendtracker.billing.PremiumPlan>>)
                            manager.plans()).setValue(plans);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            onView(withText("Yearly")).perform(androidx.test.espresso.action.ViewActions.scrollTo())
                    .check(matches(isDisplayed())).perform(androidx.test.espresso.action.ViewActions.click());
            scenario.onActivity(activity -> {
                androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.premium_plans);
                com.example.spendtracker.ui.premium.PremiumPlanAdapter adapter =
                        (com.example.spendtracker.ui.premium.PremiumPlanAdapter) list.getAdapter();
                assertEquals(4, adapter.getItemCount());
                assertEquals("yearly", adapter.selected().productId);
                adapter.submit(plans);
                assertEquals("yearly", adapter.selected().productId);
            });
            onView(withText("Yearly")).check(matches(isDisplayed()));
            onView(withText("Quarterly")).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(isDisplayed()));
            onView(withText("Six-monthly")).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void analyticsOpensMaskedWithoutBiometricPrompt() {
        assertIsolated();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                host.getNavController().navigate(R.id.advancedAnalyticsFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.btnPrivacy)).check(matches(withText("Show values")));
            onView(withId(R.id.tvTotalIncome)).check(matches(withText("***")));
        }
    }

    @Test public void moneyFlowTitleTracksGranularityAndRecreation() {
        assertIsolated();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field security = MainActivity.class.getDeclaredField("securityRepository");
                    security.setAccessible(true);
                    ((com.example.spendtracker.domain.repository.SecurityRepository) security.get(activity))
                            .setPrivacyModeEnabled(false); // Only the empty, isolated test app.
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                host.getNavController().navigate(R.id.advancedAnalyticsFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.chipWeekly).performClick();
                assertEquals("Money in and out · Weekly", ((android.widget.TextView) activity.findViewById(R.id.tv_money_flow_title)).getText().toString());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Money in and out · Weekly", ((android.widget.TextView) activity.findViewById(R.id.tv_money_flow_title)).getText().toString());
                activity.findViewById(R.id.chipDaily).performClick();
                assertEquals("Money in and out · Daily", ((android.widget.TextView) activity.findViewById(R.id.tv_money_flow_title)).getText().toString());
                activity.findViewById(R.id.chipAnnual).performClick();
                assertEquals("Money in and out · Yearly", ((android.widget.TextView) activity.findViewById(R.id.tv_money_flow_title)).getText().toString());
            });
        }
    }
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
            onView(withText("Payment setup is not complete yet.")).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withText("Payment setup is not complete yet.")).check(matches(isDisplayed()));
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
