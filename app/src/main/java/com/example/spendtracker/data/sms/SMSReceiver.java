package com.example.spendtracker.data.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.spendtracker.data.sms.model.ParseResult;
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
    AddTransactionUseCase addTransactionUseCase;

    private IncrementalPredictionService predictionService;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (predictionService == null) {
            predictionService = new IncrementalPredictionService(context);
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
        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            String sender = smsMessage.getDisplayOriginatingAddress();
            String messageBody = smsMessage.getMessageBody();
            long timestamp = smsMessage.getTimestampMillis();

            // Parse through the modular pipeline
            ParseResult result = parsingService.parse(sender, messageBody, timestamp);

            Log.d(TAG, "Parse result: " + result.getStatus()
                    + " (confidence=" + String.format("%.2f", result.getConfidence()) + ")");

            if (!result.isSuccess() || result.getTransaction() == null) {
                if (result.getStatus() != null) {
                    Log.d(TAG, "SMS not stored: " + result.getStatus());
                }
                return;
            }

            Transaction originalTransaction = result.getTransaction();
            Log.d(TAG, "Transaction detected: " + originalTransaction.getAmount()
                    + " " + originalTransaction.getType()
                    + " bank=" + result.getDetectedBank());

            // TRANSFER type — category is always "Transfer", no ML needed
            if ("TRANSFER".equalsIgnoreCase(originalTransaction.getType())) {
                addTransactionUseCase.execute(originalTransaction);
                return;
            }

            // Prediction pipeline for INCOME / EXPENSE
            Transaction transactionToSave = originalTransaction;

            boolean isCategorized = originalTransaction.getCategory() != null
                && !originalTransaction.getCategory().isBlank()
                && !originalTransaction.getCategory().equalsIgnoreCase("Other")
                && !originalTransaction.getCategory().equalsIgnoreCase("Uncategorized");

            if (!isCategorized) {
                PredictionTransaction pt = new PredictionTransaction(
                    originalTransaction.getReceiverName(),
                    originalTransaction.getUpiId(),
                    originalTransaction.getAmount(),
                    originalTransaction.getType(),
                    originalTransaction.getDate()
                );

                IncrementalPredictionResult predResult = predictionService.predict(pt);
                if (predResult != null && predResult.getCategory() != null) {
                    transactionToSave = new Transaction(
                        originalTransaction.getId(),
                        originalTransaction.getAmount(),
                        predResult.getCategory(),
                        originalTransaction.getDescription(),
                        originalTransaction.getType(),
                        originalTransaction.getDate(),
                        originalTransaction.getSource(),
                        originalTransaction.getSender(),
                        originalTransaction.getUpiId(),
                        originalTransaction.getReceiverName(),
                        originalTransaction.getBankName(),
                        originalTransaction.getSourceType()
                    );
                    Log.d(TAG, "ML categorized as: " + predResult.getCategory()
                        + " (conf=" + predResult.getConfidence()
                        + ", needsConfirm=" + predResult.needsUserConfirmation() + ")");
                }
            }

            addTransactionUseCase.execute(transactionToSave);
        }
    }
}
