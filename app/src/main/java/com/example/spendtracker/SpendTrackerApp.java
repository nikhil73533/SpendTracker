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
    }
}
