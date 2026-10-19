package com.example.spendtracker.ui.dashboard;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.*;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ChartDataLifecycleTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    @Test public void chartReadsTheSameObservedStreamOnFirstLoadAndUpdates() {
        TransactionRepository repo = mock(TransactionRepository.class);
        MutableLiveData<List<Transaction>> rows = new MutableLiveData<>();
        // Each repository call makes a new lazy mapped stream, as the actual repository does.
        when(repo.getTransactions()).thenAnswer(i -> Transformations.map(rows, value -> value));
        android.content.Context context = mock(android.content.Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        DashboardViewModel vm = new DashboardViewModel(repo, null, context);
        LiveData<List<Transaction>> data = vm.getAllTransactions();
        androidx.lifecycle.Observer<List<Transaction>> observer = value -> {};
        data.observeForever(observer);
        try {
            rows.setValue(Collections.singletonList(new Transaction()));
            assertSame(data, vm.getAllTransactions());
            assertEquals(1, vm.getAllTransactions().getValue().size());
            rows.setValue(Collections.emptyList());
            assertTrue(vm.getAllTransactions().getValue().isEmpty());
        } finally { data.removeObserver(observer); }
    }
}
