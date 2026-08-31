package com.example.spendtracker.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;
import com.example.spendtracker.domain.model.BillAlert;
import com.example.spendtracker.domain.repository.BillAlertRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

public class BillAlertRepositoryImpl implements BillAlertRepository {
    private final BillAlertDao billAlertDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public BillAlertRepositoryImpl(BillAlertDao billAlertDao) {
        this.billAlertDao = billAlertDao;
    }

    @Override
    public LiveData<List<BillAlert>> getActiveAlerts() {
        return Transformations.map(billAlertDao.getActiveAlerts(), this::mapList);
    }

    @Override
    public LiveData<List<BillAlert>> getAllAlerts() {
        return Transformations.map(billAlertDao.getAllAlerts(), this::mapList);
    }

    @Override
    public void resolveAlert(int id) {
        executor.execute(() -> billAlertDao.resolveAlert(id));
    }

    private List<BillAlert> mapList(List<BillAlertEntity> entities) {
        List<BillAlert> list = new ArrayList<>();
        if (entities != null) {
            for (BillAlertEntity e : entities) {
                list.add(new BillAlert(
                        e.id, e.sender, e.template, e.lastMessage,
                        e.occurrenceCount, e.lastSeen, e.amount, e.isResolved
                ));
            }
        }
        return list;
    }
}
