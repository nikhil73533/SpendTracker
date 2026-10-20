package com.example.spendtracker.data.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import com.example.spendtracker.data.sms.model.ParseResult;
import com.example.spendtracker.data.sms.model.ParseStatus;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import dagger.hilt.android.EntryPointAccessors;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SmsTrackingWorkerTest {
    private static final String BODY = "INR 500 debited from A/c XX1234 at ACME.";

    @Test public void readPermissionAloneSchedulesImmediateAndPeriodicRecovery() {
        try (Fixture f = new Fixture(true);
             MockedStatic<WorkManager> managers = mockStatic(WorkManager.class)) {
            WorkManager manager = mock(WorkManager.class);
            managers.when(() -> WorkManager.getInstance(f.context)).thenReturn(manager);
            android.content.SharedPreferences prefs = mock(android.content.SharedPreferences.class);
            when(f.context.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE)).thenReturn(prefs);
            when(prefs.contains("tracking_started_at")).thenReturn(true);
            SmsTrackingWorker.recoverInbox(f.context);
            verify(manager).enqueueUniquePeriodicWork(eq("sms-periodic-recovery"),
                    eq(ExistingPeriodicWorkPolicy.KEEP), any(PeriodicWorkRequest.class));
            verify(manager).enqueueUniqueWork(eq("sms-inbox-recovery"),
                    eq(ExistingWorkPolicy.KEEP), any(OneTimeWorkRequest.class));
        }
    }

    @Test public void deniedReadPermissionDoesNotScheduleRecovery() {
        try (Fixture f = new Fixture(false);
             MockedStatic<WorkManager> managers = mockStatic(WorkManager.class)) {
            SmsTrackingWorker.recoverInbox(f.context);
            managers.verifyNoInteractions();
        }
    }

    @Test public void revokedPermissionDoesNotProcessQueuedMessages() {
        try (Fixture f = new Fixture(false)) {
            assertEquals(ListenableWorker.Result.success(), f.worker.doWork());
            verifyNoInteractions(f.parser, f.repository);
        }
    }

    @Test public void nonTransactionsNeverReachLedger() {
        try (Fixture f = new Fixture(true)) {
            when(f.parser.parse("BANK", BODY, 123L)).thenReturn(
                    ParseResult.builder().status(ParseStatus.NOT_TRANSACTION).build());
            assertEquals(ListenableWorker.Result.success(), f.worker.doWork());
            verifyNoInteractions(f.repository);
        }
    }

    @Test public void successfulCommitCompletesWorkEvenIfBillDetectionFails() {
        try (Fixture f = new Fixture(true)) {
            f.transaction();
            doThrow(new IllegalStateException("Bill unavailable"))
                    .when(f.alerts).processMessage("BANK", BODY, 123L);
            doAnswer(call -> {
                ((TransactionRepository.SaveCallback) call.getArgument(2)).onComplete(null);
                return null;
            }).when(f.repository).saveTransaction(any(), eq(false), any());
            assertEquals(ListenableWorker.Result.success(), f.worker.doWork());
            verify(f.repository).saveTransaction(any(), eq(false), any());
        }
    }

    @Test public void failedCommitRetriesInsteadOfLosingMessage() {
        try (Fixture f = new Fixture(true)) {
            f.transaction();
            doAnswer(call -> {
                ((TransactionRepository.SaveCallback) call.getArgument(2)).onComplete("Database unavailable");
                return null;
            }).when(f.repository).saveTransaction(any(), eq(false), any());
            assertEquals(ListenableWorker.Result.retry(), f.worker.doWork());
        }
    }

    @Test public void parserFailureIsRetried() {
        try (Fixture f = new Fixture(true)) {
            when(f.parser.parse("BANK", BODY, 123L)).thenThrow(new IllegalStateException("Unavailable"));
            assertEquals(ListenableWorker.Result.retry(), f.worker.doWork());
            verifyNoInteractions(f.repository);
        }
    }

    @Test public void inboxCursorAdvancesOnlyAfterTransactionCommit() {
        try (Fixture f = new Fixture(true)) {
            android.content.SharedPreferences.Editor editor = f.inbox();
            f.transaction();
            doAnswer(call -> {
                verify(editor, never()).putLong(eq("last_scan"), anyLong());
                ((TransactionRepository.SaveCallback) call.getArgument(2)).onComplete(null);
                return null;
            }).when(f.repository).saveTransaction(any(), eq(false), any());
            assertEquals(ListenableWorker.Result.success(), f.worker.doWork());
            verify(editor).putLong("last_scan", 200000L);
            verify(editor).commit();
        }
    }

    @Test public void failedInboxSaveKeepsRecoveryPositionForRetry() {
        try (Fixture f = new Fixture(true)) {
            android.content.SharedPreferences.Editor editor = f.inbox();
            f.transaction();
            doAnswer(call -> {
                ((TransactionRepository.SaveCallback) call.getArgument(2)).onComplete("Save failed");
                return null;
            }).when(f.repository).saveTransaction(any(), eq(false), any());
            assertEquals(ListenableWorker.Result.retry(), f.worker.doWork());
            verifyNoInteractions(editor);
        }
    }

    @Test public void failedMessageDoesNotBlockLaterInboxTransactions() {
        try (Fixture f = new Fixture(true)) {
            android.content.SharedPreferences.Editor editor = f.inbox();
            when(f.cursor.moveToNext()).thenReturn(true, true, false);
            f.transaction();
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(call -> {
                ((TransactionRepository.SaveCallback) call.getArgument(2))
                        .onComplete(calls.getAndIncrement() == 0 ? "Save failed" : null);
                return null;
            }).when(f.repository).saveTransaction(any(), eq(false), any());
            assertEquals(ListenableWorker.Result.retry(), f.worker.doWork());
            verify(f.repository, times(2)).saveTransaction(any(), eq(false), any());
            verifyNoInteractions(editor);
            verify(f.cursor).close();
        }
    }

    private static class Fixture implements AutoCloseable {
        final Context context = mock(Context.class);
        final SMSParsingService parser = mock(SMSParsingService.class);
        final AlertParsingService alerts = mock(AlertParsingService.class);
        final TransactionRepository repository = mock(TransactionRepository.class);
        final MockedStatic<ContextCompat> permissions = mockStatic(ContextCompat.class);
        final MockedStatic<EntryPointAccessors> entryPoints = mockStatic(EntryPointAccessors.class);
        final WorkerParameters parameters = mock(WorkerParameters.class);
        final android.database.Cursor cursor = mock(android.database.Cursor.class);
        final SmsTrackingWorker worker;
        Fixture(boolean granted) {
            permissions.when(() -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS))
                    .thenReturn(granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
            SmsTrackingWorker.Dependencies deps = mock(SmsTrackingWorker.Dependencies.class);
            when(deps.parser()).thenReturn(parser);
            when(deps.alerts()).thenReturn(alerts);
            when(deps.transactions()).thenReturn(repository);
            entryPoints.when(() -> EntryPointAccessors.fromApplication(context, SmsTrackingWorker.Dependencies.class))
                    .thenReturn(deps);
            when(parameters.getInputData()).thenReturn(new Data.Builder().putString("sender", "BANK")
                    .putString("body", BODY).putLong("timestamp", 123L).build());
            worker = new SmsTrackingWorker(context, parameters);
        }
        android.content.SharedPreferences.Editor inbox() {
            when(parameters.getInputData()).thenReturn(Data.EMPTY);
            android.content.SharedPreferences prefs = mock(android.content.SharedPreferences.class);
            android.content.SharedPreferences.Editor editor = mock(android.content.SharedPreferences.Editor.class);
            when(context.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE)).thenReturn(prefs);
            when(prefs.contains("tracking_started_at")).thenReturn(true);
            when(prefs.getLong("tracking_started_at", Long.MAX_VALUE)).thenReturn(100000L);
            when(prefs.getLong("last_scan", 100000L)).thenReturn(100000L);
            when(prefs.edit()).thenReturn(editor);
            when(editor.putLong(anyString(), anyLong())).thenReturn(editor);
            when(editor.commit()).thenReturn(true);
            android.content.ContentResolver resolver = mock(android.content.ContentResolver.class);
            when(context.getContentResolver()).thenReturn(resolver);
            when(resolver.query(any(), any(), eq("date >= ?"),
                    org.mockito.AdditionalMatchers.aryEq(new String[]{"100000"}), eq("date ASC"))).thenReturn(cursor);
            when(cursor.moveToNext()).thenReturn(true, false);
            when(cursor.getString(0)).thenReturn("BANK");
            when(cursor.getString(1)).thenReturn(BODY);
            when(cursor.getLong(2)).thenReturn(200000L);
            when(cursor.getLong(3)).thenReturn(123L);
            return editor;
        }
        void transaction() {
            Transaction transaction = mock(Transaction.class);
            when(transaction.getCategory()).thenReturn("Food");
            when(transaction.getType()).thenReturn("EXPENSE");
            when(parser.parse("BANK", BODY, 123L)).thenReturn(ParseResult.builder()
                    .status(ParseStatus.SUCCESS).transaction(transaction).build());
        }
        @Override public void close() {
            entryPoints.close();
            permissions.close();
        }
    }
}
