package com.example.spendtracker.util;

import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import com.example.spendtracker.data.local.entity.TransactionEntity;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DataInitializer {

    private final CategoryDao categoryDao;
    private final RegexPatternDao regexPatternDao;
    private final TransactionDao transactionDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Inject
    public DataInitializer(CategoryDao categoryDao, RegexPatternDao regexPatternDao, TransactionDao transactionDao) {
        this.categoryDao = categoryDao;
        this.regexPatternDao = regexPatternDao;
        this.transactionDao = transactionDao;
    }

    public void initializeData() {
        executorService.execute(() -> {
            // Only populate if empty
            if (categoryDao.getAllCategoriesSync().isEmpty()) {
                categoryDao.insertCategory(new CategoryEntity(0, "Food 🍔", "fastfood", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Transport 🚗", "directions_bus", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Education 📚", "school", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Health 🏥", "local_hospital", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Shopping 🛍️", "shopping_cart", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Salary 💰", "attach_money", true, "INCOME"));
                categoryDao.insertCategory(new CategoryEntity(0, "Rent 🏠", "home", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Gift 🎁", "card_giftcard", true, "INCOME"));
                categoryDao.insertCategory(new CategoryEntity(0, "Allowance 💸", "swap_horiz", true, "INCOME"));
                categoryDao.insertCategory(new CategoryEntity(0, "Bonus 🏅", "stars", true, "INCOME"));
                categoryDao.insertCategory(new CategoryEntity(0, "Other ✨", "category", true, "EXPENSE"));
                categoryDao.insertCategory(new CategoryEntity(0, "Other (Inc) 🧧", "category", true, "INCOME"));
            }

            if (regexPatternDao.getAllPatternsSync().isEmpty()) {
                // Pre-populate sample Regex Patterns
                regexPatternDao.insertPattern(new RegexPatternEntity(0, "Universal Bank", ".*?(?:debited|spent|paid).*?(?:Rs\\.?|INR)\\s?([0-9,.]+).*?for\\s+(?!INR|Rs)([^.]+).*", 1, 0));
                regexPatternDao.insertPattern(new RegexPatternEntity(0, "Universal Bank", ".*?(?:credited|received).*?(?:Rs\\.?|INR)\\s?([0-9,.]+).*", 1, 0));
            }

            if (transactionDao.getAllTransactionsSync().isEmpty()) {
                Calendar cal = Calendar.getInstance();
                cal.set(2026, Calendar.JULY, 24, 10, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 30.0, "Food", null, "Chai", "EXPENSE", cal.getTimeInMillis(), "ICICI (Card)", "ICICI", "", "Tea Stall", "ICICI", "Card"));

                cal.set(2026, Calendar.JULY, 23, 14, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 80.0, "Food", null, "Panipuri", "EXPENSE", cal.getTimeInMillis(), "HDFC (Account)", "HDFC", "", "Street Food", "HDFC", "Account"));
                cal.set(2026, Calendar.JULY, 23, 15, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 215.0, "Food", null, "Online food", "EXPENSE", cal.getTimeInMillis(), "ICICI (Card)", "ICICI", "", "Zomato", "ICICI", "Card"));

                cal.set(2026, Calendar.JULY, 22, 18, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 2000.0, "Gift", null, "Wife", "EXPENSE", cal.getTimeInMillis(), "HDFC (Account)", "HDFC", "", "Jeweller", "HDFC", "Account"));

                cal.set(2026, Calendar.JULY, 21, 12, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 1800.0, "Food", null, "Demart groceries", "EXPENSE", cal.getTimeInMillis(), "ICICI (Card)", "ICICI", "", "DMart", "ICICI", "Card"));
                cal.set(2026, Calendar.JULY, 21, 13, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 564.0, "Transport", null, "Patrol", "EXPENSE", cal.getTimeInMillis(), "ICICI (Card)", "ICICI", "", "Petrol Pump", "ICICI", "Card"));

                cal.set(2026, Calendar.JULY, 20, 9, 0);
                transactionDao.insertTransaction(new TransactionEntity(0, 2315.0, "Food", null, "Kachori", "EXPENSE", cal.getTimeInMillis(), "HDFC (Account)", "HDFC", "", "Kachori Shop", "HDFC", "Account"));
            }
        });
    }
}
