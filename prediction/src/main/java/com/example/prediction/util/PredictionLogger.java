package com.example.prediction.util;

public class PredictionLogger {
    public interface LoggerCallback {
        void onLog(String message);
    }

    private static LoggerCallback callback;

    public static void setCallback(LoggerCallback cb) {
        callback = cb;
    }

    public static void log(String message) {
        if (callback != null) {
            callback.onLog(message);
        }
    }
}
