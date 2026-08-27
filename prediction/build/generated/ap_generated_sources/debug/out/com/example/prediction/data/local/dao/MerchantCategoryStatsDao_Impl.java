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
import com.example.prediction.data.local.entity.MerchantCategoryStatsEntity;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MerchantCategoryStatsDao_Impl implements MerchantCategoryStatsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MerchantCategoryStatsEntity> __insertionAdapterOfMerchantCategoryStatsEntity;

  private final EntityDeletionOrUpdateAdapter<MerchantCategoryStatsEntity> __updateAdapterOfMerchantCategoryStatsEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public MerchantCategoryStatsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMerchantCategoryStatsEntity = new EntityInsertionAdapter<MerchantCategoryStatsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `merchant_category_stats` (`id`,`merchantKey`,`category`,`count`,`lastSeenMs`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final MerchantCategoryStatsEntity entity) {
        if (entity.id == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.id);
        }
        if (entity.merchantKey == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.merchantKey);
        }
        if (entity.category == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.category);
        }
        statement.bindLong(4, entity.count);
        statement.bindLong(5, entity.lastSeenMs);
      }
    };
    this.__updateAdapterOfMerchantCategoryStatsEntity = new EntityDeletionOrUpdateAdapter<MerchantCategoryStatsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `merchant_category_stats` SET `id` = ?,`merchantKey` = ?,`category` = ?,`count` = ?,`lastSeenMs` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final MerchantCategoryStatsEntity entity) {
        if (entity.id == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.id);
        }
        if (entity.merchantKey == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.merchantKey);
        }
        if (entity.category == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.category);
        }
        statement.bindLong(4, entity.count);
        statement.bindLong(5, entity.lastSeenMs);
        if (entity.id == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.id);
        }
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM merchant_category_stats";
        return _query;
      }
    };
  }

  @Override
  public void insert(final MerchantCategoryStatsEntity entity) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfMerchantCategoryStatsEntity.insert(entity);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final MerchantCategoryStatsEntity entity) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfMerchantCategoryStatsEntity.handle(entity);
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
  public List<MerchantCategoryStatsEntity> getStatsForMerchant(final String merchantKey) {
    final String _sql = "SELECT * FROM merchant_category_stats WHERE merchantKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (merchantKey == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, merchantKey);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfMerchantKey = CursorUtil.getColumnIndexOrThrow(_cursor, "merchantKey");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCount = CursorUtil.getColumnIndexOrThrow(_cursor, "count");
      final int _cursorIndexOfLastSeenMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenMs");
      final List<MerchantCategoryStatsEntity> _result = new ArrayList<MerchantCategoryStatsEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final MerchantCategoryStatsEntity _item;
        _item = new MerchantCategoryStatsEntity();
        if (_cursor.isNull(_cursorIndexOfId)) {
          _item.id = null;
        } else {
          _item.id = _cursor.getString(_cursorIndexOfId);
        }
        if (_cursor.isNull(_cursorIndexOfMerchantKey)) {
          _item.merchantKey = null;
        } else {
          _item.merchantKey = _cursor.getString(_cursorIndexOfMerchantKey);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        _item.count = _cursor.getInt(_cursorIndexOfCount);
        _item.lastSeenMs = _cursor.getLong(_cursorIndexOfLastSeenMs);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public MerchantCategoryStatsEntity getById(final String id) {
    final String _sql = "SELECT * FROM merchant_category_stats WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (id == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, id);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfMerchantKey = CursorUtil.getColumnIndexOrThrow(_cursor, "merchantKey");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCount = CursorUtil.getColumnIndexOrThrow(_cursor, "count");
      final int _cursorIndexOfLastSeenMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenMs");
      final MerchantCategoryStatsEntity _result;
      if (_cursor.moveToFirst()) {
        _result = new MerchantCategoryStatsEntity();
        if (_cursor.isNull(_cursorIndexOfId)) {
          _result.id = null;
        } else {
          _result.id = _cursor.getString(_cursorIndexOfId);
        }
        if (_cursor.isNull(_cursorIndexOfMerchantKey)) {
          _result.merchantKey = null;
        } else {
          _result.merchantKey = _cursor.getString(_cursorIndexOfMerchantKey);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _result.category = null;
        } else {
          _result.category = _cursor.getString(_cursorIndexOfCategory);
        }
        _result.count = _cursor.getInt(_cursorIndexOfCount);
        _result.lastSeenMs = _cursor.getLong(_cursorIndexOfLastSeenMs);
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
