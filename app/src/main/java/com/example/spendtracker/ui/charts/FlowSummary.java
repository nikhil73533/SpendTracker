package com.example.spendtracker.ui.charts;
import android.view.View;
import android.widget.TextView;
import com.example.spendtracker.R;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.util.TransferDirection;
import java.util.List;
import java.util.function.Function;
public final class FlowSummary {
    public static void render(View root, List<Transaction> transactions, Function<Double,String> format) {
        double income=0, expense=0, transfers=0, net=0;
        for (Transaction t : transactions) {
            if (TransferDirection.isTransfer(t)) transfers += t.getAmount();
            else if ("INCOME".equals(t.getType())) income += t.getAmount();
            else expense += t.getAmount();
            net += TransferDirection.isIncoming(t) ? t.getAmount() : -t.getAmount();
        }
        int[] ids={R.id.flow_total,R.id.flow_income,R.id.flow_expense,R.id.flow_transfer};
        double[] values={net,income,expense,transfers};
        for(int i=0;i<ids.length;i++) ((TextView)root.findViewById(ids[i])).setText(format.apply(values[i]));
    }
}
