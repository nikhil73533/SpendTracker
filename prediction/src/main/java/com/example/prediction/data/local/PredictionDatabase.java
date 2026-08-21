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
        GlobalCategoryStatsEntity.class
    },
    version = 3,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class PredictionDatabase extends RoomDatabase {
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
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
