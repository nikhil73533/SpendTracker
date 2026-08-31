package com.example.spendtracker.domain.repository;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.domain.model.BillAlert;
import java.util.List;

public interface BillAlertRepository {
    LiveData<List<BillAlert>> getActiveAlerts();
    LiveData<List<BillAlert>> getAllAlerts();
    void resolveAlert(int id);
}
