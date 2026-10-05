package com.example.spendtracker.ui.dashboard;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.spendtracker.ui.testing.TransactionInteractionTestActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.example.spendtracker.domain.model.Transaction;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardChartsDeviceTest {
    @Test public void compressedBarsKeepSmallDaysVisibleAndExactAmounts() {
        try (ActivityScenario<TransactionInteractionTestActivity> scenario = ActivityScenario.launch(TransactionInteractionTestActivity.class)) {
            scenario.onActivity(activity -> {
                com.github.mikephil.charting.charts.BarChart chart = new com.github.mikephil.charting.charts.BarChart(activity);
                activity.setContentView(chart);
                Map<Long, Double> values = new TreeMap<>();
                values.put(0L, 0d); values.put(86400000L, 50d); values.put(172800000L, 10000d);
                DashboardChartRenderer.render(chart, values, false, "Activity");
                com.github.mikephil.charting.data.BarEntry small = chart.getData().getDataSetByIndex(0).getEntryForIndex(1);
                assertTrue(small.getY() / chart.getAxisLeft().getAxisMaximum() > 0.04f);
                assertEquals(50d, (Double) small.getData(), 0);
                assertEquals(0f, chart.getData().getDataSetByIndex(0).getEntryForIndex(0).getY(), 0);
                assertEquals("₹10,000", chart.getAxisLeft().getValueFormatter().getFormattedValue(100));
                assertEquals(100f, chart.getAxisLeft().getLimitLines().get(0).getLimit(), 0);
            });
        }
    }

    @Test public void firstLayoutEmptySinglePointAndChangedDataRender() {
        AtomicReference<LineChart> ref = new AtomicReference<>();
        try (ActivityScenario<TransactionInteractionTestActivity> scenario = ActivityScenario.launch(TransactionInteractionTestActivity.class)) {
            scenario.onActivity(activity -> {
                LineChart chart = new LineChart(activity); ref.set(chart);
                activity.setContentView(chart);
                DashboardChartRenderer.render(chart, DashboardChartSeries.spendingWeek(null, System.currentTimeMillis()), false, "Last 7 days");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                LineChart chart = ref.get();
                assertEquals(7, chart.getData().getEntryCount());
                assertTrue(chart.getViewPortHandler().contentWidth() > 0);
                assertTrue(chart.getAxisLeft().getAxisMaximum() > 0);
                Transaction t = new Transaction(); t.setAmount(50); t.setDate(System.currentTimeMillis());
                DashboardChartRenderer.render(chart, DashboardChartSeries.spendingWeek(Collections.singletonList(t), System.currentTimeMillis()), false, "Week");
                assertEquals(50, chart.getData().getYMax(), 0);
                assertTrue(chart.getAxisLeft().getAxisMaximum() < 100);
                t.setAmount(10000);
                DashboardChartRenderer.render(chart, DashboardChartSeries.spendingWeek(Collections.singletonList(t), System.currentTimeMillis()), false, "Week");
                assertEquals(1, chart.getAxisLeft().getLimitLines().size());
                DashboardChartRenderer.render(chart, DashboardChartSeries.spendingWeek(null, System.currentTimeMillis()), true, "Week");
                assertNull(chart.getData());
                DashboardChartRenderer.render(chart, DashboardChartSeries.spendingWeek(Collections.singletonList(t), System.currentTimeMillis()), false, "Week");
                assertEquals(10000, chart.getData().getYMax(), 0);
            });
        }
    }
}
