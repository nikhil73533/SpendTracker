package com.example.spendtracker.ui.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChartScaleTest {
    @Test public void smallAmountsRemainVisible() { assertTrue(ChartScale.maximum(50) < 100); }
    @Test public void largeSpendingIncludesTenThousandWithHeadroom() { assertTrue(ChartScale.maximum(10000) > 10000); }
    @Test public void emptyHasNonzeroRange() { assertTrue(ChartScale.maximum(0) > 0); }
    @Test public void scaleShrinksWhenDataShrinks() { assertTrue(ChartScale.maximum(20) < ChartScale.maximum(100000)); }
}
