package com.example.spendtracker.data.local.dao;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TransactionBatchDeleteTest {
    @Test public void selectAllBatchesRespectSqliteParameterLimitAndKeepTheExactIds() {
        TransactionDao dao = mock(TransactionDao.class, CALLS_REAL_METHODS);
        when(dao.softDeleteBatch(anyList(), anyLong())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= 2100; i++) ids.add(i);
        assertEquals(2100, dao.softDeleteTransactions(ids, 123L));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Integer>> chunks = ArgumentCaptor.forClass(List.class);
        verify(dao, times(3)).softDeleteBatch(chunks.capture(), eq(123L));
        List<Integer> actual = new ArrayList<>();
        for (List<Integer> chunk : chunks.getAllValues()) {
            assertTrue(chunk.size() <= 900);
            actual.addAll(chunk);
        }
        assertEquals(ids, actual);
    }
}
