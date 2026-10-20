package com.example.spendtracker;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

@HiltAndroidApp
public class SpendTrackerApp extends Application {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        try {
            com.example.spendtracker.util.ReinstallRecovery.installPending(base);
        } catch (java.io.IOException error) {
            // Preserve the previous database and staged recovery; never silently start empty.
            throw new IllegalStateException("Recovery could not finish; your backup has been preserved", error);
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        System.loadLibrary("sqlcipher");
        com.example.spendtracker.util.BackupArchive.restorePredictionIfPending(this);
        com.example.spendtracker.ui.settings.BackupWorker.ensureScheduled(this);
        com.example.spendtracker.util.AppNotifications.createChannels(this);
        com.example.spendtracker.util.AppNotifications.removeUpiChannel(this);
        com.example.spendtracker.data.sms.BillReminderWorker.ensureScheduled(this);
        com.example.spendtracker.data.sms.SmsTrackingWorker.recoverInbox(this);
        com.example.spendtracker.util.UpiLimitWorker.cancel(this);
    }
}
