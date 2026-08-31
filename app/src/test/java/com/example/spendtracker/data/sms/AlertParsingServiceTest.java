package com.example.spendtracker.data.sms;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlertParsingServiceTest {

    @Mock private BillAlertDao billAlertDao;
    @Mock private Context context;
    @Mock private SharedPreferences sharedPreferences;
    @Mock private SharedPreferences.Editor editor;

    private AlertParsingService service;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences);
        when(sharedPreferences.edit()).thenReturn(editor);
        when(editor.putStringSet(anyString(), any())).thenReturn(editor);
        
        service = new AlertParsingService(billAlertDao, context);
    }

    @Test
    public void testGenerateTemplate_replacesDigits() {
        String msg = "Your Netflix bill for Rs. 499 is due on 15th.";
        String expected = "your netflix bill for rs. # is due on #th.";
        assertEquals(expected, service.generateTemplate(msg));
    }

    @Test
    public void testExtractAmount_validAmounts() {
        assertEquals(499.0, service.extractAmount("Your Netflix bill for Rs. 499 is due."), 0.01);
        assertEquals(1500.50, service.extractAmount("Payment of INR 1500.50 received."), 0.01);
        assertEquals(2000.0, service.extractAmount("Subscription ₹ 2,000 renewed."), 0.01);
        assertEquals(0.0, service.extractAmount("No amount here."), 0.01);
    }

    @Test
    public void testAddCustomKeyword() {
        when(sharedPreferences.getStringSet(anyString(), any())).thenReturn(new HashSet<>());
        service.addCustomKeyword("netflix");
        
        ArgumentCaptor<HashSet> captor = ArgumentCaptor.forClass(HashSet.class);
        verify(editor).putStringSet(anyString(), captor.capture());
        
        assertTrue(captor.getValue().contains("netflix"));
    }
}
