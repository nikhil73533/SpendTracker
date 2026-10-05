package com.example.spendtracker.data.local.database;

import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Transactional materialized summaries. No timer, UI refresh, or network is needed. */
public final class AnalyticsMaterialization {
    private AnalyticsMaterialization() { }
    public static final RoomDatabase.Callback CALLBACK = new RoomDatabase.Callback() {
        @Override public void onCreate(SupportSQLiteDatabase db) { install(db); }
        @Override public void onOpen(SupportSQLiteDatabase db) {
            // REPLACE must fire the delete trigger for the displaced transaction too.
            db.execSQL("PRAGMA recursive_triggers = ON");
        }
    };
    public static String type(String row) {
        return "CASE WHEN " + row + "type = 'TRANSFER' OR LOWER(" + row + "category) LIKE '%transfer%' THEN 'TRANSFER' "
                + "WHEN " + row + "category IS NULL THEN 'UNCLASSIFIED' ELSE " + row + "type END";
    }
    public static void install(SupportSQLiteDatabase db) {
        for (String dimension : new String[]{"type", "category", "merchant"}) {
            String table = "analytics_daily_" + dimension;
            db.execSQL("CREATE TABLE IF NOT EXISTS " + table + " (day INTEGER NOT NULL, type TEXT NOT NULL, bucket TEXT NOT NULL, total REAL NOT NULL, transactionCount INTEGER NOT NULL, PRIMARY KEY(day,type,bucket))");
            String bucket = bucket(dimension, "");
            db.execSQL("DELETE FROM " + table);
            db.execSQL("INSERT INTO " + table + " SELECT (date - ((date % 86400000 + 86400000) % 86400000)), COALESCE(" + type("") + ",'UNCLASSIFIED'), " + bucket
                    + ", SUM(amount), COUNT(*) FROM transactions WHERE status = 'ACTIVE' GROUP BY 1,2,3");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS " + table + "_insert AFTER INSERT ON transactions BEGIN " + add(table, dimension, "NEW.") + " END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS " + table + "_delete AFTER DELETE ON transactions BEGIN " + remove(table, dimension, "OLD.") + " END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS " + table + "_update AFTER UPDATE OF date, amount, type, category, receiverName, status ON transactions WHEN OLD.date IS NOT NEW.date OR OLD.amount IS NOT NEW.amount OR OLD.type IS NOT NEW.type OR OLD.category IS NOT NEW.category OR OLD.receiverName IS NOT NEW.receiverName OR OLD.status IS NOT NEW.status BEGIN "
                    + remove(table, dimension, "OLD.") + add(table, dimension, "NEW.") + " END");
        }
    }
    private static String bucket(String dimension, String row) {
        return "type".equals(dimension) ? "''" : "COALESCE(" + row + ("category".equals(dimension) ? "category" : "receiverName") + ", '')";
    }
    private static String day(String row) { return "(" + row + "date - ((" + row + "date % 86400000 + 86400000) % 86400000))"; }
    private static String key(String dimension, String row) {
        return "day = " + day(row) + " AND type = COALESCE(" + type(row) + ",'UNCLASSIFIED') AND bucket = " + bucket(dimension, row);
    }
    private static String add(String table, String dimension, String row) {
        return "INSERT INTO " + table + " SELECT " + day(row) + ", COALESCE(" + type(row) + ",'UNCLASSIFIED'), "
                + bucket(dimension, row) + ", 0, 0 WHERE " + row + "status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM " + table + " WHERE " + key(dimension, row) + "); "
                + "UPDATE " + table + " SET total = total + " + row + "amount, transactionCount = transactionCount + 1 WHERE "
                + row + "status = 'ACTIVE' AND " + key(dimension, row) + "; ";
    }
    private static String remove(String table, String dimension, String row) {
        return "UPDATE " + table + " SET total = total - " + row + "amount, transactionCount = transactionCount - 1 WHERE "
                + row + "status = 'ACTIVE' AND " + key(dimension, row) + "; "
                + "DELETE FROM " + table + " WHERE " + key(dimension, row) + " AND transactionCount <= 0; ";
    }
}
