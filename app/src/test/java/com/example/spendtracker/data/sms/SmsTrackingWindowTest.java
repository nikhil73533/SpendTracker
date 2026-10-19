package com.example.spendtracker.data.sms;

import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SmsTrackingWindowTest {
    @Test public void freshInstallStartsNowInsteadOfEpoch() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.edit()).thenReturn(editor);
        when(editor.putLong(anyString(), anyLong())).thenReturn(editor);
        when(editor.commit()).thenReturn(true);
        assertTrue(SmsTrackingWindow.initialize(prefs, 100000));
        verify(editor).putLong("tracking_started_at", 100000);
        verify(editor).putLong("last_scan", 100000);
    }
    @Test public void overlapNeverCrossesTrackingStart() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getLong("tracking_started_at", Long.MAX_VALUE)).thenReturn(100000L);
        when(prefs.getLong("last_scan", 100000L)).thenReturn(120000L);
        assertEquals(100000L, SmsTrackingWindow.lowerBound(prefs));
        when(prefs.getLong("last_scan", 100000L)).thenReturn(200000L);
        assertEquals(140000L, SmsTrackingWindow.lowerBound(prefs));
    }
    @Test public void reopeningDoesNotResetBoundary() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.contains("tracking_started_at")).thenReturn(true);
        assertTrue(SmsTrackingWindow.initialize(prefs, 200000));
        verify(prefs, never()).edit();
    }
    @Test public void failedPersistenceDoesNotPermitScan() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.edit()).thenReturn(editor);
        when(editor.putLong(anyString(), anyLong())).thenReturn(editor);
        assertFalse(SmsTrackingWindow.initialize(prefs, 100000));
    }
    @Test public void upgradePreservesRecoveryPosition() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.getLong("last_scan", 0)).thenReturn(90000L);
        when(prefs.edit()).thenReturn(editor);
        when(editor.putLong(anyString(), anyLong())).thenReturn(editor);
        when(editor.commit()).thenReturn(true);
        assertTrue(SmsTrackingWindow.initialize(prefs, 100000));
        verify(editor).putLong("tracking_started_at", 90000L);
    }
}
