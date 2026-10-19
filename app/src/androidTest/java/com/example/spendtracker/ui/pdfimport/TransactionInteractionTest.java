package com.example.spendtracker.ui.pdfimport;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.GroupedTransactionAdapter;
import com.example.spendtracker.ui.testing.TransactionInteractionTestActivity;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TransactionInteractionTest {
    @Before public void requireUnlockedDevice() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.app.KeyguardManager keyguard = (android.app.KeyguardManager) context.getSystemService(
                android.content.Context.KEYGUARD_SERVICE);
        org.junit.Assume.assumeFalse("Unlock the device before running UI interaction tests",
                keyguard != null && keyguard.isKeyguardLocked());
    }

    @Test public void previewDeleteKeepsCandidateIdentityAfterListReorder() {
        Transaction first = transaction(0, "Synthetic One"), second = transaction(0, "Synthetic Two");
        List<Transaction> candidates = new ArrayList<>(Arrays.asList(first, second));
        AtomicReference<Transaction> removed = new AtomicReference<>();
        AtomicReference<PdfIngestionReviewAdapter> adapter = new AtomicReference<>();
        AtomicReference<RecyclerView> recycler = new AtomicReference<>();
        try (ActivityScenario<TransactionInteractionTestActivity> scenario =
                     ActivityScenario.launch(TransactionInteractionTestActivity.class)) {
            scenario.onActivity(activity -> {
                adapter.set(new PdfIngestionReviewAdapter(row -> {
                    removed.set(row);
                    candidates.remove(row);
                    adapter.get().submit(candidates);
                }));
                RecyclerView view = new RecyclerView(activity);
                view.setLayoutManager(new LinearLayoutManager(activity));
                view.setAdapter(adapter.get());
                activity.setContentView(view);
                recycler.set(view);
                adapter.get().submit(candidates);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withText("Synthetic One")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                RecyclerView.ViewHolder holder = recycler.get().findViewHolderForAdapterPosition(0);
                assertNotNull(holder);
                holder.itemView.findViewById(R.id.tv_review_delete).performClick();
                Collections.reverse(candidates);
                adapter.get().submit(candidates);
            });
            onView(withText("Remove")).perform(click());
            scenario.onActivity(activity -> {
                assertSame(first, removed.get());
                assertEquals(Collections.singletonList(second), candidates);
                assertEquals(Collections.singletonList(second), adapter.get().getSelectedTransactions());
            });
        }
    }

    @Test public void longPressStartsCheckboxSelectionAndSelectAllExcludesDateHeaders() {
        AtomicReference<GroupedTransactionAdapter> adapter = new AtomicReference<>();
        AtomicReference<RecyclerView> recycler = new AtomicReference<>();
        try (ActivityScenario<TransactionInteractionTestActivity> scenario =
                     ActivityScenario.launch(TransactionInteractionTestActivity.class)) {
            scenario.onActivity(activity -> {
                GroupedTransactionAdapter value = new GroupedTransactionAdapter(
                        new GroupedTransactionAdapter.OnTransactionClickListener() {
                            @Override public void onEdit(Transaction row) { fail("Selection must not open editing"); }
                            @Override public void onDelete(Transaction row) { fail("Long press must not delete"); }
                            @Override public void onCategoryChange(Transaction row, String category) { }
                            @Override public List<String> getCategoriesByType(String type) { return Collections.singletonList("Food"); }
                        }, new GroupedTransactionAdapter.DataFormatter() {
                            @Override public String formatAmount(double amount) { return String.valueOf(amount); }
                            @Override public String maskPII(String value) { return value; }
                        });
                value.enableSelection(() -> { });
                adapter.set(value);
                RecyclerView view = new RecyclerView(activity);
                view.setLayoutManager(new LinearLayoutManager(activity));
                view.setAdapter(value);
                activity.setContentView(view);
                recycler.set(view);
                value.submitList(Arrays.asList(new GroupedTransactionAdapter.HeaderItem(1000, 0, 200, 0),
                        new GroupedTransactionAdapter.TransactionItem(transaction(1, "Synthetic One")),
                        new GroupedTransactionAdapter.TransactionItem(transaction(2, "Synthetic Two"))));
            });
            onView(withText("Synthetic One")).perform(longClick());
            scenario.onActivity(activity -> {
                assertTrue(adapter.get().isSelecting());
                assertEquals(1, adapter.get().getSelectionCount());
                adapter.get().selectAll(true);
                assertEquals(2, adapter.get().getSelectedTransactions().size());
                assertNull(adapter.get().transactionAt(0));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                RecyclerView.ViewHolder holder = recycler.get().findViewHolderForAdapterPosition(2);
                assertNotNull(holder);
                holder.itemView.findViewById(R.id.check_daily_transaction).performClick();
                assertEquals(1, adapter.get().getSelectionCount());
                adapter.get().clearSelection();
                assertFalse(adapter.get().isSelecting());
            });
        }
    }

    private static Transaction transaction(int id, String name) {
        Transaction row = new Transaction(id, 100, "Food", "Synthetic", "EXPENSE",
                1788480000000L, "Account", "", "", name, "SBI", "Account");
        row.setTimestampPrecision("DATE_ONLY");
        return row;
    }
}
