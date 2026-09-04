package com.example.spendtracker.data.repository;

import android.content.Context;
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
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import com.example.spendtracker.di.ClonedDatabase;
import com.example.spendtracker.di.MainDatabase;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.model.BulkImportResult;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.spendtracker.util.BudgetNotificationHelper;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

public class TransactionRepositoryImpl implements TransactionRepository {
    private final TransactionDao transactionDao;
    private final TransactionDao clonedTransactionDao;
    private final CategoryDao categoryDao;
    private final TransactionGroupDao transactionGroupDao;
    private final RepeatedAlertDao repeatedAlertDao;
    private final Context context;
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
    public TransactionRepositoryImpl(@ApplicationContext Context context,
                                   @MainDatabase TransactionDao transactionDao,
                                   @ClonedDatabase TransactionDao clonedTransactionDao,
                                   CategoryDao categoryDao,
                                   TransactionGroupDao transactionGroupDao,
                                   RepeatedAlertDao repeatedAlertDao) {
        this.context = context;
        this.transactionDao = transactionDao;
        this.clonedTransactionDao = clonedTransactionDao;
        this.categoryDao = categoryDao;
        this.transactionGroupDao = transactionGroupDao;
        this.repeatedAlertDao = repeatedAlertDao;
        this.executorService = Executors.newSingleThreadExecutor();
        // Normalize existing bank names & categories in the background on first run
        executorService.execute(() -> {
            normalizeBankNamesOnce();
            syncTransferDataOnce();
            ensureTransferCategoriesExist();
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
                if ("Transfer".equalsIgnoreCase(e.category) && !"TRANSFER".equals(e.type)) {
                    e.type = "TRANSFER";
                    transactionDao.updateTransaction(e);
                }
            }
        } catch (Exception ignored) {}
    }

    /** Ensures that category "Transfer" exists in both EXPENSE and INCOME without duplicates. */
    private void ensureTransferCategoriesExist() {
        try {
            List<CategoryEntity> expenseCategories = categoryDao.getCategoriesByTypeSync("EXPENSE");
            boolean hasExpenseTransfer = false;
            for (CategoryEntity c : expenseCategories) {
                if (c.name != null && c.name.equalsIgnoreCase("Transfer")) {
                    hasExpenseTransfer = true;
                    break;
                }
            }
            if (!hasExpenseTransfer) {
                categoryDao.insertCategory(new CategoryEntity(0, "Transfer", "swap_horiz", true, "EXPENSE"));
            }

            List<CategoryEntity> incomeCategories = categoryDao.getCategoriesByTypeSync("INCOME");
            boolean hasIncomeTransfer = false;
            for (CategoryEntity c : incomeCategories) {
                if (c.name != null && c.name.equalsIgnoreCase("Transfer")) {
                    hasIncomeTransfer = true;
                    break;
                }
            }
            if (!hasIncomeTransfer) {
                categoryDao.insertCategory(new CategoryEntity(0, "Transfer", "swap_horiz", true, "INCOME"));
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
            TransactionEntity entity = mapToEntity(transaction);
            
            // Re-evaluate group
            entity.transactionGroupId = 0; // Reset
            List<TransactionGroupEntity> groups = transactionGroupDao.getActiveGroupsForDate(entity.date);
            for (TransactionGroupEntity group : groups) {
                List<String> categories = transactionGroupDao.getGroupCategoriesSync(group.id);
                if (categories.contains(entity.category)) {
                    entity.transactionGroupId = group.id;
                    break;
                }
            }
            
            long newId = transactionDao.insertTransaction(entity);
            clonedTransactionDao.insertTransaction(entity); // Mirror to cloned database

            // Evaluate category budget range and trigger warning notifications if exceeded
            BudgetNotificationHelper.checkBudgetAndNotify(context, categoryDao, transactionDao, transaction);

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
                                transaction.getCategory()
                        );
                        repeatedAlertDao.insert(alert);
                        break; // Only create one alert for the most recent match
                    }
                }
            }
        });
    }

    @Override
    public void importTransactions(List<Transaction> transactions, ImportCallback callback) {
        executorService.execute(() -> {
            if (transactions == null || transactions.isEmpty()) {
                notifyImport(callback, new BulkImportResult(0, 0, 0, null));
                return;
            }
            try {
                List<TransactionEntity> entities = new ArrayList<>();
                for (Transaction transaction : transactions) {
                    TransactionEntity entity = mapToEntity(transaction);
                    if (entity != null) {
                        entity.id = 0;
                        assignTransactionGroup(entity);
                        entities.add(entity);
                    }
                }
                List<Long> ids = transactionDao.insertTransactionsIgnore(entities);
                int imported = 0;
                int duplicates = 0;
                List<TransactionEntity> insertedForClone = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) {
                    if (ids.get(i) != null && ids.get(i) > 0) {
                        imported++;
                        insertedForClone.add(entities.get(i));
                    } else {
                        duplicates++;
                    }
                }
                // Keep the recovery clone aligned. It is intentionally best-effort: the main
                // encrypted database is the source of truth and has already committed atomically.
                if (!insertedForClone.isEmpty()) {
                    clonedTransactionDao.insertTransactionsIgnore(insertedForClone);
                }
                notifyImport(callback, new BulkImportResult(entities.size(), imported, duplicates, null));
            } catch (Exception e) {
                notifyImport(callback, new BulkImportResult(transactions.size(), 0, 0,
                        e.getMessage() != null ? e.getMessage() : "Unable to import transactions"));
            }
        });
    }

    private void notifyImport(ImportCallback callback, BulkImportResult result) {
        if (callback != null) callback.onComplete(result);
    }

    private void assignTransactionGroup(TransactionEntity entity) {
        entity.transactionGroupId = 0;
        List<TransactionGroupEntity> groups = transactionGroupDao.getActiveGroupsForDate(entity.date);
        for (TransactionGroupEntity group : groups) {
            List<String> categories = transactionGroupDao.getGroupCategoriesSync(group.id);
            if (categories.contains(entity.category)) {
                entity.transactionGroupId = group.id;
                return;
            }
        }
    }

    @Override
    public void updateTransaction(Transaction transaction) {
        executorService.execute(() -> {
            TransactionEntity entity = mapToEntity(transaction);
            
            // Re-evaluate group
            entity.transactionGroupId = 0; // Reset
            List<TransactionGroupEntity> groups = transactionGroupDao.getActiveGroupsForDate(entity.date);
            for (TransactionGroupEntity group : groups) {
                List<String> categories = transactionGroupDao.getGroupCategoriesSync(group.id);
                if (categories.contains(entity.category)) {
                    entity.transactionGroupId = group.id;
                    break;
                }
            }
            
            transactionDao.updateTransaction(entity);
            clonedTransactionDao.updateTransaction(entity); // Mirror to cloned database

            // Evaluate category budget range and trigger warning notifications if exceeded
            BudgetNotificationHelper.checkBudgetAndNotify(context, categoryDao, transactionDao, transaction);
        });
    }

    @Override
    public void deleteTransaction(Transaction transaction) {
        // Soft delete: mark as DELETED instead of removing
        executorService.execute(() -> {
            long deletedAt = System.currentTimeMillis();
            transactionDao.softDeleteTransaction(transaction.getId(), deletedAt);
            clonedTransactionDao.softDeleteTransaction(transaction.getId(), deletedAt);
        });
    }

    @Override
    public void softDeleteTransaction(int transactionId) {
        executorService.execute(() -> {
            long deletedAt = System.currentTimeMillis();
            transactionDao.softDeleteTransaction(transactionId, deletedAt);
            clonedTransactionDao.softDeleteTransaction(transactionId, deletedAt);
        });
    }

    @Override
    public void restoreTransaction(int transactionId) {
        executorService.execute(() -> {
            transactionDao.restoreTransaction(transactionId);
            clonedTransactionDao.restoreTransaction(transactionId);
        });
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
        executorService.execute(() -> {
            transactionDao.permanentlyDeleteTransaction(transactionId);
            clonedTransactionDao.permanentlyDeleteTransaction(transactionId);
        });
    }

    @Override
    public void restoreFromClone() {
        executorService.execute(() -> {
            List<TransactionEntity> clonedTransactions = clonedTransactionDao.getAllTransactionsSync();
            for (TransactionEntity entity : clonedTransactions) {
                transactionDao.insertTransaction(entity);
            }
        });
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Override
    public LiveData<Summary> getSummary(long startDate, long endDate) {
        MediatorLiveData<Summary> summaryMediator = new MediatorLiveData<>();

        LiveData<Double> incomeLive = transactionDao.getTotalIncome(startDate, endDate);
        LiveData<Double> expenseLive = transactionDao.getTotalExpense(startDate, endDate);
        LiveData<Double> transferLive = transactionDao.getTransferTotal(startDate, endDate);
        LiveData<Double> accountLive = transactionDao.getTotalAccountTransaction(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> expSumsLive = transactionDao.getExpenseCategorySummaries(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> expAvgsLive = transactionDao.getExpenseCategoryAverages(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> incSumsLive = transactionDao.getIncomeCategorySummaries(startDate, endDate);
        LiveData<List<TransactionDao.CategorySum>> incAvgsLive = transactionDao.getIncomeCategoryAverages(startDate, endDate);

        Runnable update = () -> updateSummary(summaryMediator,
            incomeLive.getValue(), expenseLive.getValue(), transferLive.getValue(), accountLive.getValue(),
            expSumsLive.getValue(), expAvgsLive.getValue(), incSumsLive.getValue(), incAvgsLive.getValue());

        summaryMediator.addSource(incomeLive,   v -> update.run());
        summaryMediator.addSource(expenseLive,  v -> update.run());
        summaryMediator.addSource(transferLive, v -> update.run());
        summaryMediator.addSource(accountLive,  v -> update.run());
        summaryMediator.addSource(expSumsLive,  v -> update.run());
        summaryMediator.addSource(expAvgsLive,  v -> update.run());
        summaryMediator.addSource(incSumsLive,  v -> update.run());
        summaryMediator.addSource(incAvgsLive,  v -> update.run());

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
    public LiveData<List<CategoryEntity>> getCategoryEntities() {
        return categoryDao.getAllCategories();
    }

    @Override
    public void saveCategory(CategoryEntity category) {
        executorService.execute(() -> {
            if (category == null || category.name == null || category.name.trim().isEmpty()) return;
            String cleanName = category.name.trim();
            category.name = cleanName;

            if (category.id > 0) {
                CategoryEntity existing = categoryDao.getCategoryByIdSync(category.id);
                if (existing != null && existing.name != null && !existing.name.trim().isEmpty() && !existing.name.equals(cleanName)) {
                    // Category was renamed -> update all transactions referencing old category name
                    transactionDao.renameCategory(existing.name.trim(), cleanName);
                }
                categoryDao.updateCategory(category);
            } else {
                CategoryEntity existing = categoryDao.getCategoryByNameSync(cleanName);
                if (existing != null) {
                    category.id = existing.id;
                    categoryDao.updateCategory(category);
                } else {
                    categoryDao.insertCategory(category);
                }
            }
        });
    }

    @Override
    public void addCategory(String name, String type) {
        executorService.execute(() -> {
            if (name == null || name.trim().isEmpty()) return;
            String cleanName = name.trim();
            List<CategoryEntity> existing = categoryDao.getCategoriesByTypeSync(type);
            for (CategoryEntity c : existing) {
                if (c.name != null && c.name.equalsIgnoreCase(cleanName)) {
                    return; // Duplicate category, ignore
                }
            }
            categoryDao.insertCategory(new CategoryEntity(0, cleanName, null, false, type));
        });
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

    private void updateSummary(MediatorLiveData<Summary> summaryMediator, Double income, Double expense, Double transfer, Double account,
                               List<TransactionDao.CategorySum> expSums, List<TransactionDao.CategorySum> expAvgs,
                               List<TransactionDao.CategorySum> incSums, List<TransactionDao.CategorySum> incAvgs) {
        double totalIncome = income != null ? income : 0.0;
        double totalExpense = expense != null ? expense : 0.0;
        double totalTransfer = transfer != null ? transfer : 0.0;
        double totalAccount = account != null ? account : 0.0;

        summaryMediator.setValue(new Summary(
            totalIncome, totalExpense, totalTransfer, totalAccount,
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
        t.setSourceTransactionId(entity.sourceTransactionId);
        t.setReferenceNumber(entity.referenceNumber);
        t.setDirection(entity.direction);
        t.setTimestampPrecision(entity.timestampPrecision);
        t.setImportBatchId(entity.importBatchId);
        // Populate group name for display
        if (entity.transactionGroupId > 0 && transactionGroupDao != null) {
            try {
                String groupName = transactionGroupDao.getGroupNameSync(entity.transactionGroupId);
                t.setTransactionGroupName(groupName != null ? groupName : "");
            } catch (Exception ignored) {}
        }
        return t;
    }

    private TransactionEntity mapToEntity(Transaction transaction) {
        if (transaction == null) return null;
        TransactionEntity entity = new TransactionEntity(
            transaction.getId(), transaction.getAmount(), transaction.getCategory(),
            transaction.getCategoryEmoji(), transaction.getDescription(), transaction.getType(),
            transaction.getDate(), transaction.getSource(), transaction.getSender(),
            transaction.getUpiId(), transaction.getReceiverName(), transaction.getBankName(),
            transaction.getSourceType(), transaction.getFromAccount(), transaction.getToAccount(),
            transaction.getFees()
        );
        entity.transactionGroupId = transaction.getTransactionGroupId();
        entity.status = transaction.getStatus() != null ? transaction.getStatus() : "ACTIVE";
        entity.deletedAt = transaction.getDeletedAt();
        entity.sourceTransactionId = emptyToNull(transaction.getSourceTransactionId());
        entity.referenceNumber = emptyToNull(transaction.getReferenceNumber());
        entity.direction = transaction.getDirection();
        entity.timestampPrecision = transaction.getTimestampPrecision();
        entity.importBatchId = emptyToNull(transaction.getImportBatchId());
        return entity;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
