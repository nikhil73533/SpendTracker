package com.example.spendtracker.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service generating comprehensive, production-grade PDF financial analysis reports using native Android {@link PdfDocument}.
 *
 * <p>Features:
 * <ul>
 *   <li>Native Canvas rendering (full Unicode & '₹' currency symbol support)</li>
 *   <li>Executive Financial Summary & Metrics Grid</li>
 *   <li>Native Category Expense Donut Chart & Comparison Bar Chart</li>
 *   <li>Grouped Analysis Tables (Category, Bank, Account/Source Type)</li>
 *   <li>Multi-page Paginated Transaction Details with colored badges for INCOME, EXPENSE, and TRANSFER</li>
 * </ul>
 */
public class PdfReportService {

    public static class CategorySummaryItem {
        public final String category;
        public final String type;
        public final int count;
        public final double amount;
        public final double percentage;

        public CategorySummaryItem(String category, String type, int count, double amount, double percentage) {
            this.category = category;
            this.type = type;
            this.count = count;
            this.amount = amount;
            this.percentage = percentage;
        }
    }

    public static class BankSummaryItem {
        public final String bankName;
        public final int count;
        public final double amount;

        public BankSummaryItem(String bankName, int count, double amount) {
            this.bankName = bankName;
            this.count = count;
            this.amount = amount;
        }
    }

    public static class SourceTypeSummaryItem {
        public final String sourceType;
        public final int count;
        public final double amount;

        public SourceTypeSummaryItem(String sourceType, int count, double amount) {
            this.sourceType = sourceType;
            this.count = count;
            this.amount = amount;
        }
    }

    public static class ReportPayload {
        public String dateRangeLabel;
        public double totalIncome;
        public double totalExpense;
        public double totalTransfers;
        public double transferIncoming;
        public double transferOutgoing;
        public double accountExpenses;
        public double cardExpenses;
        public double netSavings;
        public List<CategorySummaryItem> categoryBreakdown = new ArrayList<>();
        public List<BankSummaryItem> bankBreakdown = new ArrayList<>();
        public List<SourceTypeSummaryItem> sourceTypeBreakdown = new ArrayList<>();
        public List<Transaction> transactions = new ArrayList<>();
    }

    /** Primary method called to generate the PDF report. */
    public static File generateReport(Context context, DashboardViewModel.TotalPageData data, List<Transaction> transactions, String dateRangeLabel) throws Exception {
        ReportPayload payload = buildPayload(data, transactions, dateRangeLabel);
        return createPdfDocument(context, payload);
    }

    public static File createPdfDocument(Context context, ReportPayload payload) throws Exception {
        PdfDocument document = new PdfDocument();

        final int PAGE_WIDTH = 595;  // Standard A4 width in pt
        final int PAGE_HEIGHT = 842; // Standard A4 height in pt
        final int MARGIN_LEFT = 36;
        final int MARGIN_RIGHT = 559;

        PageBuilder pageBuilder = new PageManager(document, PAGE_WIDTH, PAGE_HEIGHT, MARGIN_LEFT, MARGIN_RIGHT, payload);

        // 1. Draw Banner Header
        pageBuilder.drawBannerHeader();

        // 2. Executive Financial Summary Section
        pageBuilder.drawSectionTitle("1. Executive Financial Summary");
        pageBuilder.drawExecutiveSummaryGrid();

        // 3. Financial Charts Section
        pageBuilder.drawSectionTitle("2. Financial Analysis & Charts");
        pageBuilder.drawCharts();

        // 4. Grouped Analysis Section
        pageBuilder.drawSectionTitle("3. Grouped Category & Account Breakdown");
        pageBuilder.drawGroupedTables();

        // 5. Detailed Transactions Section
        pageBuilder.drawSectionTitle("4. Detailed Transaction Records");
        pageBuilder.drawTransactionsTable();

        // Finish current page & save
        pageBuilder.finishDocument();

        File reportsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (reportsDir != null && !reportsDir.exists()) {
            boolean created = reportsDir.mkdirs();
            if (!created && !reportsDir.exists()) {
                throw new java.io.IOException("Failed to create directory: " + reportsDir.getAbsolutePath());
            }
        }
        File file = new File(reportsDir, "SpendTracker_Report.pdf");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }

        return file;
    }

    // ── Payload Construction ──────────────────────────────────────────────────

    public static ReportPayload buildPayload(DashboardViewModel.TotalPageData data, List<Transaction> transactions, String dateRangeLabel) {
        ReportPayload payload = new ReportPayload();
        payload.dateRangeLabel = (dateRangeLabel != null && !dateRangeLabel.isEmpty()) ? dateRangeLabel : "All Time";
        payload.transactions = transactions != null ? transactions : new ArrayList<>();

        if (data != null) {
            payload.totalIncome = data.income;
            payload.accountExpenses = data.accountExpenses;
            payload.cardExpenses = data.cardExpenses;
            payload.totalExpense = data.accountExpenses + data.cardExpenses;
            payload.totalTransfers = data.transfers;
            payload.transferIncoming = data.transferIncoming;
            payload.transferOutgoing = data.transferOutgoing;
            payload.netSavings = payload.totalIncome - payload.totalExpense;
        } else {
            // Calculate from transactions directly if data is null
            for (Transaction t : payload.transactions) {
                if ("TRANSFER".equalsIgnoreCase(t.getType()) || "Transfer".equalsIgnoreCase(t.getCategory())) {
                    payload.totalTransfers += t.getAmount();
                } else if ("INCOME".equalsIgnoreCase(t.getType())) {
                    payload.totalIncome += t.getAmount();
                } else {
                    payload.totalExpense += t.getAmount();
                    if ("Credit Card".equalsIgnoreCase(t.getSourceType())) {
                        payload.cardExpenses += t.getAmount();
                    } else {
                        payload.accountExpenses += t.getAmount();
                    }
                }
            }
            payload.netSavings = payload.totalIncome - payload.totalExpense;
        }

        // Build Category Breakdown
        Map<String, double[]> catMap = new LinkedHashMap<>(); // name -> [count, expenseSum, incomeSum]
        Map<String, String> catTypeMap = new LinkedHashMap<>();

        // Build Bank Breakdown
        Map<String, double[]> bankMap = new LinkedHashMap<>(); // name -> [count, sum]

        // Build Source Type Breakdown
        Map<String, double[]> sourceMap = new LinkedHashMap<>(); // name -> [count, sum]

        for (Transaction t : payload.transactions) {
            boolean isTransfer = "TRANSFER".equalsIgnoreCase(t.getType()) || "Transfer".equalsIgnoreCase(t.getCategory());

            // Category breakdown (exclude transfers from expense/income category percentages)
            if (!isTransfer) {
                String cat = (t.getCategory() != null && !t.getCategory().isEmpty()) ? t.getCategory() : "Other";
                double[] arr = catMap.computeIfAbsent(cat, k -> new double[]{0, 0, 0});
                arr[0]++; // count
                if ("INCOME".equalsIgnoreCase(t.getType())) {
                    arr[2] += t.getAmount();
                    catTypeMap.put(cat, "INCOME");
                } else {
                    arr[1] += t.getAmount();
                    catTypeMap.put(cat, "EXPENSE");
                }
            }

            // Bank breakdown (exclude transfers)
            if (!isTransfer && t.getBankName() != null && !t.getBankName().isEmpty()) {
                String bank = t.getBankName();
                double[] bArr = bankMap.computeIfAbsent(bank, k -> new double[]{0, 0});
                bArr[0]++;
                bArr[1] += t.getAmount();
            }

            // Source Type breakdown (exclude transfers)
            if (!isTransfer) {
                String src = (t.getSourceType() != null && !t.getSourceType().isEmpty()) ? t.getSourceType() : "Account";
                double[] sArr = sourceMap.computeIfAbsent(src, k -> new double[]{0, 0});
                sArr[0]++;
                sArr[1] += t.getAmount();
            }
        }

        double totalCatExpense = payload.totalExpense > 0 ? payload.totalExpense : 1.0;
        for (Map.Entry<String, double[]> entry : catMap.entrySet()) {
            String cat = entry.getKey();
            double[] arr = entry.getValue();
            int count = (int) arr[0];
            double amt = arr[1] > 0 ? arr[1] : arr[2];
            String type = catTypeMap.getOrDefault(cat, "EXPENSE");
            double pct = "EXPENSE".equals(type) ? (amt / totalCatExpense) * 100.0 : 0.0;
            payload.categoryBreakdown.add(new CategorySummaryItem(cat, type, count, amt, pct));
        }

        for (Map.Entry<String, double[]> entry : bankMap.entrySet()) {
            payload.bankBreakdown.add(new BankSummaryItem(entry.getKey(), (int) entry.getValue()[0], entry.getValue()[1]));
        }

        for (Map.Entry<String, double[]> entry : sourceMap.entrySet()) {
            payload.sourceTypeBreakdown.add(new SourceTypeSummaryItem(entry.getKey(), (int) entry.getValue()[0], entry.getValue()[1]));
        }

        return payload;
    }

    // ── Page & Drawing Engine ─────────────────────────────────────────────────

    private interface PageBuilder {
        void drawBannerHeader();
        void drawSectionTitle(String title);
        void drawExecutiveSummaryGrid();
        void drawCharts();
        void drawGroupedTables();
        void drawTransactionsTable();
        void finishDocument();
    }

    private static class PageManager implements PageBuilder {
        private final PdfDocument document;
        private final int pageWidth;
        private final int pageHeight;
        private final int marginLeft;
        private final int marginRight;
        private final int usableWidth;
        private final int maxY = 780;
        private final ReportPayload payload;

        private PdfDocument.Page currentPage;
        private Canvas canvas;
        private int pageNumber = 1;
        private float y = 0;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public PageManager(PdfDocument document, int pageWidth, int pageHeight, int marginLeft, int marginRight, ReportPayload payload) {
            this.document = document;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.marginLeft = marginLeft;
            this.marginRight = marginRight;
            this.usableWidth = marginRight - marginLeft;
            this.payload = payload;

            startNewPage();
        }

        private void startNewPage() {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            currentPage = document.startPage(pageInfo);
            canvas = currentPage.getCanvas();
            y = 36;
        }

        private void ensureSpace(float requiredHeight) {
            if (y + requiredHeight > maxY) {
                drawPageFooter();
                document.finishPage(currentPage);
                pageNumber++;
                startNewPage();
                drawSlimHeader();
            }
        }

        private void drawSlimHeader() {
            paint.setColor(Color.parseColor("#1E293B"));
            canvas.drawRect(0, 0, pageWidth, 28, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(10);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("SpendTracker Financial Report | " + payload.dateRangeLabel, marginLeft, 18, paint);

            y = 42;
        }

        private void drawPageFooter() {
            paint.setColor(Color.parseColor("#CBD5E1"));
            paint.setStrokeWidth(0.5f);
            canvas.drawLine(marginLeft, pageHeight - 30, marginRight, pageHeight - 30, paint);

            paint.setColor(Color.parseColor("#64748B"));
            paint.setTextSize(9);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText("Generated by SpendTracker App", marginLeft, pageHeight - 16, paint);

            String pageStr = "Page " + pageNumber;
            float textWidth = paint.measureText(pageStr);
            canvas.drawText(pageStr, marginRight - textWidth, pageHeight - 16, paint);
        }

        @Override
        public void drawBannerHeader() {
            // Main Top Banner
            paint.setColor(Color.parseColor("#0F172A")); // Dark Slate
            canvas.drawRect(0, 0, pageWidth, 85, paint);

            // Title
            paint.setColor(Color.WHITE);
            paint.setTextSize(18);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("SpendTracker Financial Analysis", marginLeft, 36, paint);

            // Subtitle
            paint.setColor(Color.parseColor("#94A3B8"));
            paint.setTextSize(10);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText("Period: " + payload.dateRangeLabel, marginLeft, 54, paint);

            String timestamp = "Generated: " + new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
            float tsWidth = paint.measureText(timestamp);
            canvas.drawText(timestamp, marginRight - tsWidth, 54, paint);

            y = 100;
        }

        @Override
        public void drawSectionTitle(String title) {
            ensureSpace(32);
            paint.setColor(Color.parseColor("#1E293B"));
            paint.setTextSize(13);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(title, marginLeft, y + 14, paint);

            paint.setColor(Color.parseColor("#3B82F6")); // Blue underline accent
            canvas.drawRect(marginLeft, y + 18, marginLeft + 120, y + 20, paint);

            y += 28;
        }

        @Override
        public void drawExecutiveSummaryGrid() {
            ensureSpace(120);

            float cardGap = 8;
            float cardWidth = (usableWidth - (cardGap * 2)) / 3f;
            float cardHeight = 48;

            // Row 1 Cards
            drawCard(marginLeft, y, cardWidth, cardHeight, "Total Income", formatCurrency(payload.totalIncome), "#16A34A", "#F0FDF4");
            drawCard(marginLeft + cardWidth + cardGap, y, cardWidth, cardHeight, "Total Expenses", formatCurrency(payload.totalExpense), "#DC2626", "#FEF2F2");
            drawCard(marginLeft + (cardWidth + cardGap) * 2, y, cardWidth, cardHeight, "Net Savings / Balance", formatCurrency(payload.netSavings), payload.netSavings >= 0 ? "#2563EB" : "#DC2626", "#EFF6FF");

            y += cardHeight + cardGap;

            // Row 2 Cards
            drawCard(marginLeft, y, cardWidth, cardHeight, "Total Transfers", formatCurrency(payload.totalTransfers), "#7C3AED", "#F3E8FF");
            drawCard(marginLeft + cardWidth + cardGap, y, cardWidth, cardHeight, "Account vs Card Exp", "Acct: " + formatCurrencyShort(payload.accountExpenses) + " | Card: " + formatCurrencyShort(payload.cardExpenses), "#475569", "#F8FAFC");
            drawCard(marginLeft + (cardWidth + cardGap) * 2, y, cardWidth, cardHeight, "Transfer Incoming/Outgoing", "In: " + formatCurrencyShort(payload.transferIncoming) + " | Out: " + formatCurrencyShort(payload.transferOutgoing), "#475569", "#F8FAFC");

            y += cardHeight + 16;
        }

        private void drawCard(float x, float top, float width, float height, String label, String value, String textColor, String bgColor) {
            RectF rect = new RectF(x, top, x + width, top + height);

            paint.setColor(Color.parseColor(bgColor));
            canvas.drawRoundRect(rect, 4, 4, paint);

            paint.setColor(Color.parseColor("#E2E8F0"));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.8f);
            canvas.drawRoundRect(rect, 4, 4, paint);
            paint.setStyle(Paint.Style.FILL);

            // Label
            paint.setColor(Color.parseColor("#64748B"));
            paint.setTextSize(8.5f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText(label, x + 8, top + 15, paint);

            // Value
            paint.setColor(Color.parseColor(textColor));
            paint.setTextSize(11f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(value, x + 8, top + 34, paint);
        }

        @Override
        public void drawCharts() {
            ensureSpace(160);

            float chartWidth = (usableWidth - 12) / 2f;
            float chartHeight = 140;

            // Donut Chart - Category Expense Distribution
            drawDonutChart(marginLeft, y, chartWidth, chartHeight);

            // Bar Chart - Income vs Expenses vs Transfers Comparison
            drawBarChart(marginLeft + chartWidth + 12, y, chartWidth, chartHeight);

            y += chartHeight + 20;
        }

        private void drawDonutChart(float x, float top, float width, float height) {
            RectF bg = new RectF(x, top, x + width, top + height);
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawRoundRect(bg, 6, 6, paint);

            // Title
            paint.setColor(Color.parseColor("#334155"));
            paint.setTextSize(9.5f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Expense Category Distribution", x + 10, top + 16, paint);

            if (payload.categoryBreakdown.isEmpty() || payload.totalExpense <= 0) {
                paint.setColor(Color.parseColor("#94A3B8"));
                paint.setTextSize(9f);
                canvas.drawText("No category expense data available", x + 20, top + 75, paint);
                return;
            }

            float cx = x + 55;
            float cy = top + 80;
            float radius = 42;
            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

            int[] palette = {
                Color.parseColor("#EF4444"), Color.parseColor("#F59E0B"), Color.parseColor("#10B981"),
                Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899")
            };

            float startAngle = -90;
            int idx = 0;
            for (CategorySummaryItem item : payload.categoryBreakdown) {
                if (!"EXPENSE".equalsIgnoreCase(item.type) || item.amount <= 0) continue;
                float sweep = (float) ((item.amount / payload.totalExpense) * 360.0);
                paint.setColor(palette[idx % palette.length]);
                canvas.drawArc(oval, startAngle, sweep, true, paint);

                // Draw legend item
                float legY = top + 32 + (idx * 14);
                if (legY < top + height - 10) {
                    canvas.drawRect(x + 110, legY - 7, x + 118, legY + 1, paint);
                    paint.setColor(Color.parseColor("#1E293B"));
                    paint.setTextSize(8f);
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                    String legStr = item.category + " (" + String.format(Locale.getDefault(), "%.0f%%", item.percentage) + ")";
                    if (legStr.length() > 20) legStr = legStr.substring(0, 18) + "..";
                    canvas.drawText(legStr, x + 122, legY, paint);
                }

                startAngle += sweep;
                idx++;
            }

            // Donut Center Hole
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawCircle(cx, cy, radius * 0.55f, paint);
        }

        private void drawBarChart(float x, float top, float width, float height) {
            RectF bg = new RectF(x, top, x + width, top + height);
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawRoundRect(bg, 6, 6, paint);

            // Title
            paint.setColor(Color.parseColor("#334155"));
            paint.setTextSize(9.5f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Financial Totals Comparison", x + 10, top + 16, paint);

            double maxVal = Math.max(payload.totalIncome, Math.max(payload.totalExpense, payload.totalTransfers));
            if (maxVal <= 0) maxVal = 1.0;

            float barWidth = 32;
            float maxBarHeight = 70;
            float baseY = top + 115;

            // Bar 1: Income
            drawSingleBar(x + 25, baseY, barWidth, maxBarHeight, payload.totalIncome, maxVal, "Income", "#16A34A");

            // Bar 2: Expenses
            drawSingleBar(x + 85, baseY, barWidth, maxBarHeight, payload.totalExpense, maxVal, "Expense", "#DC2626");

            // Bar 3: Transfers
            drawSingleBar(x + 145, baseY, barWidth, maxBarHeight, payload.totalTransfers, maxVal, "Transfer", "#7C3AED");
        }

        private void drawSingleBar(float x, float baseY, float width, float maxHeight, double val, double maxVal, String label, String colorHex) {
            float barH = (float) ((val / maxVal) * maxHeight);
            if (barH < 3 && val > 0) barH = 3;

            paint.setColor(Color.parseColor(colorHex));
            canvas.drawRect(x, baseY - barH, x + width, baseY, paint);

            // Value text above bar
            paint.setTextSize(7.5f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            String valStr = formatCurrencyShort(val);
            float vW = paint.measureText(valStr);
            canvas.drawText(valStr, x + (width - vW) / 2f, baseY - barH - 4, paint);

            // Label text below bar
            paint.setColor(Color.parseColor("#64748B"));
            paint.setTextSize(8f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            float lW = paint.measureText(label);
            canvas.drawText(label, x + (width - lW) / 2f, baseY + 12, paint);
        }

        @Override
        public void drawGroupedTables() {
            ensureSpace(120);

            // Category Table
            drawTableHeader("Category Breakdown", new String[]{"Category", "Type", "Txns", "Total Amount", "% of Total"}, new float[]{150, 70, 50, 130, 123});

            int count = 0;
            for (CategorySummaryItem item : payload.categoryBreakdown) {
                ensureSpace(18);
                boolean alt = (count % 2 == 1);
                if (alt) {
                    paint.setColor(Color.parseColor("#F8FAFC"));
                    canvas.drawRect(marginLeft, y, marginRight, y + 16, paint);
                }

                paint.setColor(Color.parseColor("#1E293B"));
                paint.setTextSize(8.5f);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

                canvas.drawText(truncate(item.category, 22), marginLeft + 4, y + 12, paint);

                // Type badge/text
                paint.setColor("INCOME".equalsIgnoreCase(item.type) ? Color.parseColor("#16A34A") : Color.parseColor("#DC2626"));
                canvas.drawText(item.type, marginLeft + 154, y + 12, paint);

                paint.setColor(Color.parseColor("#1E293B"));
                canvas.drawText(String.valueOf(item.count), marginLeft + 224, y + 12, paint);
                canvas.drawText(formatCurrency(item.amount), marginLeft + 274, y + 12, paint);
                canvas.drawText(String.format(Locale.getDefault(), "%.1f%%", item.percentage), marginLeft + 404, y + 12, paint);

                y += 16;
                count++;
            }

            y += 12;

            // Bank Breakdown Table
            if (!payload.bankBreakdown.isEmpty()) {
                ensureSpace(80);
                drawTableHeader("Bank & Institution Totals (Excludes Transfers)", new String[]{"Bank Name", "Transactions", "Total Expense / Activity"}, new float[]{220, 100, 203});
                int bCount = 0;
                for (BankSummaryItem item : payload.bankBreakdown) {
                    ensureSpace(18);
                    if (bCount % 2 == 1) {
                        paint.setColor(Color.parseColor("#F8FAFC"));
                        canvas.drawRect(marginLeft, y, marginRight, y + 16, paint);
                    }
                    paint.setColor(Color.parseColor("#1E293B"));
                    paint.setTextSize(8.5f);
                    canvas.drawText(truncate(item.bankName, 30), marginLeft + 4, y + 12, paint);
                    canvas.drawText(String.valueOf(item.count), marginLeft + 224, y + 12, paint);
                    canvas.drawText(formatCurrency(item.amount), marginLeft + 324, y + 12, paint);
                    y += 16;
                    bCount++;
                }
                y += 12;
            }

            // Source Type Breakdown Table
            if (!payload.sourceTypeBreakdown.isEmpty()) {
                ensureSpace(80);
                drawTableHeader("Account & Source Type Totals (Excludes Transfers)", new String[]{"Source Type", "Transactions", "Total Amount"}, new float[]{220, 100, 203});
                int sCount = 0;
                for (SourceTypeSummaryItem item : payload.sourceTypeBreakdown) {
                    ensureSpace(18);
                    if (sCount % 2 == 1) {
                        paint.setColor(Color.parseColor("#F8FAFC"));
                        canvas.drawRect(marginLeft, y, marginRight, y + 16, paint);
                    }
                    paint.setColor(Color.parseColor("#1E293B"));
                    paint.setTextSize(8.5f);
                    canvas.drawText(truncate(item.sourceType, 30), marginLeft + 4, y + 12, paint);
                    canvas.drawText(String.valueOf(item.count), marginLeft + 224, y + 12, paint);
                    canvas.drawText(formatCurrency(item.amount), marginLeft + 324, y + 12, paint);
                    y += 16;
                    sCount++;
                }
                y += 16;
            }
        }

        private void drawTableHeader(String title, String[] headers, float[] colWidths) {
            paint.setColor(Color.parseColor("#334155"));
            paint.setTextSize(9.5f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(title, marginLeft, y + 12, paint);
            y += 18;

            paint.setColor(Color.parseColor("#0F172A")); // Dark Header Row
            canvas.drawRect(marginLeft, y, marginRight, y + 18, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(8f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            float curX = marginLeft + 4;
            for (int i = 0; i < headers.length; i++) {
                canvas.drawText(headers[i], curX, y + 12, paint);
                curX += colWidths[i];
            }
            y += 18;
        }

        @Override
        public void drawTransactionsTable() {
            ensureSpace(60);

            // Columns: Date & Time (100pt), Category (80pt), Description/Contact (140pt), Source (83pt), Type (50pt), Amount (70pt)
            drawTableHeader("Transaction Records (" + payload.transactions.size() + " Total)",
                new String[]{"Date & Time", "Category", "Description / Contact", "Source", "Type", "Amount"},
                new float[]{95, 80, 140, 85, 55, 68});

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault());
            int rowIdx = 0;

            for (Transaction t : payload.transactions) {
                ensureSpace(20);

                if (rowIdx % 2 == 1) {
                    paint.setColor(Color.parseColor("#F8FAFC"));
                    canvas.drawRect(marginLeft, y, marginRight, y + 18, paint);
                }

                paint.setTextSize(7.5f);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setColor(Color.parseColor("#1E293B"));

                // Date
                canvas.drawText(sdf.format(new Date(t.getDate())), marginLeft + 2, y + 12, paint);

                // Category
                canvas.drawText(truncate(t.getCategory(), 14), marginLeft + 97, y + 12, paint);

                // Description / Contact
                String contact = "INCOME".equalsIgnoreCase(t.getType()) ? t.getSender() : t.getReceiverName();
                String desc = (t.getDescription() != null && !t.getDescription().isEmpty()) ? t.getDescription() : contact;
                canvas.drawText(truncate(desc, 25), marginLeft + 177, y + 12, paint);

                // Source
                canvas.drawText(truncate(t.getSource(), 15), marginLeft + 317, y + 12, paint);

                // Type Badge
                drawTypeBadge(marginLeft + 402, y + 2, t.getType());

                // Amount
                paint.setColor(Color.parseColor("#0F172A"));
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(formatCurrency(t.getAmount()), marginLeft + 457, y + 12, paint);

                y += 18;
                rowIdx++;
            }
        }

        private void drawTypeBadge(float x, float top, String rawType) {
            String type = rawType != null ? rawType.toUpperCase() : "EXPENSE";
            String bgHex = "#FEE2E2"; // Light red
            String textHex = "#B91C1C"; // Dark red

            if ("INCOME".equals(type)) {
                bgHex = "#DCFCE7"; // Light green
                textHex = "#15803D";
            } else if ("TRANSFER".equals(type) || "TRANSFER".equalsIgnoreCase(type)) {
                bgHex = "#F3E8FF"; // Light purple
                textHex = "#6B21A8";
            }

            RectF badge = new RectF(x, top, x + 48, top + 13);
            paint.setColor(Color.parseColor(bgHex));
            canvas.drawRoundRect(badge, 3, 3, paint);

            paint.setColor(Color.parseColor(textHex));
            paint.setTextSize(7f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            float textW = paint.measureText(type);
            canvas.drawText(type, x + (48 - textW) / 2f, top + 9.5f, paint);
        }

        @Override
        public void finishDocument() {
            drawPageFooter();
            document.finishPage(currentPage);
        }
    }

    // ── Helper Formatter Functions ───────────────────────────────────────────

    private static String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "₹ %.2f", amount);
    }

    private static String formatCurrencyShort(double amount) {
        if (amount >= 100000) {
            return String.format(Locale.getDefault(), "₹ %.1fL", amount / 100000.0);
        } else if (amount >= 1000) {
            return String.format(Locale.getDefault(), "₹ %.1fK", amount / 1000.0);
        } else {
            return String.format(Locale.getDefault(), "₹ %.0f", amount);
        }
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "N/A";
        String clean = str.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "").trim(); // Strip emojis for PDF
        if (clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen - 2) + "..";
    }
}
