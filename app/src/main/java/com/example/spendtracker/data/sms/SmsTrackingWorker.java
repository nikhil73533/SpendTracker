package com.example.spendtracker.data.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.*;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.android.EntryPointAccessors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.spendtracker.domain.model.Transaction;

/** Durable SMS processing; finishes only after the primary ledger has committed. */
public final class SmsTrackingWorker extends Worker {
    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies {
        SMSParsingService parser();
        AlertParsingService alerts();
        TransactionRepository transactions();
    }
    public SmsTrackingWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    public static void recoverInbox(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return;
        if (!SmsTrackingWindow.initialize(context.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE),
                System.currentTimeMillis())) return;
        WorkManager.getInstance(context).enqueueUniqueWork("sms-inbox-recovery", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(SmsTrackingWorker.class).build());
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        Dependencies deps = EntryPointAccessors.fromApplication(context, Dependencies.class);
        try {
            if (getInputData().getString("body") != null) {
                return process(deps, getInputData().getString("sender"), getInputData().getString("body"),
                        getInputData().getLong("timestamp", System.currentTimeMillis())) ? Result.success() : Result.retry();
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
                return Result.success();
            android.content.SharedPreferences prefs = context.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE);
            if (!SmsTrackingWindow.initialize(prefs, System.currentTimeMillis())) return Result.retry();
            long since = SmsTrackingWindow.lowerBound(prefs);
            try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{"address", "body", "date", "date_sent"}, "date >= ?",
                    new String[]{String.valueOf(since)}, "date ASC")) {
                if (cursor == null) return Result.retry();
                while (!isStopped() && cursor.moveToNext()) {
                    long timestamp = cursor.getLong(2);
                    if (!process(deps, cursor.getString(0), cursor.getString(1), cursor.getLong(3) > 0 ? cursor.getLong(3) : timestamp)) return Result.retry();
                    if (!prefs.edit().putLong("last_scan", timestamp).commit()) return Result.retry();
                }
            }
            return isStopped() ? Result.retry() : Result.success();
        } catch (Exception error) {
            android.util.Log.e("SmsTracking", "SMS processing will retry", error);
            return Result.retry();
        }
    }

    private boolean process(Dependencies deps, String sender, String body, long timestamp) throws InterruptedException {
        try { deps.alerts().processMessage(sender, body, timestamp); }
        catch (RuntimeException error) { android.util.Log.w("SmsTracking", "Bill detection unavailable", error); }
        com.example.spendtracker.data.sms.model.ParseResult result = deps.parser().parse(sender, body, timestamp);
        if (!result.isSuccess() || result.getTransaction() == null) return true;
        Transaction transaction = result.getTransaction();
        try {
            if (!"TRANSFER".equalsIgnoreCase(transaction.getType()) && (transaction.getCategory() == null
                    || "Other".equalsIgnoreCase(transaction.getCategory()) || "Uncategorized".equalsIgnoreCase(transaction.getCategory()))) {
                com.example.prediction.domain.model.IncrementalPredictionResult prediction =
                        com.example.spendtracker.util.CategoryPrediction.service(getApplicationContext()).predict(
                                com.example.spendtracker.util.CategoryPrediction.from(transaction));
                if (prediction != null && prediction.getCategory() != null) {
                    transaction.setPredictedCategory(prediction.getCategory());
                    if (!prediction.needsUserConfirmation()) transaction.setCategory(prediction.getCategory());
                    transaction.setConfidenceScore(prediction.getConfidence());
                }
            }
        } catch (RuntimeException error) { android.util.Log.w("SmsTracking", "Saving without category prediction", error); }
        CountDownLatch saved = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        deps.transactions().saveTransaction(transaction, false, error -> { failure.set(error); saved.countDown(); });
        return saved.await(30, TimeUnit.SECONDS) && failure.get() == null;
    }
}
