package com.example.spendtracker.billing.autopay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import com.example.spendtracker.BuildConfig;
import com.example.spendtracker.billing.BillingManager;
import com.example.spendtracker.billing.PremiumPlan;
import com.example.spendtracker.billing.PremiumState;
import com.example.spendtracker.billing.PremiumPurchaseType;
import com.example.spendtracker.billing.PurchaseEvent;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import dagger.hilt.android.qualifiers.ApplicationContext;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AutoPayManager {
    public interface Completion { void complete(JSONObject value, Exception error); }
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String token, account, version;
    private AccessLease lease;
    private boolean refreshing;
    private int operations;
    private final MutableLiveData<List<PremiumPlan>> plans = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<BillingManager.State> state = new MutableLiveData<>(BillingManager.State.IDLE);
    private final MutableLiveData<PremiumState> premium = new MutableLiveData<>(PremiumState.free());
    private final MutableLiveData<PurchaseEvent> events = new MutableLiveData<>(PurchaseEvent.NONE);
    private final MutableLiveData<String> message = new MutableLiveData<>("Sign in to view AutoPay plans and restore purchases.");
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> cancellable = new MutableLiveData<>(false);
    private JSONObject currentSubscription;
    private JSONObject catalog;

    @Inject public AutoPayManager(@ApplicationContext Context context) {
        this.context = context;
        SharedPreferences secure = null;
        if (BuildConfig.AUTOPAY_ENABLED) {
            try {
                secure = EncryptedSharedPreferences.create("autopay_billing", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            } catch (Exception ignored) { }
        }
        prefs = secure;
    }

    public boolean configured() { return BuildConfig.AUTOPAY_ENABLED && prefs != null
            && BuildConfig.BILLING_BASE_URL.startsWith("https://") && !BuildConfig.BILLING_GOOGLE_CLIENT_ID.isEmpty(); }
    public GoogleSignInOptions signInOptions() {
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(BuildConfig.BILLING_GOOGLE_CLIENT_ID).requestEmail().build();
    }
    public LiveData<List<PremiumPlan>> plans() { return plans; }
    public LiveData<BillingManager.State> state() { return state; }
    public LiveData<PremiumState> premium() { return premium; }
    public LiveData<PurchaseEvent> events() { return events; }
    public LiveData<String> message() { return message; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<Boolean> cancellable() { return cancellable; }
    public JSONObject catalog() { return catalog; }
    public boolean hasAccess() {
        GoogleSignInAccount signedIn = GoogleSignIn.getLastSignedInAccount(context);
        String current = signedIn == null ? null : "google:" + signedIn.getId();
        return lease != null && lease.allows(current, System.currentTimeMillis(), SystemClock.elapsedRealtime());
    }
    public void launch(Activity activity, String action, String plan) {
        if (!configured()) { message.setValue("AutoPay is not configured yet."); return; }
        Intent intent = new Intent(activity, AutoPayActivity.class).putExtra("action", action).putExtra("plan", plan);
        activity.startActivity(intent);
    }
    public void refresh() {
        if (!configured()) { state.setValue(BillingManager.State.CONFIGURATION_MISSING); return; }
        if (refreshing || Boolean.TRUE.equals(busy.getValue())) return;
        refreshing = true;
        GoogleSignIn.getClient(context, signInOptions()).silentSignIn().addOnCompleteListener(task -> {
            refreshing = false;
            if (task.isSuccessful() && task.getResult() != null) {
                try { signedIn(task.getResult()); refreshAll(null); }
                catch (Exception error) { failure(error); }
            } else {
                token = null; lease = null; account = null;
                premium.setValue(PremiumState.free());
                GoogleSignInAccount cached = GoogleSignIn.getLastSignedInAccount(context);
                if (cached != null && cached.getId() != null) loadCachedLease("google:" + cached.getId());
                plans.setValue(Collections.emptyList());
                state.setValue(BillingManager.State.READY);
                message.setValue("Sign in to view plans or restore your AutoPay subscription.");
            }
        });
    }
    public void signedIn(GoogleSignInAccount user) throws Exception {
        if (user.getId() == null || user.getIdToken() == null) throw new IllegalStateException("Google sign-in did not return a billing token");
        String next = "google:" + user.getId();
        if (operations > 0 && account != null && !next.equals(account))
            throw new IllegalStateException("Wait for the current billing operation before switching accounts");
        if (!next.equals(account)) {
            lease = null;
            premium.setValue(PremiumState.free());
            currentSubscription = null;
            if (!next.equals(prefs.getString("account", "")) && !prefs.edit().clear().putString("account", next).commit()) {
                throw new IllegalStateException("Cannot save billing recovery details");
            }
            loadCachedLease(next);
        }
        account = next; token = user.getIdToken();
    }
    private void loadCachedLease(String next) {
        if (!next.equals(prefs.getString("account", ""))) return;
        int boot = android.provider.Settings.Global.getInt(context.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1);
        if (boot >= 0 && boot == prefs.getInt("boot", -2)) {
            lease = new AccessLease(next, prefs.getLong("received", 0), prefs.getLong("elapsed", 0),
                    prefs.getLong("server", 0), prefs.getLong("lease", 0), prefs.getLong("expires", 0));
            if (lease.allows(next, System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
                premium.setValue(new PremiumState(PremiumState.Status.ACTIVE, "razorpay", PremiumPurchaseType.SUBSCRIPTION,
                        "", lease.receivedAt, lease.receivedAt + lease.duration));
            }
        }
    }
    private interface Work { JSONObject run() throws Exception; }
    private void run(Work work, Completion completion) {
        operations++;
        busy.setValue(true);
        executor.execute(() -> {
            JSONObject result = null; Exception failure = null;
            try { result = work.run(); } catch (Exception error) { failure = error; }
            JSONObject value = result; Exception error = failure;
            main.post(() -> {
                operations--;
                busy.setValue(operations > 0);
                if (error != null) failure(error);
                if (completion != null) completion.complete(value, error);
            });
        });
    }
    private BillingApi api() { return new BillingApi(BuildConfig.BILLING_BASE_URL); }
    public void refreshAll(Completion completion) {
        state.setValue(BillingManager.State.CONNECTING);
        run(() -> new JSONObject().put("catalog", api().request("GET", "/catalog", token, null, null))
                .put("access", api().request("GET", "/me/entitlements", token, null, null)), (result, error) -> {
            if (error == null) {
                try {
                    catalog = result.getJSONObject("catalog"); version = catalog.getString("version");
                    List<PremiumPlan> choices = new ArrayList<>();
                    JSONArray items = catalog.getJSONArray("plans");
                    NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String period = item.getString("period").equals("yearly") ? "every year" :
                                item.getInt("interval") == 1 ? "every month" : "every " + item.getInt("interval") + " months";
                        choices.add(new PremiumPlan(item.getString("id"), item.getString("title"),
                                currency.format(item.getLong("amount") / 100.0),
                                "Auto-renews " + period + ". Up to " + item.getInt("total_count") + " payments; cancel anytime.", ""));
                    }
                    acceptAccess(result.getJSONObject("access"));
                    plans.setValue(catalog.optBoolean("checkout_enabled") ? choices : Collections.emptyList());
                    state.setValue(BillingManager.State.READY);
                } catch (Exception parseError) { failure(parseError); error = parseError; }
            }
            if (completion != null) completion.complete(result, error);
        });
    }
    private void acceptAccess(JSONObject access) throws Exception {
        if (!access.getString("account_id").equals(account)) throw new IllegalStateException("Billing account mismatch");
        long now = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        long server = access.getLong("server_time") * 1000L, until = access.getLong("lease_until") * 1000L;
        long expires = access.getLong("expires_at") * 1000L;
        lease = access.getBoolean("active") ? new AccessLease(account, now, elapsed, server, until, expires) : null;
        prefs.edit().putString("account", account).putLong("received", now).putLong("elapsed", elapsed)
                .putLong("server", server).putLong("lease", lease == null ? 0 : until).putLong("expires", expires)
                .putInt("boot", android.provider.Settings.Global.getInt(context.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1)).commit();
        premium.setValue(new PremiumState(lease == null ? PremiumState.Status.FREE : PremiumState.Status.ACTIVE,
                "razorpay", PremiumPurchaseType.SUBSCRIPTION, "", now, now + (lease == null ? 0 : lease.duration)));
        JSONArray subscriptions = access.getJSONArray("subscriptions");
        currentSubscription = subscriptions.length() > 0 ? subscriptions.getJSONObject(0) : null;
        String status = currentSubscription == null ? "" : currentSubscription.optString("status");
        boolean terminal = isTerminal(status);
        cancellable.setValue(currentSubscription != null && !terminal);
        if (currentSubscription != null && terminal) prefs.edit().remove("request_key").remove("plan").remove("attempt").commit();
        if (lease != null) message.setValue("Premium active until " + java.text.DateFormat.getDateInstance().format(new java.util.Date(expires))
                + (terminal ? ". Renewal stopped." : ". AutoPay status: " + status + "."));
        else if (currentSubscription != null && !terminal) message.setValue("Subscription status: " + status + ". Access starts after a confirmed payment. Use Check status to recover.");
        else message.setValue("Choose a plan. Automatic renewal begins only after you approve the mandate.");
    }
    private static boolean isTerminal(String state) {
        return state.equals("cancelled") || state.equals("completed") || state.equals("expired") || state.equals("rejected");
    }
    public void create(String plan, Completion completion) {
        if (currentSubscription != null && !isTerminal(currentSubscription.optString("status"))) {
            if (!plan.equals(currentSubscription.optString("plan_id"))) {
                completion.complete(null, new BillingApi.ApiException(409, "Resume your existing plan or cancel its renewal first."));
                message.setValue("Resume your existing plan or cancel its renewal first.");
                return;
            }
            if (!prefs.edit().putString("attempt", currentSubscription.optString("id")).commit()) {
                completion.complete(null, new IllegalStateException("Cannot save recovery details")); return;
            }
            completion.complete(currentSubscription, null); return;
        }
        message.setValue("Preparing your subscription…");
        run(() -> {
            String key = prefs.getString("request_key", "");
            String storedPlan = prefs.getString("plan", plan);
            if (!key.isEmpty() && !storedPlan.equals(plan))
                throw new IllegalStateException("Check the status of your earlier subscription first");
            if (key.isEmpty()) {
                key = UUID.randomUUID().toString(); storedPlan = plan;
                if (!prefs.edit().putString("request_key", key).putString("plan", plan).commit())
                    throw new IllegalStateException("Cannot save checkout recovery details");
            }
            JSONObject attempt = api().request("POST", "/subscription-attempts", token,
                    new JSONObject().put("plan_id", storedPlan).put("catalog_version", version), key);
            if (!prefs.edit().putString("attempt", attempt.getString("id")).commit())
                throw new IllegalStateException("Cannot save checkout recovery details");
            return attempt;
        }, (result, error) -> {
            // Definitive validation/configuration errors did not reserve a provider attempt.
            if (error instanceof BillingApi.ApiException && (((BillingApi.ApiException) error).status == 400
                    || ((BillingApi.ApiException) error).status == 409)) {
                prefs.edit().remove("request_key").remove("plan").commit();
            }
            completion.complete(result, error);
        });
    }
    public void verify(JSONObject evidence, Completion completion) {
        message.setValue("Checking payment with Razorpay…");
        String id = prefs.getString("attempt", "");
        run(() -> api().request("POST", "/subscription-attempts/" + id + "/verify", token, evidence, null), (result, error) -> {
            if (error == null) {
                try { acceptAccess(result); } catch (Exception parseError) { failure(parseError); error = parseError; }
            }
            if (completion != null) completion.complete(result, error);
        });
    }
    public void cancel(Completion completion) {
        if (currentSubscription == null) { completion.complete(null, new IllegalStateException("No subscription to cancel")); return; }
        String id = currentSubscription.optString("id");
        run(() -> api().request("POST", "/subscription-attempts/" + id + "/cancel", token, new JSONObject(), null), (value, error) -> {
            if (error == null) refreshAll(completion);
            else if (completion != null) completion.complete(null, error);
        });
    }
    public void failure(Exception error) {
        state.setValue(BillingManager.State.UNAVAILABLE);
        plans.setValue(Collections.emptyList());
        if (error instanceof BillingApi.ApiException && ((BillingApi.ApiException) error).status == 401) {
            lease = null; token = null; premium.setValue(PremiumState.free());
            prefs.edit().remove("lease").commit();
        }
        message.setValue(error instanceof BillingApi.ApiException ? error.getMessage()
                : "Unable to confirm billing. Use Check status; your pending checkout is saved.");
    }
}
