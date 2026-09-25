package com.example.spendtracker.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/** Opens configured web destinations without assuming a browser is present. */
public final class OnlineIntentLauncher {
    public enum Result { OPENED, NOT_CONFIGURED, OFFLINE, NO_HANDLER }

    private OnlineIntentLauncher() { }

    public static Result open(Context context, String url) {
        if (TextUtils.isEmpty(url)) return Result.NOT_CONFIGURED;
        if (!ConnectivityHelper.isOnline(context)) return Result.OFFLINE;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(context.getPackageManager()) == null) return Result.NO_HANDLER;
        try {
            context.startActivity(intent);
            return Result.OPENED;
        } catch (RuntimeException ignored) {
            return Result.NO_HANDLER;
        }
    }
}
