package com.example.spendtracker.util;
import android.content.Context;
import com.example.spendtracker.domain.repository.TransactionRepository;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.android.EntryPointAccessors;
public final class BookmarkActions {
    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies { TransactionRepository transactions(); }
    public static void set(Context context, int id, boolean value) {
        EntryPointAccessors.fromApplication(context.getApplicationContext(), Dependencies.class).transactions().setBookmarked(id, value);
    }
}
