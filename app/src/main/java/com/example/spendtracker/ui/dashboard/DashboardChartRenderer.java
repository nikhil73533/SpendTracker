package com.example.spendtracker.ui.dashboard;

import android.content.Context;
import com.example.spendtracker.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.*;

public final class DashboardChartRenderer {
    private DashboardChartRenderer() { }
    public static String currency(double value) {
        return "₹" + NumberFormat.getNumberInstance(new Locale("en", "IN")).format(value);
    }
    static void render(BarChart chart, Map<Long, Double> series, boolean masked, String title) {
        if (!configure(chart, series, masked, title)) return;
        List<BarEntry> entries = new ArrayList<>();
        for (double value : series.values()) entries.add(new BarEntry(entries.size(), (float) Math.sqrt(value), value));
        BarDataSet set = new BarDataSet(entries, title);
        set.setColor(chart.getContext().getColor(R.color.dashboard_brand_primary));
        set.setDrawValues(false);
        BarData data = new BarData(set);
        data.setBarWidth(0.56f);
        chart.setData(data);
        chart.setFitBars(true);
        refresh(chart);
    }
    public static void render(LineChart chart, Map<Long, Double> series, boolean masked, String title) {
        if (!configure(chart, series, masked, title)) return;
        List<Entry> entries = new ArrayList<>();
        for (double value : series.values()) entries.add(new Entry(entries.size(), (float) value));
        int accent = chart.getContext().getColor(R.color.dashboard_income_blue);
        LineDataSet set = new LineDataSet(entries, title);
        set.setColor(accent);
        set.setCircleColor(accent);
        set.setCircleRadius(4f);
        set.setDrawCircleHole(false);
        set.setLineWidth(2.5f);
        set.setMode(LineDataSet.Mode.LINEAR);
        set.setDrawValues(false);
        set.setHighLightColor(chart.getContext().getColor(R.color.dashboard_text_primary));
        chart.setData(new LineData(set));
        refresh(chart);
    }
    private static boolean configure(BarLineChartBase<?> chart, Map<Long, Double> series, boolean masked, String title) {
        Context c = chart.getContext();
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataTextColor(c.getColor(R.color.dashboard_text_secondary));
        chart.setNoDataText(c.getString(masked ? R.string.chart_private : R.string.daily_activity_empty));
        if (masked || series.isEmpty()) {
            chart.clear();
            chart.setContentDescription(c.getString(masked ? R.string.chart_private : R.string.daily_activity_empty));
            chart.invalidate();
            return false;
        }
        List<String> labels = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.getDefault());
        StringBuilder description = new StringBuilder(title);
        double max = 0;
        for (Map.Entry<Long, Double> day : series.entrySet()) {
            String label = format.format(new Date(day.getKey()));
            labels.add(label);
            max = Math.max(max, day.getValue());
            description.append("; ").append(label).append(": ").append(currency(day.getValue()));
        }
        chart.setContentDescription(description.toString());
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setLabelCount(Math.min(4, labels.size()));
        chart.getXAxis().setAxisMinimum(-0.2f);
        chart.getXAxis().setAxisMaximum(Math.max(0.8f, labels.size() - 0.8f));
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setDrawAxisLine(false);
        chart.getXAxis().setTextColor(c.getColor(R.color.dashboard_text_secondary));
        chart.getAxisLeft().setAxisMinimum(0f);
        boolean compressed = chart instanceof BarChart;
        float ceiling = compressed ? (float) Math.sqrt(ChartScale.maximum(max)) : ChartScale.maximum(max);
        chart.getAxisLeft().setAxisMaximum(ceiling);
        chart.getAxisLeft().setLabelCount(5, true);
        chart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) { return currency(compressed ? Math.round(value * value) : value); }
        });
        chart.getAxisLeft().setTextColor(c.getColor(R.color.dashboard_text_secondary));
        chart.getAxisLeft().setGridColor(c.getColor(R.color.dashboard_dark_outline));
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().removeAllLimitLines();
        float referenceLevel = compressed ? 100f : 10000f;
        if (ceiling >= referenceLevel) {
            LimitLine reference = new LimitLine(referenceLevel, "₹10,000");
            reference.setLineColor(c.getColor(R.color.dashboard_brand_transfer));
            reference.setTextColor(c.getColor(R.color.dashboard_brand_transfer));
            reference.enableDashedLine(6f, 4f, 0);
            reference.setTextSize(10);
            chart.getAxisLeft().addLimitLine(reference);
        }
        chart.getAxisRight().setEnabled(false);
        chart.setExtraOffsets(4, 8, 12, 4);
        return true;
    }
    private static void refresh(BarLineChartBase<?> chart) {
        chart.notifyDataSetChanged();
        chart.invalidate();
        // MPAndroidChart needs measured bounds for its viewport offsets on the first frame.
        chart.post(() -> { chart.notifyDataSetChanged(); chart.invalidate(); });
    }
}
