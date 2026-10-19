package com.example.spendtracker.billing.autopay;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS only; bearer token stays in memory, merchant secrets never enter the APK. */
public final class BillingApi {
    public static final class ApiException extends Exception {
        public final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
    private final String baseUrl;
    public BillingApi(String baseUrl) {
        if (!baseUrl.startsWith("https://")) throw new IllegalArgumentException("Billing requires HTTPS");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }
    public JSONObject request(String method, String path, String token, JSONObject data, String key) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/v1" + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.setRequestProperty("Content-Type", "application/json");
        if (key != null) connection.setRequestProperty("Idempotency-Key", key);
        try {
            if (data != null) {
                connection.setDoOutput(true);
                try (java.io.OutputStream stream = connection.getOutputStream()) {
                    stream.write(data.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream source = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (source == null) throw new ApiException(code, "Billing unavailable. Check status before retrying.");
            JSONObject result;
            try (InputStream stream = source; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 1048576) throw new IllegalStateException("Response too large");
                }
                result = new JSONObject(bytes.toString("UTF-8"));
            }
            if (code < 200 || code >= 300) throw new ApiException(code, result.optString("error", "Billing unavailable"));
            return result;
        } finally { connection.disconnect(); }
    }
}
