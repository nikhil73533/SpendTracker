package com.example.spendtracker.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

public class TransactionRepositoryImpl implements TransactionRepository {
    private final TransactionDao transactionDao;
    private final CategoryDao categoryDao;
    private final ExecutorService executorService;

    @Inject
    public TransactionRepositoryImpl(TransactionDao transactionDao, CategoryDao categoryDao) {
        this.transactionDao = transactionDao;
        this.categoryDao = categoryDao;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<List<Transaction>> getTransactions() {
        return Transformations.map(transactionDao.getAllTransactions(), entities -> {
            List<Transaction> transactions = new ArrayList<>();
            for (TransactionEntity entity : entities) {
                transactions.add(mapToDomain(entity));
            }
            return transactions;
        });
    }

    @Override
    public LiveData<List<Transaction>> getTransactionsInRange(long start, long end) {
        return Transformations.map(transactionDao.getTransactionsInRange(start, end), entities -> {
            List<Transaction> transactions = new ArrayList<>();
            for (TransactionEntity entity : entities) {
                transactions.add(mapToDomain(entity));
            }
            return transactions;
        });
    }

    @Override
    public LiveData<Transaction> getTransactionById(int id) {
        return Transformations.map(transactionDao.getTransactionById(id), this::mapToDomain);
    }

    @Override
    public void addTransaction(Transaction transaction) {
        executorService.execute(() -> transactionDao.insertTransaction(mapToEntity(transaction)));
    }

    @Override
    public void updateTransaction(Transaction transaction) {
        executorService.execute(() -> transactionDao.updateTransaction(mapToEntity(transaction)));
    }

    @Override
    public void deleteTransaction(Transaction transaction) {
        executorService.execute(() -> transactionDao.deleteTransaction(mapToEntity(transaction)));
    }

    @Override
    public LiveData<Summary> getSummary(long startDate, long endDate) {
        MediatorLiveData<Summary> summaryMediator = new MediatorLiveData<>();
        
        LiveData<Double> incomeLive = transactionDao.getTotalIncome(startDate, endDate);
        LiveData<Double> expenseLive = transactionDao.getTotalExpense(startDate, endDate);
        LiveData<Double> accountLive = transactionDao.getTotalAccountTransaction(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> expSumsLive = transactionDao.getExpenseCategorySummaries(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> expAvgsLive = transactionDao.getExpenseCategoryAverages(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> incSumsLive = transactionDao.getIncomeCategorySummaries(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> incAvgsLive = transactionDao.getIncomeCategoryAverages(startDate, endDate);

        summaryMediator.addSource(incomeLive, v -> updateSummary(summaryMediator, v, expenseLive.getValue(), accountLive.getValue(), expSumsLive.getValue(), expAvgsLive.getValue(), incSumsLive.getValue(), incAvgsLive.getValue()));
        summaryMediator.addSource(expenseLive, v -> updateSummary(summaryMediator, incomeLive.getValue(), v, accountLive.getValue(), expSumsLive.getValue(), expAvgsLive.getValue(), incSumsLive.getValue(), incAvgsLive.getValue()));
        summaryMediator.addSource(accountLive, v -> updateSummary(summaryMediator, incomeLive.getValue(), expenseLive.getValue(), v, expSumsLive.getValue(), expAvgsLive.getValue(), incSumsLive.getValue(), incAvgsLive.getValue()));
        summaryMediator.addSource(expSumsLive, v -> updateSummary(summaryMediator, incomeLive.getValue(), expenseLive.getValue(), accountLive.getValue(), v, expAvgsLive.getValue(), incSumsLive.getValue(), incAvgsLive.getValue()));
        summaryMediator.addSource(expAvgsLive, v -> updateSummary(summaryMediator, incomeLive.getValue(), expenseLive.getValue(), accountLive.getValue(), expSumsLive.getValue(), v, incSumsLive.getValue(), incAvgsLive.getValue()));
        summaryMediator.addSource(incSumsLive, v -> updateSummary(summaryMediator, incomeLive.getValue(), expenseLive.getValue(), accountLive.getValue(), expSumsLive.getValue(), expAvgsLive.getValue(), v, incAvgsLive.getValue()));
        summaryMediator.addSource(incAvgsLive, v -> updateSummary(summaryMediator, incomeLive.getValue(), expenseLive.getValue(), accountLive.getValue(), expSumsLive.getValue(), expAvgsLive.getValue(), incSumsLive.getValue(), v));

        return summaryMediator;
    }

    @Override
    public LiveData<List<String>> getCategories() {
        return Transformations.map(categoryDao.getAllCategories(), entities -> {
            List<String> names = new ArrayList<>();
            for (com.example.spendtracker.data.local.entity.CategoryEntity entity : entities) {
                names.add(entity.name);
            }
            return names;
        });
    }

    @Override
    public LiveData<List<String>> getCategoriesByType(String type) {
        return Transformations.map(categoryDao.getCategoriesByType(type), entities -> {
            List<String> names = new ArrayList<>();
            for (com.example.spendtracker.data.local.entity.CategoryEntity entity : entities) {
                names.add(entity.name);
            }
            return names;
        });
    }

    @Override
    public void addCategory(String name, String type) {
        executorService.execute(() -> categoryDao.insertCategory(new CategoryEntity(0, name, null, false, type)));
    }

    @Override
    public void deleteCategory(String name) {
        executorService.execute(() -> {
            List<CategoryEntity> categories = categoryDao.getAllCategoriesSync();
            for (CategoryEntity c : categories) {
                if (c.name.equals(name)) {
                    categoryDao.deleteCategory(c);
                    break;
                }
            }
        });
    }

    @Override
    public void renameCategory(String oldName, String newName) {
        executorService.execute(() -> {
            transactionDao.renameCategory(oldName, newName);
            List<CategoryEntity> categories = categoryDao.getAllCategoriesSync();
            for (CategoryEntity c : categories) {
                if (c.name.equals(oldName)) {
                    c.name = newName;
                    categoryDao.updateCategory(c);
                    break;
                }
            }
        });
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getDailyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getDailyTotals(start, end, type), list -> mapTrends(list));
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getWeeklyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getWeeklyTotals(start, end, type), list -> mapTrends(list));
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getMonthlyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getMonthlyTotals(start, end, type), list -> mapTrends(list));
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getAnnuallyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getAnnuallyTotals(start, end, type), list -> mapTrends(list));
    }

    private List<com.example.spendtracker.domain.model.DailyTrend> mapTrends(List<TransactionDao.TimeSum> list) {
        List<com.example.spendtracker.domain.model.DailyTrend> trends = new ArrayList<>();
        if (list != null) {
            for (com.example.spendtracker.data.local.dao.TransactionDao.TimeSum item : list) {
                trends.add(new com.example.spendtracker.domain.model.DailyTrend(item.timestamp, item.total));
            }
        }
        return trends;
    }

    @Override
    public List<Transaction> getTransactionsSync() {
        List<TransactionEntity> entities = transactionDao.getAllTransactionsSync();
        List<Transaction> transactions = new ArrayList<>();
        for (TransactionEntity entity : entities) {
            transactions.add(mapToDomain(entity));
        }
        return transactions;
    }

    @Override
    public LiveData<List<TransactionDao.AccountSummary>> getUniqueAccounts() {
        return transactionDao.getUniqueAccounts();
    }

    @Override
    public LiveData<List<Transaction>> getAccountHistory(String accountId, long start, long end) {
        return Transformations.map(transactionDao.getAccountHistory(accountId, start, end), entities -> {
            List<Transaction> transactions = new ArrayList<>();
            for (TransactionEntity entity : entities) {
                transactions.add(mapToDomain(entity));
            }
            return transactions;
        });
    }

    private void updateSummary(MediatorLiveData<Summary> summaryMediator, Double income, Double expense, Double account, 
                               List<TransactionDao.CategorySum> expSums, List<TransactionDao.CategorySum> expAvgs,
                               List<TransactionDao.CategorySum> incSums, List<TransactionDao.CategorySum> incAvgs) {
        double totalIncome = income != null ? income : 0.0;
        double totalExpense = expense != null ? expense : 0.0;
        
        // Fix: Total value should be negative if expense is greater than income
        double totalAccount = totalIncome - totalExpense;
        
        summaryMediator.setValue(new Summary(
            totalIncome, totalExpense, totalAccount,
            toMap(expSums), toMap(expAvgs), toMap(incSums), toMap(incAvgs)
        ));
    }

    private Map<String, Double> toMap(List<TransactionDao.CategorySum> list) {
        Map<String, Double> map = new HashMap<>();
        if (list != null) {
            for (TransactionDao.CategorySum sum : list) {
                map.put(sum.category, sum.total);
            }
        }
        return map;
    }

    private Transaction mapToDomain(TransactionEntity entity) {
        if (entity == null) return null;
        return new Transaction(
                entity.id,
                entity.amount,
                entity.category,
                entity.description,
                entity.type,
                entity.date,
                entity.source,
                entity.sender,
                entity.upiId,
                entity.receiverName,
                entity.bankName,
                entity.sourceType,
                entity.fromAccount,
                entity.toAccount,
                entity.fees
        );
    }

    private TransactionEntity mapToEntity(Transaction transaction) {
        if (transaction == null) return null;
        return new TransactionEntity(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getType(),
                transaction.getDate(),
                transaction.getSource(),
                transaction.getSender(),
                transaction.getUpiId(),
                transaction.getReceiverName(),
                transaction.getBankName(),
                transaction.getSourceType(),
                transaction.getFromAccount(),
                transaction.getToAccount(),
                transaction.getFees()
        );
    }
}
