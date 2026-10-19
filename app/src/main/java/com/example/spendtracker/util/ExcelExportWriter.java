package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import java.io.OutputStream;
import java.io.IOException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Complete active ledger, with month detail and dashboard view summaries. */
public final class ExcelExportWriter {
    private ExcelExportWriter() { }
    public static void write(OutputStream output, List<Transaction> transactions, boolean masked, ZoneId zone) throws IOException {
        try (Workbook book = new Workbook(output, "SpendTracker", "1.0")) {
            Worksheet views = book.newWorksheet("Views");
            views.value(0, 0, "View"); views.value(0, 1, "Contents");
            String[][] definitions = {{"Transactions", "All months, all active transactions"},
                    {"Daily", "Daily income, expense and transfer totals"}, {"Weekly", "Week beginning Monday"},
                    {"Monthly", "All calendar months"}, {"Yearly", "All calendar years"},
                    {"Categories", "Category and transaction type totals"}, {"Accounts", "Source and transaction type totals"},
                    {"Transaction groups", "Created groups and transaction type totals"},
                    {"YYYY-MM", "One detail sheet for each month with transactions"}};
            for (int i=0;i<definitions.length;i++) { views.value(i+1,0,definitions[i][0]); views.value(i+1,1,definitions[i][1]); }
            details(book.newWorksheet("Transactions"), transactions, masked, zone);
            Map<String,List<Transaction>> months = new TreeMap<>();
            Map<String,Map<String,double[]>> summaries = new LinkedHashMap<>();
            for (String view : new String[]{"Daily", "Weekly", "Monthly", "Yearly", "Categories", "Accounts", "Transaction groups"})
                summaries.put(view, new TreeMap<>());
            for (Transaction t : transactions) {
                LocalDate date = Instant.ofEpochMilli(t.getDate()).atZone(zone).toLocalDate();
                String month = YearMonth.from(date).toString();
                months.computeIfAbsent(month, k -> new ArrayList<>()).add(t);
                add(summaries.get("Daily"), date.toString(), t);
                add(summaries.get("Weekly"), date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(), t);
                add(summaries.get("Monthly"), month, t);
                add(summaries.get("Yearly"), String.valueOf(date.getYear()), t);
                add(summaries.get("Categories"), text(t.getCategory()), t);
                add(summaries.get("Accounts"), masked ? "Hidden" : text(t.getSource()), t);
                add(summaries.get("Transaction groups"), t.getTransactionGroupName().isEmpty() ? "Ungrouped" : t.getTransactionGroupName(), t);
            }
            for (Map.Entry<String,Map<String,double[]>> view : summaries.entrySet()) {
                Worksheet sheet = book.newWorksheet(view.getKey());
                String[] columns = {"Period / Group", "Income", "Expense", "Transfer", "Net income - expense", "Transactions"};
                for (int i=0;i<columns.length;i++) sheet.value(0,i,columns[i]);
                int row=1;
                for (Map.Entry<String,double[]> entry : view.getValue().entrySet()) {
                    sheet.value(row,0,entry.getKey()); double[] totals=entry.getValue();
                    for (int col=1;col<=4;col++) {
                        if (masked) sheet.value(row,col,"***");
                        else sheet.value(row,col,col==4 ? totals[0]-totals[1] : totals[col-1]);
                    }
                    sheet.value(row,5,totals[3]); row++;
                }
            }
            for (Map.Entry<String,List<Transaction>> month : months.entrySet())
                details(book.newWorksheet(month.getKey()), month.getValue(), masked, zone);
        }
    }
    private static void add(Map<String,double[]> summary, String key, Transaction t) {
        double[] totals=summary.computeIfAbsent(key,k->new double[4]);
        int column="INCOME".equals(t.getType()) ? 0 : "TRANSFER".equals(t.getType()) ? 2 : 1;
        totals[column]+=t.getAmount(); totals[3]++;
    }
    private static String text(String value) { return value==null ? "" : value; }
    private static void details(Worksheet sheet, List<Transaction> transactions, boolean masked, ZoneId zone) {
        String[] headers={"Date", "Category", "Description", "Amount", "Type", "Source", "Sender", "Receiver", "UPI ID", "Transaction group"};
        for(int i=0;i<headers.length;i++) sheet.value(0,i,headers[i]);
        int row=1;
        for(Transaction t:transactions) {
            sheet.value(row,0,Instant.ofEpochMilli(t.getDate()).atZone(zone).toLocalDateTime().toString());
            sheet.value(row,1,text(t.getCategory())); sheet.value(row,2,masked ? "***" : text(t.getDescription()));
            if(masked) sheet.value(row,3,"***"); else sheet.value(row,3,t.getAmount());
            sheet.value(row,4,text(t.getType())); sheet.value(row,5,masked ? "***" : text(t.getSource()));
            sheet.value(row,6,masked ? "***" : text(t.getSender())); sheet.value(row,7,masked ? "***" : text(t.getReceiverName()));
            sheet.value(row,8,masked ? "***" : text(t.getUpiId())); sheet.value(row,9,t.getTransactionGroupName()); row++;
        }
    }
}
