package com.example.spendtracker.domain.repository;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.domain.model.RepeatedAlert;
import java.util.List;

public interface RepeatedAlertRepository {
    LiveData<List<RepeatedAlert>> getActiveAlerts();
    LiveData<List<RepeatedAlert>> getAllAlerts();
    void dismissAlert(int id);
    void setAlertEnabled(int id, boolean enabled);
    void clearDismissedAlerts();
}
