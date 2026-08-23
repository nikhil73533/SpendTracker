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
