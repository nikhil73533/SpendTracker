package com.example.spendtracker.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.RepeatedAlert;
import com.google.android.material.switchmaterial.SwitchMaterial;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class RepeatedAlertsFragment extends Fragment {

    private RepeatedAlertsViewModel viewModel;
    private RecyclerView rvAlerts;
    private View layoutEmptyState;
    private AlertAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_repeated_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RepeatedAlertsViewModel.class);

        view.findViewById(R.id.toolbar).setOnClickListener(v ->
            Navigation.findNavController(v).navigateUp());
        ((com.google.android.material.appbar.MaterialToolbar) view.findViewById(R.id.toolbar))
            .setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        rvAlerts = view.findViewById(R.id.rv_repeated_alerts);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);

        adapter = new AlertAdapter(
            id -> viewModel.dismissAlert(id),
            (id, enabled) -> viewModel.setAlertEnabled(id, enabled)
        );
        rvAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAlerts.setAdapter(adapter);

        viewModel.getActiveAlerts().observe(getViewLifecycleOwner(), alerts -> {
            if (alerts == null || alerts.isEmpty()) {
                rvAlerts.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            } else {
                rvAlerts.setVisibility(View.VISIBLE);
                layoutEmptyState.setVisibility(View.GONE);
                adapter.submitList(alerts);
            }
        });
    }

    static class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.ViewHolder> {
        private List<RepeatedAlert> items = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final OnDismissListener dismissListener;
        private final OnEnableToggleListener toggleListener;

        interface OnDismissListener {
            void onDismiss(int id);
        }

        interface OnEnableToggleListener {
            void onToggle(int id, boolean enabled);
        }

        AlertAdapter(OnDismissListener dismissListener, OnEnableToggleListener toggleListener) {
            this.dismissListener = dismissListener;
            this.toggleListener = toggleListener;
        }

        void submitList(List<RepeatedAlert> list) {
            this.items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_repeated_alert, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RepeatedAlert alert = items.get(position);
            holder.tvMerchant.setText(alert.getMerchantName());
            holder.tvAmount.setText(String.format(Locale.getDefault(), "₹ %.0f", alert.getAmount()));
            
            String date1 = sdf.format(new Date(alert.getFirstTransactionDate()));
            String date2 = sdf.format(new Date(alert.getSecondTransactionDate()));
            holder.tvDetails.setText(String.format(Locale.getDefault(), "Matches detected on %s and %s", date1, date2));
            
            holder.switchEnabled.setOnCheckedChangeListener(null);
            holder.switchEnabled.setChecked(alert.isEnabled());
            holder.switchEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
                toggleListener.onToggle(alert.getId(), isChecked);
            });
            
            holder.btnDismiss.setOnClickListener(v -> dismissListener.onDismiss(alert.getId()));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMerchant, tvAmount, tvDetails;
            SwitchMaterial switchEnabled;
            View btnDismiss;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMerchant = itemView.findViewById(R.id.tv_alert_merchant);
                tvAmount = itemView.findViewById(R.id.tv_alert_amount);
                tvDetails = itemView.findViewById(R.id.tv_alert_details);
                switchEnabled = itemView.findViewById(R.id.switch_alert_enabled);
                btnDismiss = itemView.findViewById(R.id.btn_dismiss_alert);
            }
        }
    }
}
