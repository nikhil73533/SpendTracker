package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentDashboardDailyBinding;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AndroidEntryPoint
public class DailyTransactionsFragment extends Fragment {

    private FragmentDashboardDailyBinding binding;
    private DashboardViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private GroupedTransactionAdapter adapter;
    private List<String> incomeCategories = new ArrayList<>();
    private List<String> expenseCategories = new ArrayList<>();
    private OnBackPressedCallback selectionBack;
    private ItemTouchHelper swipeHelper;
    private ArrayList<Integer> restoredSelection;
    private List<Transaction> activityTransactions = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardDailyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DashboardViewModel.class);
        transactionViewModel = new ViewModelProvider(requireParentFragment()).get(TransactionViewModel.class);
        if (savedInstanceState != null) restoredSelection = savedInstanceState.getIntegerArrayList("dailySelection");

        setupRecyclerView();
        selectionBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if (binding != null && binding.dailyChartOverlay.getVisibility() == View.VISIBLE) {
                    hideActivityChart();
                } else {
                    adapter.clearSelection();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), selectionBack);
        adapter.enableSelection(this::renderSelection);
        binding.btnCancelSelection.setOnClickListener(v -> adapter.clearSelection());
        binding.btnDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());
        binding.checkSelectAll.setOnClickListener(v -> adapter.selectAll(binding.checkSelectAll.isChecked()));
        binding.btnCloseDailyChart.setOnClickListener(v -> hideActivityChart());
        binding.btnShowDailyChart.setOnClickListener(v -> showActivityChart());
        if (savedInstanceState != null) {
            binding.activityMetric.check(savedInstanceState.getBoolean("weekExpenses") ? R.id.metric_week : R.id.metric_activity);
            binding.activityStyle.check(savedInstanceState.getBoolean("linePlot") ? R.id.style_line : R.id.style_bar);
        }
        binding.activityMetric.addOnButtonCheckedListener((group, id, checked) -> { if (checked) renderActivityChart(activityTransactions); });
        binding.activityStyle.addOnButtonCheckedListener((group, id, checked) -> { if (checked) renderActivityChart(activityTransactions); });
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), ignored -> {
            if (binding != null && binding.dailyChartOverlay.getVisibility() == View.VISIBLE) renderActivityChart(activityTransactions);
        });
        setupSwipe();
        setupActivityChartGesture();
        observeViewModel();
        if (savedInstanceState != null && savedInstanceState.getBoolean("dailyChartVisible")) showActivityChart();
    }

    private void renderSelection() {
        if (binding == null) return;
        binding.selectionBar.setVisibility(adapter.isSelecting() ? View.VISIBLE : View.GONE);
        binding.tvSelectionCount.setText(getString(R.string.selection_count, adapter.getSelectionCount()));
        binding.checkSelectAll.setChecked(adapter.getTransactionCount() > 0
                && adapter.getSelectionCount() == adapter.getTransactionCount());
        binding.btnDeleteSelected.setEnabled(adapter.getSelectionCount() > 0);
        updateBackCallback();
    }

    private void confirmDeleteSelected() {
        List<Transaction> selected = adapter.getSelectedTransactions();
        if (selected.isEmpty()) return;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_selected_title)
                .setMessage(getString(R.string.delete_selected_message, selected.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_selected, (dialog, which) -> {
                    transactionViewModel.deleteTransactions(selected);
                    if (binding != null) adapter.clearSelection();
                }).show();
    }

    private void setupSwipe() {
        swipeHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public int getMovementFlags(@NonNull RecyclerView recycler, @NonNull RecyclerView.ViewHolder holder) {
                return makeMovementFlags(0, !adapter.isSelecting()
                        && adapter.transactionAt(holder.getAdapterPosition()) != null ? ItemTouchHelper.RIGHT : 0);
            }
            @Override public boolean onMove(@NonNull RecyclerView recycler, @NonNull RecyclerView.ViewHolder holder,
                                             @NonNull RecyclerView.ViewHolder target) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                Transaction transaction = adapter.transactionAt(holder.getAdapterPosition());
                if (transaction != null) transactionViewModel.deleteTransactions(java.util.Collections.singletonList(transaction));
            }
            @Override public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recycler,
                    @NonNull RecyclerView.ViewHolder holder, float dx, float dy, int state, boolean active) {
                if (dx > 0) {
                    View item = holder.itemView;
                    paint.setColor(requireContext().getColor(R.color.dashboard_expense_red));
                    canvas.drawRect(item.getLeft(), item.getTop(), item.getLeft() + dx, item.getBottom(), paint);
                    paint.setColor(requireContext().getColor(R.color.dashboard_brand_on_error));
                    paint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
                    canvas.save();
                    canvas.clipRect(item.getLeft(), item.getTop(), item.getLeft() + dx, item.getBottom());
                    canvas.drawText(getString(R.string.delete_selected), item.getLeft() + 20,
                            item.getTop() + item.getHeight() / 2f, paint);
                    canvas.restore();
                }
                super.onChildDraw(canvas, recycler, holder, dx, dy, state, active);
            }
        });
        swipeHelper.attachToRecyclerView(binding.rvTransactions);
        // The dashboard is a ViewPager2. Reserve rightward gestures that start on a transaction.
        binding.rvTransactions.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float downX, downY;
            private boolean transactionTouch;
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView recycler, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    View child = recycler.findChildViewUnder(downX, downY);
                    transactionTouch = child != null && adapter.transactionAt(recycler.getChildAdapterPosition(child)) != null;
                    recycler.getParent().requestDisallowInterceptTouchEvent(transactionTouch);
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && transactionTouch) {
                    float dx = event.getX() - downX, dy = event.getY() - downY;
                    int slop = ViewConfiguration.get(recycler.getContext()).getScaledTouchSlop();
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop)
                        recycler.getParent().requestDisallowInterceptTouchEvent(dx > 0 && Math.abs(dx) > Math.abs(dy));
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    recycler.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
    }

    /** A pull from the top of the activity stream opens the compact daily bar view. */
    private void setupActivityChartGesture() {
        binding.rvTransactions.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float startY, startX;
            private boolean startedAtTop;

            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView recycler, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startY = event.getY();
                    startX = event.getX();
                    startedAtTop = !recycler.canScrollVertically(-1);
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && startedAtTop
                        && event.getY() - startY > 64 * recycler.getResources().getDisplayMetrics().density
                        && Math.abs(event.getY() - startY) > Math.abs(event.getX() - startX) * 1.5f
                        && !adapter.isSelecting()) {
                    showActivityChart();
                    startedAtTop = false;
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        && Math.abs(event.getX() - startX) > ViewConfiguration.get(recycler.getContext()).getScaledTouchSlop()
                        && Math.abs(event.getX() - startX) > Math.abs(event.getY() - startY)) {
                    startedAtTop = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP) {
                    startedAtTop = false;
                }
                return false;
            }
        });
    }

    private void showActivityChart() {
        if (binding == null || binding.dailyChartOverlay.getVisibility() == View.VISIBLE) return;
        binding.dailyChartOverlay.animate().cancel();
        binding.dailyChartOverlay.setVisibility(View.VISIBLE);
        binding.dailyChartOverlay.setAlpha(0f);
        binding.dailyChartOverlay.setTranslationY(-32f * getResources().getDisplayMetrics().density);
        binding.dailyChartOverlay.animate().alpha(1f).translationY(0f).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 220 : 0).start();
        renderActivityChart(activityTransactions);
        updateBackCallback();
    }

    private void hideActivityChart() {
        if (binding == null) return;
        binding.dailyChartOverlay.animate().cancel();
        binding.dailyChartOverlay.animate().alpha(0f)
                .translationY(-20f * getResources().getDisplayMetrics().density)
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 160 : 0).withEndAction(() -> {
                    if (binding != null) {
                        binding.dailyChartOverlay.setVisibility(View.GONE);
                        updateBackCallback();
                    }
                }).start();
    }

    private void updateBackCallback() {
        if (selectionBack != null && binding != null) {
            selectionBack.setEnabled(isResumed() && (adapter.isSelecting()
                    || binding.dailyChartOverlay.getVisibility() == View.VISIBLE));
        }
    }

    private void renderActivityChart(List<Transaction> transactions) {
        if (binding == null) return;
        boolean week = binding.activityMetric.getCheckedButtonId() == R.id.metric_week;
        boolean line = binding.activityStyle.getCheckedButtonId() == R.id.style_line;
        boolean masked = Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue());
        Map<Long, Double> series = week
                ? DashboardChartSeries.spendingWeek(viewModel.getAllTransactions().getValue(), System.currentTimeMillis())
                : DashboardChartSeries.activity(transactions);
        String title = week ? getString(R.string.activity_week) : getString(R.string.daily_activity_title);
        binding.tvDailyChartTitle.setText(title);
        double total = 0;
        for (double amount : series.values()) total += amount;
        String caption = week ? "Last 7 calendar days · expenses only" : "Selected period · income, expenses and transfers";
        String scale = line ? "Linear scale" : "Square-root scale";
        binding.tvDailyChartSubtitle.setText(masked ? getString(R.string.chart_private)
                : caption + "\n" + DashboardChartRenderer.currency(total) + " total · " + scale);
        List<Long> dates = new ArrayList<>(series.keySet());
        com.github.mikephil.charting.listener.OnChartValueSelectedListener selection = new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override public void onValueSelected(com.github.mikephil.charting.data.Entry entry, com.github.mikephil.charting.highlight.Highlight highlight) {
                int index = Math.round(entry.getX());
                if (binding == null || Boolean.TRUE.equals(viewModel.isPrivacyModeEnabled().getValue()) || index < 0 || index >= dates.size()) return;
                binding.tvDailyChartSubtitle.setText(new java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(new java.util.Date(dates.get(index)))
                        + " · " + DashboardChartRenderer.currency(series.get(dates.get(index))) + "\n" + scale);
            }
            @Override public void onNothingSelected() { }
        };
        binding.dailyActivityChart.setOnChartValueSelectedListener(selection);
        binding.dailyActivityLine.setOnChartValueSelectedListener(selection);
        binding.dailyActivityChart.setVisibility(line ? View.GONE : View.VISIBLE);
        binding.dailyActivityLine.setVisibility(line ? View.VISIBLE : View.GONE);
        // Clear both chart buffers when privacy is enabled, including the hidden plot.
        DashboardChartRenderer.render(binding.dailyActivityChart, series, masked, title);
        DashboardChartRenderer.render(binding.dailyActivityLine, series, masked, title);
    }

    private void setupRecyclerView() {
        adapter = new GroupedTransactionAdapter(new GroupedTransactionAdapter.OnTransactionClickListener() {
            @Override
            public void onEdit(Transaction transaction) {
                Bundle args = new Bundle();
                args.putInt("transactionId", transaction.getId());
                Navigation.findNavController(requireView()).navigate(R.id.transactionFormFragment, args);
            }

            @Override
            public void onDelete(Transaction transaction) {
                transactionViewModel.deleteTransaction(transaction);
            }

            @Override
            public void onCategoryChange(Transaction transaction, String newCategory) {
                viewModel.updateTransactionCategory(transaction, newCategory);
            }

            @Override
            public List<String> getCategoriesByType(String type) {
                List<String> list = "INCOME".equals(type) ? incomeCategories : expenseCategories;
                List<String> result = new ArrayList<>();
                if (list.isEmpty()) {
                    result.add("Transfer");
                    if ("INCOME".equals(type)) {
                        result.addAll(Arrays.asList("Salary", "Allowance", "Bonus", "Petty Cash", "Gift", "Other"));
                    } else {
                        result.addAll(Arrays.asList("Food", "Rent", "Travel", "Shopping", "Medical", "Other"));
                    }
                } else {
                    if (!list.contains("Transfer")) {
                        result.add("Transfer");
                    }
                    result.addAll(list);
                }
                return result;
            }
        }, new GroupedTransactionAdapter.DataFormatter() {
            @Override public String formatAmount(double amount) { return viewModel.formatAmount(amount); }
            @Override public String maskPII(String value) { return transactionViewModel.maskPII(value); }
        });
        binding.rvTransactions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getGroupedTransactions().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items == null ? java.util.Collections.emptyList() : items, () -> {
                if (binding != null && restoredSelection != null && adapter.getTransactionCount() > 0) {
                    adapter.restoreSelection(restoredSelection);
                    restoredSelection = null;
                }
            });
            if (items == null || items.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.rvTransactions.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvTransactions.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            activityTransactions = transactions == null ? Collections.emptyList() : transactions;
            if (binding != null && binding.dailyChartOverlay.getVisibility() == View.VISIBLE) {
                renderActivityChart(activityTransactions);
            }
        });

        transactionViewModel.getDeletionResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            transactionViewModel.consumeDeletionResult();
            if (result.error != null) {
                adapter.notifyDataSetChanged(); // Reset a swiped row if persistence failed.
                Snackbar.make(binding.getRoot(), result.error, Snackbar.LENGTH_LONG).show();
            } else {
                Snackbar snackbar = Snackbar.make(binding.getRoot(),
                        getString(R.string.batch_deleted, result.count), Snackbar.LENGTH_LONG);
                if (result.count > 0 && result.undoId != null)
                    snackbar.setAction(R.string.undo, v -> transactionViewModel.restoreTransaction(result.undoId));
                snackbar.show();
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            adapter.notifyDataSetChanged();
            if (binding != null && binding.dailyChartOverlay.getVisibility() == View.VISIBLE) {
                renderActivityChart(activityTransactions);
            }
        });

        transactionViewModel.getIncomeCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) incomeCategories = list;
        });

        transactionViewModel.getExpenseCategories().observe(getViewLifecycleOwner(), list -> {
            if (list != null) expenseCategories = list;
        });
    }

    /** Called by {@link DashboardFragment} after biometric auth to force re-render with actual values. */
    public void refreshAdapter() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override public void onResume() {
        super.onResume();
        updateBackCallback();
    }

    @Override public void onPause() {
        if (binding != null) { binding.dailyChartOverlay.animate().cancel(); binding.dailyChartOverlay.setVisibility(View.GONE); }
        if (selectionBack != null) selectionBack.setEnabled(false);
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("weekExpenses", binding != null && binding.activityMetric.getCheckedButtonId() == R.id.metric_week);
        outState.putBoolean("linePlot", binding != null && binding.activityStyle.getCheckedButtonId() == R.id.style_line);
        outState.putBoolean("dailyChartVisible", binding != null && binding.dailyChartOverlay.getVisibility() == View.VISIBLE);
        if (adapter != null && adapter.isSelecting()) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (Transaction transaction : adapter.getSelectedTransactions()) ids.add(transaction.getId());
            outState.putIntegerArrayList("dailySelection", ids);
        }
    }

    @Override
    public void onDestroyView() {
        if (swipeHelper != null) swipeHelper.attachToRecyclerView(null);
        binding.dailyChartOverlay.animate().cancel();
        binding.rvTransactions.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }
}
