package com.example.spendtracker.data.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import androidx.work.*;
import com.example.spendtracker.data.sms.preprocessing.SmsMessageAssembler;

/** Persists incoming messages as work before releasing the broadcast. */
public class SMSReceiver extends BroadcastReceiver {
    private static final java.util.concurrent.Executor EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        SmsTrackingWindow.initialize(app.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE),
                System.currentTimeMillis());
        EXECUTOR.execute(() -> {
            try {
                java.util.List<SmsMessageAssembler.Part> parts = new java.util.ArrayList<>();
                for (SmsMessage sms : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    if (sms != null) parts.add(new SmsMessageAssembler.Part(sms.getDisplayOriginatingAddress(),
                            sms.getMessageBody(), sms.getTimestampMillis()));
                }
                for (SmsMessageAssembler.Part part : SmsMessageAssembler.assemble(parts)) {
                    Data data = new Data.Builder().putString("sender", part.sender).putString("body", part.body)
                            .putLong("timestamp", part.timestamp).build();
                    WorkManager.getInstance(app).enqueue(new OneTimeWorkRequest.Builder(SmsTrackingWorker.class)
                            .setInputData(data).build()).getResult().get(8, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception error) {
                android.util.Log.e("SMSReceiver", "Could not queue SMS; scheduling inbox recovery", error);
                SmsTrackingWorker.recoverInbox(app);
            } finally { pending.finish(); }
        });
    }
}
