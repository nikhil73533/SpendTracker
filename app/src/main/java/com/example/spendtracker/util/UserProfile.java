package com.example.spendtracker.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

public final class UserProfile {
    public final String name, email, phone;
    public UserProfile(String name, String email, String phone) {
        this.name = name.trim(); this.email = email.trim(); this.phone = phone.trim();
    }
    public static UserProfile load(Context context) {
        SharedPreferences p = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE);
        return new UserProfile(p.getString("name", ""), p.getString("email", ""), p.getString("phone", ""));
    }
    public void save(Context context) {
        context.getSharedPreferences("user_profile", Context.MODE_PRIVATE).edit()
                .putString("name", name).putString("email", email).putString("phone", phone).apply();
    }
    public String initials() {
        if (name.isEmpty()) return "?";
        String[] parts = name.split("\\s+");
        return (first(parts[0]) + (parts.length > 1 ? first(parts[parts.length - 1]) : "")).toUpperCase(Locale.ROOT);
    }
    private static String first(String text) { return text.substring(0, text.offsetByCodePoints(0, 1)); }
}
