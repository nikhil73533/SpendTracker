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
import com.example.prediction.data.local.entity.GlobalCategoryStatsEntity;
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
public final class GlobalCategoryStatsDao_Impl implements GlobalCategoryStatsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<GlobalCategoryStatsEntity> __insertionAdapterOfGlobalCategoryStatsEntity;

  private final EntityDeletionOrUpdateAdapter<GlobalCategoryStatsEntity> __updateAdapterOfGlobalCategoryStatsEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public GlobalCategoryStatsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfGlobalCategoryStatsEntity = new EntityInsertionAdapter<GlobalCategoryStatsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `global_category_stats` (`category`,`count`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final GlobalCategoryStatsEntity entity) {
        if (entity.category == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.category);
        }
        statement.bindLong(2, entity.count);
      }
    };
    this.__updateAdapterOfGlobalCategoryStatsEntity = new EntityDeletionOrUpdateAdapter<GlobalCategoryStatsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `global_category_stats` SET `category` = ?,`count` = ? WHERE `category` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final GlobalCategoryStatsEntity entity) {
        if (entity.category == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.category);
        }
        statement.bindLong(2, entity.count);
        if (entity.category == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.category);
        }
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM global_category_stats";
        return _query;
      }
    };
  }

  @Override
  public void insert(final GlobalCategoryStatsEntity entity) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfGlobalCategoryStatsEntity.insert(entity);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final GlobalCategoryStatsEntity entity) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfGlobalCategoryStatsEntity.handle(entity);
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
  public List<GlobalCategoryStatsEntity> getAll() {
    final String _sql = "SELECT * FROM global_category_stats";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCount = CursorUtil.getColumnIndexOrThrow(_cursor, "count");
      final List<GlobalCategoryStatsEntity> _result = new ArrayList<GlobalCategoryStatsEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final GlobalCategoryStatsEntity _item;
        _item = new GlobalCategoryStatsEntity();
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        _item.count = _cursor.getInt(_cursorIndexOfCount);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public GlobalCategoryStatsEntity getByCategory(final String category) {
    final String _sql = "SELECT * FROM global_category_stats WHERE category = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (category == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, category);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCount = CursorUtil.getColumnIndexOrThrow(_cursor, "count");
      final GlobalCategoryStatsEntity _result;
      if (_cursor.moveToFirst()) {
        _result = new GlobalCategoryStatsEntity();
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _result.category = null;
        } else {
          _result.category = _cursor.getString(_cursorIndexOfCategory);
        }
        _result.count = _cursor.getInt(_cursorIndexOfCount);
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
