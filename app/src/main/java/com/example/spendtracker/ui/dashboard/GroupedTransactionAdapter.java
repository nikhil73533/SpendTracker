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

        public HeaderItem(long date, double totalIncome, double totalExpense) {
            this.date = date;
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
        }

        @Override
        int getType() { return TYPE_HEADER; }
        public long getDate() { return date; }
        public double getTotalIncome() { return totalIncome; }
        public double getTotalExpense() { return totalExpense; }
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
        private final DataFormatter formatter;

        public HeaderViewHolder(@NonNull View itemView, DataFormatter formatter) {
            super(itemView);
            this.formatter = formatter;
            tvDayNumber = itemView.findViewById(R.id.tv_day_number);
            tvDayOfWeek = itemView.findViewById(R.id.tv_day_of_week);
            tvMonthYear = itemView.findViewById(R.id.tv_month_year);
            tvDayIncome = itemView.findViewById(R.id.tv_day_income);
            tvDayExpense = itemView.findViewById(R.id.tv_day_expense);
        }

        public void bind(HeaderItem item) {
            Date date = new Date(item.getDate());
            tvDayNumber.setText(dayNumberFormat.format(date));
            tvDayOfWeek.setText(dayOfWeekFormat.format(date));
            tvMonthYear.setText(monthYearFormat.format(date));
            tvDayIncome.setText(formatter.formatAmount(item.getTotalIncome()));
            tvDayExpense.setText(formatter.formatAmount(item.getTotalExpense()));
        }
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.ImageView ivIcon;
        private final TextView tvCategory, tvReceiver, tvDescription, tvSource, tvIncomeAmount, tvExpenseAmount, tvTime, tvGroupTag;
        private final DataFormatter formatter;

        public TransactionViewHolder(@NonNull View itemView, DataFormatter formatter) {
            super(itemView);
            this.formatter = formatter;
            ivIcon = itemView.findViewById(R.id.iv_category_icon);
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
            tvCategory.setText(transaction.getCategory());
            
            // Requirement 12: Mask PII (Receiver, Description)
            tvReceiver.setText(formatter.maskPII("INCOME".equals(transaction.getType()) ? transaction.getSender() : transaction.getReceiverName()));
            tvDescription.setText(formatter.maskPII(transaction.getDescription()));
            
            tvSource.setText(transaction.getSource());
            tvTime.setText(timeFormat.format(new Date(transaction.getDate())));

            if ("INCOME".equals(transaction.getType())) {
                tvIncomeAmount.setTextColor(itemView.getContext().getColor(android.R.color.holo_blue_light));
                tvIncomeAmount.setText(formatter.formatAmount(transaction.getAmount()));
                tvExpenseAmount.setText("");
            } else if ("TRANSFER".equals(transaction.getType())) {
                // Transfer: shown in gray, not counted as income or expense
                tvIncomeAmount.setText("");
                tvExpenseAmount.setTextColor(0xFF9E9E9E); // gray
                tvExpenseAmount.setText("↔ " + formatter.formatAmount(transaction.getAmount()));
            } else {
                tvIncomeAmount.setText("");
                tvExpenseAmount.setTextColor(itemView.getContext().getColor(android.R.color.holo_red_light));
                tvExpenseAmount.setText(formatter.formatAmount(transaction.getAmount()));
            }

            // Category click for dropdown
            tvCategory.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(v.getContext(), v);
                java.util.List<String> categories = listener.getCategoriesByType(transaction.getType());
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
            String category = transaction.getCategory().toLowerCase();
            if (category.contains("food")) iconRes = android.R.drawable.ic_menu_gallery;
            else if (category.contains("transport")) iconRes = android.R.drawable.ic_menu_directions;
            else if (category.contains("gift")) iconRes = android.R.drawable.btn_star_big_on;
            else if (category.contains("health")) iconRes = android.R.drawable.ic_menu_mylocation;
            
            ivIcon.setImageResource(iconRes);

            // Clicking the item opens the edit form
            itemView.setOnClickListener(v -> listener.onEdit(transaction));

            itemView.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(v.getContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Delete this transaction of " + formatter.formatAmount(transaction.getAmount()) + "?")
                    .setPositiveButton("Delete", (dialog, which) -> listener.onDelete(transaction))
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });

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
                       Double.compare(oldHeader.totalExpense, newHeader.totalExpense) == 0;
            } else {
                Transaction oldT = ((TransactionItem) oldItem).getTransaction();
                Transaction newT = ((TransactionItem) newItem).getTransaction();
                return Double.compare(oldT.getAmount(), newT.getAmount()) == 0 &&
                       oldT.getCategory().equals(newT.getCategory()) &&
                       oldT.getType().equals(newT.getType()) &&          // type change triggers rebind
                       oldT.getDescription().equals(newT.getDescription());
            }
        }
    }
}
