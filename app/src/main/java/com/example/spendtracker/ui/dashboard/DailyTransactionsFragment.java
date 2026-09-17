package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Canvas;
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
import java.util.List;

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
            @Override public void handleOnBackPressed() { adapter.clearSelection(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), selectionBack);
        adapter.enableSelection(this::renderSelection);
        binding.btnCancelSelection.setOnClickListener(v -> adapter.clearSelection());
        binding.btnDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());
        binding.checkSelectAll.setOnClickListener(v -> adapter.selectAll(binding.checkSelectAll.isChecked()));
        setupSwipe();
        observeViewModel();
    }

    private void renderSelection() {
        if (binding == null) return;
        binding.selectionBar.setVisibility(adapter.isSelecting() ? View.VISIBLE : View.GONE);
        binding.tvSelectionCount.setText(getString(R.string.selection_count, adapter.getSelectionCount()));
        binding.checkSelectAll.setChecked(adapter.getTransactionCount() > 0
                && adapter.getSelectionCount() == adapter.getTransactionCount());
        binding.btnDeleteSelected.setEnabled(adapter.getSelectionCount() > 0);
        if (selectionBack != null) selectionBack.setEnabled(adapter.isSelecting());
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
                    paint.setColor(0xFFD32F2F);
                    canvas.drawRect(item.getLeft(), item.getTop(), item.getLeft() + dx, item.getBottom(), paint);
                    paint.setColor(android.graphics.Color.WHITE);
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (adapter != null && adapter.isSelecting()) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (Transaction transaction : adapter.getSelectedTransactions()) ids.add(transaction.getId());
            outState.putIntegerArrayList("dailySelection", ids);
        }
    }

    @Override
    public void onDestroyView() {
        if (swipeHelper != null) swipeHelper.attachToRecyclerView(null);
        binding.rvTransactions.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }
}
