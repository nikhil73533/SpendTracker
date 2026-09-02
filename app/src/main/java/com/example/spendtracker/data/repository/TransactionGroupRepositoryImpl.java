package com.example.spendtracker.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.dao.TransactionGroupDao;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import com.example.spendtracker.di.MainDatabase;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionGroupRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

public class TransactionGroupRepositoryImpl implements TransactionGroupRepository {

    private final TransactionGroupDao groupDao;
    private final TransactionDao transactionDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public TransactionGroupRepositoryImpl(TransactionGroupDao groupDao, @MainDatabase TransactionDao transactionDao) {
        this.groupDao = groupDao;
        this.transactionDao = transactionDao;
    }

    @Override
    public void createGroup(String name, long startDate, long endDate, List<String> categoryNames) {
        executor.execute(() -> {
            TransactionGroupEntity group = new TransactionGroupEntity(0, name, startDate, endDate);
            group.tag = TransactionGroupEntity.deriveTag(name);
            groupDao.insertGroupWithCategories(group, categoryNames);
        });
    }

    @Override
    public void updateGroup(int groupId, String name, long startDate, long endDate, List<String> categoryNames) {
        executor.execute(() -> {
            TransactionGroupEntity group = groupDao.getGroupByIdSync(groupId);
            if (group == null) return;
            group.name = name;
            group.startDate = startDate;
            group.endDate = endDate;
            group.tag = TransactionGroupEntity.deriveTag(name);
            groupDao.updateGroupWithCategories(group, categoryNames);
        });
    }

    @Override
    public void deleteGroup(int groupId) {
        executor.execute(() -> {
            groupDao.disassociateTransactionsFromGroup(groupId);
            TransactionGroupEntity group = groupDao.getGroupByIdSync(groupId);
            if (group != null) groupDao.deleteGroup(group);
        });
    }

    @Override
    public LiveData<List<TransactionGroupEntity>> getAllActiveGroups() {
        return groupDao.getAllActiveGroups();
    }

    @Override
    public LiveData<List<TransactionGroupEntity>> getAllGroups() {
        return groupDao.getAllGroups();
    }

    @Override
    public LiveData<TransactionGroupEntity> getGroupById(int id) {
        return groupDao.getGroupById(id);
    }

    @Override
    public LiveData<List<String>> getGroupCategories(int groupId) {
        return groupDao.getGroupCategories(groupId);
    }

    @Override
    public LiveData<List<Transaction>> getTransactionsForGroup(int groupId) {
        return Transformations.map(groupDao.getTransactionsForGroup(groupId), entities -> {
            List<Transaction> transactions = new ArrayList<>();
            if (entities != null) {
                for (TransactionEntity e : entities) {
                    transactions.add(mapToDomain(e));
                }
            }
            return transactions;
        });
    }

    @Override
    public LiveData<Integer> getTransactionCountForGroup(int groupId) {
        return groupDao.getTransactionCountForGroup(groupId);
    }

    @Override
    public void evaluateTransactionsForGroup(int groupId) {
        executor.execute(() -> {
            TransactionGroupEntity group = groupDao.getGroupByIdSync(groupId);
            if (group != null && group.isActive) {
                groupDao.associateTransactionsWithGroup(groupId, group.startDate, group.endDate);
            }
        });
    }

    /**
     * Evaluates a newly inserted transaction against all active groups.
     * Conflict resolution: most recently created group wins (deterministic: highest createdAt).
     */
    @Override
    public void evaluateNewTransaction(int transactionId) {
        executor.execute(() -> {
            TransactionEntity txn = transactionDao.getTransactionByIdSync(transactionId);
            if (txn == null || !"ACTIVE".equals(txn.status)) return;

            List<TransactionGroupEntity> groups = groupDao.getActiveGroupsForDate(txn.date);
            for (TransactionGroupEntity group : groups) {
                List<String> categories = groupDao.getGroupCategoriesSync(group.id);
                if (categories.contains(txn.category)) {
                    txn.transactionGroupId = group.id;
                    transactionDao.updateTransaction(txn);
                    return; // First match (most recent due to ORDER BY createdAt DESC)
                }
            }
        });
    }

    @Override
    public String getGroupNameSync(int groupId) {
        if (groupId <= 0) return null;
        return groupDao.getGroupNameSync(groupId);
    }

    private Transaction mapToDomain(TransactionEntity entity) {
        if (entity == null) return null;
        Transaction t = new Transaction(
            entity.id, entity.amount,
            entity.category != null ? entity.category : "",
            entity.categoryEmoji != null ? entity.categoryEmoji : "",
            entity.description != null ? entity.description : "",
            entity.type != null ? entity.type : "EXPENSE",
            entity.date,
            entity.source != null ? entity.source : "",
            entity.sender != null ? entity.sender : "",
            entity.upiId != null ? entity.upiId : "",
            entity.receiverName != null ? entity.receiverName : "",
            entity.bankName != null ? entity.bankName : "",
            entity.sourceType != null ? entity.sourceType : "",
            entity.fromAccount != null ? entity.fromAccount : "",
            entity.toAccount != null ? entity.toAccount : "",
            entity.fees
        );
        t.setTransactionGroupId(entity.transactionGroupId);
        t.setStatus(entity.status != null ? entity.status : "ACTIVE");
        t.setDeletedAt(entity.deletedAt);
        return t;
    }
}
