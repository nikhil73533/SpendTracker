package com.example.spendtracker.ui.dashboard;

/** Four equal, rounded currency intervals; never force small spending onto a ₹10,000 axis. */
public final class ChartScale {
    private ChartScale() { }
    public static float maximum(double largest) {
        double target = Math.max(1d, largest * 1.12d) / 4d;
        double power = Math.pow(10, Math.floor(Math.log10(target)));
        double fraction = target / power;
        double step = (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10) * power;
        return (float) (step * 4);
    }
}
