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
import com.example.prediction.data.local.entity.PrototypeEntity;
import com.example.prediction.util.Converters;
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
public final class PrototypeDao_Impl implements PrototypeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PrototypeEntity> __insertionAdapterOfPrototypeEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public PrototypeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPrototypeEntity = new EntityInsertionAdapter<PrototypeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `prototypes` (`id`,`category`,`vector`,`merchantName`,`upiId`,`amount`,`type`,`dayOfWeek`,`hourOfDay`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final PrototypeEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.category == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.category);
        }
        final byte[] _tmp = Converters.toBlob(entity.vector);
        if (_tmp == null) {
          statement.bindNull(3);
        } else {
          statement.bindBlob(3, _tmp);
        }
        if (entity.merchantName == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.merchantName);
        }
        if (entity.upiId == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.upiId);
        }
        statement.bindDouble(6, entity.amount);
        if (entity.type == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.type);
        }
        statement.bindLong(8, entity.dayOfWeek);
        statement.bindLong(9, entity.hourOfDay);
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM prototypes";
        return _query;
      }
    };
  }

  @Override
  public void insert(final PrototypeEntity prototype) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfPrototypeEntity.insert(prototype);
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
  public List<PrototypeEntity> getAllPrototypes() {
    final String _sql = "SELECT * FROM prototypes";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfVector = CursorUtil.getColumnIndexOrThrow(_cursor, "vector");
      final int _cursorIndexOfMerchantName = CursorUtil.getColumnIndexOrThrow(_cursor, "merchantName");
      final int _cursorIndexOfUpiId = CursorUtil.getColumnIndexOrThrow(_cursor, "upiId");
      final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfDayOfWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "dayOfWeek");
      final int _cursorIndexOfHourOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "hourOfDay");
      final List<PrototypeEntity> _result = new ArrayList<PrototypeEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final PrototypeEntity _item;
        _item = new PrototypeEntity();
        _item.id = _cursor.getInt(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final byte[] _tmp;
        if (_cursor.isNull(_cursorIndexOfVector)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getBlob(_cursorIndexOfVector);
        }
        _item.vector = Converters.fromBlob(_tmp);
        if (_cursor.isNull(_cursorIndexOfMerchantName)) {
          _item.merchantName = null;
        } else {
          _item.merchantName = _cursor.getString(_cursorIndexOfMerchantName);
        }
        if (_cursor.isNull(_cursorIndexOfUpiId)) {
          _item.upiId = null;
        } else {
          _item.upiId = _cursor.getString(_cursorIndexOfUpiId);
        }
        _item.amount = _cursor.getDouble(_cursorIndexOfAmount);
        if (_cursor.isNull(_cursorIndexOfType)) {
          _item.type = null;
        } else {
          _item.type = _cursor.getString(_cursorIndexOfType);
        }
        _item.dayOfWeek = _cursor.getInt(_cursorIndexOfDayOfWeek);
        _item.hourOfDay = _cursor.getInt(_cursorIndexOfHourOfDay);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<PrototypeEntity> getPrototypesByType(final String type) {
    final String _sql = "SELECT * FROM prototypes WHERE type = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfVector = CursorUtil.getColumnIndexOrThrow(_cursor, "vector");
      final int _cursorIndexOfMerchantName = CursorUtil.getColumnIndexOrThrow(_cursor, "merchantName");
      final int _cursorIndexOfUpiId = CursorUtil.getColumnIndexOrThrow(_cursor, "upiId");
      final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfDayOfWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "dayOfWeek");
      final int _cursorIndexOfHourOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "hourOfDay");
      final List<PrototypeEntity> _result = new ArrayList<PrototypeEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final PrototypeEntity _item;
        _item = new PrototypeEntity();
        _item.id = _cursor.getInt(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final byte[] _tmp;
        if (_cursor.isNull(_cursorIndexOfVector)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getBlob(_cursorIndexOfVector);
        }
        _item.vector = Converters.fromBlob(_tmp);
        if (_cursor.isNull(_cursorIndexOfMerchantName)) {
          _item.merchantName = null;
        } else {
          _item.merchantName = _cursor.getString(_cursorIndexOfMerchantName);
        }
        if (_cursor.isNull(_cursorIndexOfUpiId)) {
          _item.upiId = null;
        } else {
          _item.upiId = _cursor.getString(_cursorIndexOfUpiId);
        }
        _item.amount = _cursor.getDouble(_cursorIndexOfAmount);
        if (_cursor.isNull(_cursorIndexOfType)) {
          _item.type = null;
        } else {
          _item.type = _cursor.getString(_cursorIndexOfType);
        }
        _item.dayOfWeek = _cursor.getInt(_cursorIndexOfDayOfWeek);
        _item.hourOfDay = _cursor.getInt(_cursorIndexOfHourOfDay);
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
