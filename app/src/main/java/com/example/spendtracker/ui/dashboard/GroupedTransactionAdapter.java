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
            tvDayIncome.setText("+ " + formatter.formatAmount(item.getTotalIncome()));
            tvDayExpense.setText("− " + formatter.formatAmount(item.getTotalExpense()));
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
            android.widget.ImageButton bookmark = itemView.findViewById(R.id.bookmark_transaction);
            bookmark.setVisibility(isSelecting() ? View.GONE : View.VISIBLE);
            bookmark.setImageResource(transaction.isBookmarked() ? R.drawable.ic_symbol_bookmark_filled : R.drawable.ic_symbol_bookmark);
            bookmark.setContentDescription(transaction.isBookmarked() ? "Remove bookmark" : "Bookmark transaction");
            bookmark.setOnClickListener(v -> com.example.spendtracker.util.BookmarkActions.set(v.getContext(), transaction.getId(), !transaction.isBookmarked()));
            View share = itemView.findViewById(R.id.share_receipt);
            share.setVisibility(isSelecting() ? View.GONE : View.VISIBLE);
            share.setOnClickListener(v -> com.example.spendtracker.util.TransactionReceipt.share(v.getContext(), transaction));
            
            // Requirement 12: Mask PII (Receiver, Description)
            tvReceiver.setText(formatter.maskPII(com.example.spendtracker.util.TransferDirection.isIncoming(transaction) ? transaction.getSender() : transaction.getReceiverName()));
            if (com.example.spendtracker.util.TransferDirection.isTransfer(transaction)) {
                String owner = com.example.spendtracker.util.UserProfile.load(itemView.getContext()).name;
                tvReceiver.setText(formatter.maskPII(com.example.spendtracker.util.TransferDirection.from(transaction, owner))
                        + " → " + formatter.maskPII(com.example.spendtracker.util.TransferDirection.to(transaction, owner)));
            }
            tvDescription.setText(formatter.maskPII(transaction.getDescription()));
            
            tvSource.setText(transaction.getSource());
            boolean statementDateOnly = "DATE_ONLY".equals(transaction.getTimestampPrecision())
                    && !transaction.getImportBatchId().isEmpty();
            tvTime.setText(statementDateOnly ? ""
                    : timeFormat.format(new Date(transaction.getDate())));

            if ("INCOME".equals(transaction.getType())) {
                tvIncomeAmount.setTextColor(itemView.getContext().getColor(R.color.dashboard_income_blue));
                tvIncomeAmount.setText("+ " + formatter.formatAmount(transaction.getAmount()));
                tvExpenseAmount.setText("");
            } else if ("TRANSFER".equals(transaction.getType())) {
                // Transfer: shown in gray, not counted as income or expense
                tvIncomeAmount.setText("");
                tvExpenseAmount.setTextColor(itemView.getContext().getColor(R.color.dashboard_brand_transfer));
                tvExpenseAmount.setText((com.example.spendtracker.util.TransferDirection.isIncoming(transaction) ? "↓ " : "↑ ") + formatter.formatAmount(transaction.getAmount()));
            } else {
                tvIncomeAmount.setText("");
                tvExpenseAmount.setTextColor(itemView.getContext().getColor(R.color.dashboard_expense_red));
                tvExpenseAmount.setText("− " + formatter.formatAmount(transaction.getAmount()));
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
                java.util.LinkedHashSet<String> choices = new java.util.LinkedHashSet<>();
                choices.add("Transfer");
                choices.addAll(categories);
                for (String cat : choices) {
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

            String category = transaction.getCategory() == null ? "" : transaction.getCategory().toLowerCase(Locale.ROOT);
            int iconRes = R.drawable.ic_symbol_category;
            if ("TRANSFER".equals(transaction.getType())) iconRes = R.drawable.ic_symbol_transfer;
            else if (category.contains("food")) iconRes = R.drawable.ic_symbol_food;
            else if (category.contains("transport")) iconRes = R.drawable.ic_symbol_transport;
            else if (category.contains("gift")) iconRes = R.drawable.ic_symbol_gift;
            else if (category.contains("health") || category.contains("medical")) iconRes = R.drawable.ic_symbol_health;
            else if (category.contains("education")) iconRes = R.drawable.ic_symbol_education;
            else if (category.contains("shopping")) iconRes = R.drawable.ic_symbol_shopping;
            else if (category.contains("salary")) iconRes = R.drawable.ic_symbol_salary;
            else if (category.contains("rent") || category.contains("home")) iconRes = R.drawable.ic_symbol_home;
            else if (category.contains("entertainment")) iconRes = R.drawable.ic_symbol_entertainment;
            else if (category.contains("travel")) iconRes = R.drawable.ic_symbol_travel;
            else if (category.contains("grocer")) iconRes = R.drawable.ic_symbol_groceries;
            else if (category.contains("invest")) iconRes = R.drawable.ic_symbol_investment;
            else if (category.contains("insurance")) iconRes = R.drawable.ic_symbol_insurance;
            else if (category.contains("phone") || category.contains("mobile")) iconRes = R.drawable.ic_symbol_phone;
            else if (category.contains("utilit") || category.contains("electric")) iconRes = R.drawable.ic_symbol_utilities;
            else if (category.contains("bill")) iconRes = R.drawable.ic_symbol_bill;
            else if (category.contains("allowance")) iconRes = R.drawable.ic_symbol_allowance;
            else if (category.contains("bonus")) iconRes = R.drawable.ic_symbol_bonus;
            ivIcon.setImageResource(iconRes);
            ivIcon.setContentDescription(transaction.getCategory());

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
                       oldT.isBookmarked() == newT.isBookmarked() &&
                       Objects.equals(oldT.getFromAccount(), newT.getFromAccount()) &&
                       Objects.equals(oldT.getToAccount(), newT.getToAccount()) &&
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
