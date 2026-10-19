package com.example.spendtracker.ui.analytics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Define view types if we add different types of cards later
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CARD = 1;

    private List<SectionItem> items = new ArrayList<>();

    public void submitList(List<SectionItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new HeaderViewHolder(v);
        } else {
            // Reusable card shared by insights and transaction drill-down.
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_analytics_card, parent, false);
            return new CardViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SectionItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((HeaderItem) item);
        } else if (holder instanceof CardViewHolder) {
            ((CardViewHolder) holder).bind((CardItem) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        SectionItem item = items.get(position);
        if (item instanceof HeaderItem) {
            return TYPE_HEADER;
        } else {
            return TYPE_CARD;
        }
    }

    // --- Data Models for Adapter ---
    
    public interface SectionItem {}

    public static class HeaderItem implements SectionItem {
        public String title;
        public HeaderItem(String title) { this.title = title; }
    }

    public static class CardItem implements SectionItem {
        public String title;
        public String value;
        public Runnable action;
        public CardItem(String title, String value) {
            this(title, value, null);
        }
        public CardItem(String title, String value, Runnable action) {
            this.title = title;
            this.value = value;
            this.action = action;
        }
    }

    // --- ViewHolders ---

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(android.R.id.text1);
        }
        void bind(HeaderItem item) {
            tvTitle.setText(item.title);
            tvTitle.setTextAppearance(itemView.getContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Widget_ActionBar_Title);
            float unit = itemView.getResources().getDisplayMetrics().density;
            tvTitle.setPadding(0, Math.round(24 * unit), 0, Math.round(12 * unit));
            tvTitle.setTextColor(itemView.getContext().getColor(R.color.dashboard_text_primary));
        }
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvValue;
        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(android.R.id.text1);
            tvValue = itemView.findViewById(android.R.id.text2);
        }
        void bind(CardItem item) {
            tvTitle.setText(item.title);
            tvValue.setText(item.value);
            tvValue.setTextColor(itemView.getContext().getResources().getColor(com.example.spendtracker.R.color.dashboard_text_secondary));
            itemView.setOnClickListener(item.action == null ? null : view -> item.action.run());
            itemView.setClickable(item.action != null);
            itemView.setFocusable(item.action != null);
            itemView.findViewById(R.id.insight_chevron).setVisibility(item.action == null ? View.GONE : View.VISIBLE);
        }
    }
}
