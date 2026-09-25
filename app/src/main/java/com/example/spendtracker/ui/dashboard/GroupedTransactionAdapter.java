package com.example.spendtracker.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class GroupedTransactionAdapter extends ListAdapter<GroupedTransactionAdapter.ListItem, RecyclerView.ViewHolder> {

    private final OnTransactionClickListener listener;
    private static final SimpleDateFormat dayNumberFormat = new SimpleDateFormat("d", Locale.getDefault());
    private static final SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
    private static final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MM.yyyy", Locale.getDefault());

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public interface OnTransactionClickListener {
        void onEdit(Transaction transaction);
        void onDelete(Transaction transaction);
        void onCategoryChange(Transaction transaction, String newCategory);
        java.util.List<String> getCategoriesByType(String type);
    }

    public interface DataFormatter {
        String formatAmount(double amount);
        String maskPII(String value);
    }

    private final DataFormatter formatter;
    private final TransactionSelection selection = new TransactionSelection();
    private Runnable selectionChanged;

    public void enableSelection(Runnable selectionChanged) { this.selectionChanged = selectionChanged; }
    public boolean isSelecting() { return selection.isActive(); }
    public int getSelectionCount() { return selection.size(); }

    public Transaction transactionAt(int position) {
        if (position < 0 || position >= getItemCount()) return null;
        ListItem item = getItem(position);
        return item instanceof TransactionItem ? ((TransactionItem) item).getTransaction() : null;
    }

    public java.util.List<Transaction> getSelectedTransactions() {
        java.util.List<Transaction> result = new java.util.ArrayList<>();
        for (ListItem item : getCurrentList()) {
            if (item instanceof TransactionItem) {
                Transaction transaction = ((TransactionItem) item).getTransaction();
                if (selection.contains(transaction.getId())) result.add(transaction);
            }
        }
        return result;
    }

    private java.util.List<Integer> transactionIds() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (ListItem item : getCurrentList()) {
            if (item instanceof TransactionItem) ids.add(((TransactionItem) item).getTransaction().getId());
        }
        return ids;
    }

    public int getTransactionCount() { return transactionIds().size(); }
    public void selectAll(boolean checked) {
        selection.selectAll(checked ? transactionIds() : java.util.Collections.emptyList());
        refreshSelection();
    }
    public void clearSelection() { selection.clear(); refreshSelection(); }
    public void restoreSelection(java.util.List<Integer> ids) {
        selection.selectAll(ids);
        selection.retain(transactionIds());
        refreshSelection();
    }
    private void toggleSelection(Transaction transaction) {
        selection.toggle(transaction.getId());
        refreshSelection();
    }
    private void refreshSelection() {
        notifyItemRangeChanged(0, getItemCount());
        if (selectionChanged != null) selectionChanged.run();
    }
    @Override
    public void onCurrentListChanged(@NonNull java.util.List<ListItem> previous,
                                     @NonNull java.util.List<ListItem> current) {
        super.onCurrentListChanged(previous, current);
        selection.retain(transactionIds());
        if (selectionChanged != null) selectionChanged.run();
    }

    public GroupedTransactionAdapter(OnTransactionClickListener listener, DataFormatter formatter) {
        super(new DiffCallback());
        this.listener = listener;
        this.formatter = formatter;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ListItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date_header, parent, false);
            return new HeaderViewHolder(view, formatter);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
            return new TransactionViewHolder(view, formatter);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((HeaderItem) item);
        } else if (holder instanceof TransactionViewHolder) {
            ((TransactionViewHolder) holder).bind((TransactionItem) item, listener);
        }
    }

    public static abstract class ListItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_TRANSACTION = 1;
        abstract int getType();
    }

    public static class HeaderItem extends ListItem {
        private final long date;
        private final double totalIncome;
        private final double totalExpense;
        private final double totalTransfer;

        public HeaderItem(long date, double totalIncome, double totalExpense, double totalTransfer) {
            this.date = date;
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
            this.totalTransfer = totalTransfer;
        }

        @Override
        int getType() { return TYPE_HEADER; }
        public long getDate() { return date; }
        public double getTotalIncome() { return totalIncome; }
        public double getTotalExpense() { return totalExpense; }
        public double getTotalTransfer() { return totalTransfer; }
    }

    public static class TransactionItem extends ListItem {
        private final Transaction transaction;

        public TransactionItem(Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        int getType() { return TYPE_TRANSACTION; }
        public Transaction getTransaction() { return transaction; }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDayNumber;
        private final TextView tvDayOfWeek;
        private final TextView tvMonthYear;
        private final TextView tvDayIncome;
        private final TextView tvDayExpense;
        private final TextView tvDayTransfer;
        private final DataFormatter formatter;

        public HeaderViewHolder(@NonNull View itemView, DataFormatter formatter) {
            super(itemView);
            this.formatter = formatter;
            tvDayNumber = itemView.findViewById(R.id.tv_day_number);
            tvDayOfWeek = itemView.findViewById(R.id.tv_day_of_week);
            tvMonthYear = itemView.findViewById(R.id.tv_month_year);
            tvDayIncome = itemView.findViewById(R.id.tv_day_income);
            tvDayExpense = itemView.findViewById(R.id.tv_day_expense);
            tvDayTransfer = itemView.findViewById(R.id.tv_day_transfer);
        }

        public void bind(HeaderItem item) {
            Date date = new Date(item.getDate());
            tvDayNumber.setText(dayNumberFormat.format(date));
            tvDayOfWeek.setText(dayOfWeekFormat.format(date));
            tvMonthYear.setText(monthYearFormat.format(date));
            tvDayIncome.setText(formatter.formatAmount(item.getTotalIncome()));
            tvDayExpense.setText(formatter.formatAmount(item.getTotalExpense()));
            // Show Transfer column only when transfers exist for the day
            if (tvDayTransfer != null) {
                if (item.getTotalTransfer() != 0) {
                    tvDayTransfer.setVisibility(View.VISIBLE);
                    tvDayTransfer.setText("↔ " + formatter.formatAmount(item.getTotalTransfer()));
                } else {
                    tvDayTransfer.setVisibility(View.GONE);
                }
            }
        }
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.ImageView ivIcon;
        private final android.widget.CheckBox selectionBox;
        private final TextView tvCategory, tvReceiver, tvDescription, tvSource, tvIncomeAmount, tvExpenseAmount, tvTime, tvGroupTag;
        private final DataFormatter formatter;

        public TransactionViewHolder(@NonNull View itemView, DataFormatter formatter) {
            super(itemView);
            this.formatter = formatter;
            ivIcon = itemView.findViewById(R.id.iv_category_icon);
            selectionBox = itemView.findViewById(R.id.check_daily_transaction);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvReceiver = itemView.findViewById(R.id.tv_receiver);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvSource = itemView.findViewById(R.id.tv_source);
            tvIncomeAmount = itemView.findViewById(R.id.tv_income_amount);
            tvExpenseAmount = itemView.findViewById(R.id.tv_expense_amount);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvGroupTag = itemView.findViewById(R.id.tv_group_tag);
        }

        public void bind(TransactionItem item, OnTransactionClickListener listener) {
            Transaction transaction = item.getTransaction();
            selectionBox.setOnCheckedChangeListener(null);
            selectionBox.setVisibility(isSelecting() ? View.VISIBLE : View.GONE);
            ivIcon.setVisibility(isSelecting() ? View.INVISIBLE : View.VISIBLE);
            selectionBox.setChecked(selection.contains(transaction.getId()));
            selectionBox.setOnCheckedChangeListener((button, checked) -> {
                Transaction current = transactionAt(getAdapterPosition());
                if (current != null) toggleSelection(current);
            });
            tvCategory.setText(transaction.getCategory());
            int weight = transaction.getConfidenceScore() < com.example.spendtracker.util.CategoryPrediction.REVIEW_THRESHOLD ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL;
            for (TextView label : new TextView[]{tvCategory, tvReceiver, tvDescription, tvIncomeAmount, tvExpenseAmount}) label.setTypeface(null, weight);
            View share = itemView.findViewById(R.id.share_receipt);
            share.setVisibility(isSelecting() ? View.GONE : View.VISIBLE);
            share.setOnClickListener(v -> com.example.spendtracker.util.TransactionReceipt.share(v.getContext(), transaction));
            
            // Requirement 12: Mask PII (Receiver, Description)
            tvReceiver.setText(formatter.maskPII(com.example.spendtracker.util.TransferDirection.isIncoming(transaction) ? transaction.getSender() : transaction.getReceiverName()));
            tvDescription.setText(formatter.maskPII(transaction.getDescription()));
            
            tvSource.setText(transaction.getSource());
            tvTime.setText("DATE_ONLY".equals(transaction.getTimestampPrecision()) ? ""
                    : "SMS_RECEIVED".equals(transaction.getTimestampPrecision())
                    ? itemView.getContext().getString(R.string.sms_received_time, timeFormat.format(new Date(transaction.getDate())))
                    : timeFormat.format(new Date(transaction.getDate())));

            if ("INCOME".equals(transaction.getType())) {
                tvIncomeAmount.setTextColor(itemView.getContext().getColor(android.R.color.holo_blue_light));
                tvIncomeAmount.setText(formatter.formatAmount(transaction.getAmount()));
                tvExpenseAmount.setText("");
            } else if ("TRANSFER".equals(transaction.getType())) {
                // Transfer: shown in gray, not counted as income or expense
                tvIncomeAmount.setText("");
                tvExpenseAmount.setTextColor(0xFF9E9E9E); // gray
                tvExpenseAmount.setText((com.example.spendtracker.util.TransferDirection.isIncoming(transaction) ? "↓ " : "↑ ") + formatter.formatAmount(transaction.getAmount()));
            } else {
                tvIncomeAmount.setText("");
                tvExpenseAmount.setTextColor(itemView.getContext().getColor(android.R.color.holo_red_light));
                tvExpenseAmount.setText(formatter.formatAmount(transaction.getAmount()));
            }

            // Category click for dropdown
            tvCategory.setOnClickListener(v -> {
                if (isSelecting()) {
                    Transaction current = transactionAt(getAdapterPosition());
                    if (current != null) toggleSelection(current);
                    return;
                }
                android.widget.PopupMenu popup = new android.widget.PopupMenu(v.getContext(), v);
                java.util.List<String> categories = listener.getCategoriesByType("TRANSFER".equals(transaction.getType()) ? (com.example.spendtracker.util.TransferDirection.isIncoming(transaction) ? "INCOME" : "EXPENSE") : transaction.getType());
                for (String cat : categories) {
                    popup.getMenu().add(cat);
                }
                popup.setOnMenuItemClickListener(menuItem -> {
                    CharSequence title = menuItem.getTitle();
                    if (title != null) {
                        listener.onCategoryChange(transaction, title.toString());
                    }
                    return true;
                });
                popup.show();
            });

            // Simple icon mapping (using some emojis or standard icons)
            int iconRes = android.R.drawable.ic_menu_help;
            String category = transaction.getCategory() == null ? "" : transaction.getCategory().toLowerCase(Locale.ROOT);
            if (category.contains("food")) iconRes = android.R.drawable.ic_menu_gallery;
            else if (category.contains("transport")) iconRes = android.R.drawable.ic_menu_directions;
            else if (category.contains("gift")) iconRes = android.R.drawable.btn_star_big_on;
            else if (category.contains("health")) iconRes = android.R.drawable.ic_menu_mylocation;
            
            ivIcon.setImageResource(iconRes);

            // Clicking the item opens the edit form
            itemView.setOnClickListener(v -> {
                Transaction current = transactionAt(getAdapterPosition());
                if (current == null) return;
                if (isSelecting()) toggleSelection(current);
                else listener.onEdit(current);
            });

            itemView.setOnLongClickListener(v -> {
                Transaction current = transactionAt(getAdapterPosition());
                if (current == null) return true;
                if (selectionChanged != null) {
                    toggleSelection(current);
                    return true;
                }
                new android.app.AlertDialog.Builder(v.getContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Delete this transaction of " + formatter.formatAmount(transaction.getAmount()) + "?")
                    .setPositiveButton("Delete", (dialog, which) -> listener.onDelete(transaction))
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
            tvCategory.setOnLongClickListener(v -> itemView.performLongClick());

            // Transaction Group tag
            String groupName = transaction.getTransactionGroupName();
            if (groupName != null && !groupName.isEmpty()) {
                tvGroupTag.setText(groupName);
                tvGroupTag.setVisibility(View.VISIBLE);
            } else {
                tvGroupTag.setVisibility(View.GONE);
            }
        }

    }

    static class DiffCallback extends DiffUtil.ItemCallback<ListItem> {
        @Override
        public boolean areItemsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            if (oldItem.getType() != newItem.getType()) return false;
            if (oldItem instanceof HeaderItem) {
                return ((HeaderItem) oldItem).getDate() == ((HeaderItem) newItem).getDate();
            } else {
                return ((TransactionItem) oldItem).getTransaction().getId() == ((TransactionItem) newItem).getTransaction().getId();
            }
        }

        @Override
        public boolean areContentsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            if (oldItem instanceof HeaderItem) {
                HeaderItem oldHeader = (HeaderItem) oldItem;
                HeaderItem newHeader = (HeaderItem) newItem;
                return Double.compare(oldHeader.totalIncome, newHeader.totalIncome) == 0 &&
                       Double.compare(oldHeader.totalExpense, newHeader.totalExpense) == 0 &&
                       Double.compare(oldHeader.totalTransfer, newHeader.totalTransfer) == 0;
            } else {
                Transaction oldT = ((TransactionItem) oldItem).getTransaction();
                Transaction newT = ((TransactionItem) newItem).getTransaction();
                return Double.compare(oldT.getConfidenceScore(), newT.getConfidenceScore()) == 0 &&
                       Objects.equals(oldT.getDirection(), newT.getDirection()) &&
                       Double.compare(oldT.getAmount(), newT.getAmount()) == 0 &&
                       oldT.getDate() == newT.getDate() &&
                       Objects.equals(oldT.getTimestampPrecision(), newT.getTimestampPrecision()) &&
                       Objects.equals(oldT.getSender(), newT.getSender()) &&
                       Objects.equals(oldT.getReceiverName(), newT.getReceiverName()) &&
                       Objects.equals(oldT.getSource(), newT.getSource()) &&
                       Objects.equals(oldT.getTransactionGroupName(), newT.getTransactionGroupName()) &&
                       Objects.equals(oldT.getCategory(), newT.getCategory()) &&
                       Objects.equals(oldT.getType(), newT.getType()) &&          // type change triggers rebind
                       Objects.equals(oldT.getDescription(), newT.getDescription());
            }
        }
    }
}
