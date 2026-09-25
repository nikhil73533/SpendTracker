package com.example.spendtracker.ui.premium;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.billing.PremiumPlan;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class PremiumPlanAdapter extends RecyclerView.Adapter<PremiumPlanAdapter.Holder> {
    private final List<PremiumPlan> plans = new ArrayList<>();
    private final Runnable selectionChanged;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public PremiumPlanAdapter(Runnable selectionChanged) { this.selectionChanged = selectionChanged; }

    public void submit(List<PremiumPlan> plans) {
        this.plans.clear();
        if (plans != null) this.plans.addAll(plans);
        selectedPosition = this.plans.isEmpty() ? RecyclerView.NO_POSITION : 0;
        notifyDataSetChanged();
        selectionChanged.run();
    }

    public PremiumPlan selected() {
        return selectedPosition >= 0 && selectedPosition < plans.size() ? plans.get(selectedPosition) : null;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_premium_plan, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        PremiumPlan plan = plans.get(position);
        holder.title.setText(plan.title);
        holder.price.setText(plan.price);
        holder.period.setText(plan.period);
        holder.card.setChecked(position == selectedPosition);
        holder.itemView.setOnClickListener(view -> {
            if (selectedPosition == position) return;
            int old = selectedPosition;
            selectedPosition = position;
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(position);
            selectionChanged.run();
        });
    }

    @Override public int getItemCount() { return plans.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        final TextView price;
        final TextView period;
        Holder(View view) {
            super(view);
            card = (MaterialCardView) view;
            title = view.findViewById(R.id.plan_title);
            price = view.findViewById(R.id.plan_price);
            period = view.findViewById(R.id.plan_period);
        }
    }
}
