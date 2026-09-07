package com.example.spendtracker.ui.pdfimport;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Review adapter: every extracted row is opt-in and can be corrected before persistence. */
public class PdfIngestionReviewAdapter extends RecyclerView.Adapter<PdfIngestionReviewAdapter.ViewHolder> {
    public interface OnDeleteListener {
        void onDelete(Transaction transaction);
    }

    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Boolean> selected = new ArrayList<>();
    private final OnDeleteListener onDeleteListener;

    public PdfIngestionReviewAdapter(OnDeleteListener onDeleteListener) {
        this.onDeleteListener = onDeleteListener;
    }

    public void submit(List<Transaction> items) {
        Map<Transaction, Boolean> previousSelections = new IdentityHashMap<>();
        for (int i = 0; i < transactions.size(); i++) {
            previousSelections.put(transactions.get(i), selected.get(i));
        }
        transactions.clear();
        selected.clear();
        if (items != null) {
            transactions.addAll(items);
            for (Transaction item : items) {
                Boolean previous = previousSelections.get(item);
                selected.add(previous == null || previous);
            }
        }
        notifyDataSetChanged();
    }

    public List<Transaction> getSelectedTransactions() {
        List<Transaction> result = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++) if (selected.get(i)) result.add(transactions.get(i));
        return result;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_review_transaction, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(position); }
    @Override public int getItemCount() { return transactions.size(); }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox selectedBox;
        private final TextView title;
        private final TextView detail;
        private final TextView edit;
        private final TextView delete;

        ViewHolder(View view) {
            super(view);
            selectedBox = view.findViewById(R.id.check_transaction);
            title = view.findViewById(R.id.tv_review_title);
            detail = view.findViewById(R.id.tv_review_detail);
            edit = view.findViewById(R.id.tv_review_edit);
            delete = view.findViewById(R.id.tv_review_delete);
        }

        void bind(int position) {
            Transaction transaction = transactions.get(position);
            selectedBox.setOnCheckedChangeListener(null);
            selectedBox.setChecked(selected.get(position));
            selectedBox.setOnCheckedChangeListener((button, checked) -> {
                int adapterPosition = getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) selected.set(adapterPosition, checked);
            });
            String counterpartyName = transaction.getReceiverName().isEmpty() ? transaction.getSender() : transaction.getReceiverName();
            title.setText(counterpartyName.isEmpty() ? transaction.getDescription() : counterpartyName);
            String date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(new Date(transaction.getDate()));
            String time = "DATE_ONLY".equals(transaction.getTimestampPrecision()) ? "time unavailable" :
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date(transaction.getDate()));
            String typeLabel = "INCOME".equals(transaction.getType())
                    ? "Income • Credit/Deposit" : "Expense • Debit/Withdrawal";
            detail.setText(String.format(Locale.getDefault(), "%s • ₹%.2f • %s • %s",
                    typeLabel, transaction.getAmount(), date, time));
            edit.setOnClickListener(v -> {
                int adapterPosition = getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) showEditDialog(transaction, adapterPosition);
            });
            delete.setOnClickListener(v -> new AlertDialog.Builder(itemView.getContext())
                    .setTitle("Remove transaction?")
                    .setMessage("This removes the transaction from this import preview.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        int adapterPosition = getAdapterPosition();
                        if (adapterPosition != RecyclerView.NO_POSITION && onDeleteListener != null) {
                            onDeleteListener.onDelete(transactions.get(adapterPosition));
                        }
                    }).show());
            itemView.setOnClickListener(v -> selectedBox.setChecked(!selectedBox.isChecked()));
        }

        private void showEditDialog(Transaction transaction, int position) {
            LinearLayout layout = new LinearLayout(itemView.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * itemView.getResources().getDisplayMetrics().density);
            layout.setPadding(padding, 0, padding, 0);
            EditText counterparty = new EditText(itemView.getContext());
            counterparty.setHint("Counterparty");
            counterparty.setText(transaction.getReceiverName().isEmpty() ? transaction.getSender() : transaction.getReceiverName());
            EditText amount = new EditText(itemView.getContext());
            amount.setHint("Amount");
            amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amount.setText(String.format(Locale.ROOT, "%.2f", transaction.getAmount()));
            EditText transactionDate = new EditText(itemView.getContext());
            transactionDate.setHint("Transaction date");
            transactionDate.setFocusable(false);
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.setTimeInMillis(transaction.getDate());
            transactionDate.setText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                    .format(selectedDate.getTime()));
            transactionDate.setOnClickListener(v -> new DatePickerDialog(itemView.getContext(),
                    (picker, year, month, day) -> {
                        selectedDate.set(Calendar.YEAR, year);
                        selectedDate.set(Calendar.MONTH, month);
                        selectedDate.set(Calendar.DAY_OF_MONTH, day);
                        transactionDate.setText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                                .format(selectedDate.getTime()));
                    }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)).show());
            Spinner type = new Spinner(itemView.getContext());
            String[] typeLabels = {"Expense (Debit / Withdrawal)", "Income (Credit / Deposit)"};
            type.setAdapter(new ArrayAdapter<>(itemView.getContext(),
                    android.R.layout.simple_spinner_dropdown_item, typeLabels));
            type.setSelection("INCOME".equals(transaction.getType()) ? 1 : 0);
            layout.addView(counterparty);
            layout.addView(amount);
            layout.addView(transactionDate);
            layout.addView(type);
            new AlertDialog.Builder(itemView.getContext())
                    .setTitle("Edit extracted transaction")
                    .setView(layout)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String name = counterparty.getText().toString().trim();
                        try {
                            transaction.setAmount(Double.parseDouble(amount.getText().toString().trim()));
                            transaction.setDate(selectedDate.getTimeInMillis());
                            boolean income = type.getSelectedItemPosition() == 1;
                            transaction.setType(income ? "INCOME" : "EXPENSE");
                            transaction.setDirection(income ? "CREDIT" : "DEBIT");
                            transaction.setSender(income ? name : "");
                            transaction.setReceiverName(income ? "" : name);
                            notifyItemChanged(position);
                        } catch (NumberFormatException ignored) { }
                    }).show();
        }
    }
}
