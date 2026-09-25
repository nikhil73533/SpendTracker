package com.example.spendtracker.sharing;

import android.content.Context;
import android.content.Intent;
import com.example.spendtracker.R;

public final class ShareManager {
    private ShareManager() { }

    public static boolean shareApp(Context context) {
        String storeUrl = "https://play.google.com/store/apps/details?id=" + context.getPackageName();
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message) + "\n" + storeUrl);
        if (intent.resolveActivity(context.getPackageManager()) == null) return false;
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_chooser)));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
