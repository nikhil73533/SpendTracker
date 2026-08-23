package com.example.spendtracker.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import com.example.spendtracker.databinding.FragmentDashboardNotesBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class TransactionGroupFragment extends Fragment {

    private FragmentDashboardNotesBinding binding;
    private TransactionGroupViewModel viewModel;
    private GroupListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionGroupViewModel.class);

        adapter = new GroupListAdapter(group -> showGroupForm(group.id));
        binding.rvGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGroups.setAdapter(adapter);

        binding.fabAddGroup.setOnClickListener(v -> showGroupForm(-1));

        viewModel.getAllGroups().observe(getViewLifecycleOwner(), groups -> {
            if (groups == null || groups.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.rvGroups.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvGroups.setVisibility(View.VISIBLE);
                adapter.submitList(groups);
            }
        });
    }

    private void showGroupForm(int groupId) {
        TransactionGroupFormFragment form = TransactionGroupFormFragment.newInstance(groupId);
        form.show(getChildFragmentManager(), "group_form");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    interface OnGroupClickListener {
        void onClick(TransactionGroupEntity group);
    }

    static class GroupListAdapter extends ListAdapter<TransactionGroupEntity, GroupListAdapter.ViewHolder> {
        private final OnGroupClickListener listener;
        private static final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        protected GroupListAdapter(OnGroupClickListener listener) {
            super(new DiffUtil.ItemCallback<TransactionGroupEntity>() {
                @Override public boolean areItemsTheSame(@NonNull TransactionGroupEntity a, @NonNull TransactionGroupEntity b) { return a.id == b.id; }
                @Override public boolean areContentsTheSame(@NonNull TransactionGroupEntity a, @NonNull TransactionGroupEntity b) {
                    return a.id == b.id && a.startDate == b.startDate && a.endDate == b.endDate
                            && (a.name != null ? a.name.equals(b.name) : b.name == null);
                }
            });
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_group, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDateRange, tvStatus;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_group_name);
                tvDateRange = itemView.findViewById(R.id.tv_date_range);
                tvStatus = itemView.findViewById(R.id.tv_group_status);
            }

            void bind(TransactionGroupEntity group, OnGroupClickListener listener) {
                tvName.setText(group.name);
                String range = sdf.format(new Date(group.startDate)) + " — " + sdf.format(new Date(group.endDate));
                tvDateRange.setText(range);
                tvStatus.setText(group.isActive ? "Active" : "Inactive");
                tvStatus.setTextColor(group.isActive ? 0xFF4CAF50 : 0xFF9E9E9E);
                itemView.setOnClickListener(v -> listener.onClick(group));
            }
        }
    }
}
