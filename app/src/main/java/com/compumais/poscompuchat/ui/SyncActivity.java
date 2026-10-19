package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.SyncRepository;

public class SyncActivity extends AppCompatActivity {
    public static final String EXTRA_GO_TO_LOGIN = "goToLogin";
    public static final String EXTRA_RETURN_TO_MESAS = "returnToMesas";

    private ProgressBar bar;
    private TextView txtPercent;
    private TextView txtStep;
    private TextView txtError;
    private View panelRetry;
    private boolean running;
    private boolean goToLogin;
    private boolean returnToMesas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        ScreenInsets.apply(this);
        goToLogin = getIntent().getBooleanExtra(EXTRA_GO_TO_LOGIN, false);
        returnToMesas = getIntent().getBooleanExtra(EXTRA_RETURN_TO_MESAS, false);
        bar = findViewById(R.id.progressSync);
        txtPercent = findViewById(R.id.txtPercent);
        txtStep = findViewById(R.id.txtStep);
        txtError = findViewById(R.id.txtError);
        panelRetry = findViewById(R.id.panelRetry);
        findViewById(R.id.btnRetry).setOnClickListener(v -> startSync());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!running) {
                    finish();
                }
            }
        });
        startSync();
    }

    private void startSync() {
        running = true;
        panelRetry.setVisibility(View.GONE);
        txtError.setVisibility(View.GONE);
        update(0, "Preparando sincronização…");
        new Thread(() -> {
            try {
                new SyncRepository(this).sync(true, (percent, message) ->
                        runOnUiThread(() -> update(percent, message)));
                runOnUiThread(this::onSuccess);
            } catch (Exception e) {
                runOnUiThread(() -> onError(PosApi.friendlyError(e)));
            }
        }).start();
    }

    private void update(int percent, String message) {
        bar.setProgress(percent);
        txtPercent.setText(percent + "%");
        txtStep.setText(message);
    }

    private void onSuccess() {
        running = false;
        update(100, "Sincronização concluída");
        if (returnToMesas) {
            Intent i = new Intent(this, MesasActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } else if (goToLogin) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        finish();
    }

    private void onError(String message) {
        running = false;
        txtError.setVisibility(View.VISIBLE);
        txtError.setText(message == null ? "Falha na sincronização" : message);
        txtStep.setText("Não foi possível sincronizar");
        panelRetry.setVisibility(View.VISIBLE);
    }
}
