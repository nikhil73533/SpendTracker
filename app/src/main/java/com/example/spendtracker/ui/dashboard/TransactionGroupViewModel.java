package com.example.spendtracker.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionGroupRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class TransactionGroupViewModel extends ViewModel {

    private final TransactionGroupRepository groupRepository;
    private final TransactionRepository transactionRepository;

    @Inject
    public TransactionGroupViewModel(TransactionGroupRepository groupRepository, TransactionRepository transactionRepository) {
        this.groupRepository = groupRepository;
        this.transactionRepository = transactionRepository;
    }

    public LiveData<List<TransactionGroupEntity>> getAllGroups() {
        return groupRepository.getAllGroups();
    }

    public LiveData<List<TransactionGroupEntity>> getActiveGroups() {
        return groupRepository.getAllActiveGroups();
    }

    public LiveData<TransactionGroupEntity> getGroupById(int id) {
        return groupRepository.getGroupById(id);
    }

    public LiveData<List<String>> getGroupCategories(int groupId) {
        return groupRepository.getGroupCategories(groupId);
    }

    public LiveData<List<Transaction>> getTransactionsForGroup(int groupId) {
        return groupRepository.getTransactionsForGroup(groupId);
    }

    public LiveData<List<GroupedTransactionAdapter.ListItem>> getGroupedTransactionsForGroup(int groupId) {
        return androidx.lifecycle.Transformations.map(getTransactionsForGroup(groupId), transactions -> {
            java.util.List<GroupedTransactionAdapter.ListItem> items = new java.util.ArrayList<>();
            if (transactions == null || transactions.isEmpty()) return items;

            java.util.Map<Long, java.util.List<Transaction>> grouped = new java.util.LinkedHashMap<>();
            for (Transaction t : transactions) {
                long tDay = getStartOfDay(t.getDate());
                if (!grouped.containsKey(tDay)) grouped.put(tDay, new java.util.ArrayList<>());
                java.util.List<Transaction> list = grouped.get(tDay);
                if (list != null) list.add(t);
            }

            for (java.util.Map.Entry<Long, java.util.List<Transaction>> entry : grouped.entrySet()) {
                double income = 0, expense = 0;
                for (Transaction t : entry.getValue()) {
                    if ("INCOME".equals(t.getType())) income += t.getAmount();
                    else if ("EXPENSE".equals(t.getType())) expense += t.getAmount();
                }
                items.add(new GroupedTransactionAdapter.HeaderItem(entry.getKey(), income, expense));
                for (Transaction t : entry.getValue()) {
                    items.add(new GroupedTransactionAdapter.TransactionItem(t));
                }
            }
            return items;
        });
    }

    private long getStartOfDay(long timestamp) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public LiveData<Integer> getTransactionCountForGroup(int groupId) {
        return groupRepository.getTransactionCountForGroup(groupId);
    }

    public void createGroup(String name, long startDate, long endDate, List<String> categories) {
        groupRepository.createGroup(name, startDate, endDate, categories);
    }

    public void updateGroup(int groupId, String name, long startDate, long endDate, List<String> categories) {
        groupRepository.updateGroup(groupId, name, startDate, endDate, categories);
    }

    public void deleteGroup(int groupId) {
        groupRepository.deleteGroup(groupId);
    }

    public LiveData<List<String>> getCategories() {
        return transactionRepository.getCategories();
    }
}
