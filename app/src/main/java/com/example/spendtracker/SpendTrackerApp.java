package com.example.spendtracker;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp;
import net.sqlcipher.database.SQLiteDatabase;

@HiltAndroidApp
public class SpendTrackerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SQLiteDatabase.loadLibs(this);
        com.example.spendtracker.util.BackupArchive.restorePredictionIfPending(this);
        com.example.spendtracker.util.AppNotifications.createChannels(this);
        com.example.spendtracker.util.AppNotifications.removeUpiChannel(this);
        com.example.spendtracker.data.sms.BillReminderWorker.ensureScheduled(this);
        com.example.spendtracker.util.UpiLimitWorker.cancel(this);
    }
}
