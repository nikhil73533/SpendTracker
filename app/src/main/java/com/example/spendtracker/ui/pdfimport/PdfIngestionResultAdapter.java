package com.example.spendtracker.ui.pdfimport;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.spendtracker.R;
import java.util.ArrayList;
import java.util.List;

public class PdfIngestionResultAdapter extends RecyclerView.Adapter<PdfIngestionResultAdapter.ViewHolder> {

    private final List<PdfParserService.FileImportResult> results = new ArrayList<>();

    public void setResults(List<PdfParserService.FileImportResult> newResults) {
        results.clear();
        if (newResults != null) {
            results.addAll(newResults);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pdf_ingestion_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PdfParserService.FileImportResult item = results.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvStatusIcon;
        private final TextView tvFileName;
        private final TextView tvFileDetail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStatusIcon = itemView.findViewById(R.id.tv_status_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileDetail = itemView.findViewById(R.id.tv_file_detail);
        }

        public void bind(PdfParserService.FileImportResult result) {
            tvFileName.setText(result.fileName);

            if (result.error != null && result.successfullyParsed == 0) {
                tvStatusIcon.setText("✗");
                tvStatusIcon.setTextColor(Color.parseColor("#F44336"));
                tvFileDetail.setText("Parsing failed: " + result.error);
                tvFileDetail.setTextColor(Color.parseColor("#EF5350"));
            } else {
                tvStatusIcon.setText("✓");
                tvStatusIcon.setTextColor(Color.parseColor("#4CAF50"));

                StringBuilder sb = new StringBuilder();
                sb.append(result.bankName).append(" • ");
                sb.append(result.successfullyParsed).append(" transactions imported");

                if (result.duplicatesSkipped > 0) {
                    sb.append(" (").append(result.duplicatesSkipped).append(" duplicates skipped)");
                }

                if (result.error != null) {
                    sb.append(" [Warning: ").append(result.error).append("]");
                }

                tvFileDetail.setText(sb.toString());
                tvFileDetail.setTextColor(Color.parseColor("#B0BEC5"));
            }
        }
    }
}
