package com.example.spendtracker.ui.dashboard;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
    public interface CalendarFormatter {
        String formatAmount(double amount);
    }
    private final List<CalendarDay> days;
    private final OnDayClickListener listener;
    private final CalendarFormatter formatter;

    public interface OnDayClickListener {
        void onDayClick(CalendarDay day);
    }

    public static class CalendarDay {
        public final int day;
        public final double income;
        public final double expense;
        public final double transfer;
        public final boolean isCurrentMonth;
        public final long timestamp;

        public CalendarDay(int day, double income, double expense, boolean isCurrentMonth, long timestamp) {
            this(day, income, expense, 0.0, isCurrentMonth, timestamp);
        }

        public CalendarDay(int day, double income, double expense, double transfer, boolean isCurrentMonth, long timestamp) {
            this.day = day;
            this.income = income;
            this.expense = expense;
            this.transfer = transfer;
            this.isCurrentMonth = isCurrentMonth;
            this.timestamp = timestamp;
        }
    }

    public CalendarAdapter(List<CalendarDay> days, OnDayClickListener listener, CalendarFormatter formatter) {
        this.days = days;
        this.listener = listener;
        this.formatter = formatter;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view, formatter);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CalendarDay day = days.get(position);
        holder.bind(day, listener);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDay;
        private final TextView tvIncome;
        private final TextView tvExpense;
        private final TextView tvTransfer;
        private final CalendarFormatter formatter;

        public ViewHolder(@NonNull View itemView, CalendarFormatter formatter) {
            super(itemView);
            this.formatter = formatter;
            tvDay = itemView.findViewById(R.id.tv_day);
            tvIncome = itemView.findViewById(R.id.tv_income);
            tvExpense = itemView.findViewById(R.id.tv_expense);
            tvTransfer = itemView.findViewById(R.id.tv_transfer);
        }

        public void bind(CalendarDay day, OnDayClickListener listener) {
            tvDay.setText(String.valueOf(day.day));

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(day.timestamp);

            Calendar today = Calendar.getInstance();
            boolean isToday = today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                              today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

            if (isToday) {
                tvDay.setBackgroundResource(R.drawable.calendar_day_bg);
                tvDay.setTextColor(Color.WHITE);
            } else {
                tvDay.setBackground(null);
                if (day.isCurrentMonth) {
                    tvDay.setTextColor(Color.WHITE);
                } else {
                    tvDay.setTextColor(Color.GRAY);
                }
            }

            if (!day.isCurrentMonth) {
                itemView.setAlpha(0.4f);
                itemView.setBackgroundColor(0xFF161616);
            } else {
                itemView.setAlpha(1.0f);
                itemView.setBackgroundColor(0xFF1E1E1E);
            }

            if (day.income > 0 || day.expense > 0 || day.transfer > 0) {
                if (day.income > 0) {
                    tvIncome.setVisibility(View.VISIBLE);
                    String amount = formatter.formatAmount(day.income);
                    tvIncome.setText("+" + amount.replace("₹ ", ""));
                    tvIncome.setTextColor(0xFF4CAF50);
                } else {
                    tvIncome.setVisibility(View.GONE);
                }

                if (day.expense > 0) {
                    tvExpense.setVisibility(View.VISIBLE);
                    String amount = formatter.formatAmount(day.expense);
                    tvExpense.setText("-" + amount.replace("₹ ", ""));
                    tvExpense.setTextColor(0xFFFF5252);
                } else {
                    tvExpense.setVisibility(View.GONE);
                }

                if (day.transfer > 0 && tvTransfer != null) {
                    tvTransfer.setVisibility(View.VISIBLE);
                    String amount = formatter.formatAmount(day.transfer);
                    tvTransfer.setText(amount.replace("₹ ", ""));
                    tvTransfer.setTextColor(0xFF9E9E9E);
                } else if (tvTransfer != null) {
                    tvTransfer.setVisibility(View.GONE);
                }
            } else {
                tvIncome.setVisibility(View.GONE);
                tvExpense.setVisibility(View.GONE);
                if (tvTransfer != null) tvTransfer.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onDayClick(day));
        }
    }
}
