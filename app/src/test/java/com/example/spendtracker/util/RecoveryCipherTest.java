package com.example.spendtracker.util;

import org.junit.Test;
import java.io.*;
import java.util.Arrays;
import static org.junit.Assert.*;

public class RecoveryCipherTest {
    private byte[] encrypt(byte[] data, String password) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCipher.encrypt(new ByteArrayInputStream(data), out, password.toCharArray());
        return out.toByteArray();
    }
    private byte[] decrypt(byte[] data, String password) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCipher.decrypt(new ByteArrayInputStream(data), out, password.toCharArray());
        return out.toByteArray();
    }
    @Test public void portableAcrossIndependentInstances() throws Exception {
        byte[] db = new byte[100_000]; new java.util.Random(13).nextBytes(db);
        byte[] backup = encrypt(db, "correct horse battery");
        assertArrayEquals(db, decrypt(backup, "correct horse battery"));
        assertFalse(Arrays.equals(backup, encrypt(db, "correct horse battery")));
    }
    @Test public void wrongPasswordIsRejected() throws Exception {
        byte[] backup = encrypt(new byte[]{1,2,3}, "long password");
        assertThrows(Exception.class, () -> decrypt(backup, "wrong password"));
    }
    @Test public void tamperingIsRejected() throws Exception {
        byte[] backup = encrypt(new byte[]{1,2,3}, "long password");
        backup[backup.length - 1] ^= 1;
        assertThrows(Exception.class, () -> decrypt(backup, "long password"));
    }
    @Test public void truncatedArchiveIsRejected() throws Exception {
        byte[] backup = encrypt(new byte[100], "long password");
        assertThrows(Exception.class, () -> decrypt(Arrays.copyOf(backup, backup.length - 8), "long password"));
    }
    @Test public void unknownFormatIsRejected() throws Exception {
        byte[] backup = encrypt(new byte[100], "long password"); backup[0] = 0;
        assertThrows(IOException.class, () -> decrypt(backup, "long password"));
    }
}
