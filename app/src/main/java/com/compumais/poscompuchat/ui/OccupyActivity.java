package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.compumais.poscompuchat.Prefs;
import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.MesaEntity;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.SyncRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OccupyActivity extends AppCompatActivity {
    private int mesaId;
    private SyncRepository repo;
    private ConnectionWatch connectionWatch;
    private boolean online = true;
    private boolean ignoreText;
    private int searchGen;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final List<CatalogModels.CustomerDto> customers = new ArrayList<>();
    private TextInputEditText inputName;
    private TextInputEditText inputPhone;
    private TextInputLayout phoneLayout;
    private ListView listCustomers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_occupy);
        ScreenInsets.apply(this);
        mesaId = getIntent().getIntExtra("mesaId", 0);
        repo = new SyncRepository(this);
        connectionWatch = new ConnectionWatch(this, 8000);
        TextView txt = findViewById(R.id.txtMesa);
        inputName = findViewById(R.id.inputName);
        inputPhone = findViewById(R.id.inputPhone);
        phoneLayout = findViewById(R.id.phoneLayout);
        listCustomers = findViewById(R.id.listCustomers);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        listCustomers.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= customers.size()) return;
            CatalogModels.CustomerDto customer = customers.get(position);
            ignoreText = true;
            inputName.setText(customer.name == null ? "" : customer.name);
            inputPhone.setText(customer.phone == null ? "" : customer.phone);
            ignoreText = false;
            listCustomers.setVisibility(View.GONE);
        });
        inputName.addTextChangedListener(searchWatcher("name"));
        inputPhone.addTextChangedListener(searchWatcher("phone"));
        inputName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) scheduleSearch("name");
        });
        inputPhone.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) scheduleSearch("phone");
        });

        new Thread(() -> {
            MesaEntity m = repo.dao().mesa(mesaId);
            boolean ask = m != null && (m.askPhone || m.keywordValidation);
            try {
                PosApi.OccupationSettings settings = repo.api().occupationSettings();
                if (settings.askPhone || settings.keywordValidation) ask = true;
            } catch (Exception ignored) {
            }
            boolean showPhone = ask;
            runOnUiThread(() -> {
                txt.setText(m == null ? "Mesa" : "Ocupar " + label(m));
                phoneLayout.setVisibility(showPhone ? View.VISIBLE : View.GONE);
            });
        }).start();

        findViewById(R.id.btnOccupy).setOnClickListener(v -> occupy());
    }

    private void occupy() {
        if (!online) {
            Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = String.valueOf(inputName.getText()).trim();
        if (name.isEmpty()) name = "Cliente";
        String phone = phoneLayout.getVisibility() == View.VISIBLE
                ? String.valueOf(inputPhone.getText()).trim() : "";
        String finalName = name;
        findViewById(R.id.btnOccupy).setEnabled(false);
        new Thread(() -> {
            try {
                PosApi.OccupyResult result = repo.api().ocupar(
                        mesaId, finalName, phone, Prefs.userId(this));
                if (result != null && "pending_confirmation".equals(result.status)) {
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        showKeywordDialog(finalName, phone, result.message);
                    });
                    return;
                }
                markOccupied(finalName, result == null ? phone : firstNonEmpty(result.contactPhone, phone));
                MesaEntity m = repo.dao().mesa(mesaId);
                runOnUiThread(() -> openOrder(m));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    findViewById(R.id.btnOccupy).setEnabled(online);
                    Toast.makeText(this, keywordError(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showKeywordDialog(String name, String phone, String message) {
        EditText input = new EditText(this);
        input.setHint("Palavra-chave");
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        String hint = message == null || message.trim().isEmpty()
                ? "A palavra-chave foi enviada no WhatsApp do cliente."
                : message;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar ocupação")
                .setMessage(hint)
                .setView(input)
                .setNegativeButton("Cancelar", (d, w) ->
                        findViewById(R.id.btnOccupy).setEnabled(online))
                .setPositiveButton("Confirmar", null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String keyword = String.valueOf(input.getText()).trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "Informe a palavra-chave.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(() -> {
                try {
                    PosApi.OccupyResult result = repo.api().confirmarOcupacao(mesaId, keyword, name, phone);
                    String savedPhone = result == null ? phone : firstNonEmpty(result.contactPhone, phone);
                    String savedName = result == null || result.contactName == null || result.contactName.isEmpty()
                            ? name : result.contactName;
                    markOccupied(savedName, savedPhone);
                    MesaEntity m = repo.dao().mesa(mesaId);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        openOrder(m);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(this, keywordError(e), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        }));
        dialog.show();
    }

    private void markOccupied(String name, String phone) {
        MesaEntity m = repo.dao().mesa(mesaId);
        if (m == null) return;
        m.status = "ocupada";
        m.contactName = name;
        m.contactPhone = phone == null || phone.isEmpty() ? null : phone;
        repo.dao().upsertMesa(m);
    }

    private void openOrder(MesaEntity m) {
        Intent i = new Intent(this, OrderActivity.class);
        i.putExtra("mesaId", mesaId);
        i.putExtra("tableNumber", numericTable(m));
        startActivity(i);
        finish();
    }

    private TextWatcher searchWatcher(String source) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch(source);
            }
        };
    }

    private void scheduleSearch(String source) {
        if (ignoreText || phoneLayout.getVisibility() != View.VISIBLE) return;
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> {
            TextInputEditText field = "phone".equals(source) ? inputPhone : inputName;
            String q = String.valueOf(field.getText()).trim();
            int gen = ++searchGen;
            new Thread(() -> {
                try {
                    List<CatalogModels.CustomerDto> found = repo.api().customers(q);
                    runOnUiThread(() -> {
                        if (isFinishing() || gen != searchGen) return;
                        showCustomers(found);
                    });
                } catch (Exception ignored) {
                    runOnUiThread(() -> {
                        if (isFinishing() || gen != searchGen) return;
                        showCustomers(Collections.emptyList());
                    });
                }
            }).start();
        }, 250);
    }

    private void showCustomers(List<CatalogModels.CustomerDto> found) {
        customers.clear();
        if (found != null) customers.addAll(found);
        if (customers.isEmpty()) {
            listCustomers.setVisibility(View.GONE);
            return;
        }
        List<String> labels = new ArrayList<>();
        for (CatalogModels.CustomerDto customer : customers) {
            String label = customer.name == null ? "" : customer.name;
            if (customer.phone != null && !customer.phone.isEmpty()) {
                label = label.isEmpty() ? customer.phone : label + " · " + customer.phone;
            }
            labels.add(label);
        }
        listCustomers.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        listCustomers.setVisibility(View.VISIBLE);
    }

    private static String firstNonEmpty(String preferred, String fallback) {
        if (preferred != null && !preferred.trim().isEmpty()) return preferred.trim();
        return fallback == null ? "" : fallback.trim();
    }

    private static String keywordError(Exception e) {
        String raw = e == null || e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("KEYWORD_INVALID")) return "Palavra-chave incorreta.";
        if (raw.contains("EXPIRED")) return "Palavra-chave expirada. Ocupe a mesa de novo.";
        if (raw.contains("CONFIRMATION_NOT_FOUND")) return "Não há confirmação pendente. Ocupe a mesa de novo.";
        if (raw.contains("ERR_CONTACT_NOT_FOUND") || raw.contains("ERR_NO_CONTACT_FOUND")) {
            return "Não foi possível usar esse cliente. Tente de novo ou apague o telefone.";
        }
        if (raw.contains("ERR_INVALID_PHONE") || raw.contains("telefone_invalido")) return "Telefone inválido.";
        if (raw.contains("WHATSAPP")) return "Não foi possível enviar a palavra-chave pelo WhatsApp.";
        return PosApi.friendlyError(e);
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectionWatch != null) {
            connectionWatch.start((connected, message) -> {
                online = connected;
                ConnectionWatch.bindBanner(this, connected, message);
                findViewById(R.id.btnOccupy).setEnabled(connected);
            });
        }
    }

    @Override
    protected void onPause() {
        if (connectionWatch != null) {
            connectionWatch.stop();
        }
        super.onPause();
    }

    static String label(MesaEntity m) {
        String n = numericTable(m);
        if (n.isEmpty()) {
            n = m.number != null && !m.number.isEmpty() ? m.number : m.name;
        }
        return ("comanda".equalsIgnoreCase(m.type) ? "Comanda " : "Mesa ") + n;
    }

    static String numericTable(MesaEntity m) {
        if (m == null) return "";
        String fromNumber = lastDigits(m.number);
        if (!fromNumber.isEmpty()) return fromNumber;
        return lastDigits(m.name);
    }

    private static String lastDigits(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(raw);
        String last = "";
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }
}
