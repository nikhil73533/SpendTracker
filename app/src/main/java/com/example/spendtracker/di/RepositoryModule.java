package com.example.spendtracker.di;

import com.example.spendtracker.data.repository.SecurityRepositoryImpl;
import com.example.spendtracker.data.repository.TransactionRepositoryImpl;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
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
    public abstract SecurityRepository bindSecurityRepository(SecurityRepositoryImpl implementation);
}
