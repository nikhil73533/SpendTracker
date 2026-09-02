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
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.spendtracker.ui.transaction.TransactionViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PdfIngestionFragment extends Fragment {

    @Inject
    TransactionRepository transactionRepository;

    private TransactionViewModel transactionViewModel;
    private PdfParserService pdfParserService;
    private ExecutorService executorService;
    private PdfIngestionResultAdapter resultAdapter;

    private View layoutProgress;
    private View cardResults;
    private TextView tvProgressStatus;
    private TextView tvProgressCounter;
    private ProgressBar progressBar;
    private TextView tvResultSummaryHeader;
    private RecyclerView rvFileResults;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    List<Uri> selectedUris = new ArrayList<>();
                    Intent data = result.getData();

                    if (data.getClipData() != null) {
                        ClipData clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            Uri uri = clipData.getItemAt(i).getUri();
                            if (uri != null) {
                                selectedUris.add(uri);
                            }
                        }
                    } else if (data.getData() != null) {
                        selectedUris.add(data.getData());
                    }

                    if (!selectedUris.isEmpty()) {
                        processPdfs(selectedUris);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pdf_ingestion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        pdfParserService = new PdfParserService();
        executorService = Executors.newSingleThreadExecutor();

        view.findViewById(R.id.toolbar).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        ((com.google.android.material.appbar.MaterialToolbar) view.findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        layoutProgress = view.findViewById(R.id.layout_progress);
        cardResults = view.findViewById(R.id.card_results);
        tvProgressStatus = view.findViewById(R.id.tv_progress_status);
        tvProgressCounter = view.findViewById(R.id.tv_progress_counter);
        progressBar = view.findViewById(R.id.progress_bar);
        tvResultSummaryHeader = view.findViewById(R.id.tv_result_summary_header);
        rvFileResults = view.findViewById(R.id.rv_file_results);

        resultAdapter = new PdfIngestionResultAdapter();
        rvFileResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFileResults.setAdapter(resultAdapter);

        view.findViewById(R.id.btn_select_pdf).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pdfPickerLauncher.launch(Intent.createChooser(intent, "Select Bank Statements"));
        });

        view.findViewById(R.id.btn_done).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    private void processPdfs(List<Uri> uris) {
        layoutProgress.setVisibility(View.VISIBLE);
        cardResults.setVisibility(View.GONE);
        progressBar.setIndeterminate(false);
        progressBar.setMax(uris.size());
        progressBar.setProgress(0);

        tvProgressStatus.setText("Parsing PDF statements offline...");
        tvProgressCounter.setText("Processing file 1 of " + uris.size());

        executorService.execute(() -> {
            List<Transaction> existingTransactions = transactionRepository != null ?
                    transactionRepository.getTransactionsSync() : new ArrayList<>();

            List<PdfParserService.FileImportResult> results = new ArrayList<>();
            int totalImportedCount = 0;
            int totalDuplicatesCount = 0;
            int totalFailedCount = 0;

            for (int i = 0; i < uris.size(); i++) {
                final int fileIndex = i + 1;
                Uri uri = uris.get(i);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            tvProgressCounter.setText("Processing file " + fileIndex + " of " + uris.size());
                            progressBar.setProgress(fileIndex);
                        }
                    });
                }

                PdfParserService.FileImportResult fileResult = pdfParserService.parsePdf(
                        requireContext().getApplicationContext(), uri, existingTransactions);

                results.add(fileResult);

                if (fileResult.error == null || !fileResult.transactions.isEmpty()) {
                    for (Transaction t : fileResult.transactions) {
                        transactionViewModel.addTransaction(t);
                        existingTransactions.add(t);
                    }
                    totalImportedCount += fileResult.successfullyParsed;
                    totalDuplicatesCount += fileResult.duplicatesSkipped;
                } else {
                    totalFailedCount++;
                }
            }

            final int finalImported = totalImportedCount;
            final int finalDuplicates = totalDuplicatesCount;
            final int finalFailed = totalFailedCount;

            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;

                layoutProgress.setVisibility(View.GONE);
                cardResults.setVisibility(View.VISIBLE);

                int successfulFiles = uris.size() - finalFailed;
                StringBuilder summary = new StringBuilder();
                summary.append(uris.size()).append(" PDF file").append(uris.size() > 1 ? "s" : "").append(" selected • ");
                summary.append(finalImported).append(" transactions imported");
                if (finalDuplicates > 0) {
                    summary.append(" (").append(finalDuplicates).append(" duplicates skipped)");
                }
                summary.append("\nSuccessful: ").append(successfulFiles).append(" file").append(successfulFiles != 1 ? "s" : "");
                if (finalFailed > 0) {
                    summary.append(" | Failed: ").append(finalFailed).append(" file").append(finalFailed != 1 ? "s" : "");
                }

                tvResultSummaryHeader.setText(summary.toString());
                resultAdapter.setResults(results);

                Toast.makeText(requireContext(), "Successfully imported " + finalImported + " transactions", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
