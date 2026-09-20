package com.example.spendtracker.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.example.spendtracker.domain.model.Transaction;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class TransactionReceipt {
    private TransactionReceipt() {}
    private static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();
    public static void choose(Context context, List<Transaction> transactions, java.util.function.Function<Double, String> amountFormatter) {
        if (transactions == null || transactions.isEmpty()) {
            Toast.makeText(context, "No transactions to share in this period", Toast.LENGTH_SHORT).show(); return;
        }
        String[] labels = new String[transactions.size()];
        SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        for (int i = 0; i < labels.length; i++) {
            Transaction t = transactions.get(i);
            labels[i] = date.format(new Date(t.getDate())) + " · " + t.getCategory() + " · " + amountFormatter.apply(t.getAmount());
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Share transaction receipt")
                .setItems(labels, (dialog, which) -> share(context, transactions.get(which))).setNegativeButton("Cancel", null).show();
    }
    public static void share(Context context, Transaction transaction) {
        UserProfile profile = UserProfile.load(context);
        EXECUTOR.execute(() -> {
            try {
                File file = create(context, transaction, profile);
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "Transaction receipt")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(ClipData.newRawUri("Transaction receipt", uri));
                    try { context.startActivity(Intent.createChooser(intent, "Share receipt")); }
                    catch (RuntimeException e) { Toast.makeText(context, "No app available to share PDF", Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception error) {
                android.util.Log.e("Receipt", "Could not generate receipt", error);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Could not create receipt. Please try again.", Toast.LENGTH_LONG).show());
            }
        });
    }
    static File create(Context context, Transaction t, UserProfile profile) throws java.io.IOException {
        File directory = new File(context.getCacheDir(), "receipts");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Receipt directory unavailable");
        File file = File.createTempFile("receipt-" + t.getId() + "-", ".pdf", directory);
        PdfDocument document = new PdfDocument();
        try {
            new ReceiptRenderer(document, t, profile).render();
            try (FileOutputStream output = new FileOutputStream(file)) { document.writeTo(output); }
        } finally { document.close(); }
        return file;
    }

    /** Canvas renderer kept separate so every receipt uses the same hierarchy and pagination. */
    private static final class ReceiptRenderer {
        private static final int PAGE_WIDTH = 595;
        private static final int PAGE_HEIGHT = 842;
        private static final int LEFT = 44;
        private static final int RIGHT = 551;
        private final PdfDocument document;
        private final Transaction transaction;
        private final UserProfile profile;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNumber = 1;
        private float y;

        ReceiptRenderer(PdfDocument document, Transaction transaction, UserProfile profile) {
            this.document = document;
            this.transaction = transaction;
            this.profile = profile;
        }

        void render() {
            startPage();
            drawHeader();
            drawAmountCard();
            drawSection("Transaction details");
            drawDetail("Category", valueOrDash(transaction.getCategory()));
            drawDetail("Type", transactionTypeLabel());
            String datePattern = "DATE_ONLY".equals(transaction.getTimestampPrecision())
                    ? "dd MMM yyyy" : "dd MMM yyyy, hh:mm a";
            drawDetail("Date", new SimpleDateFormat(datePattern, Locale.getDefault()).format(new Date(transaction.getDate())));
            if ("SMS_RECEIVED".equals(transaction.getTimestampPrecision())) {
                drawDetail("Time source", "SMS received time");
            }
            drawDetailIfPresent("From", transaction.getSender());
            drawDetailIfPresent("To", transaction.getReceiverName());
            drawDetailIfPresent("Bank", transaction.getBankName());
            drawDetailIfPresent("Payment source", transaction.getSource());
            drawDetailIfPresent("From account", transaction.getFromAccount());
            drawDetailIfPresent("To account", transaction.getToAccount());
            drawDetailIfPresent("UPI ID", transaction.getUpiId());
            drawDetailIfPresent("Reference", transaction.getReferenceNumber());
            if (transaction.getFees() != 0) drawDetail("Fees", currency(transaction.getFees()));
            drawDetailIfPresent("Description", transaction.getDescription());

            if (profile != null && hasProfileDetails()) {
                drawSection("Profile");
                drawDetailIfPresent("Name", profile.name);
                drawDetailIfPresent("Email", profile.email);
                drawDetailIfPresent("Phone", profile.phone);
            }
            drawFooter();
            document.finishPage(page);
        }

        private void startPage() {
            page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
            canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            y = 38;
        }

        private void newPage() {
            drawFooter();
            document.finishPage(page);
            pageNumber++;
            startPage();
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawRect(0, 0, PAGE_WIDTH, 34, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(10);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("SpendTracker receipt", LEFT, 22, paint);
            y = 54;
        }

        private void ensure(float height) {
            // Leave a footer gap but keep normal profile fields together on the first page.
            if (y + height > 790) newPage();
        }

        private void drawHeader() {
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + 80), 12, 12, paint);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(22);
            canvas.drawText("Transaction receipt", LEFT + 20, y + 31, paint);
            paint.setColor(Color.rgb(203, 213, 225));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10);
            canvas.drawText("Personal transaction record", LEFT + 20, y + 51, paint);
            String generated = "Generated " + new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
            canvas.drawText(generated, LEFT + 20, y + 68, paint);
            y += 100;
        }

        private void drawAmountCard() {
            ensure(104);
            String type = transactionTypeLabel();
            boolean income = "INCOME".equalsIgnoreCase(transaction.getType()) || TransferDirection.isIncoming(transaction);
            int color = income ? Color.rgb(22, 163, 74) : "TRANSFER".equalsIgnoreCase(transaction.getType())
                    ? Color.rgb(124, 58, 237) : Color.rgb(220, 38, 38);
            paint.setColor(Color.rgb(248, 250, 252));
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + 88), 10, 10, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(Color.rgb(226, 232, 240));
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + 88), 10, 10, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(100, 116, 139));
            paint.setTextSize(10);
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("AMOUNT", LEFT + 18, y + 24, paint);
            paint.setColor(Color.rgb(15, 23, 42));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(25);
            canvas.drawText(currency(transaction.getAmount()), LEFT + 18, y + 58, paint);
            paint.setColor(color);
            paint.setTextSize(10);
            float badgeWidth = paint.measureText(type) + 22;
            float badgeLeft = RIGHT - badgeWidth - 16;
            canvas.drawRoundRect(new RectF(badgeLeft, y + 18, RIGHT - 16, y + 42), 12, 12, paint);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float labelWidth = paint.measureText(type);
            canvas.drawText(type, badgeLeft + (badgeWidth - labelWidth) / 2f, y + 34, paint);
            y += 108;
        }

        private void drawSection(String title) {
            ensure(34);
            paint.setColor(Color.rgb(30, 41, 59));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(13);
            canvas.drawText(title, LEFT, y + 14, paint);
            paint.setColor(Color.rgb(59, 130, 246));
            canvas.drawRect(LEFT, y + 21, LEFT + 64, y + 23, paint);
            y += 34;
        }

        private void drawDetailIfPresent(String label, String value) {
            if (value != null && !value.trim().isEmpty()) drawDetail(label, value);
        }

        private void drawDetail(String label, String value) {
            String clean = valueOrDash(value).replaceAll("[\\r\\n\\t]+", " ");
            paint.setTextSize(11);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float labelWidth = Math.max(112, paint.measureText(label) + 16);
            paint.setTypeface(Typeface.DEFAULT);
            int lines = lineCount(clean, RIGHT - LEFT - labelWidth - 12);
            ensure(Math.max(28, lines * 16 + 12));
            paint.setColor(Color.rgb(100, 116, 139));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(label, LEFT + 4, y + 15, paint);
            paint.setColor(Color.rgb(30, 41, 59));
            paint.setTypeface(Typeface.DEFAULT);
            float textY = y + 15;
            String remaining = clean;
            while (!remaining.isEmpty()) {
                int count = paint.breakText(remaining, true, RIGHT - LEFT - labelWidth - 12, null);
                if (count <= 0) count = 1;
                canvas.drawText(remaining.substring(0, count).trim(), LEFT + labelWidth, textY, paint);
                remaining = remaining.substring(count).trim();
                textY += 16;
            }
            y = textY + 7;
            paint.setColor(Color.rgb(241, 245, 249));
            canvas.drawLine(LEFT, y, RIGHT, y, paint);
            y += 6;
        }

        private int lineCount(String value, float width) {
            paint.setTextSize(11);
            paint.setTypeface(Typeface.DEFAULT);
            int count = 0;
            String remaining = value;
            while (!remaining.isEmpty()) {
                int chars = paint.breakText(remaining, true, width, null);
                if (chars <= 0) chars = 1;
                remaining = remaining.substring(chars).trim();
                count++;
            }
            return Math.max(1, count);
        }

        private boolean hasProfileDetails() {
            return profile.name != null && !profile.name.trim().isEmpty()
                    || profile.email != null && !profile.email.trim().isEmpty()
                    || profile.phone != null && !profile.phone.trim().isEmpty();
        }

        private String transactionTypeLabel() {
            if ("TRANSFER".equalsIgnoreCase(transaction.getType())) {
                return TransferDirection.isIncoming(transaction) ? "Incoming transfer" : "Outgoing transfer";
            }
            String type = valueOrDash(transaction.getType());
            return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1).toLowerCase(Locale.ROOT);
        }

        private void drawFooter() {
            paint.setColor(Color.rgb(203, 213, 225));
            canvas.drawLine(LEFT, 800, RIGHT, 800, paint);
            paint.setColor(Color.rgb(100, 116, 139));
            paint.setTextSize(8.5f);
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Generated from your saved transaction. This is not a bank-issued receipt.", LEFT, 818, paint);
            String pageLabel = "Page " + pageNumber;
            canvas.drawText(pageLabel, RIGHT - paint.measureText(pageLabel), 818, paint);
        }

        private String valueOrDash(String value) { return value == null || value.trim().isEmpty() ? "-" : value.trim(); }
        private String currency(double value) { return "INR " + String.format(Locale.ENGLISH, "%,.2f", value); }
    }
}
