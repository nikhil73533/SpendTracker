package com.example.spendtracker.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.databinding.FragmentTrashBinding;
import com.example.spendtracker.domain.model.Transaction;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@AndroidEntryPoint
public class TrashFragment extends Fragment {

    private FragmentTrashBinding binding;
    private TrashViewModel viewModel;
    private TrashAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTrashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TrashViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        adapter = new TrashAdapter(
            transactionId -> {
                viewModel.restoreTransaction(transactionId);
                Toast.makeText(requireContext(), R.string.msg_transaction_restored, Toast.LENGTH_SHORT).show();
            },
            transactionId -> {
                new android.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_permanent_delete_title)
                    .setMessage(R.string.dialog_permanent_delete_msg)
                    .setPositiveButton(R.string.btn_delete_permanently, (dialog, which) -> {
                        viewModel.permanentlyDeleteTransaction(transactionId);
                        Toast.makeText(requireContext(), R.string.msg_permanently_deleted, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        );

        binding.rvTrash.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrash.setAdapter(adapter);

        viewModel.getDeletedTransactions().observe(getViewLifecycleOwner(), transactions -> {
            if (transactions == null || transactions.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.rvTrash.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvTrash.setVisibility(View.VISIBLE);
                adapter.submitList(transactions);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    interface TrashActionListener {
        void onAction(int transactionId);
    }

    static class TrashAdapter extends ListAdapter<Transaction, TrashAdapter.ViewHolder> {
        private final TrashActionListener onRestore;
        private final TrashActionListener onDelete;
        private static final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        protected TrashAdapter(TrashActionListener onRestore, TrashActionListener onDelete) {
            super(new DiffUtil.ItemCallback<Transaction>() {
                @Override public boolean areItemsTheSame(@NonNull Transaction a, @NonNull Transaction b) { return a.getId() == b.getId(); }
                @Override public boolean areContentsTheSame(@NonNull Transaction a, @NonNull Transaction b) { return a.getDeletedAt() == b.getDeletedAt(); }
            });
            this.onRestore = onRestore;
            this.onDelete = onDelete;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trash, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(getItem(position), onRestore, onDelete);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCategory, tvAmount, tvDescription, tvDeletedOn;
            View btnRestore, btnDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCategory = itemView.findViewById(R.id.tv_category);
                tvAmount = itemView.findViewById(R.id.tv_amount);
                tvDescription = itemView.findViewById(R.id.tv_description);
                tvDeletedOn = itemView.findViewById(R.id.tv_deleted_on);
                btnRestore = itemView.findViewById(R.id.btn_restore);
                btnDelete = itemView.findViewById(R.id.btn_permanent_delete);
            }

            void bind(Transaction t, TrashActionListener onRestore, TrashActionListener onDelete) {
                tvCategory.setText(t.getCategory());
                tvAmount.setText(String.format(Locale.getDefault(), "₹ %.0f", t.getAmount()));
                tvDescription.setText(t.getDescription());
                
                String dateStr = t.getDeletedAt() > 0 ? sdf.format(new Date(t.getDeletedAt())) : "Unknown";
                tvDeletedOn.setText(itemView.getContext().getString(R.string.label_deleted_on, dateStr));

                btnRestore.setOnClickListener(v -> onRestore.onAction(t.getId()));
                btnDelete.setOnClickListener(v -> onDelete.onAction(t.getId()));
            }
        }
    }
}
