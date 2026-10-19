package com.example.spendtracker.ui.calculator;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CalculationEngineTest {

    @Test
    public void evaluate_basicOperations_returnsCorrectResult() {
        assertEquals(new BigDecimal("15"), CalculationEngine.evaluate("10 + 5"));
        assertEquals(new BigDecimal("5"), CalculationEngine.evaluate("10 - 5"));
        assertEquals(new BigDecimal("50"), CalculationEngine.evaluate("10 * 5"));
        assertEquals(new BigDecimal("2"), CalculationEngine.evaluate("10 / 5"));
        assertEquals(new BigDecimal("2.5"), CalculationEngine.evaluate("10 / 4"));
    }

    @Test
    public void evaluate_operatorPrecedence_returnsCorrectResult() {
        // Multiplication before addition
        assertEquals(new BigDecimal("25"), CalculationEngine.evaluate("10 + 3 * 5"));
        // Division before subtraction
        assertEquals(new BigDecimal("8"), CalculationEngine.evaluate("10 - 4 / 2"));
    }

    @Test
    public void evaluate_parentheses_returnsCorrectResult() {
        assertEquals(new BigDecimal("65"), CalculationEngine.evaluate("(10 + 3) * 5"));
        assertEquals(new BigDecimal("-15"), CalculationEngine.evaluate("10 - (20 + 5)"));
    }

    @Test
    public void evaluate_percent_returnsCorrectResult() {
        assertEquals(new BigDecimal("0.5"), CalculationEngine.evaluate("50 %"));
        assertEquals(new BigDecimal("10"), CalculationEngine.evaluate("20 * 50 %"));
    }

    @Test
    public void evaluate_divisionByZero_throwsException() {
        assertThrows(ArithmeticException.class, () -> {
            CalculationEngine.evaluate("10 / 0");
        });
    }

    @Test
    public void calculateEmi_validInputs_returnsCorrectResult() {
        // 1 Lakh at 10% for 12 months
        // EMI = 100000 * (10/1200) * (1+10/1200)^12 / ((1+10/1200)^12 - 1) ≈ 8791.59
        CalculationEngine.EmiResult result = CalculationEngine.calculateEmi(100000, 10, 12);
        assertEquals(8791.59, result.monthlyEmi, 0.01);
        // totalPayment = roundedEMI × months = 8791.59 × 12 = 105499.08
        assertEquals(5499.08, result.totalInterest, 0.01);
        assertEquals(105499.08, result.totalPayment, 0.01);
    }

    @Test
    public void calculateEmi_zeroInterest_returnsSimpleDivision() {
        CalculationEngine.EmiResult result = CalculationEngine.calculateEmi(120000, 0, 12);
        assertEquals(10000.0, result.monthlyEmi, 0.01);
        assertEquals(0.0, result.totalInterest, 0.01);
        assertEquals(120000.0, result.totalPayment, 0.01);
    }

    @Test
    public void calculateLoan_withDownPaymentAndFee_returnsCorrectResult() {
        // Car cost: 5 Lakhs, Down Payment: 1 Lakh, Effective Principal: 4 Lakhs
        // Interest: 8%, Tenure: 36 months, Fee: 1%
        CalculationEngine.LoanResult result = CalculationEngine.calculateLoan(500000, 8, 36, 1, 100000);
        
        assertEquals(400000.0, result.effectivePrincipal, 0.01);
        assertEquals(4000.0, result.processingFeeAmount, 0.01);
        assertEquals(100000.0, result.downPaymentAmount, 0.01);
        
        // EMI for 4 Lakhs at 8% for 36 months
        assertEquals(12534.55, result.emi, 0.05);
    }

    @Test
    public void formatIndianCurrency_formatsCorrectly() {
        assertEquals("₹ 10", CalculationEngine.formatIndianCurrency(10));
        assertEquals("₹ 1,000", CalculationEngine.formatIndianCurrency(1000));
        assertEquals("₹ 1,00,000", CalculationEngine.formatIndianCurrency(100000));
        assertEquals("₹ 1,23,45,678.90", CalculationEngine.formatIndianCurrency(12345678.90));
        assertEquals("-₹ 1,500", CalculationEngine.formatIndianCurrency(-1500));
    }
}
