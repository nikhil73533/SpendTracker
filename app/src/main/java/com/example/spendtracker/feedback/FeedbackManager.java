package com.example.spendtracker.feedback;

import android.content.Context;
import com.example.spendtracker.R;
import com.example.spendtracker.util.OnlineIntentLauncher;

/** Routes explicit feedback actions without attaching financial or diagnostic data. */
public final class FeedbackManager {
    public enum Type { FEEDBACK, PROBLEM, FEATURE }

    private FeedbackManager() { }

    public static OnlineIntentLauncher.Result open(Context context, Type type) {
        // A single hosted endpoint may provide its own type selector. No user data is appended.
        return OnlineIntentLauncher.open(context, context.getString(R.string.feedback_url));
    }
}
