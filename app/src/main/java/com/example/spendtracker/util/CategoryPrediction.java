package com.example.spendtracker.util;

import android.content.Context;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.domain.service.IncrementalPredictionService;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.domain.model.Transaction;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import java.util.ArrayList;

/** Shared mapping used by SMS, statements, manual edits and prediction refinement. */
public final class CategoryPrediction {
    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies { CategoryDao categoryDao(); }
    public static final double REVIEW_THRESHOLD = 0.70;
    private CategoryPrediction() {}
    public static IncrementalPredictionService service(Context context) {
        return new IncrementalPredictionService(context, type -> {
            CategoryDao dao = EntryPointAccessors.fromApplication(context.getApplicationContext(), Dependencies.class).categoryDao();
            java.util.List<String> names = new ArrayList<>();
            for (com.example.spendtracker.data.local.entity.CategoryEntity c : dao.getCategoriesByTypeSync(type)) names.add(c.name);
            return names;
        });
    }
    public static PredictionTransaction from(Transaction tx) {
        String type = "TRANSFER".equals(tx.getType())
                ? (TransferDirection.isIncoming(tx) ? "INCOME" : "EXPENSE") : tx.getType();
        String merchant = "INCOME".equalsIgnoreCase(type) ? tx.getSender() : tx.getReceiverName();
        return new PredictionTransaction(merchant, tx.getUpiId(), tx.getAmount(), type, tx.getDate(), tx.getDescription());
    }
}
