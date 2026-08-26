package com.example.spendtracker.ui.pdfimport;

import android.app.Activity;
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
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.transaction.TransactionViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AndroidEntryPoint
public class PdfIngestionFragment extends Fragment {

    private TransactionViewModel transactionViewModel;
    private PdfParserService pdfParserService;
    private ExecutorService executorService;

    private View layoutProgress;
    private View cardResults;
    private TextView tvProgressStatus;
    private ProgressBar progressBar;
    private TextView tvResultSummary;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        processPdf(uri);
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
        progressBar = view.findViewById(R.id.progress_bar);
        tvResultSummary = view.findViewById(R.id.tv_result_summary);

        view.findViewById(R.id.btn_select_pdf).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            pdfPickerLauncher.launch(Intent.createChooser(intent, "Select Bank Statement"));
        });

        view.findViewById(R.id.btn_done).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    private void processPdf(Uri uri) {
        layoutProgress.setVisibility(View.VISIBLE);
        cardResults.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        tvProgressStatus.setText("Extracting text and parsing transactions...");

        executorService.execute(() -> {
            PdfParserService.ImportResult result = pdfParserService.parsePdf(requireContext().getApplicationContext(), uri);
            
            if (getActivity() == null) return;
            
            getActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                
                layoutProgress.setVisibility(View.GONE);
                cardResults.setVisibility(View.VISIBLE);
                
                if (result.error != null) {
                    tvResultSummary.setText("Import failed:\n" + result.error);
                } else {
                    // Save to DB
                    for (Transaction t : result.transactions) {
                        transactionViewModel.addTransaction(t);
                    }
                    
                    String summary = "Status: Success\n" +
                            "Lines analyzed: " + result.totalFound + "\n" +
                            "Imported transactions: " + result.successfullyParsed;
                    tvResultSummary.setText(summary);
                    Toast.makeText(requireContext(), "Imported " + result.successfullyParsed + " transactions", Toast.LENGTH_SHORT).show();
                }
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
