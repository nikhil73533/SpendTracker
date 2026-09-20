package com.example.spendtracker.ui.settings;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.example.spendtracker.util.UserProfile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class ProfileEditor {
    private ProfileEditor() {}
    public static void show(Context context, Runnable onSaved) {
        UserProfile profile = UserProfile.load(context);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, 0);
        EditText name = field(form, "Name", profile.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = field(form, "Email", profile.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = field(form, "Phone number", profile.phone, InputType.TYPE_CLASS_PHONE);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle("Profile")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            String emailText = email.getText().toString().trim();
            if (!emailText.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                email.setError("Enter a valid email address"); return;
            }
            new UserProfile(name.getText().toString(), emailText, phone.getText().toString()).save(context);
            onSaved.run(); dialog.dismiss();
        }));
        dialog.show();
    }
    private static EditText field(LinearLayout form, String label, String value, int inputType) {
        EditText field = new EditText(form.getContext());
        field.setHint(label); field.setContentDescription(label); field.setInputType(inputType);
        field.setSingleLine(true); field.setText(value);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        form.addView(field, new LinearLayout.LayoutParams(-1, -2)); return field;
    }
}
