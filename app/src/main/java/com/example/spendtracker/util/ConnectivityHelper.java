package com.example.spendtracker.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Small, best-effort check used only before explicitly online actions. */
public final class ConnectivityHelper {
    private ConnectivityHelper() { }

    public static boolean isOnline(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
