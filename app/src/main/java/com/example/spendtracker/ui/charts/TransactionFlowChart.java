package com.example.spendtracker.ui.charts;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.util.TransferDirection;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import java.util.*;
import java.text.SimpleDateFormat;

/** One time axis and three independent series, including zeroes in each populated bucket. */
public final class TransactionFlowChart {
    private TransactionFlowChart() {}
    public static void render(LineChart chart, List<Transaction> transactions, String period, boolean masked) {
        chart.clear();
        chart.getDescription().setEnabled(false);
        chart.setNoDataText(masked ? "Unlock privacy mode to view amounts" : "No transactions");
        if (masked || transactions.isEmpty()) { chart.invalidate(); return; }
        TreeMap<Long, double[]> buckets = new TreeMap<>();
        for (Transaction t : transactions) {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(t.getDate());
            if ("WEEKLY".equals(period)) c.add(Calendar.DAY_OF_MONTH, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7));
            else if ("ANNUAL".equals(period)) c.set(Calendar.DAY_OF_YEAR, 1);
            else c.set(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            double[] totals = buckets.computeIfAbsent(c.getTimeInMillis(), k -> new double[3]);
            totals[TransferDirection.isTransfer(t) ? 2 : "INCOME".equals(t.getType()) ? 0 : 1] += t.getAmount();
        }
        List<String> labels = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("WEEKLY".equals(period) ? "dd MMM yy" : "ANNUAL".equals(period) ? "yyyy" : "MMM yy", Locale.getDefault());
        for (long date : buckets.keySet()) labels.add(format.format(new Date(date)));
        int[] colors = {com.example.spendtracker.R.color.dashboard_income_blue, com.example.spendtracker.R.color.dashboard_expense_red, com.example.spendtracker.R.color.dashboard_brand_transfer};
        String[] names = {"Income", "Expense", "Transfers"};
        List<ILineDataSet> sets = new ArrayList<>();
        for (int kind = 0; kind < 3; kind++) {
            List<Entry> entries = new ArrayList<>(); int index = 0;
            for (double[] totals : buckets.values()) entries.add(new Entry(index++, (float) totals[kind]));
            LineDataSet set = new LineDataSet(entries, names[kind]);
            set.setColor(chart.getContext().getColor(colors[kind])); set.setCircleColor(chart.getContext().getColor(colors[kind]));
            set.setLineWidth(2f); set.setCircleRadius(3f); set.setDrawValues(false); sets.add(set);
        }
        int text = chart.getContext().getColor(com.example.spendtracker.R.color.text_secondary);
        chart.getLegend().setEnabled(true); chart.getLegend().setTextColor(text);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM); chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels)); chart.getXAxis().setTextColor(text);
        chart.getAxisLeft().setTextColor(text); chart.getAxisLeft().setAxisMinimum(0); chart.getAxisRight().setEnabled(false);
        chart.setData(new LineData(sets)); chart.invalidate();
    }
}
