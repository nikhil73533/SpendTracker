package com.example.spendtracker.help;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import com.example.spendtracker.feedback.FeedbackManager;
import com.example.spendtracker.util.OnlineIntentLauncher;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class HelpFragment extends Fragment {
    private HelpAdapter adapter;
    private TextView empty;
    private ChipGroup categories;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                                   @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_help, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        HelpViewModel viewModel = new ViewModelProvider(this).get(HelpViewModel.class);
        ((com.google.android.material.appbar.MaterialToolbar) view.findViewById(R.id.help_toolbar))
                .setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        adapter = new HelpAdapter();
        RecyclerView list = view.findViewById(R.id.help_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        empty = view.findViewById(R.id.help_empty);
        categories = view.findViewById(R.id.help_categories);
        addCategory(viewModel, getString(R.string.label_all));

        SearchView search = view.findViewById(R.id.help_search);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String text) { viewModel.setQuery(text); return true; }
            @Override public boolean onQueryTextChange(String text) { viewModel.setQuery(text); return true; }
        });
        viewModel.getCategories().observe(getViewLifecycleOwner(), values -> {
            for (String category : values) addCategory(viewModel, category);
        });
        viewModel.getItems().observe(getViewLifecycleOwner(), values -> {
            adapter.submit(values);
            empty.setVisibility(values == null || values.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getLoadFailed().observe(getViewLifecycleOwner(), failed -> {
            if (Boolean.TRUE.equals(failed)) {
                empty.setText(R.string.help_load_error);
                empty.setVisibility(View.VISIBLE);
            }
        });

        view.findViewById(R.id.help_online).setOnClickListener(v -> {
            showOnlineResult(OnlineIntentLauncher.open(requireContext(), getString(R.string.help_center_url)));
        });
        view.findViewById(R.id.help_feedback).setOnClickListener(v -> {
            showOnlineResult(FeedbackManager.open(requireContext(), FeedbackManager.Type.FEEDBACK));
        });
    }

    private void addCategory(HelpViewModel viewModel, String label) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setEnsureMinTouchTargetSize(false);
        if (categories.getChildCount() == 0) chip.setChecked(true);
        chip.setOnClickListener(v -> viewModel.setCategory(getString(R.string.label_all).equals(label) ? "" : label));
        categories.addView(chip);
    }

    private void showOnlineResult(OnlineIntentLauncher.Result result) {
        if (result == OnlineIntentLauncher.Result.OPENED) return;
        int message = result == OnlineIntentLauncher.Result.OFFLINE ? R.string.internet_required
                : result == OnlineIntentLauncher.Result.NO_HANDLER ? R.string.browser_unavailable
                : R.string.online_service_unavailable;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
