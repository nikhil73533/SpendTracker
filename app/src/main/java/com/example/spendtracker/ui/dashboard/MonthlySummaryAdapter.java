package com.example.spendtracker.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MonthlySummaryAdapter extends RecyclerView.Adapter<MonthlySummaryAdapter.MonthViewHolder> {

    public interface AmountFormatter {
        String format(double amount);
    }

    private final List<MonthSummary> items = new ArrayList<>();
    private final SimpleDateFormat monthNameFormat = new SimpleDateFormat("MMM", Locale.getDefault());
    private final AmountFormatter formatter;

    public MonthlySummaryAdapter(AmountFormatter formatter) {
        this.formatter = formatter;
    }

    public static class MonthSummary {
        public final long monthTimestamp;
        public final double income;
        public final double expense;
        public final List<WeeklySummary> weeks;
        public boolean isExpanded = false;

        public MonthSummary(long monthTimestamp, double income, double expense, List<WeeklySummary> weeks) {
            this.monthTimestamp = monthTimestamp;
            this.income = income;
            this.expense = expense;
            this.weeks = weeks;
        }
    }

    public static class WeeklySummary {
        public final String range;
        public final double income;
        public final double expense;

        public WeeklySummary(String range, double income, double expense) {
            this.range = range;
            this.income = income;
            this.expense = expense;
        }
    }

    public void submitList(List<MonthSummary> newList) {
        items.clear();
        items.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_summary, parent, false);
        return new MonthViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class MonthViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName, tvRange, tvIncome, tvExpense, tvTotal;
        private final RecyclerView rvWeeks;
        private final View layoutRow;

        public MonthViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_month_name);
            tvRange = itemView.findViewById(R.id.tv_month_range);
            tvIncome = itemView.findViewById(R.id.tv_month_income);
            tvExpense = itemView.findViewById(R.id.tv_month_expense);
            tvTotal = itemView.findViewById(R.id.tv_month_total);
            rvWeeks = itemView.findViewById(R.id.rv_weeks);
            layoutRow = itemView.findViewById(R.id.layout_month_row);
        }

        public void bind(MonthSummary item) {
            Date date = new Date(item.monthTimestamp);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            
            tvName.setText(monthNameFormat.format(date));
            
            SimpleDateFormat rangeSdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
            String start = rangeSdf.format(date);
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            String end = rangeSdf.format(cal.getTime());
            
            tvRange.setText(start + " – " + end);
            
            tvIncome.setText(formatter.format(item.income));
            tvExpense.setText(formatter.format(item.expense));
            
            double total = item.income - item.expense;
            String totalStr = formatter.format(Math.abs(total));
            tvTotal.setText(String.format(Locale.getDefault(), "%s%s", total >= 0 ? "" : "-", totalStr));

            rvWeeks.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
            if (item.isExpanded) {
                rvWeeks.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                rvWeeks.setAdapter(new WeeklyAdapter(item.weeks, formatter));
            }

            layoutRow.setOnClickListener(v -> {
                item.isExpanded = !item.isExpanded;
                notifyItemChanged(getAdapterPosition());
            });
        }
    }

    static class WeeklyAdapter extends RecyclerView.Adapter<WeeklyAdapter.WeekViewHolder> {
        private final List<WeeklySummary> weeks;
        private final AmountFormatter formatter;

        WeeklyAdapter(List<WeeklySummary> weeks, AmountFormatter formatter) { 
            this.weeks = weeks; 
            this.formatter = formatter;
        }

        @NonNull
        @Override
        public WeekViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weekly_summary, parent, false);
            return new WeekViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WeekViewHolder holder, int position) {
            WeeklySummary w = weeks.get(position);
            holder.tvRange.setText(w.range);
            holder.tvIncome.setText(formatter.format(w.income));
            holder.tvExpense.setText(formatter.format(w.expense));
            double total = w.income - w.expense;
            String totalStr = formatter.format(Math.abs(total));
            holder.tvTotal.setText(String.format(Locale.getDefault(), "%s%s", total >= 0 ? "" : "-", totalStr));
        }

        @Override
        public int getItemCount() { return weeks.size(); }

        static class WeekViewHolder extends RecyclerView.ViewHolder {
            TextView tvRange, tvIncome, tvExpense, tvTotal;
            public WeekViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRange = itemView.findViewById(R.id.tv_week_range);
                tvIncome = itemView.findViewById(R.id.tv_week_income);
                tvExpense = itemView.findViewById(R.id.tv_week_expense);
                tvTotal = itemView.findViewById(R.id.tv_week_total);
            }
        }
    }
}
