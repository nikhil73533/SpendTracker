package com.example.spendtracker.data.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.spendtracker.data.sms.model.ParseResult;
import com.example.spendtracker.data.sms.preprocessing.SmsMessageAssembler;
import com.example.spendtracker.domain.usecase.AddTransactionUseCase;
import com.example.spendtracker.domain.model.Transaction;
import com.example.prediction.domain.service.IncrementalPredictionService;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.domain.model.IncrementalPredictionResult;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Receives incoming SMS messages and delegates to {@link SMSParsingService}.
 *
 * <p>This class has minimal responsibility:
 * <ol>
 *   <li>Receive the SMS</li>
 *   <li>Extract sender and body</li>
 *   <li>Delegate to the parsing service</li>
 *   <li>Run category prediction for parsed transactions</li>
 *   <li>Save to repository</li>
 * </ol>
 *
 * <p>No parsing logic, bank-specific regexes, or extraction code belongs here.
 */
@AndroidEntryPoint
public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Inject
    SMSParsingService parsingService;

    @Inject
    AlertParsingService alertParsingService;

    @Inject
    AddTransactionUseCase addTransactionUseCase;

    private IncrementalPredictionService predictionService;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (predictionService == null) {
            predictionService = com.example.spendtracker.util.CategoryPrediction.service(context);
        }
        if (intent != null && "android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            final PendingResult pendingResult = goAsync();
            executor.execute(() -> {
                try {
                    processSms(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing SMS", e);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }

    private void processSms(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        String format = bundle.getString("format");
        java.util.List<SmsMessageAssembler.Part> parts = new java.util.ArrayList<>();
        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (smsMessage != null) parts.add(new SmsMessageAssembler.Part(
                    smsMessage.getDisplayOriginatingAddress(), smsMessage.getMessageBody(), smsMessage.getTimestampMillis()));
        }
        for (SmsMessageAssembler.Part message : SmsMessageAssembler.assemble(parts)) {
            String sender = message.sender;
            String messageBody = message.body;
            long timestamp = message.timestamp;

            // Proactive alert system for repeating messages
            try {
                alertParsingService.processMessage(sender, messageBody, timestamp);
            } catch (Exception e) {
                android.util.Log.e("SMSReceiver", "Bill detection failed; continuing transaction parsing", e);
            }

            // Parse through the modular pipeline
            ParseResult result = parsingService.parse(sender, messageBody, timestamp);

            Log.d(TAG, "Parse result: " + result.getStatus()
                    + " (confidence=" + String.format("%.2f", result.getConfidence()) + ")");

            if (!result.isSuccess() || result.getTransaction() == null) {
                if (result.getStatus() != null) {
                    Log.d(TAG, "SMS not stored: " + result.getStatus());
                }
                continue;
            }

            Transaction originalTransaction = result.getTransaction();
            Log.d(TAG, "Transaction detected: " + originalTransaction.getAmount()
                    + " " + originalTransaction.getType()
                    + " bank=" + result.getDetectedBank());

            // TRANSFER type — category is always "Transfer", no ML needed
            if ("TRANSFER".equalsIgnoreCase(originalTransaction.getType())) {
                addTransactionUseCase.execute(originalTransaction);
                continue;
            }

            // Prediction pipeline for INCOME / EXPENSE
            Transaction transactionToSave = originalTransaction;

            boolean isCategorized = originalTransaction.getCategory() != null
                && !originalTransaction.getCategory().isBlank()
                && !originalTransaction.getCategory().equalsIgnoreCase("Other")
                && !originalTransaction.getCategory().equalsIgnoreCase("Uncategorized");

            if (!isCategorized) {
                PredictionTransaction pt = com.example.spendtracker.util.CategoryPrediction.from(originalTransaction);

                IncrementalPredictionResult predResult = predictionService.predict(pt);
                if (predResult != null && predResult.getCategory() != null) {
                    // Keep reference/direction/time provenance when assigning a category.
                    if (!predResult.needsUserConfirmation()) {
                        transactionToSave.setCategory(predResult.getCategory());
                    }
                    // Store confidence score for suspicious transaction detection
                    transactionToSave.setConfidenceScore(predResult.getConfidence());
                    Log.d(TAG, "ML categorized as: " + predResult.getCategory()
                        + " (conf=" + predResult.getConfidence()
                        + ", needsConfirm=" + predResult.needsUserConfirmation() + ")");
                }
            }

            addTransactionUseCase.execute(transactionToSave);
        }
    }
}
