package com.example.spendtracker.util;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/** Opens configured web destinations without relying on package-visibility lookups. */
public final class OnlineIntentLauncher {
    public enum Result { OPENED, NOT_CONFIGURED, OFFLINE, NO_HANDLER }

    private OnlineIntentLauncher() { }

    public static Result open(Context context, String url) {
        if (TextUtils.isEmpty(url)) return Result.NOT_CONFIGURED;
        if (!ConnectivityHelper.isOnline(context)) return Result.OFFLINE;
        // Do not use resolveActivity() here. On Android 11+ a browser can handle this
        // intent but be invisible to that package-manager query unless every browser
        // package is declared in <queries>. Starting the intent is the reliable test.
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            context.startActivity(intent);
            return Result.OPENED;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return Result.NO_HANDLER;
        }
    }
}
