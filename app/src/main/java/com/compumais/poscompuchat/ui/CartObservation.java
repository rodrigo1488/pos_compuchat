package com.compumais.poscompuchat.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.google.android.material.textfield.TextInputEditText;

final class CartObservation {
    private CartObservation() {}

    static void show(Activity activity, CatalogModels.CartLine line, boolean addedNow, Runnable onChanged) {
        View body = LayoutInflater.from(activity).inflate(R.layout.dialog_observation, null, false);
        TextView product = body.findViewById(R.id.txtObsProduct);
        TextInputEditText input = body.findViewById(R.id.inputObservation);
        product.setText(line.productName);
        if (line.observation != null && !line.observation.isEmpty()) {
            input.setText(line.observation);
            input.setSelection(line.observation.length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Observação")
                .setView(body)
                .setPositiveButton("Salvar", (d, w) -> {
                    line.observation = sanitize(String.valueOf(input.getText()));
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
        if (addedNow) {
            builder.setNegativeButton("Pular", null);
        } else {
            builder.setNegativeButton("Cancelar", null);
            builder.setNeutralButton("Limpar", (d, w) -> {
                line.observation = "";
                if (onChanged != null) {
                    onChanged.run();
                }
            });
        }
        builder.show();
        input.requestFocus();
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace('\n', ' ').replace('\r', ' ').trim();
        while (text.contains("  ")) {
            text = text.replace("  ", " ");
        }
        if (text.length() > 120) {
            text = text.substring(0, 120).trim();
        }
        return text;
    }
}
