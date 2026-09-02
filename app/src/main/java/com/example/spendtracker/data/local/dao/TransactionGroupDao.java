package com.example.spendtracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import com.example.spendtracker.data.local.entity.TransactionGroupCategoryEntity;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import java.util.List;

@Dao
public interface TransactionGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(TransactionGroupEntity group);

    @Update
    void updateGroup(TransactionGroupEntity group);

    @Delete
    void deleteGroup(TransactionGroupEntity group);

    @Query("SELECT * FROM transaction_groups WHERE isActive = 1 ORDER BY createdAt DESC")
    LiveData<List<TransactionGroupEntity>> getAllActiveGroups();

    @Query("SELECT * FROM transaction_groups ORDER BY createdAt DESC")
    LiveData<List<TransactionGroupEntity>> getAllGroups();

    @Query("SELECT * FROM transaction_groups ORDER BY createdAt DESC")
    List<TransactionGroupEntity> getAllGroupsSync();

    @Query("SELECT * FROM transaction_groups WHERE id = :id")
    LiveData<TransactionGroupEntity> getGroupById(int id);

    @Query("SELECT * FROM transaction_groups WHERE id = :id")
    TransactionGroupEntity getGroupByIdSync(int id);

    @Query("SELECT * FROM transaction_groups WHERE isActive = 1 AND startDate <= :date AND endDate >= :date ORDER BY createdAt DESC")
    List<TransactionGroupEntity> getActiveGroupsForDate(long date);

    // Category associations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroupCategory(TransactionGroupCategoryEntity category);

    @Query("DELETE FROM transaction_group_categories WHERE groupId = :groupId")
    void deleteGroupCategories(int groupId);

    @Query("SELECT categoryName FROM transaction_group_categories WHERE groupId = :groupId")
    LiveData<List<String>> getGroupCategories(int groupId);

    @Query("SELECT categoryName FROM transaction_group_categories WHERE groupId = :groupId")
    List<String> getGroupCategoriesSync(int groupId);

    // Transaction association
    @Query("UPDATE transactions SET transactionGroupId = :groupId WHERE date BETWEEN :startDate AND :endDate AND category IN (SELECT categoryName FROM transaction_group_categories WHERE groupId = :groupId) AND status = 'ACTIVE'")
    void associateTransactionsWithGroup(int groupId, long startDate, long endDate);

    @Query("UPDATE transactions SET transactionGroupId = 0 WHERE transactionGroupId = :groupId")
    void disassociateTransactionsFromGroup(int groupId);

    @Query("SELECT * FROM transactions WHERE transactionGroupId = :groupId AND status = 'ACTIVE' ORDER BY date DESC, id DESC")
    LiveData<List<com.example.spendtracker.data.local.entity.TransactionEntity>> getTransactionsForGroup(int groupId);

    @Query("SELECT COUNT(*) FROM transactions WHERE transactionGroupId = :groupId AND status = 'ACTIVE'")
    LiveData<Integer> getTransactionCountForGroup(int groupId);

    @Transaction
    default void insertGroupWithCategories(TransactionGroupEntity group, List<String> categories) {
        long groupId = insertGroup(group);
        for (String cat : categories) {
            insertGroupCategory(new TransactionGroupCategoryEntity((int) groupId, cat));
        }
        associateTransactionsWithGroup((int) groupId, group.startDate, group.endDate);
    }

    @Transaction
    default void updateGroupWithCategories(TransactionGroupEntity group, List<String> categories) {
        updateGroup(group);
        deleteGroupCategories(group.id);
        for (String cat : categories) {
            insertGroupCategory(new TransactionGroupCategoryEntity(group.id, cat));
        }
        disassociateTransactionsFromGroup(group.id);
        associateTransactionsWithGroup(group.id, group.startDate, group.endDate);
    }

    /** Group name lookup for a given transaction's group id. Returns null if groupId is 0 or no group found. */
    @Query("SELECT name FROM transaction_groups WHERE id = :groupId LIMIT 1")
    String getGroupNameSync(int groupId);
}
