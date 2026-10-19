package com.example.prediction.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import com.example.prediction.data.local.dao.GlobalCategoryStatsDao;
import com.example.prediction.data.local.dao.MerchantCategoryStatsDao;
import com.example.prediction.data.local.dao.MerchantStatsDao;
import com.example.prediction.data.local.dao.PrototypeDao;
import com.example.prediction.data.local.entity.GlobalCategoryStatsEntity;
import com.example.prediction.data.local.entity.MerchantCategoryStatsEntity;
import com.example.prediction.data.local.entity.MerchantStatsEntity;
import com.example.prediction.data.local.entity.PrototypeEntity;
import com.example.prediction.util.Converters;

@Database(
    entities = {
        PrototypeEntity.class,
        MerchantStatsEntity.class,
        MerchantCategoryStatsEntity.class,
        GlobalCategoryStatsEntity.class,
        com.example.prediction.data.local.entity.CategoryFeedbackEntity.class
    },
    version = 5,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class PredictionDatabase extends RoomDatabase {
    public abstract com.example.prediction.data.local.dao.CategoryFeedbackDao categoryFeedbackDao();
    public static final androidx.room.migration.Migration MIGRATION_4_5 =
            new androidx.room.migration.Migration(4, 5) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS category_feedback (id TEXT NOT NULL PRIMARY KEY, merchant TEXT NOT NULL, tokens TEXT NOT NULL, type TEXT NOT NULL, category TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
        }
    };
    public abstract PrototypeDao prototypeDao();
    public abstract MerchantStatsDao merchantStatsDao();
    public abstract MerchantCategoryStatsDao merchantCategoryStatsDao();
    public abstract GlobalCategoryStatsDao globalCategoryStatsDao();

    private static volatile PredictionDatabase INSTANCE;

    public static PredictionDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (PredictionDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    PredictionDatabase.class, "prediction_database")
                            .addMigrations(MIGRATION_4_5)
                            .fallbackToDestructiveMigrationFrom(1, 2, 3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
