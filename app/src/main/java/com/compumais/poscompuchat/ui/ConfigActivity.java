package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.compumais.poscompuchat.Prefs;
import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.PosApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ConfigActivity extends AppCompatActivity {
    private TextInputEditText inputIp;
    private TextInputEditText inputPort;
    private TextInputEditText inputToken;
    private TextView txtStatus;
    private MaterialButton btnSave;
    private MaterialButton btnCatalog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        ScreenInsets.apply(this);

        inputIp = findViewById(R.id.inputIp);
        inputPort = findViewById(R.id.inputPort);
        inputToken = findViewById(R.id.inputToken);
        txtStatus = findViewById(R.id.txtStatus);
        btnSave = findViewById(R.id.btnSave);
        btnCatalog = findViewById(R.id.btnCatalog);
        View btnBack = findViewById(R.id.btnBack);

        fillFromSaved();
        inputToken.setText(Prefs.token(this));

        if (Prefs.isConfigured(this)) {
            btnBack.setVisibility(View.VISIBLE);
            btnBack.setOnClickListener(v -> finish());
        }

        btnSave.setOnClickListener(v -> testAndSave(false));
        btnCatalog.setOnClickListener(v -> testAndSave(true));
        findViewById(R.id.btnCancelFila).setOnClickListener(v -> confirmCancelFila());
        bindLogout();
    }

    private void confirmCancelFila() {
        new AlertDialog.Builder(this)
                .setTitle("Cancelar fila")
                .setMessage("Descartar os cupons ainda na memória de todas as impressoras?")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Limpar fila", (d, w) -> cancelFila())
                .show();
    }

    private void cancelFila() {
        setBusy(true);
        txtStatus.setText("Cancelando fila de impressão…");
        new Thread(() -> {
            try {
                CatalogModels.CancelFilaResult result = new PosApi(this).cancelarFila(null);
                runOnUiThread(() -> {
                    setBusy(false);
                    txtStatus.setText(formatCancelResult(result));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    txtStatus.setText(PosApi.friendlyError(e));
                });
            }
        }).start();
    }

    private static String formatCancelResult(CatalogModels.CancelFilaResult result) {
        if (result == null) {
            return "Falha ao cancelar a fila";
        }
        if (result.results != null && !result.results.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CatalogModels.CancelFilaPrinter r : result.results) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                String name = r.name == null || r.name.isEmpty() ? "Impressora" : r.name;
                sb.append(name).append(": ").append(r.ok
                        ? (r.message == null || r.message.isEmpty() ? "fila cancelada" : r.message)
                        : (r.message == null || r.message.isEmpty() ? "falhou" : r.message));
            }
            return sb.toString();
        }
        if (result.ok) {
            return "Fila cancelada";
        }
        return result.error == null || result.error.isEmpty() ? "Falha ao cancelar a fila" : result.error;
    }

    private void bindLogout() {
        TextView logged = findViewById(R.id.txtLoggedUser);
        View btnLogout = findViewById(R.id.btnLogout);
        String name = Prefs.userName(this);
        if (Prefs.userId(this) <= 0) {
            logged.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
            return;
        }
        logged.setVisibility(View.VISIBLE);
        logged.setText("Logado como " + (name == null || name.isEmpty() ? "atendente" : name));
        btnLogout.setVisibility(View.VISIBLE);
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        String name = Prefs.userName(this);
        String who = name == null || name.isEmpty() ? "esta conta" : name;
        new AlertDialog.Builder(this)
                .setTitle("Sair")
                .setMessage("Deseja sair de " + who + "? O IP do servidor será mantido.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair", (d, w) -> logout())
                .show();
    }

    private void logout() {
        Prefs.clearUser(this);
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void testAndSave(boolean loadCatalog) {
        String url = buildUrl(textOf(inputIp), textOf(inputPort));
        if (url == null) {
            txtStatus.setText("Informe o IP do servidor (ex.: 192.168.0.10)");
            return;
        }
        String token = textOf(inputToken);
        setBusy(true);
        txtStatus.setText(loadCatalog
                ? "Testando conexão para carregar o catálogo…"
                : "Testando conexão com o servidor…");
        new Thread(() -> {
            try {
                new PosApi(this).ping(url, token);
                runOnUiThread(() -> {
                    Prefs.saveAgent(this, url, token);
                    if (loadCatalog) {
                        startCatalogSync();
                    } else {
                        setBusy(false);
                        txtStatus.setText("Servidor conectado: " + url);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    txtStatus.setText(PosApi.friendlyError(e));
                });
            }
        }).start();
    }

    private void startCatalogSync() {
        Intent i = new Intent(this, SyncActivity.class);
        if (Prefs.userId(this) > 0) {
            i.putExtra(SyncActivity.EXTRA_RETURN_TO_MESAS, true);
        } else {
            i.putExtra(SyncActivity.EXTRA_GO_TO_LOGIN, true);
        }
        startActivity(i);
        setBusy(false);
        txtStatus.setText("Carga do Print Agent iniciada.");
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
        btnCatalog.setEnabled(!busy);
        View cancel = findViewById(R.id.btnCancelFila);
        if (cancel != null) {
            cancel.setEnabled(!busy);
        }
    }

    private void fillFromSaved() {
        String saved = Prefs.agentUrl(this);
        if (saved == null || saved.isEmpty()) {
            inputPort.setText("5000");
            return;
        }
        try {
            Uri uri = Uri.parse(saved);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host != null && !host.isEmpty()) {
                inputIp.setText(host);
            } else {
                inputIp.setText(saved.replaceFirst("^https?://", ""));
            }
            inputPort.setText(port > 0 ? String.valueOf(port) : "5000");
        } catch (Exception e) {
            inputIp.setText(saved);
            inputPort.setText("5000");
        }
    }

    private static String buildUrl(String ip, String port) {
        if (ip == null) {
            return null;
        }
        ip = ip.trim();
        if (ip.isEmpty()) {
            return null;
        }
        if (ip.startsWith("http://") || ip.startsWith("https://")) {
            return ip.replaceAll("/+$", "");
        }
        if (port == null || port.trim().isEmpty()) {
            port = "5000";
        }
        return "http://" + ip.replaceAll("/+$", "") + ":" + port.trim();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : String.valueOf(input.getText()).trim();
    }
}
