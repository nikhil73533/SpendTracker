package com.example.spendtracker.help;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import java.util.ArrayList;
import java.util.List;

public class HelpAdapter extends RecyclerView.Adapter<HelpAdapter.Holder> {
    private final List<HelpItem> items = new ArrayList<>();
    private String expandedId;

    public void submit(List<HelpItem> items) {
        this.items.clear();
        if (items != null) this.items.addAll(items);
        if (expandedId != null) {
            boolean present = false;
            for (HelpItem item : this.items) {
                if (expandedId.equals(item.id)) {
                    present = true;
                    break;
                }
            }
            if (!present) expandedId = null;
        }
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_help_faq, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        HelpItem item = items.get(position);
        boolean expanded = item.id.equals(expandedId);
        holder.category.setText(item.category);
        holder.question.setText(item.question);
        holder.answer.setText(item.answer);
        holder.answer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(view -> {
            expandedId = expanded ? null : item.id;
            notifyDataSetChanged();
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView category;
        final TextView question;
        final TextView answer;
        Holder(View view) {
            super(view);
            category = view.findViewById(R.id.help_category);
            question = view.findViewById(R.id.help_question);
            answer = view.findViewById(R.id.help_answer);
        }
    }
}
