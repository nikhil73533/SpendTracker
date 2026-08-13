package com.example.spendtracker.data.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.spendtracker.domain.usecase.AddTransactionUseCase;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import com.example.prediction.domain.service.PredictionService;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.domain.service.KNNPredictor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Inject
    SMSParser smsParser;

    @Inject
    AddTransactionUseCase addTransactionUseCase;

    @Inject
    RegexPatternDao regexPatternDao;

    private PredictionService predictionService;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (predictionService == null) {
            predictionService = new PredictionService(context);
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

            Log.d(TAG, "SMS Received from: " + sender + ", Body: " + messageBody);

            List<RegexPatternEntity> patterns = regexPatternDao.getAllPatternsSync();
            Transaction originalTransaction = smsParser.parseSMS(sender, messageBody, patterns);
            
            if (originalTransaction != null) {
                Log.d(TAG, "Transaction detected: " + originalTransaction.getAmount());
                
                Transaction transactionToSave = originalTransaction;

                // Prediction Integration
                if ("Other".equals(originalTransaction.getCategory()) || "Uncategorized".equals(originalTransaction.getCategory())) {
                    PredictionTransaction pt = new PredictionTransaction(
                        originalTransaction.getReceiverName(),
                        originalTransaction.getUpiId(),
                        originalTransaction.getAmount(),
                        originalTransaction.getType(),
                        originalTransaction.getDate()
                    );
                    
                    KNNPredictor.PredictionResult result = predictionService.predict(pt);
                    if (result != null && result.getConfidence() > 0.5) {
                        transactionToSave = new Transaction(
                            originalTransaction.getId(),
                            originalTransaction.getAmount(),
                            result.getCategory(),
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
                    }
                }
                
                addTransactionUseCase.execute(transactionToSave);
            }
        }
    }
}
