package com.example.prediction.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.example.prediction.data.local.dao.MerchantStatsDao;
import com.example.prediction.data.local.dao.MerchantStatsDao_Impl;
import com.example.prediction.data.local.dao.PrototypeDao;
import com.example.prediction.data.local.dao.PrototypeDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PredictionDatabase_Impl extends PredictionDatabase {
  private volatile PrototypeDao _prototypeDao;

  private volatile MerchantStatsDao _merchantStatsDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `prototypes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT, `vector` BLOB, `merchantName` TEXT, `upiId` TEXT, `amount` REAL NOT NULL, `type` TEXT, `dayOfWeek` INTEGER NOT NULL, `hourOfDay` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_stats` (`merchantName` TEXT NOT NULL, `frequency` INTEGER NOT NULL, `totalAmount` REAL NOT NULL, `averageAmount` REAL NOT NULL, `preferredCategory` TEXT, `lastCategory` TEXT, `lastTransactionDate` INTEGER NOT NULL, PRIMARY KEY(`merchantName`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '57746429a716a7a0caa78594ba63cda0')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `prototypes`");
        db.execSQL("DROP TABLE IF EXISTS `merchant_stats`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPrototypes = new HashMap<String, TableInfo.Column>(9);
        _columnsPrototypes.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("category", new TableInfo.Column("category", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("vector", new TableInfo.Column("vector", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("merchantName", new TableInfo.Column("merchantName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("upiId", new TableInfo.Column("upiId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("type", new TableInfo.Column("type", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("dayOfWeek", new TableInfo.Column("dayOfWeek", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrototypes.put("hourOfDay", new TableInfo.Column("hourOfDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPrototypes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPrototypes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPrototypes = new TableInfo("prototypes", _columnsPrototypes, _foreignKeysPrototypes, _indicesPrototypes);
        final TableInfo _existingPrototypes = TableInfo.read(db, "prototypes");
        if (!_infoPrototypes.equals(_existingPrototypes)) {
          return new RoomOpenHelper.ValidationResult(false, "prototypes(com.example.prediction.data.local.entity.PrototypeEntity).\n"
                  + " Expected:\n" + _infoPrototypes + "\n"
                  + " Found:\n" + _existingPrototypes);
        }
        final HashMap<String, TableInfo.Column> _columnsMerchantStats = new HashMap<String, TableInfo.Column>(7);
        _columnsMerchantStats.put("merchantName", new TableInfo.Column("merchantName", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantStats.put("frequency", new TableInfo.Column("frequency", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantStats.put("totalAmount", new TableInfo.Column("totalAmount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantStats.put("averageAmount", new TableInfo.Column("averageAmount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantStats.put("preferredCategory", new TableInfo.Column("preferredCategory", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantStats.put("lastCategory", new TableInfo.Column("lastCategory", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantStats.put("lastTransactionDate", new TableInfo.Column("lastTransactionDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMerchantStats = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMerchantStats = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMerchantStats = new TableInfo("merchant_stats", _columnsMerchantStats, _foreignKeysMerchantStats, _indicesMerchantStats);
        final TableInfo _existingMerchantStats = TableInfo.read(db, "merchant_stats");
        if (!_infoMerchantStats.equals(_existingMerchantStats)) {
          return new RoomOpenHelper.ValidationResult(false, "merchant_stats(com.example.prediction.data.local.entity.MerchantStatsEntity).\n"
                  + " Expected:\n" + _infoMerchantStats + "\n"
                  + " Found:\n" + _existingMerchantStats);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "57746429a716a7a0caa78594ba63cda0", "f7d4c9e0ee32305ba769d647cfe1ef39");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "prototypes","merchant_stats");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `prototypes`");
      _db.execSQL("DELETE FROM `merchant_stats`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PrototypeDao.class, PrototypeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MerchantStatsDao.class, MerchantStatsDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public PrototypeDao prototypeDao() {
    if (_prototypeDao != null) {
      return _prototypeDao;
    } else {
      synchronized(this) {
        if(_prototypeDao == null) {
          _prototypeDao = new PrototypeDao_Impl(this);
        }
        return _prototypeDao;
      }
    }
  }

  @Override
  public MerchantStatsDao merchantStatsDao() {
    if (_merchantStatsDao != null) {
      return _merchantStatsDao;
    } else {
      synchronized(this) {
        if(_merchantStatsDao == null) {
          _merchantStatsDao = new MerchantStatsDao_Impl(this);
        }
        return _merchantStatsDao;
      }
    }
  }
}
