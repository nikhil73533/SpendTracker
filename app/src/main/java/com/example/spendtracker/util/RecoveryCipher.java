package com.example.spendtracker.util;

import java.io.*;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned, authenticated portable backup. The password is never stored in the archive. */
public final class RecoveryCipher {
    private static final byte[] MAGIC = {'P','I','N','C','H',1};
    private static final int ITERATIONS = 210_000;
    public static final int MAX_BYTES = 128 * 1024 * 1024;
    private RecoveryCipher() {}

    public static void encrypt(InputStream input, OutputStream output, char[] password) throws Exception {
        byte[] salt = new byte[16], nonce = new byte[12];
        SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
        output.write(MAGIC); output.write(salt); output.write(nonce);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce);
        try (CipherOutputStream encrypted = new CipherOutputStream(output, cipher)) { copy(input, encrypted); }
    }

    public static void decrypt(InputStream input, OutputStream output, char[] password) throws Exception {
        DataInputStream header = new DataInputStream(input);
        byte[] magic = new byte[MAGIC.length], salt = new byte[16], nonce = new byte[12];
        header.readFully(magic); header.readFully(salt); header.readFully(nonce);
        if (!Arrays.equals(MAGIC, magic)) throw new IOException("Unsupported backup file");
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, nonce);
        // Explicit doFinal: never accept a truncated stream or ignore an authentication failure.
        byte[] buffer = new byte[8192]; int count; long total = 0;
        while ((count = input.read(buffer)) != -1) {
            if ((total += count) > MAX_BYTES) throw new IOException("Backup is too large");
            byte[] decoded = cipher.update(buffer, 0, count);
            if (decoded != null) output.write(decoded);
        }
        output.write(cipher.doFinal());
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] nonce) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC); cipher.updateAAD(salt); cipher.updateAAD(nonce);
            return cipher;
        } finally { spec.clearPassword(); if (key != null) Arrays.fill(key, (byte) 0); }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192]; int count; long total = 0;
        while ((count = in.read(buffer)) != -1) {
            if ((total += count) > MAX_BYTES) throw new IOException("Backup is too large");
            out.write(buffer, 0, count);
        }
    }
}
