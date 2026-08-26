package com.example.spendtracker.ui.more;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.RepeatedAlert;
import com.example.spendtracker.domain.repository.RepeatedAlertRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class RepeatedAlertsViewModel extends ViewModel {

    private final RepeatedAlertRepository repository;

    @Inject
    public RepeatedAlertsViewModel(RepeatedAlertRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<RepeatedAlert>> getActiveAlerts() {
        return repository.getActiveAlerts();
    }

    public void dismissAlert(int id) {
        repository.dismissAlert(id);
    }

    public void setAlertEnabled(int id, boolean enabled) {
        repository.setAlertEnabled(id, enabled);
    }

    public void clearDismissedAlerts() {
        repository.clearDismissedAlerts();
    }
}
