package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.compumais.poscompuchat.Prefs;
import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.SyncRepository;
import com.compumais.poscompuchat.data.UserEntity;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {
    private static final int[] AVATAR_COLORS = {
            0xFF0F766E, 0xFF1D4ED8, 0xFFB45309, 0xFF7C3AED, 0xFFBE123C, 0xFF0369A1
    };

    private final List<UserEntity> all = new ArrayList<>();
    private final List<UserEntity> shown = new ArrayList<>();
    private SyncRepository repo;
    private UserAdapter adapter;
    private String query = "";
    private ConnectionWatch connectionWatch;
    private boolean online = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.isConfigured(this)) {
            startActivity(new Intent(this, ConfigActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);
        ScreenInsets.apply(this);
        repo = new SyncRepository(this);
        connectionWatch = new ConnectionWatch(this, 10000);

        TextView server = findViewById(R.id.txtServer);
        server.setText(Prefs.agentUrl(this));

        RecyclerView list = findViewById(R.id.listUsers);
        list.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new UserAdapter();
        list.setAdapter(adapter);

        TextInputEditText search = findViewById(R.id.inputSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.btnConfig).setOnClickListener(v ->
                startActivity(new Intent(this, ConfigActivity.class)));
        findViewById(R.id.btnRefresh).setOnClickListener(v ->
                startActivity(new Intent(this, SyncActivity.class)));
        loadUsers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo != null) {
            loadUsers();
        }
        if (connectionWatch != null) {
            connectionWatch.start((connected, message) -> {
                online = connected;
                ConnectionWatch.bindBanner(this, connected, message);
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

    private void loadUsers() {
        new Thread(() -> {
            List<UserEntity> loaded = repo.dao().users();
            Collections.sort(loaded, Comparator.comparing(
                    u -> u.name == null ? "" : u.name.toLowerCase(Locale.ROOT)));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(loaded);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (UserEntity u : all) {
            String name = u.name == null ? "" : u.name.toLowerCase(Locale.ROOT);
            String profile = u.profile == null ? "" : u.profile.toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !name.contains(q) && !profile.contains(q)) {
                continue;
            }
            shown.add(u);
        }
        adapter.notifyDataSetChanged();
        boolean empty = shown.isEmpty();
        findViewById(R.id.txtEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.listUsers).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void pick(UserEntity user) {
        if (!online) {
            Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!user.hasPin) {
            finishLogin(user, "");
            return;
        }
        View body = getLayoutInflater().inflate(R.layout.dialog_pin, null, false);
        TextInputEditText input = body.findViewById(R.id.inputPin);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PIN de " + user.name)
                .setView(body)
                .setPositiveButton("Entrar", (d, w) ->
                        finishLogin(user, String.valueOf(input.getText()).trim()))
                .setNegativeButton("Cancelar", null)
                .show();
        input.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        input.requestFocus();
    }

    private void finishLogin(UserEntity user, String pin) {
        if (!user.hasPin) {
            Prefs.saveUser(this, user.id, user.name);
            startActivity(new Intent(this, MesasActivity.class));
            finish();
            return;
        }
        new Thread(() -> {
            try {
                repo.api().login(user.id, pin);
                Prefs.saveUser(this, user.id, user.name);
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MesasActivity.class));
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            UserEntity u = shown.get(position);
            holder.title.setText(u.name);
            String profile = u.profile == null || u.profile.isEmpty() ? "Atendente" : u.profile;
            holder.sub.setText(u.hasPin ? profile + " · PIN" : profile);
            holder.avatar.setText(initial(u.name));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(AVATAR_COLORS[Math.abs(u.id) % AVATAR_COLORS.length]);
            holder.avatar.setBackground(bg);
            holder.itemView.setOnClickListener(v -> pick(u));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView avatar, title, sub;
            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.txtAvatar);
                title = v.findViewById(R.id.txtTitle);
                sub = v.findViewById(R.id.txtSubtitle);
            }
        }
    }

    private static String initial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
