package com.example.spendtracker.ui.bookmarks;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.*;
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.*;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.*;

@AndroidEntryPoint
public class BookmarksFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        LinearLayout root = new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(requireContext().getColor(R.color.dark_background));
        TextView title = new TextView(requireContext()); title.setText("Bookmarks"); title.setTextSize(24); title.setPadding(24,24,24,16);
        title.setTextColor(requireContext().getColor(R.color.white)); root.addView(title);
        TextView empty = new TextView(requireContext()); empty.setText("No bookmarks yet. Tap the bookmark beside any transaction to save it here."); empty.setPadding(24,24,24,24);
        empty.setTextColor(requireContext().getColor(R.color.text_secondary)); root.addView(empty);
        RecyclerView list = new RecyclerView(requireContext()); list.setLayoutManager(new LinearLayoutManager(requireContext())); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        return root;
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        TransactionViewModel vm = new ViewModelProvider(this).get(TransactionViewModel.class);
        DashboardViewModel dashboard = new ViewModelProvider(this).get(DashboardViewModel.class);
        LinearLayout root = (LinearLayout)view; TextView empty=(TextView)root.getChildAt(1); RecyclerView list=(RecyclerView)root.getChildAt(2);
        GroupedTransactionAdapter adapter = new GroupedTransactionAdapter(new GroupedTransactionAdapter.OnTransactionClickListener() {
            public void onEdit(Transaction t) { Bundle b=new Bundle(); b.putInt("transactionId",t.getId()); Navigation.findNavController(view).navigate(R.id.transactionFormFragment,b); }
            public void onDelete(Transaction t) { vm.deleteTransaction(t); }
            public void onCategoryChange(Transaction t,String c) { dashboard.updateTransactionCategory(t,c); }
            public List<String> getCategoriesByType(String type) { List<String> values=("INCOME".equals(type)?vm.getIncomeCategories():vm.getExpenseCategories()).getValue(); return values==null?Collections.singletonList("Transfer"):values; }
        }, new GroupedTransactionAdapter.DataFormatter() {
            public String formatAmount(double amount) { return vm.formatAmount(amount); }
            public String maskPII(String value) { return vm.maskPII(value); }
        });
        list.setAdapter(adapter);
        vm.getTransactions().observe(getViewLifecycleOwner(), transactions -> {
            List<GroupedTransactionAdapter.ListItem> rows=new ArrayList<>();
            if(transactions!=null) for(Transaction t:transactions) if(t.isBookmarked()) rows.add(new GroupedTransactionAdapter.TransactionItem(t));
            adapter.submitList(rows); empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
        });
        vm.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> adapter.notifyDataSetChanged());
        vm.getIncomeCategories().observe(getViewLifecycleOwner(), ignored -> {});
        vm.getExpenseCategories().observe(getViewLifecycleOwner(), ignored -> {});
    }
}
