package com.example.spendtracker.di;

import com.example.spendtracker.data.repository.AnalyticsRepositoryImpl;
import com.example.spendtracker.data.repository.SecurityRepositoryImpl;
import com.example.spendtracker.data.repository.TransactionGroupRepositoryImpl;
import com.example.spendtracker.data.repository.TransactionRepositoryImpl;
import com.example.spendtracker.domain.repository.AnalyticsRepository;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionGroupRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.spendtracker.domain.repository.RepeatedAlertRepository;
import com.example.spendtracker.data.repository.RepeatedAlertRepositoryImpl;
import com.example.spendtracker.domain.repository.BillAlertRepository;
import com.example.spendtracker.data.repository.BillAlertRepositoryImpl;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public abstract class RepositoryModule {

@Binds
    @Singleton
    public abstract TransactionRepository bindTransactionRepository(TransactionRepositoryImpl implementation);

    @Binds
    @Singleton
    public abstract AnalyticsRepository bindAnalyticsRepository(AnalyticsRepositoryImpl implementation);

    @Binds
    @Singleton
    public abstract SecurityRepository bindSecurityRepository(SecurityRepositoryImpl implementation);

    @Binds
    @Singleton
    public abstract TransactionGroupRepository bindTransactionGroupRepository(TransactionGroupRepositoryImpl implementation);

    @Binds
    @Singleton
    public abstract RepeatedAlertRepository bindRepeatedAlertRepository(RepeatedAlertRepositoryImpl implementation);

    @Binds
    @Singleton
    public abstract BillAlertRepository bindBillAlertRepository(BillAlertRepositoryImpl implementation);
}
