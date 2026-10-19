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
    private String checkoutReference;
    private String purchaseType;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        status.setText("Preparing secure checkout…");
        layout.addView(status);
        Button back = new Button(this); back.setText("Back to plans");
        back.setOnClickListener(v -> finish()); layout.addView(back);
        setContentView(layout);
        if (!manager.configured()) { status.setText("Payment setup is not complete yet."); return; }
        manager.message().observe(this, status::setText);
        Checkout.preload(getApplicationContext());
        if (savedInstanceState != null) {
            checkoutOpened = savedInstanceState.getBoolean("opened");
            checkoutReference = savedInstanceState.getString("checkout_reference");
            purchaseType = savedInstanceState.getString("purchase_type");
            status.setText("Return to plans and use Check status to recover your payment.");
            return; // Recreating an Activity must not reopen checkout.
        }
        manager.loadConfiguration((configuration, error) -> {
            if (error != null || isFinishing() || isDestroyed()) return;
            if (manager.usesTestIdentity()) {
                manager.prepareTestSession((session, sessionError) -> {
                    if (sessionError == null && !isFinishing() && !isDestroyed()) continueAction();
                });
            } else beginSignIn();
        });
    }

    private void beginSignIn() {
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
        continueAction();
    }
    private void continueAction() {
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
        final PremiumPlan selectedPlan = selected;
        manager.create(selectedPlan.productId, "one_time", (attempt, error) -> {
            if (error != null || isFinishing() || isDestroyed()) return;
            if (!attempt.optBoolean("checkout_allowed")) {
                manager.refreshAll((v, e) -> { if (e == null) finish(); });
                return;
            }
            if (!"INR".equals(attempt.optString("currency")) || attempt.optLong("amount") <= 0) {
                status.setText("Unable to confirm this payment price. Please contact support.");
                return;
            }
            openCheckout(attempt, selectedPlan);
        });
    }
    private void openCheckout(JSONObject attempt, PremiumPlan plan) {
        if (checkoutOpened || isFinishing() || isDestroyed()) return;
        try {
            purchaseType = attempt.getString("purchase_type");
            Checkout checkout = new Checkout();
            checkout.setKeyID(attempt.getString("key_id"));
            JSONObject options = new JSONObject().put("name", "TrackMint")
                    .put("description", "one_time".equals(purchaseType)
                            ? plan.title + " prepaid premium" : plan.title + " premium AutoPay")
                    .put("theme", new JSONObject().put("color", "#1B7A66"));
            if (!"one_time".equals(purchaseType)) throw new IllegalStateException("Expected a one-time order");
            checkoutReference = attempt.getString("order_id");
            PaymentCheckoutOptions.configure(options, checkoutReference,
                    attempt.getLong("amount"), attempt.getString("currency"));
            checkoutOpened = true;
            checkout.open(this, options);
        } catch (Exception failure) { manager.failure(failure); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("opened", checkoutOpened);
        out.putString("checkout_reference", checkoutReference);
        out.putString("purchase_type", purchaseType);
    }
    @Override public void onPaymentSuccess(String paymentId, PaymentData data) {
        try {
            JSONObject evidence = new JSONObject().put("razorpay_payment_id", paymentId)
                    .put("razorpay_signature", data.getSignature());
            evidence.put("razorpay_order_id", checkoutReference);
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
