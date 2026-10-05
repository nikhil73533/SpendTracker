package com.example.spendtracker.ui.analytics;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentPredictionTransactionsBinding;
import com.example.spendtracker.domain.model.PredictionInsights;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import com.google.android.material.tabs.TabLayout;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.*;

/** Reloads authoritative transactions after review; navigation carries only the outcome filter. */
@AndroidEntryPoint
public class PredictionTransactionsFragment extends Fragment {
    private FragmentPredictionTransactionsBinding binding;
    private DashboardViewModel model;
    private TransactionViewModel transactions;
    private AnalyticsSectionAdapter adapter;
    private int selected;
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentPredictionTransactionsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        model = new ViewModelProvider(this).get(DashboardViewModel.class);
        transactions = new ViewModelProvider(this).get(TransactionViewModel.class);
        selected = Math.max(0, Math.min(2, state != null ? state.getInt("outcome") : requireArguments().getInt("outcome")));
        adapter = new AnalyticsSectionAdapter();
        binding.transactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.transactions.setAdapter(adapter);
        binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        for (String label : new String[]{"Retained", "Corrected", "Unreviewed"}) binding.outcomes.addTab(binding.outcomes.newTab().setText(label));
        binding.outcomes.selectTab(binding.outcomes.getTabAt(selected));
        binding.outcomes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { selected = tab.getPosition(); render(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        model.getAllTransactions().observe(getViewLifecycleOwner(), ignored -> render());
        model.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), ignored -> render());
    }
    private void render() {
        if (binding == null) return;
        List<AnalyticsSectionAdapter.SectionItem> rows = new ArrayList<>();
        List<Transaction> all = model.getAllTransactions().getValue();
        if (all != null) for (Transaction tx : all) {
            if (PredictionInsights.outcome(tx) != PredictionInsights.Outcome.values()[selected]) continue;
            String merchant = "INCOME".equals(tx.getType()) ? tx.getSender() : tx.getReceiverName();
            if (merchant.isEmpty()) merchant = tx.getCategory();
            String detail = new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(tx.getDate()))
                    + " · " + model.formatAmount(tx.getAmount()) + "\nPredicted: " + tx.getPredictedCategory()
                    + "\n" + (selected == 2 ? "Awaiting your review" : "Reviewed: " + tx.getPredictionFinalCategory());
            rows.add(new AnalyticsSectionAdapter.CardItem(transactions.maskPII(merchant), detail, () -> {
                Bundle args = new Bundle(); args.putInt("transactionId", tx.getId());
                Navigation.findNavController(requireView()).navigate(R.id.transactionFormFragment, args);
            }));
        }
        binding.summary.setText(all == null ? "Loading transactions…" : rows.size() + " transactions · All time\nTap a transaction to review its category.");
        binding.empty.setVisibility(all != null && rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submitList(rows);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) { super.onSaveInstanceState(state); state.putInt("outcome", selected); }
    @Override public void onDestroyView() { binding.transactions.setAdapter(null); binding = null; super.onDestroyView(); }
}
