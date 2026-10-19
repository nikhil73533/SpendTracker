package com.example.spendtracker.util;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;
public class ExcelExportWriterTest {
    private Transaction entry(String date, double amount) {
        return new Transaction(0, amount, "Food", "", "private-note", "EXPENSE",
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                "Private Bank", "Sender", "secret@upi", "Receiver", "Bank", "Account", "", "", 0);
    }
    private Map<String,String> workbook(boolean masked) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelExportWriter.write(output, Arrays.asList(entry("2025-12-31", 123.45), entry("2026-01-01", 678.90)), masked, ZoneOffset.UTC);
        Map<String,String> files = new HashMap<>();
        File file = File.createTempFile("spendtracker-export-test", ".xlsx");
        java.nio.file.Files.write(file.toPath(), output.toByteArray());
        // Read the ZIP64 central directory, as spreadsheet readers do.
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try (InputStream input = zip.getInputStream(entry)) {
                    files.put(entry.getName(), new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } finally { file.delete(); }
        return files;
    }
    @Test public void exportsEveryMonthAndViewAcrossYears() throws Exception {
        Map<String,String> files=workbook(false);
        String xml=files.get("xl/workbook.xml");
        for(String sheet:new String[]{"Views", "Transactions", "2025-12", "2026-01", "Monthly", "Yearly", "Transaction groups"})
            assertTrue(sheet, xml.contains("name=\""+sheet+"\""));
        String all=files.values().toString(); assertTrue(all.contains("123.45")); assertTrue(all.contains("678.9"));
    }
    @Test public void masksEverySheetIncludingSummaries() throws Exception {
        String all=workbook(true).values().toString();
        for(String secret:new String[]{"123.45", "678.9", "private-note", "secret@upi", "Private Bank"})
            assertFalse(secret, all.contains(secret));
        assertTrue(all.contains("***"));
    }
}
