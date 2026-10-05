package com.example.spendtracker.util;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.example.spendtracker.domain.model.Transaction;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Paged account reports. Each image page is bounded so long histories do not allocate giant bitmaps. */
public final class AccountReport {
    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor();
    private AccountReport() {}
    public static void share(Fragment fragment, String name, List<Transaction> transactions, boolean masked) {
        if (transactions == null || transactions.isEmpty()) {
            Toast.makeText(fragment.requireContext(), "No transactions to share", Toast.LENGTH_SHORT).show(); return;
        }
        List<Transaction> snapshot = new ArrayList<>(transactions);
        Context context = fragment.requireContext().getApplicationContext();
        new android.app.AlertDialog.Builder(fragment.requireContext()).setTitle("Share account transactions")
                .setItems(new String[]{"PDF", "Images"}, (dialog, option) -> WORKER.execute(() -> {
                    try {
                        ArrayList<Uri> files = generate(context, masked ? "Private account" : name, snapshot, masked, option == 1);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!fragment.isAdded() || fragment.getView() == null) return;
                            Intent intent = new Intent(files.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
                            intent.setType(option == 1 ? "image/png" : "application/pdf");
                            if (files.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, files.get(0));
                            else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                            ClipData clip = ClipData.newRawUri("Account transactions", files.get(0));
                            for (int i=1;i<files.size();i++) clip.addItem(new ClipData.Item(files.get(i)));
                            intent.setClipData(clip); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try { fragment.startActivity(Intent.createChooser(intent, "Share account transactions")); }
                            catch (ActivityNotFoundException error) { Toast.makeText(context,"No sharing app available",Toast.LENGTH_LONG).show(); }
                        });
                    } catch (Exception error) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if(fragment.isAdded()) Toast.makeText(context,"Could not create account report",Toast.LENGTH_LONG).show();
                        });
                    }
                })).show();
    }
    static ArrayList<Uri> generate(Context context, String name, List<Transaction> transactions, boolean masked, boolean images) throws IOException {
        transactions.sort(Comparator.comparingLong(Transaction::getDate).thenComparingInt(Transaction::getId));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(11);
        List<String> lines = new ArrayList<>();
        wrap(lines, name, paint);
        lines.add("");
        SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy",Locale.getDefault());
        double income=0,expense=0,transfer=0;
        for(Transaction t:transactions) { if(TransferDirection.isTransfer(t)) transfer+=t.getAmount(); else if("INCOME".equals(t.getType())) income+=t.getAmount(); else expense+=t.getAmount(); }
        wrap(lines,"Income: "+amount(income,masked)+"   Expense: "+amount(expense,masked)+"   Transfers: "+amount(transfer,masked),paint);
        lines.add(""); String lastDate="";
        for(Transaction t:transactions) {
            String day=date.format(new Date(t.getDate()));
            if(!day.equals(lastDate)) { lines.add(day); lastDate=day; }
            wrap(lines,t.getType()+"   "+amount(t.getAmount(),masked)+"   "+t.getCategory(),paint);
            if(TransferDirection.isTransfer(t)) wrap(lines,masked?"*** → ***":TransferDirection.from(t,UserProfile.load(context).name)+" → "+TransferDirection.to(t,UserProfile.load(context).name),paint);
            lines.add("");
        }
        File directory=new File(context.getCacheDir(),"receipts/account-"+UUID.randomUUID());
        if(!directory.mkdirs()) throw new IOException("Report storage unavailable");
        int perPage=40, pages=Math.max(1,(lines.size()+perPage-1)/perPage);
        ArrayList<Uri> uris=new ArrayList<>();
        if(images) {
            for(int page=0;page<pages;page++) {
                Bitmap bitmap=Bitmap.createBitmap(1190,1684,Bitmap.Config.ARGB_8888);
                try { Canvas canvas=new Canvas(bitmap); canvas.scale(2,2); draw(canvas,name,lines,page,pages,perPage);
                    File file=new File(directory,"account-"+(page+1)+".png");
                    try(FileOutputStream out=new FileOutputStream(file)) { if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("Image encoding failed"); }
                    uris.add(uri(context,file));
                } finally { bitmap.recycle(); }
            }
        } else {
            PdfDocument document=new PdfDocument();
            try {
                for(int page=0;page<pages;page++) { PdfDocument.Page sheet=document.startPage(new PdfDocument.PageInfo.Builder(595,842,page+1).create()); draw(sheet.getCanvas(),name,lines,page,pages,perPage); document.finishPage(sheet); }
                File file=new File(directory,"account.pdf"); try(FileOutputStream out=new FileOutputStream(file)) { document.writeTo(out); } uris.add(uri(context,file));
            } finally { document.close(); }
        }
        return uris;
    }
    private static String amount(double value,boolean masked) { return masked?"***":String.format(Locale.getDefault(),"INR %.2f",value); }
    private static Uri uri(Context context,File file) { return FileProvider.getUriForFile(context,context.getPackageName()+".provider",file); }
    private static void wrap(List<String> lines,String text,Paint paint) {
        String remaining=text==null?"":text.replace('\n',' ');
        while(!remaining.isEmpty()) { int count=paint.breakText(remaining,true,515,null); count=Math.max(1,count); lines.add(remaining.substring(0,count)); remaining=remaining.substring(count); }
    }
    private static void draw(Canvas canvas,String name,List<String> lines,int page,int pages,int perPage) {
        canvas.drawColor(Color.WHITE); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.rgb(30,41,59)); paint.setTextSize(17);
        canvas.drawText("Account transactions",40,40,paint); paint.setTextSize(11);
        List<String> title=new ArrayList<>(); wrap(title,name,paint);
        canvas.drawText(title.isEmpty()?"Account":title.get(0),40,62,paint);
        int start=page*perPage,end=Math.min(lines.size(),start+perPage);
        for(int i=start;i<end;i++) canvas.drawText(lines.get(i),40,94+(i-start)*17,paint);
        canvas.drawText("TrackMint · "+(page+1)+" / "+pages,40,813,paint);
    }
}
