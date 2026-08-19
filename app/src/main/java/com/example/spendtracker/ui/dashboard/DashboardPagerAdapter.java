package com.example.spendtracker.ui.dashboard;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class DashboardPagerAdapter extends FragmentStateAdapter {

    public DashboardPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new DailyTransactionsFragment();
            case 1: return new CalendarFragment();
            case 2: return new MonthlySummaryFragment();
            case 3: return new TotalSummaryFragment();
            case 4: return new NotesFragment();
            default: return new DailyTransactionsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}
