package com.example.spendtracker.ui.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline calculation engine for Normal arithmetic, EMI, and Loan calculations.
 * Uses BigDecimal for precision. No eval() or script engine usage.
 */
public class CalculationEngine {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 10;

    // ── Normal Calculator ────────────────────────────────────────────────────

    /**
     * Evaluates a mathematical expression string safely without eval().
     * Supports: +, -, *, /, %, parentheses, decimal values.
     * Implements proper operator precedence via recursive descent parsing.
     */
    public static BigDecimal evaluate(String expression) throws ArithmeticException {
        if (expression == null || expression.trim().isEmpty()) return BigDecimal.ZERO;
        String expr = expression.replaceAll("\\s+", "");
        Parser parser = new Parser(expr);
        BigDecimal result = parser.parseExpression();
        if (parser.pos < parser.input.length()) {
            throw new ArithmeticException("Unexpected character: " + parser.input.charAt(parser.pos));
        }
        BigDecimal res = result.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        if (res.scale() < 0) {
            res = res.setScale(0);
        }
        return res;
    }

    // Recursive descent parser for proper operator precedence
    private static class Parser {
        final String input;
        int pos = 0;

        Parser(String input) { this.input = input; }

        BigDecimal parseExpression() {
            BigDecimal result = parseTerm();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '+') { pos++; result = result.add(parseTerm(), MC); }
                else if (op == '-') { pos++; result = result.subtract(parseTerm(), MC); }
                else break;
            }
            return result;
        }

        BigDecimal parseTerm() {
            BigDecimal result = parseFactor();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '*' || op == '×') { pos++; result = result.multiply(parseFactor(), MC); }
                else if (op == '/' || op == '÷') {
                    pos++;
                    BigDecimal divisor = parseFactor();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) throw new ArithmeticException("Division by zero");
                    result = result.divide(divisor, SCALE, RoundingMode.HALF_UP);
                }
                else if (op == '%') {
                    pos++;
                    result = result.divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
                }
                else break;
            }
            return result;
        }

        BigDecimal parseFactor() {
            boolean negative = false;
            if (pos < input.length() && input.charAt(pos) == '-') { negative = true; pos++; }
            else if (pos < input.length() && input.charAt(pos) == '+') { pos++; }

            BigDecimal result;
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++; // skip '('
                result = parseExpression();
                if (pos < input.length() && input.charAt(pos) == ')') pos++; // skip ')'
            } else {
                int start = pos;
                while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
                if (start == pos) throw new ArithmeticException("Expected number at position " + pos);
                result = new BigDecimal(input.substring(start, pos));
            }
            return negative ? result.negate() : result;
        }
    }

    // ── EMI Calculator (Reducing Balance) ────────────────────────────────────

    public static class EmiResult {
        public final double monthlyEmi;
        public final double totalInterest;
        public final double totalPayment;

        public EmiResult(double monthlyEmi, double totalInterest, double totalPayment) {
            this.monthlyEmi = monthlyEmi;
            this.totalInterest = totalInterest;
            this.totalPayment = totalPayment;
        }
    }

    /**
     * Standard reducing-balance EMI calculation as per Indian banking norms (RBI guideline).
     * Formula: EMI = P × r × (1+r)^n / ((1+r)^n - 1)
     * where P = principal, r = monthly interest rate (annual / 12 / 100), n = tenure in months.
     *
     * Note: Indian banks compute EMI to the nearest paise (2 decimal places),
     * then derive total payment = EMI × n, and total interest = total payment - P.
     * The last EMI may differ by a few paise due to rounding; this is standard practice.
     */
    public static EmiResult calculateEmi(double principal, double annualRate, int tenureMonths) {
        if (principal <= 0 || tenureMonths <= 0) {
            return new EmiResult(0, 0, 0);
        }
        if (annualRate <= 0) {
            // Zero interest: simple division
            double emi = principal / tenureMonths;
            return new EmiResult(Math.round(emi * 100.0) / 100.0, 0, principal);
        }

        double monthlyRate = annualRate / 12.0 / 100.0;
        double pow = Math.pow(1 + monthlyRate, tenureMonths);
        double emi = principal * monthlyRate * pow / (pow - 1);

        // Round EMI to 2 decimal places (paise precision)
        emi = Math.round(emi * 100.0) / 100.0;

        // Total Payment & Interest derived from rounded EMI (how banks actually bill)
        double totalPayment = emi * tenureMonths;
        totalPayment = Math.round(totalPayment * 100.0) / 100.0;
        double totalInterest = totalPayment - principal;
        totalInterest = Math.round(totalInterest * 100.0) / 100.0;

        return new EmiResult(emi, totalInterest, totalPayment);
    }

    // ── Loan Calculator ──────────────────────────────────────────────────────

    public static class LoanResult {
        public final double emi;
        public final double totalInterest;
        public final double totalRepayment;
        public final double effectivePrincipal;
        public final double processingFeeAmount;
        public final double downPaymentAmount;

        public LoanResult(double emi, double totalInterest, double totalRepayment, double effectivePrincipal, double processingFeeAmount, double downPaymentAmount) {
            this.emi = emi;
            this.totalInterest = totalInterest;
            this.totalRepayment = totalRepayment;
            this.effectivePrincipal = effectivePrincipal;
            this.processingFeeAmount = processingFeeAmount;
            this.downPaymentAmount = downPaymentAmount;
        }
    }

    /**
     * Loan calculation with processing fee and down payment.
     *
     * @param totalCost    Total cost of the asset / loan amount requested
     * @param annualRate   Annual interest rate (%)
     * @param tenureMonths Tenure in months
     * @param processingFeePercent Processing fee as percentage of loan amount
     * @param downPayment  Down payment amount
     */
    public static LoanResult calculateLoan(double totalCost, double annualRate, int tenureMonths,
                                            double processingFeePercent, double downPayment) {
        double effectivePrincipal = totalCost - downPayment;
        if (effectivePrincipal <= 0) effectivePrincipal = 0;

        double processingFeeAmount = effectivePrincipal * processingFeePercent / 100.0;
        EmiResult emiResult = calculateEmi(effectivePrincipal, annualRate, tenureMonths);

        return new LoanResult(
            emiResult.monthlyEmi,
            emiResult.totalInterest,
            emiResult.totalPayment,
            Math.round(effectivePrincipal * 100.0) / 100.0,
            Math.round(processingFeeAmount * 100.0) / 100.0,
            downPayment
        );
    }

    // ── Indian Number Formatting ─────────────────────────────────────────────

    /**
     * Formats a number in Indian numbering system (e.g., 12,34,567.89).
     */
    public static String formatIndianCurrency(double amount) {
        if (amount < 0) return "-" + formatIndianCurrency(-amount);
        long intPart = (long) amount;
        long decPart = Math.round((amount - intPart) * 100);

        String intStr = String.valueOf(intPart);
        StringBuilder sb = new StringBuilder();
        int len = intStr.length();
        if (len <= 3) {
            sb.append(intStr);
        } else {
            String lastThree = intStr.substring(len - 3);
            String prefix = intStr.substring(0, len - 3);
            StringBuilder formattedPrefix = new StringBuilder();
            for (int i = prefix.length(); i > 0; i -= 2) {
                if (formattedPrefix.length() > 0) formattedPrefix.insert(0, ",");
                formattedPrefix.insert(0, prefix.substring(Math.max(0, i - 2), i));
            }
            sb.append(formattedPrefix).append(",").append(lastThree);
        }

        if (decPart > 0) {
            sb.append(String.format(Locale.US, ".%02d", decPart));
        }
        return "₹ " + sb.toString();
    }
}
