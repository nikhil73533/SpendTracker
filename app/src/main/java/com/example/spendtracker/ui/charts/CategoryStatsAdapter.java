package com.example.spendtracker.ui.charts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryStatsAdapter extends RecyclerView.Adapter<CategoryStatsAdapter.StatViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    public interface AmountFormatter {
        String format(double amount);
    }

    private final List<CategoryStat> items = new ArrayList<>();
    private final OnCategoryClickListener listener;
    private final AmountFormatter formatter;

    public CategoryStatsAdapter(OnCategoryClickListener listener, AmountFormatter formatter) {
        this.listener = listener;
        this.formatter = formatter;
    }

    public static class CategoryStat {
        public final String name;
        public final double amount;
        public final int percentage;
        public final int color;

        public CategoryStat(String name, double amount, int percentage, int color) {
            this.name = name;
            this.amount = amount;
            this.percentage = percentage;
            this.color = color;
        }
    }

    public void submitList(List<CategoryStat> newList) {
        items.clear();
        items.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_stat, parent, false);
        return new StatViewHolder(view, formatter);
    }

    @Override
    public void onBindViewHolder(@NonNull StatViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class StatViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvPercentage, tvName, tvAmount;
        private final ImageView ivIcon;
        private final AmountFormatter formatter;

        public StatViewHolder(@NonNull View itemView, AmountFormatter formatter) {
            super(itemView);
            this.formatter = formatter;
            tvPercentage = itemView.findViewById(R.id.tv_percentage);
            tvName = itemView.findViewById(R.id.tv_category_name);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            ivIcon = itemView.findViewById(R.id.iv_category_icon);
        }

        public void bind(CategoryStat stat, OnCategoryClickListener listener) {
            tvPercentage.setText(stat.percentage + "%");
            tvPercentage.getBackground().setTint(stat.color);
            tvName.setText(stat.name);
            tvAmount.setText(formatter.format(stat.amount));
            
            // Icon logic
            int iconRes = android.R.drawable.ic_menu_help;
            String category = stat.name.toLowerCase();
            if (category.contains("food")) iconRes = android.R.drawable.ic_menu_gallery;
            else if (category.contains("transport")) iconRes = android.R.drawable.ic_menu_directions;
            else if (category.contains("gift")) iconRes = android.R.drawable.btn_star_big_on;
            else if (category.contains("health")) iconRes = android.R.drawable.ic_menu_mylocation;
            
            ivIcon.setImageResource(iconRes);
            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onCategoryClick(stat.name));
            } else {
                itemView.setClickable(false);
            }
        }
    }
}
