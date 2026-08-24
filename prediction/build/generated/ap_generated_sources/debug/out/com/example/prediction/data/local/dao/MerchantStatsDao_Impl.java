package com.example.prediction.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.prediction.data.local.entity.MerchantStatsEntity;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MerchantStatsDao_Impl implements MerchantStatsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MerchantStatsEntity> __insertionAdapterOfMerchantStatsEntity;

  private final EntityDeletionOrUpdateAdapter<MerchantStatsEntity> __updateAdapterOfMerchantStatsEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public MerchantStatsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMerchantStatsEntity = new EntityInsertionAdapter<MerchantStatsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `merchant_stats` (`merchantName`,`frequency`,`totalAmount`,`averageAmount`,`preferredCategory`,`lastCategory`,`lastTransactionDate`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final MerchantStatsEntity entity) {
        if (entity.merchantName == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.merchantName);
        }
        statement.bindLong(2, entity.frequency);
        statement.bindDouble(3, entity.totalAmount);
        statement.bindDouble(4, entity.averageAmount);
        if (entity.preferredCategory == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.preferredCategory);
        }
        if (entity.lastCategory == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.lastCategory);
        }
        statement.bindLong(7, entity.lastTransactionDate);
      }
    };
    this.__updateAdapterOfMerchantStatsEntity = new EntityDeletionOrUpdateAdapter<MerchantStatsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `merchant_stats` SET `merchantName` = ?,`frequency` = ?,`totalAmount` = ?,`averageAmount` = ?,`preferredCategory` = ?,`lastCategory` = ?,`lastTransactionDate` = ? WHERE `merchantName` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final MerchantStatsEntity entity) {
        if (entity.merchantName == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.merchantName);
        }
        statement.bindLong(2, entity.frequency);
        statement.bindDouble(3, entity.totalAmount);
        statement.bindDouble(4, entity.averageAmount);
        if (entity.preferredCategory == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.preferredCategory);
        }
        if (entity.lastCategory == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.lastCategory);
        }
        statement.bindLong(7, entity.lastTransactionDate);
        if (entity.merchantName == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.merchantName);
        }
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM merchant_stats";
        return _query;
      }
    };
  }

  @Override
  public void insert(final MerchantStatsEntity stats) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfMerchantStatsEntity.insert(stats);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final MerchantStatsEntity stats) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfMerchantStatsEntity.handle(stats);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteAll.release(_stmt);
    }
  }

  @Override
  public MerchantStatsEntity getStatsForMerchant(final String name) {
    final String _sql = "SELECT * FROM merchant_stats WHERE merchantName = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (name == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, name);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfMerchantName = CursorUtil.getColumnIndexOrThrow(_cursor, "merchantName");
      final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
      final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
      final int _cursorIndexOfAverageAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "averageAmount");
      final int _cursorIndexOfPreferredCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "preferredCategory");
      final int _cursorIndexOfLastCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "lastCategory");
      final int _cursorIndexOfLastTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastTransactionDate");
      final MerchantStatsEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpMerchantName;
        if (_cursor.isNull(_cursorIndexOfMerchantName)) {
          _tmpMerchantName = null;
        } else {
          _tmpMerchantName = _cursor.getString(_cursorIndexOfMerchantName);
        }
        _result = new MerchantStatsEntity(_tmpMerchantName);
        _result.frequency = _cursor.getInt(_cursorIndexOfFrequency);
        _result.totalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
        _result.averageAmount = _cursor.getDouble(_cursorIndexOfAverageAmount);
        if (_cursor.isNull(_cursorIndexOfPreferredCategory)) {
          _result.preferredCategory = null;
        } else {
          _result.preferredCategory = _cursor.getString(_cursorIndexOfPreferredCategory);
        }
        if (_cursor.isNull(_cursorIndexOfLastCategory)) {
          _result.lastCategory = null;
        } else {
          _result.lastCategory = _cursor.getString(_cursorIndexOfLastCategory);
        }
        _result.lastTransactionDate = _cursor.getLong(_cursorIndexOfLastTransactionDate);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
