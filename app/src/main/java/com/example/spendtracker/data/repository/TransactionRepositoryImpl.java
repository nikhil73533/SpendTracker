package com.example.spendtracker.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.dao.TransactionGroupDao;
import com.example.spendtracker.data.local.dao.RepeatedAlertDao;
import com.example.spendtracker.data.local.entity.RepeatedAlertEntity;
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
    private final TransactionGroupDao transactionGroupDao;
    private final RepeatedAlertDao repeatedAlertDao;
    private final ExecutorService executorService;

    /**
     * Canonical bank-name normalization table.
     * Maps patterns (lowercase, may be partial) → canonical short name.
     */
    private static final String[][] BANK_NORMALIZATION = {
        {"icici",    "ICICI"},
        {"hdfc",     "HDFC"},
        {"axis",     "Axis"},
        {"kotak",    "Kotak"},
        {"sbi",      "SBI"},
        {"state bank", "SBI"},
        {"pnb",      "PNB"},
        {"union bank", "Union Bank"},
        {"bank of baroda", "Bank of Baroda"},
        {"bob",      "Bank of Baroda"},
        {"yes bank", "Yes Bank"},
        {"yesb",     "Yes Bank"},
        {"canara",   "Canara"},
        {"idbi",     "IDBI"},
        {"indusind", "IndusInd"},
        {"federal",  "Federal"},
        {"rbl",      "RBL"},
        {"au bank",  "AU Bank"},
        {"au small", "AU Bank"},
        {"bajaj",    "Bajaj Finance"},
        {"paytm",    "Paytm"},
        {"phonepe",  "PhonePe"},
        {"amazon pay","Amazon Pay"},
        {"airtel payments","Airtel Payments"},
        {"jio payments","Jio Payments"},
        {"onecard",  "OneCard"},
        {"one card", "OneCard"},
        {"slice",    "Slice"},
        {"navi",     "Navi"},
    };

    @Inject
    public TransactionRepositoryImpl(TransactionDao transactionDao, CategoryDao categoryDao, TransactionGroupDao transactionGroupDao, RepeatedAlertDao repeatedAlertDao) {
        this.transactionDao = transactionDao;
        this.categoryDao = categoryDao;
        this.transactionGroupDao = transactionGroupDao;
        this.repeatedAlertDao = repeatedAlertDao;
        this.executorService = Executors.newSingleThreadExecutor();
        // Normalize existing bank names in the background on first run
        executorService.execute(() -> {
            normalizeBankNamesOnce();
            syncTransferDataOnce();
        });
    }

    // ── Bank name normalization ──────────────────────────────────────────────

    /**
     * Returns the canonical short bank name for the given raw string.
     * E.g. "ICICI Bank" → "ICICI", "ICICi Bank" → "ICICI".
     */
    public static String standardizeBankName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        String lower = raw.trim().toLowerCase();
        for (String[] entry : BANK_NORMALIZATION) {
            if (lower.contains(entry[0])) return entry[1];
        }
        return raw.trim();
    }

    /** One-time background migration: normalizes bankName for all existing rows. */
    private void normalizeBankNamesOnce() {
        try {
            List<TransactionEntity> all = transactionDao.getAllTransactionsSync();
            for (TransactionEntity e : all) {
                if (e.bankName == null || e.bankName.isEmpty()) continue;
                String canonical = standardizeBankName(e.bankName);
                if (!canonical.equals(e.bankName)) {
                    e.bankName = canonical;
                    // also update source field for consistency
                    if (e.source != null && e.source.contains("(")) {
                        e.source = canonical + " (" + e.sourceType + ")";
                    }
                    transactionDao.updateTransaction(e);
                }
            }
        } catch (Exception ignored) {}
    }

    /** One-time background migration: ensures category "Transfer" implies type "TRANSFER". */
    private void syncTransferDataOnce() {
        try {
            List<TransactionEntity> all = transactionDao.getAllTransactionsSync();
            for (TransactionEntity e : all) {
                if ("Transfer".equalsIgnoreCase(e.categoryName) && !"TRANSFER".equals(e.type)) {
                    e.type = "TRANSFER";
                    transactionDao.updateTransaction(e);
                }
            }
        } catch (Exception ignored) {}
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

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
        executorService.execute(() -> {
            long newId = transactionDao.insertTransaction(mapToEntity(transaction));
            if (newId > 0 && transaction.getReceiverName() != null && !transaction.getReceiverName().trim().isEmpty()) {
                // Check for duplicate transactions within 48 hours
                long fortyEightHours = 48L * 60 * 60 * 1000;
                long start = transaction.getDate() - fortyEightHours;
                long end = transaction.getDate() + fortyEightHours;
                
                List<TransactionEntity> duplicates = repeatedAlertDao.findPotentialDuplicates(
                        transaction.getReceiverName(), transaction.getAmount(), (int) newId, start, end);
                
                for (TransactionEntity dup : duplicates) {
                    if (repeatedAlertDao.alertExistsForPair((int) newId, dup.id) == 0 &&
                        repeatedAlertDao.alertExistsForPair(dup.id, (int) newId) == 0) {
                        
                        RepeatedAlertEntity alert = new RepeatedAlertEntity(
                                transaction.getReceiverName().trim(),
                                transaction.getAmount(),
                                Math.min(transaction.getDate(), dup.date),
                                Math.max(transaction.getDate(), dup.date),
                                transaction.getDate() < dup.date ? (int) newId : dup.id,
                                transaction.getDate() >= dup.date ? (int) newId : dup.id,
                                transaction.getCategoryName()
                        );
                        repeatedAlertDao.insert(alert);
                        break; // Only create one alert for the most recent match
                    }
                }
            }
        });
    }

    @Override
    public void updateTransaction(Transaction transaction) {
        executorService.execute(() -> transactionDao.updateTransaction(mapToEntity(transaction)));
    }

    @Override
    public void deleteTransaction(Transaction transaction) {
        // Soft delete: mark as DELETED instead of removing
        executorService.execute(() -> transactionDao.softDeleteTransaction(transaction.getId(), System.currentTimeMillis()));
    }

    @Override
    public void softDeleteTransaction(int transactionId) {
        executorService.execute(() -> transactionDao.softDeleteTransaction(transactionId, System.currentTimeMillis()));
    }

    @Override
    public void restoreTransaction(int transactionId) {
        executorService.execute(() -> transactionDao.restoreTransaction(transactionId));
    }

    @Override
    public LiveData<List<Transaction>> getDeletedTransactions() {
        return Transformations.map(transactionDao.getDeletedTransactions(), entities -> {
            List<Transaction> transactions = new ArrayList<>();
            for (TransactionEntity entity : entities) {
                transactions.add(mapToDomain(entity));
            }
            return transactions;
        });
    }

    @Override
    public void permanentlyDeleteTransaction(int transactionId) {
        executorService.execute(() -> transactionDao.permanentlyDeleteTransaction(transactionId));
    }

    // ── Summary ───────────────────────────────────────────────────────────────

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

        Runnable update = () -> updateSummary(summaryMediator,
            incomeLive.getValue(), expenseLive.getValue(), accountLive.getValue(),
            expSumsLive.getValue(), expAvgsLive.getValue(), incSumsLive.getValue(), incAvgsLive.getValue());

        summaryMediator.addSource(incomeLive,  v -> update.run());
        summaryMediator.addSource(expenseLive, v -> update.run());
        summaryMediator.addSource(accountLive, v -> update.run());
        summaryMediator.addSource(expSumsLive, v -> update.run());
        summaryMediator.addSource(expAvgsLive, v -> update.run());
        summaryMediator.addSource(incSumsLive, v -> update.run());
        summaryMediator.addSource(incAvgsLive, v -> update.run());

        return summaryMediator;
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @Override
    public LiveData<List<String>> getCategories() {
        return Transformations.map(categoryDao.getAllCategories(), entities -> {
            List<String> names = new ArrayList<>();
            for (CategoryEntity entity : entities) names.add(entity.name);
            return names;
        });
    }

    @Override
    public LiveData<List<String>> getCategoriesByType(String type) {
        return Transformations.map(categoryDao.getCategoriesByType(type), entities -> {
            List<String> names = new ArrayList<>();
            for (CategoryEntity entity : entities) names.add(entity.name);
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
                if (c.name.equals(name)) { categoryDao.deleteCategory(c); break; }
            }
        });
    }

    @Override
    public void renameCategory(String oldName, String newName) {
        executorService.execute(() -> {
            transactionDao.renameCategory(oldName, newName);
            List<CategoryEntity> categories = categoryDao.getAllCategoriesSync();
            for (CategoryEntity c : categories) {
                if (c.name.equals(oldName)) { c.name = newName; categoryDao.updateCategory(c); break; }
            }
        });
    }

    // ── Trend queries ─────────────────────────────────────────────────────────

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getDailyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getDailyTotals(start, end, type), this::mapTrends);
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getWeeklyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getWeeklyTotals(start, end, type), this::mapTrends);
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getMonthlyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getMonthlyTotals(start, end, type), this::mapTrends);
    }

    @Override
    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getAnnuallyTotals(long start, long end, String type) {
        return Transformations.map(transactionDao.getAnnuallyTotals(start, end, type), this::mapTrends);
    }

    private List<com.example.spendtracker.domain.model.DailyTrend> mapTrends(List<TransactionDao.TimeSum> list) {
        List<com.example.spendtracker.domain.model.DailyTrend> trends = new ArrayList<>();
        if (list != null) {
            for (TransactionDao.TimeSum item : list) {
                trends.add(new com.example.spendtracker.domain.model.DailyTrend(item.timestamp, item.total));
            }
        }
        return trends;
    }

    // ── Account / chart queries ───────────────────────────────────────────────

    @Override
    public List<Transaction> getTransactionsSync() {
        List<TransactionEntity> entities = transactionDao.getAllTransactionsSync();
        List<Transaction> transactions = new ArrayList<>();
        for (TransactionEntity entity : entities) transactions.add(mapToDomain(entity));
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
            for (TransactionEntity entity : entities) transactions.add(mapToDomain(entity));
            return transactions;
        });
    }

    @Override
    public LiveData<List<TransactionDao.CategorySum>> getWeekdayWeekendTotals(long start, long end, String type) {
        return transactionDao.getWeekdayWeekendTotals(start, end, type);
    }

    @Override
    public LiveData<List<TransactionDao.CategorySum>> getBankTotals(long start, long end, String type) {
        return transactionDao.getBankTotals(start, end, type);
    }

    @Override
    public LiveData<List<TransactionDao.CategorySum>> getSourceTypeTotals(long start, long end, String type) {
        return transactionDao.getSourceTypeTotals(start, end, type);
    }

    @Override
    public LiveData<Double> getTotalCardExpense(long start, long end) {
        return transactionDao.getCreditCardExpense(start, end);
    }

    @Override
    public LiveData<Double> getTotalAccountExpense(long start, long end) {
        return transactionDao.getAccountExpense(start, end);
    }

    @Override
    public LiveData<Double> getTotalTransfer(long start, long end) {
        return transactionDao.getTransferTotal(start, end);
    }

    @Override
    public LiveData<Double> getTotalTransferIncoming(long start, long end) {
        return transactionDao.getTransferIncoming(start, end);
    }

    @Override
    public LiveData<Double> getTotalTransferOutgoing(long start, long end) {
        return transactionDao.getTransferOutgoing(start, end);
    }

    @Override
    public void markAsRead(String accountName) {
        executorService.execute(() -> transactionDao.markAsRead(accountName));
    }

    @Override
    public LiveData<List<String>> getUniqueContacts() {
        return transactionDao.getUniqueContacts();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void updateSummary(MediatorLiveData<Summary> summaryMediator, Double income, Double expense, Double account,
                               List<TransactionDao.CategorySum> expSums, List<TransactionDao.CategorySum> expAvgs,
                               List<TransactionDao.CategorySum> incSums, List<TransactionDao.CategorySum> incAvgs) {
        double totalIncome = income != null ? income : 0.0;
        double totalExpense = expense != null ? expense : 0.0;
        double totalAccount = account != null ? account : 0.0;

        summaryMediator.setValue(new Summary(
            totalIncome, totalExpense, totalAccount,
            toMap(expSums), toMap(expAvgs), toMap(incSums), toMap(incAvgs)
        ));
    }

    private Map<String, Double> toMap(List<TransactionDao.CategorySum> list) {
        Map<String, Double> map = new HashMap<>();
        if (list != null) {
            for (TransactionDao.CategorySum sum : list) map.put(sum.category, sum.total);
        }
        return map;
    }

    private Transaction mapToDomain(TransactionEntity entity) {
        if (entity == null) return null;
        Transaction t = new Transaction(
            entity.id, entity.amount, entity.categoryName, entity.categoryEmoji, entity.description,
            entity.type, entity.date, entity.source, entity.sender, entity.upiId,
            entity.receiverName, entity.bankName, entity.sourceType,
            entity.fromAccount, entity.toAccount, entity.fees
        );
        t.setTransactionGroupId(entity.transactionGroupId);
        t.setStatus(entity.status != null ? entity.status : "ACTIVE");
        t.setDeletedAt(entity.deletedAt);
        
        // Removed synchronous group name lookup to avoid main-thread crash in LiveData transformations
        return t;
    }

    private TransactionEntity mapToEntity(Transaction transaction) {
        if (transaction == null) return null;
        TransactionEntity entity = new TransactionEntity(
            transaction.getId(), transaction.getAmount(), transaction.getCategoryName(),
            transaction.getCategoryEmoji(), transaction.getDescription(), transaction.getType(),
            transaction.getDate(), transaction.getSource(), transaction.getSender(),
            transaction.getUpiId(), transaction.getReceiverName(), transaction.getBankName(),
            transaction.getSourceType(), transaction.getFromAccount(), transaction.getToAccount(),
            transaction.getFees()
        );
        entity.transactionGroupId = transaction.getTransactionGroupId();
        entity.status = transaction.getStatus() != null ? transaction.getStatus() : "ACTIVE";
        entity.deletedAt = transaction.getDeletedAt();
        return entity;
    }
}
