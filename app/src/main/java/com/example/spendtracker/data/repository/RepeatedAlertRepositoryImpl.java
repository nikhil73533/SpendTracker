package com.example.spendtracker.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.spendtracker.data.local.dao.RepeatedAlertDao;
import com.example.spendtracker.data.local.entity.RepeatedAlertEntity;
import com.example.spendtracker.domain.model.RepeatedAlert;
import com.example.spendtracker.domain.repository.RepeatedAlertRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

public class RepeatedAlertRepositoryImpl implements RepeatedAlertRepository {

    private final RepeatedAlertDao alertDao;
    private final ExecutorService executorService;

    @Inject
    public RepeatedAlertRepositoryImpl(RepeatedAlertDao alertDao) {
        this.alertDao = alertDao;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<List<RepeatedAlert>> getActiveAlerts() {
        return Transformations.map(alertDao.getActiveAlerts(), this::mapList);
    }

    @Override
    public LiveData<List<RepeatedAlert>> getAllAlerts() {
        return Transformations.map(alertDao.getAllAlerts(), this::mapList);
    }

    @Override
    public void dismissAlert(int id) {
        executorService.execute(() -> alertDao.dismissAlert(id));
    }

    @Override
    public void setAlertEnabled(int id, boolean enabled) {
        executorService.execute(() -> alertDao.setAlertEnabled(id, enabled));
    }

    @Override
    public void clearDismissedAlerts() {
        executorService.execute(() -> alertDao.clearDismissedAlerts());
    }

    private List<RepeatedAlert> mapList(List<RepeatedAlertEntity> entities) {
        List<RepeatedAlert> list = new ArrayList<>();
        if (entities != null) {
            for (RepeatedAlertEntity e : entities) {
                list.add(new RepeatedAlert(
                        e.id, e.merchantName, e.amount,
                        e.firstTransactionDate, e.secondTransactionDate,
                        e.firstTransactionId, e.secondTransactionId,
                        e.enabled, e.dismissed, e.createdAt, e.category
                ));
            }
        }
        return list;
    }
}
