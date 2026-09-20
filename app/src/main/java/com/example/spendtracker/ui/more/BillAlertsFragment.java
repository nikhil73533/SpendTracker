package com.example.spendtracker.ui.more;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.*;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.*;
import com.example.spendtracker.R;
import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;
import com.example.spendtracker.data.sms.*;
import com.example.spendtracker.util.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@AndroidEntryPoint
public class BillAlertsFragment extends Fragment {
    @Inject BillAlertDao dao;
    @Inject AlertParsingService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View root;
    private final BillsAdapter adapter = new BillsAdapter();
    private final DateTimeFormatter dates = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.getDefault());
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refresh());
    private final ActivityResultLauncher<String> smsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { if (granted) scanSms(); else toast("SMS permission is needed to scan. You can still paste a message."); });
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 30_000); }
    };

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_bill_alerts, container, false);
        return root;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ((MaterialToolbar) view.findViewById(R.id.toolbar)).setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        RecyclerView list = view.findViewById(R.id.rv_bill_alerts);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        view.findViewById(R.id.btn_add_bill).setOnClickListener(v -> pasteMessage());
        view.findViewById(R.id.btn_scan_bills).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
                smsPermission.launch(Manifest.permission.READ_SMS);
            else scanSms();
        });
        view.findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !requireContext().getSharedPreferences("bill_ui", 0).getBoolean("permission_requested", false)) {
                requireContext().getSharedPreferences("bill_ui", 0).edit().putBoolean("permission_requested", true).apply();
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                boolean appEnabled = androidx.core.app.NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
                Intent settings = new Intent(appEnabled ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                        : Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                if (appEnabled) settings.putExtra(Settings.EXTRA_CHANNEL_ID, AppNotifications.BILLS);
                startActivity(settings);
            }
        });
        dao.getActiveAlerts().observe(getViewLifecycleOwner(), bills -> {
            adapter.bills = bills == null ? new ArrayList<>() : new ArrayList<>(bills);
            adapter.bills.sort(Comparator.comparingLong(b -> b.dueEpochDay));
            view.findViewById(R.id.tv_empty).setVisibility(adapter.bills.isEmpty() ? View.VISIBLE : View.GONE);
            refresh();
        });
    }

    @Override public void onResume() {
        super.onResume();
        BillReminderWorker.checkNow(requireContext());
        handler.post(ticker);
    }
    @Override public void onPause() { handler.removeCallbacks(ticker); super.onPause(); }
    @Override public void onDestroyView() { handler.removeCallbacks(ticker); root = null; super.onDestroyView(); }
    @Override public void onDestroy() { executor.shutdown(); super.onDestroy(); }

    private void refresh() {
        if (root == null) return;
        boolean enabled = AppNotifications.enabled(requireContext(), AppNotifications.BILLS);
        ((Button) root.findViewById(R.id.btn_notifications)).setText(enabled
                ? "Notifications enabled · Settings" : "Notifications blocked · Enable");
        adapter.notifyDataSetChanged();
    }
    private void toast(String message) { if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }

    private void scanSms() {
        Context context = requireContext().getApplicationContext();
        root.findViewById(R.id.btn_scan_bills).setEnabled(false);
        executor.execute(() -> {
            String result;
            int count = 0;
            long since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90);
            try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{"address", "body", "date"}, "date >= ?", new String[]{Long.toString(since)}, "date ASC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    String body = cursor.getString(1);
                    long timestamp = cursor.getLong(2);
                    if (new BillMessageParser().parse(body, timestamp, ZoneId.systemDefault()).isBill) {
                        service.processMessage(cursor.getString(0), body, timestamp);
                        count++;
                    }
                }
                result = count + " bill message(s) checked. Review extracted amounts and dates.";
            } catch (Exception e) { result = "Could not scan SMS. Check SMS permission and try again."; }
            String message = result;
            handler.post(() -> {
                if (root != null) { root.findViewById(R.id.btn_scan_bills).setEnabled(true); toast(message); }
            });
        });
    }

    private void pasteMessage() {
        EditText input = new EditText(requireContext());
        input.setHint("Paste the bill or payment-due message");
        input.setMinLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Add bill from message")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Extract & review", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            if (body.isEmpty()) { input.setError("Paste a message first"); return; }
            BillMessageParser.Result parsed = new BillMessageParser().parse(body, System.currentTimeMillis(), ZoneId.systemDefault());
            BillAlertEntity bill = new BillAlertEntity();
            bill.sender = parsed.biller;
            bill.lastMessage = body;
            bill.amount = parsed.amount;
            bill.dueEpochDay = parsed.dueDate == null ? 0 : parsed.dueDate.toEpochDay();
            bill.dueMinuteOfDay = parsed.dueMinuteOfDay;
            dialog.dismiss();
            review(bill);
        }));
        dialog.show();
    }

    private EditText field(LinearLayout layout, String hint, String value, int type) {
        EditText input = new EditText(requireContext());
        input.setHint(hint); input.setInputType(type); input.setText(value); layout.addView(input);
        return input;
    }

    private void review(BillAlertEntity bill) {
        LinearLayout fields = new LinearLayout(requireContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding / 2, padding, padding / 2);
        TextView source = new TextView(requireContext());
        source.setText(bill.lastMessage);
        fields.addView(source);
        EditText name = field(fields, "Biller / sender name", bill.sender, InputType.TYPE_CLASS_TEXT);
        EditText amount = field(fields, "Amount due (₹)", bill.amount > 0 ? String.format(Locale.US, "%.2f", bill.amount) : "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final LocalDate[] selected = {bill.dueEpochDay == 0 ? null : LocalDate.ofEpochDay(bill.dueEpochDay)};
        final int[] dueMinute = {bill.dueMinuteOfDay};
        Button dateButton = new Button(requireContext());
        dateButton.setText(selected[0] == null ? "Choose due date (required)" : "Due " + dates.format(selected[0]));
        dateButton.setOnClickListener(v -> {
            LocalDate initial = selected[0] == null ? LocalDate.now() : selected[0];
            new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
                selected[0] = LocalDate.of(year, month + 1, day);
                dateButton.setText("Due " + dates.format(selected[0]));
            }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
        });
        fields.addView(dateButton);
        Button timeButton = new Button(requireContext());
        timeButton.setText(dueMinute[0] < 0 ? "Choose due time (optional)" : dueTimeLabel(dueMinute[0]));
        timeButton.setOnClickListener(v -> {
            int initialHour = dueMinute[0] < 0 ? 9 : dueMinute[0] / 60;
            int initialMinute = dueMinute[0] < 0 ? 0 : dueMinute[0] % 60;
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                dueMinute[0] = hour * 60 + minute;
                timeButton.setText(dueTimeLabel(dueMinute[0]));
            }, initialHour, initialMinute, false).show();
        });
        fields.addView(timeButton);
        ScrollView scroll = new ScrollView(requireContext()); scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Review bill details")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save reminder", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String sender = name.getText().toString().trim();
            if (sender.isEmpty()) { name.setError("Enter a biller name"); return; }
            double value;
            try { value = Double.parseDouble(amount.getText().toString()); }
            catch (NumberFormatException e) { amount.setError("Enter a valid amount"); return; }
            if (!Double.isFinite(value) || value <= 0) { amount.setError("Amount must be greater than zero"); return; }
            if (selected[0] == null) { toast("Choose the due date first"); return; }
            double confirmed = value;
            LocalDate due = selected[0];
            int confirmedTime = dueMinute[0];
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            executor.execute(() -> {
                try {
                    service.saveReviewed(bill.id, sender, bill.lastMessage, confirmed, due, confirmedTime);
                    handler.post(() -> { dialog.dismiss(); if (root != null) toast(due.isBefore(LocalDate.now())
                            ? "Saved as overdue. No future reminders scheduled." : "Bill reminder saved"); });
                } catch (Exception e) {
                    handler.post(() -> { if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); toast("Could not save bill. Please try again."); });
                }
            });
        }));
        dialog.show();
    }

    private String dueTimeLabel(int minuteOfDay) {
        return "Due time " + java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()));
    }

    private void finishBill(BillAlertEntity bill, boolean delete) {
        new AlertDialog.Builder(requireContext()).setTitle(delete ? "Delete bill?" : "Mark bill paid?")
                .setMessage("Reminders for this bill will stop.")
                .setNegativeButton("Cancel", null).setPositiveButton(delete ? "Delete" : "Mark paid", (d, w) ->
                    executor.execute(() -> {
                        try { service.resolve(bill.id, delete); }
                        catch (Exception e) { handler.post(() -> toast("Could not update bill. Please try again.")); }
                    })).show();
    }

    private class BillsAdapter extends RecyclerView.Adapter<BillHolder> {
        List<BillAlertEntity> bills = new ArrayList<>();
        @NonNull @Override public BillHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new BillHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bill_alert, parent, false));
        }
        @Override public int getItemCount() { return bills.size(); }
        @Override public void onBindViewHolder(@NonNull BillHolder holder, int position) {
            BillAlertEntity bill = bills.get(position);
            View v = holder.itemView;
            ((TextView) v.findViewById(R.id.tv_bill_name)).setText(bill.sender);
            ((TextView) v.findViewById(R.id.tv_bill_amount)).setText(bill.amount > 0
                    ? String.format(Locale.getDefault(), "₹%,.2f", bill.amount) : "Amount needs review");
            ((TextView) v.findViewById(R.id.tv_bill_due)).setText(bill.dueEpochDay == 0
                    ? "Due date needs review" : "Due " + dates.format(LocalDate.ofEpochDay(bill.dueEpochDay))
                    + (bill.dueMinuteOfDay < 0 ? "" : " at " + dueTimeLabel(bill.dueMinuteOfDay).replace("Due time ", "")));
            long now = System.currentTimeMillis();
            long next = BillReminderSchedule.next(bill.dueEpochDay, bill.dueMinuteOfDay, bill.lastNotifiedAt, now, ZoneId.systemDefault());
            String status;
            boolean review = bill.dueEpochDay == 0 || bill.amount <= 0;
            if (review) status = "Review required · reminders not scheduled";
            else if (LocalDate.now().toEpochDay() > bill.dueEpochDay) status = "Overdue · mark paid or update the due date";
            else if (!AppNotifications.enabled(requireContext(), AppNotifications.BILLS)) status = "Notifications blocked · enable above";
            else if (next == 0) status = "Final reminder sent · awaiting payment";
            else if (next <= now) status = "Reminder ready · waiting for Android to run";
            else {
                long minutes = Math.max(1, (next - now) / 60_000);
                status = String.format(Locale.getDefault(), "Next reminder in %dd %dh %dm · around %s",
                        minutes / 1440, minutes / 60 % 24, minutes % 60,
                        Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a")));
            }
            ((TextView) v.findViewById(R.id.tv_bill_countdown)).setText(status);
            LinearProgressIndicator progress = v.findViewById(R.id.bill_progress);
            progress.setProgress(review ? 0 : BillReminderSchedule.progress(Math.max(bill.createdAt, bill.lastNotifiedAt), next, now));
            progress.setContentDescription(status);
            v.findViewById(R.id.btn_review).setOnClickListener(button -> review(bill));
            v.findViewById(R.id.btn_paid).setOnClickListener(button -> finishBill(bill, false));
            v.findViewById(R.id.btn_delete).setOnClickListener(button -> finishBill(bill, true));
        }
    }
    private static class BillHolder extends RecyclerView.ViewHolder {
        BillHolder(View view) { super(view); }
    }
}
