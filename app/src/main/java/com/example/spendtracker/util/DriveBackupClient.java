package com.example.spendtracker.util;

import android.accounts.Account;
import android.content.Context;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal Drive app-data uploader. It needs OAuth consent, never an API key or public folder access. */
public final class DriveBackupClient {
    public static final String APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";

    private DriveBackupClient() {}

    public static boolean isConnected(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && account.getAccount() != null
                && GoogleSignIn.hasPermissions(account, new com.google.android.gms.common.api.Scope(APPDATA_SCOPE));
    }

    public static void upload(Context context, File archive) throws IOException, UserRecoverableAuthException {
        if (archive == null || !archive.isFile()) throw new IOException("Backup archive is unavailable");
        GoogleSignInAccount signedIn = GoogleSignIn.getLastSignedInAccount(context);
        Account account = signedIn == null ? null : signedIn.getAccount();
        if (account == null || !GoogleSignIn.hasPermissions(signedIn, new com.google.android.gms.common.api.Scope(APPDATA_SCOPE))) {
            throw new IOException("Connect Google Drive before uploading a backup");
        }
        final String token;
        try {
            token = GoogleAuthUtil.getToken(context, account, "oauth2:" + APPDATA_SCOPE);
        } catch (UserRecoverableAuthException e) { throw e;
        } catch (Exception e) { throw new IOException("Could not authorize Google Drive", e); }
        // Archives include a timestamp in the filename, so every successful backup is an immutable daily record.
        uploadArchive(token, archive);
    }

    private static void uploadArchive(String token, File archive) throws IOException {
        String boundary = "spendtracker" + System.nanoTime();
        URL url = new URL(UPLOAD + "?uploadType=multipart");
        HttpURLConnection connection = open(url, token, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        try (BufferedOutputStream output = new BufferedOutputStream(connection.getOutputStream());
             BufferedInputStream input = new BufferedInputStream(new java.io.FileInputStream(archive))) {
            String metadata = "{\"name\":\"" + archive.getName() + "\",\"parents\":[\"appDataFolder\"]}";
            write(output, "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n");
            write(output, "--" + boundary + "\r\nContent-Type: application/zip\r\n\r\n");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            write(output, "\r\n--" + boundary + "--\r\n");
        }
        int status = connection.getResponseCode();
        if (status / 100 != 2) throw failure(connection, status);
        connection.disconnect();
    }

    private static HttpURLConnection open(URL url, String token, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private static void write(BufferedOutputStream output, String text) throws IOException { output.write(text.getBytes(StandardCharsets.UTF_8)); }
    private static IOException failure(HttpURLConnection connection, int status) throws IOException {
        java.io.InputStream error = connection.getErrorStream();
        String detail = error == null ? "" : readUtf8(error);
        return new IOException("Google Drive upload failed (HTTP " + status + "): " + detail);
    }

    private static String readUtf8(java.io.InputStream input) throws IOException {
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
