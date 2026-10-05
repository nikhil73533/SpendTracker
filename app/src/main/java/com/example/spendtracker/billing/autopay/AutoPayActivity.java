package com.example.spendtracker.billing.autopay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.spendtracker.billing.PremiumPlan;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.razorpay.Checkout;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.json.JSONObject;

/** Owns SDK callbacks independently of fragment navigation. Never grants access. */
@AndroidEntryPoint
public class AutoPayActivity extends AppCompatActivity implements PaymentResultWithDataListener {
    @Inject AutoPayManager manager;
    private boolean checkoutOpened;
    private String subscription;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        status.setText("Preparing secure AutoPay…");
        layout.addView(status);
        Button back = new Button(this); back.setText("Back to plans");
        back.setOnClickListener(v -> finish()); layout.addView(back);
        setContentView(layout);
        if (!manager.configured()) { status.setText("AutoPay setup is not complete yet."); return; }
        manager.message().observe(this, status::setText);
        Checkout.preload(getApplicationContext());
        if (savedInstanceState != null) {
            checkoutOpened = savedInstanceState.getBoolean("opened");
            subscription = savedInstanceState.getString("subscription");
            status.setText("Return to plans and use Check status to recover your subscription.");
            return; // Recreating an Activity must not reopen checkout.
        }
        GoogleSignIn.getClient(this, manager.signInOptions()).silentSignIn().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (task.isSuccessful()) authenticated(task.getResult());
            else startActivityForResult(GoogleSignIn.getClient(this, manager.signInOptions()).getSignInIntent(), 4101);
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 4101) {
            try { authenticated(GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class)); }
            catch (Exception error) { status.setText("Sign-in was not completed. Return to plans to try again."); }
        }
    }
    private void authenticated(GoogleSignInAccount account) {
        try { manager.signedIn(account); }
        catch (Exception error) { manager.failure(error); return; }
        manager.refreshAll((result, error) -> {
            if (error != null || isFinishing() || isDestroyed()) return;
            String action = getIntent().getStringExtra("action");
            if ("buy".equals(action)) confirmPurchase();
            else if ("cancel".equals(action)) new MaterialAlertDialogBuilder(this)
                    .setTitle("Stop automatic renewal?")
                    .setMessage("Your mandate will be cancelled. Any unrefunded paid access remains available until its expiry.")
                    .setNegativeButton("Keep renewal", (d, w) -> finish())
                    .setPositiveButton("Cancel renewal", (d, w) -> manager.cancel((v, e) -> { if (e == null) finish(); })).show();
            else finish();
        });
    }
    private void confirmPurchase() {
        PremiumPlan selected = null;
        if (manager.plans().getValue() != null) for (PremiumPlan plan : manager.plans().getValue()) {
            if (plan.productId.equals(getIntent().getStringExtra("plan"))) selected = plan;
        }
        if (selected == null) { status.setText("Plan unavailable. Return to plans and reload prices."); return; }
        final PremiumPlan plan = selected;
        new MaterialAlertDialogBuilder(this).setTitle("Approve " + plan.title + " AutoPay")
                .setMessage(plan.price + " — " + plan.period
                        + "\n\nNo free trial. Access starts after the first full payment is confirmed. You can stop future debits using Cancel renewal. Any refund revokes access for that payment's period."
                        + "\n\nReview Terms, Privacy and Support on the paywall before continuing.")
                .setNegativeButton("Back", (d, w) -> finish())
                .setPositiveButton("Continue to Razorpay", (d, w) -> manager.create(plan.productId, (attempt, error) -> {
                    if (error != null || isFinishing() || isDestroyed()) return;
                    if (!attempt.optBoolean("checkout_allowed")) { manager.refreshAll((v, e) -> { if (e == null) finish(); }); return; }
                    java.text.NumberFormat money = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("en", "IN"));
                    String reservedPrice = money.format(attempt.optLong("amount") / 100.0);
                    if (!"INR".equals(attempt.optString("currency")) || attempt.optLong("amount") <= 0) {
                        status.setText("Unable to confirm this subscription's price. Please contact support."); return;
                    }
                    if (!reservedPrice.equals(plan.price)) {
                        new MaterialAlertDialogBuilder(this).setTitle("Resume earlier subscription?")
                                .setMessage("Your saved " + plan.title + " subscription renews at " + reservedPrice
                                        + ". This differs from the current catalog price. Continue only if you want this saved subscription.")
                                .setNegativeButton("Back", (dialog, which) -> finish())
                                .setPositiveButton("Continue at " + reservedPrice, (dialog, which) -> openCheckout(attempt, plan)).show();
                    } else openCheckout(attempt, plan);
                })).show();
    }
    private void openCheckout(JSONObject attempt, PremiumPlan plan) {
        if (checkoutOpened || isFinishing() || isDestroyed()) return;
        try {
            subscription = attempt.getString("subscription_id");
            Checkout checkout = new Checkout();
            checkout.setKeyID(attempt.getString("key_id"));
            JSONObject options = new JSONObject().put("subscription_id", subscription)
                    .put("name", "TrackMint").put("description", plan.title + " premium subscription")
                    .put("theme", new JSONObject().put("color", "#1B7A66"));
            checkoutOpened = true;
            checkout.open(this, options);
        } catch (Exception failure) { manager.failure(failure); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("opened", checkoutOpened); out.putString("subscription", subscription);
    }
    @Override public void onPaymentSuccess(String paymentId, PaymentData data) {
        try {
            // SDK's subscription ID is compared against the server-stored ID by the backend.
            JSONObject evidence = new JSONObject().put("razorpay_payment_id", paymentId)
                    .put("razorpay_subscription_id", subscription).put("razorpay_signature", data.getSignature());
            manager.verify(evidence, (value, error) -> { if (error == null) finish(); });
        } catch (Exception error) { manager.failure(error); }
    }
    @Override public void onPaymentError(int code, String response, PaymentData data) {
        // Closing checkout does not prove that a debit failed. Reconcile the saved attempt.
        manager.refreshAll((value, error) -> {
            if (!isFinishing() && !isDestroyed()) status.setText("Checkout closed. " + manager.message().getValue());
        });
    }
}
