package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.compumais.poscompuchat.Prefs;
import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.MesaEntity;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.SyncRepository;
import com.google.android.material.textfield.TextInputEditText;

public class OccupyActivity extends AppCompatActivity {
    private int mesaId;
    private SyncRepository repo;
    private ConnectionWatch connectionWatch;
    private boolean online = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_occupy);
        ScreenInsets.apply(this);
        mesaId = getIntent().getIntExtra("mesaId", 0);
        repo = new SyncRepository(this);
        connectionWatch = new ConnectionWatch(this, 8000);
        TextView txt = findViewById(R.id.txtMesa);
        TextInputEditText input = findViewById(R.id.inputName);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        new Thread(() -> {
            MesaEntity m = repo.dao().mesa(mesaId);
            runOnUiThread(() -> txt.setText(m == null ? "Mesa" : "Ocupar " + label(m)));
        }).start();

        findViewById(R.id.btnOccupy).setOnClickListener(v -> {
            if (!online) {
                Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = String.valueOf(input.getText()).trim();
            if (name.isEmpty()) name = "Cliente";
            String finalName = name;
            findViewById(R.id.btnOccupy).setEnabled(false);
            new Thread(() -> {
                try {
                    repo.api().ocupar(mesaId, finalName, Prefs.userId(this));
                    MesaEntity m = repo.dao().mesa(mesaId);
                    if (m != null) {
                        m.status = "ocupada";
                        m.contactName = finalName;
                        repo.dao().upsertMesa(m);
                    }
                    runOnUiThread(() -> {
                        Intent i = new Intent(this, OrderActivity.class);
                        i.putExtra("mesaId", mesaId);
                        i.putExtra("tableNumber", numericTable(m));
                        startActivity(i);
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        findViewById(R.id.btnOccupy).setEnabled(online);
                        Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
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
