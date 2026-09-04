package com.example.spendtracker.ui.pdfimport;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.BulkImportResult;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/** PDF import UI: extract first, let the user review, then commit an approved batch. */
@AndroidEntryPoint
public class PdfIngestionFragment extends Fragment {
    private PdfIngestionViewModel viewModel;
    private PdfIngestionResultAdapter resultAdapter;
    private PdfIngestionReviewAdapter reviewAdapter;

    private View layoutProgress;
    private View cardResults;
    private View cardReview;
    private TextView tvProgressStatus;
    private TextView tvProgressCounter;
    private ProgressBar progressBar;
    private TextView tvResultSummaryHeader;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                List<Uri> selectedUris = new ArrayList<>();
                if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null) selectedUris.add(uri);
                    }
                } else if (data.getData() != null) {
                    selectedUris.add(data.getData());
                }
                for (Uri uri : selectedUris) {
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                        // Some document providers intentionally do not grant persistent access.
                    }
                }
                if (!selectedUris.isEmpty()) viewModel.parsePdfs(requireContext(), selectedUris);
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pdf_ingestion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PdfIngestionViewModel.class);
        layoutProgress = view.findViewById(R.id.layout_progress);
        cardResults = view.findViewById(R.id.card_results);
        cardReview = view.findViewById(R.id.card_review);
        tvProgressStatus = view.findViewById(R.id.tv_progress_status);
        tvProgressCounter = view.findViewById(R.id.tv_progress_counter);
        progressBar = view.findViewById(R.id.progress_bar);
        tvResultSummaryHeader = view.findViewById(R.id.tv_result_summary_header);

        resultAdapter = new PdfIngestionResultAdapter();
        RecyclerView results = view.findViewById(R.id.rv_file_results);
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        results.setAdapter(resultAdapter);
        reviewAdapter = new PdfIngestionReviewAdapter();
        RecyclerView review = view.findViewById(R.id.rv_review_transactions);
        review.setLayoutManager(new LinearLayoutManager(requireContext()));
        review.setAdapter(reviewAdapter);

        ((com.google.android.material.appbar.MaterialToolbar) view.findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btn_select_pdf).setOnClickListener(v -> selectPdfs());
        view.findViewById(R.id.btn_done).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btn_import_selected).setOnClickListener(v -> viewModel.importApproved(reviewAdapter.getSelectedTransactions()));

        viewModel.getState().observe(getViewLifecycleOwner(), this::renderState);
    }

    private void selectPdfs() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pdfPickerLauncher.launch(Intent.createChooser(intent, "Select Bank Statements"));
    }

    private void renderState(PdfIngestionViewModel.UiState state) {
        layoutProgress.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
        if (state.isLoading && state.completedFiles == 0) {
            cardResults.setVisibility(View.GONE);
            cardReview.setVisibility(View.GONE);
            reviewAdapter.submit(new ArrayList<>());
        }
        if (state.isLoading) {
            tvProgressStatus.setText(state.progress);
            progressBar.setIndeterminate(false);
            progressBar.setMax(Math.max(1, state.totalFiles));
            progressBar.setProgress(state.completedFiles);
            tvProgressCounter.setText("Processing file " + Math.min(state.completedFiles + 1, Math.max(1, state.totalFiles)) + " of " + state.totalFiles);
        }
        if (!state.fileResults.isEmpty()) {
            cardResults.setVisibility(View.VISIBLE);
            resultAdapter.setResults(state.fileResults);
            int parsed = 0;
            for (PdfParserService.FileImportResult result : state.fileResults) parsed += result.successfullyParsed;
            tvResultSummaryHeader.setText(state.fileResults.size() + " PDF file(s) processed • " + parsed + " candidate transaction(s) extracted");
        }
        if (!state.reviewTransactions.isEmpty()) {
            cardReview.setVisibility(View.VISIBLE);
            reviewAdapter.submit(state.reviewTransactions);
        }
        BulkImportResult result = state.importResult;
        if (result != null) {
            if (result.isSuccess()) {
                cardReview.setVisibility(View.GONE);
                Toast.makeText(requireContext(), result.getImported() + " imported, " + result.getDuplicates() + " duplicate(s) skipped", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), result.getError(), Toast.LENGTH_LONG).show();
            }
        }
        if (state.error != null) Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show();
    }
}
