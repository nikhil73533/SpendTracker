package com.example.spendtracker.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfReportService {

    public static File generateReport(Context context, DashboardViewModel.TotalPageData data, List<Transaction> transactions, Bitmap chartBitmap) throws Exception {
        PDFBoxResourceLoader.init(context);

        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        // Draw Header
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 24);
        contentStream.newLineAtOffset(50, 720);
        contentStream.showText("SpendTracker Financial Report");
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.newLineAtOffset(50, 700);
        contentStream.showText("Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        contentStream.endText();

        int yPosition = 650;

        // Draw Summary Section
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
        contentStream.newLineAtOffset(50, yPosition);
        contentStream.showText("Executive Summary");
        contentStream.endText();
        
        yPosition -= 20;
        
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.newLineAtOffset(50, yPosition);
        contentStream.showText("Total Income: INR " + String.format(Locale.getDefault(), "%.2f", data.income));
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Account Expenses: INR " + String.format(Locale.getDefault(), "%.2f", data.accountExpenses));
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Card Expenses: INR " + String.format(Locale.getDefault(), "%.2f", data.cardExpenses));
        contentStream.newLineAtOffset(0, -15);
        contentStream.showText("Net Transfers: INR " + String.format(Locale.getDefault(), "%.2f", data.transfers));
        contentStream.endText();

        yPosition -= 70;

        // Draw Chart (if provided)
        if (chartBitmap != null) {
            PDImageXObject pdImage = JPEGFactory.createFromImage(document, chartBitmap);
            // Scale image to fit width (margin 50 on both sides -> max width 500)
            float scale = 500f / pdImage.getWidth();
            float height = pdImage.getHeight() * scale;
            
            if (yPosition - height < 50) {
                // Not enough space, add new page
                contentStream.close();
                page = new PDPage();
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                yPosition = 750;
            }
            
            yPosition -= height;
            contentStream.drawImage(pdImage, 50, yPosition, 500, height);
            yPosition -= 40;
        }

        // Draw Recent Transactions
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
        contentStream.newLineAtOffset(50, yPosition);
        contentStream.showText("Recent Transactions (up to 20)");
        contentStream.endText();

        yPosition -= 20;

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.newLineAtOffset(50, yPosition);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
        int count = 0;
        for (Transaction t : transactions) {
            if (count >= 20) break;
            
            if (yPosition < 50) {
                contentStream.endText();
                contentStream.close();
                
                page = new PDPage();
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                yPosition = 750;
                contentStream.newLineAtOffset(50, yPosition);
            }
            
            String desc = t.getDescription() != null ? t.getDescription() : "N/A";
            if (desc.length() > 30) desc = desc.substring(0, 27) + "...";
            
            String line = String.format("%-10s | %-15s | %-30s | INR %.2f", 
                    sdf.format(new Date(t.getDate())), 
                    t.getCategory(), 
                    desc, 
                    t.getAmount());
            
            // Fix special characters for PDFBox standard fonts
            line = line.replace('\n', ' ').replace('\r', ' ');
            
            contentStream.showText(line);
            contentStream.newLineAtOffset(0, -15);
            yPosition -= 15;
            count++;
        }
        contentStream.endText();
        contentStream.close();

        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SpendTracker_Report.pdf");
        document.save(file);
        document.close();

        return file;
    }
}
