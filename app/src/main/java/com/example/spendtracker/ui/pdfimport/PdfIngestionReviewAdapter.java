package com.example.spendtracker.ui.pdfimport;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Review adapter: every extracted row is opt-in and can be corrected before persistence. */
public class PdfIngestionReviewAdapter extends RecyclerView.Adapter<PdfIngestionReviewAdapter.ViewHolder> {
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Boolean> selected = new ArrayList<>();

    public void submit(List<Transaction> items) {
        transactions.clear();
        selected.clear();
        if (items != null) {
            transactions.addAll(items);
            for (int i = 0; i < items.size(); i++) selected.add(true);
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

        ViewHolder(View view) {
            super(view);
            selectedBox = view.findViewById(R.id.check_transaction);
            title = view.findViewById(R.id.tv_review_title);
            detail = view.findViewById(R.id.tv_review_detail);
            edit = view.findViewById(R.id.tv_review_edit);
        }

        void bind(int position) {
            Transaction transaction = transactions.get(position);
            selectedBox.setOnCheckedChangeListener(null);
            selectedBox.setChecked(selected.get(position));
            selectedBox.setOnCheckedChangeListener((button, checked) -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) selected.set(adapterPosition, checked);
            });
            String counterpartyName = transaction.getReceiverName().isEmpty() ? transaction.getSender() : transaction.getReceiverName();
            title.setText(counterpartyName.isEmpty() ? transaction.getDescription() : counterpartyName);
            String date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(new Date(transaction.getDate()));
            String time = "DATE_ONLY".equals(transaction.getTimestampPrecision()) ? "time unavailable" :
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date(transaction.getDate()));
            detail.setText(String.format(Locale.getDefault(), "%s • ₹%.2f • %s • %s", transaction.getDirection(), transaction.getAmount(), date, time));
            edit.setOnClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) showEditDialog(transaction, adapterPosition);
            });
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
            layout.addView(counterparty);
            layout.addView(amount);
            new AlertDialog.Builder(itemView.getContext())
                    .setTitle("Edit extracted transaction")
                    .setView(layout)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String name = counterparty.getText().toString().trim();
                        try {
                            transaction.setAmount(Double.parseDouble(amount.getText().toString().trim()));
                            if ("CREDIT".equals(transaction.getDirection())) transaction.setSender(name);
                            else transaction.setReceiverName(name);
                            notifyItemChanged(position);
                        } catch (NumberFormatException ignored) { }
                    }).show();
        }
    }
}
