package com.example.spendtracker.domain.repository;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import com.example.spendtracker.domain.model.Transaction;
import java.util.List;

public interface TransactionGroupRepository {
    void createGroup(String name, long startDate, long endDate, List<String> categoryNames);
    void updateGroup(int groupId, String name, long startDate, long endDate, List<String> categoryNames);
    void deleteGroup(int groupId);
    LiveData<List<TransactionGroupEntity>> getAllActiveGroups();
    LiveData<List<TransactionGroupEntity>> getAllGroups();
    LiveData<TransactionGroupEntity> getGroupById(int id);
    LiveData<List<String>> getGroupCategories(int groupId);
    LiveData<List<Transaction>> getTransactionsForGroup(int groupId);
    LiveData<Integer> getTransactionCountForGroup(int groupId);
    void evaluateTransactionsForGroup(int groupId);
    void evaluateNewTransaction(int transactionId);
    String getGroupNameSync(int groupId);
}
