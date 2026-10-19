package com.example.prediction.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.prediction.data.local.entity.CategoryFeedbackEntity;
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
public final class CategoryFeedbackDao_Impl implements CategoryFeedbackDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CategoryFeedbackEntity> __insertionAdapterOfCategoryFeedbackEntity;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public CategoryFeedbackDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCategoryFeedbackEntity = new EntityInsertionAdapter<CategoryFeedbackEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `category_feedback` (`id`,`merchant`,`tokens`,`type`,`category`,`updatedAt`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final CategoryFeedbackEntity entity) {
        if (entity.id == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.id);
        }
        if (entity.merchant == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.merchant);
        }
        if (entity.tokens == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.tokens);
        }
        if (entity.type == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.type);
        }
        if (entity.category == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.category);
        }
        statement.bindLong(6, entity.updatedAt);
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM category_feedback WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM category_feedback";
        return _query;
      }
    };
  }

  @Override
  public void put(final CategoryFeedbackEntity feedback) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfCategoryFeedbackEntity.insert(feedback);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final String id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
    int _argIndex = 1;
    if (id == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, id);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDelete.release(_stmt);
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
  public List<CategoryFeedbackEntity> getAll() {
    final String _sql = "SELECT * FROM category_feedback ORDER BY updatedAt, id";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
      final int _cursorIndexOfTokens = CursorUtil.getColumnIndexOrThrow(_cursor, "tokens");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
      final List<CategoryFeedbackEntity> _result = new ArrayList<CategoryFeedbackEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final CategoryFeedbackEntity _item;
        _item = new CategoryFeedbackEntity();
        if (_cursor.isNull(_cursorIndexOfId)) {
          _item.id = null;
        } else {
          _item.id = _cursor.getString(_cursorIndexOfId);
        }
        if (_cursor.isNull(_cursorIndexOfMerchant)) {
          _item.merchant = null;
        } else {
          _item.merchant = _cursor.getString(_cursorIndexOfMerchant);
        }
        if (_cursor.isNull(_cursorIndexOfTokens)) {
          _item.tokens = null;
        } else {
          _item.tokens = _cursor.getString(_cursorIndexOfTokens);
        }
        if (_cursor.isNull(_cursorIndexOfType)) {
          _item.type = null;
        } else {
          _item.type = _cursor.getString(_cursorIndexOfType);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        _item.updatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
        _result.add(_item);
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
