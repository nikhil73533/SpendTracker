package com.example.spendtracker.ui.calculator;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.math.BigDecimal;

public class CalculatorViewModel extends ViewModel {

    private final MutableLiveData<String> expression = new MutableLiveData<>("");
    private final MutableLiveData<String> normalResult = new MutableLiveData<>("0");

    // EMI Data
    private final MutableLiveData<CalculationEngine.EmiResult> emiResult = new MutableLiveData<>();
    
    // Loan Data
    private final MutableLiveData<CalculationEngine.LoanResult> loanResult = new MutableLiveData<>();

    // ── Normal Calculator ────────────────────────────────────────────────────

    public LiveData<String> getExpression() { return expression; }
    public LiveData<String> getNormalResult() { return normalResult; }

    public void appendExpression(String value) {
        String current = expression.getValue() != null ? expression.getValue() : "";
        expression.setValue(current + value);
        evaluateExpressionQuietly();
    }

    public void clearExpression() {
        expression.setValue("");
        normalResult.setValue("0");
    }

    public void evaluateExpression() {
        String expr = expression.getValue();
        if (expr == null || expr.isEmpty()) return;
        try {
            BigDecimal result = CalculationEngine.evaluate(expr);
            String formatted = CalculationEngine.formatIndianCurrency(result.doubleValue());
            normalResult.setValue(formatted.replace("₹ ", "")); // just number for normal calc
        } catch (Exception e) {
            normalResult.setValue("Error");
        }
    }

    private void evaluateExpressionQuietly() {
        String expr = expression.getValue();
        if (expr == null || expr.isEmpty()) {
            normalResult.setValue("0");
            return;
        }
        try {
            BigDecimal result = CalculationEngine.evaluate(expr);
            String formatted = CalculationEngine.formatIndianCurrency(result.doubleValue());
            normalResult.setValue(formatted.replace("₹ ", ""));
        } catch (Exception ignored) {
            // Keep previous valid result or show 0
        }
    }

    // ── EMI & Loan Calculator ────────────────────────────────────────────────

    public LiveData<CalculationEngine.EmiResult> getEmiResult() { return emiResult; }
    public LiveData<CalculationEngine.LoanResult> getLoanResult() { return loanResult; }

    public void calculateEmi(String principalStr, String rateStr, String yearsStr, String monthsStr) {
        try {
            double p = parseDoubleOrZero(principalStr);
            double r = parseDoubleOrZero(rateStr);
            int y = parseIntOrZero(yearsStr);
            int m = parseIntOrZero(monthsStr);
            int totalMonths = (y * 12) + m;

            CalculationEngine.EmiResult result = CalculationEngine.calculateEmi(p, r, totalMonths);
            emiResult.setValue(result);
        } catch (Exception e) {
            emiResult.setValue(new CalculationEngine.EmiResult(0, 0, 0));
        }
    }

    public void calculateLoan(String principalStr, String rateStr, String yearsStr, String monthsStr, 
                              String processingFeeStr, String downPaymentStr) {
        try {
            double p = parseDoubleOrZero(principalStr);
            double r = parseDoubleOrZero(rateStr);
            int y = parseIntOrZero(yearsStr);
            int m = parseIntOrZero(monthsStr);
            double fee = parseDoubleOrZero(processingFeeStr);
            double dp = parseDoubleOrZero(downPaymentStr);
            int totalMonths = (y * 12) + m;

            CalculationEngine.LoanResult result = CalculationEngine.calculateLoan(p, r, totalMonths, fee, dp);
            loanResult.setValue(result);
        } catch (Exception e) {
            loanResult.setValue(new CalculationEngine.LoanResult(0, 0, 0, 0, 0, 0));
        }
    }

    private double parseDoubleOrZero(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private int parseIntOrZero(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
