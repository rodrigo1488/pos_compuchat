package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.compumais.poscompuchat.Prefs;
import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.MesaEntity;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.SyncRepository;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MesasActivity extends AppCompatActivity {
    private static final long POLL_MS = 4000;
    private final List<MesaEntity> all = new ArrayList<>();
    private final List<MesaEntity> shown = new ArrayList<>();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTick = this::pollOnce;
    private MesaAdapter adapter;
    private SyncRepository repo;
    private String filter = "all";
    private String query = "";
    private volatile boolean syncing;
    private boolean online = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mesas);
        ScreenInsets.apply(this);
        repo = new SyncRepository(this);
        TextView header = findViewById(R.id.txtHeader);
        header.setText("Olá, " + Prefs.userName(this));
        RecyclerView list = findViewById(R.id.listMesas);
        list.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new MesaAdapter();
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
        RadioGroup rg = findViewById(R.id.filterStatus);
        rg.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.filterLivre) filter = "livre";
            else if (id == R.id.filterOcupada) filter = "ocupada";
            else filter = "all";
            applyFilter();
        });
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, ConfigActivity.class)));
        findViewById(R.id.btnCardapio).setOnClickListener(v -> {
            Intent i = new Intent(this, OrderActivity.class);
            i.putExtra(OrderActivity.EXTRA_BROWSE_ONLY, true);
            startActivity(i);
        });
        findViewById(R.id.btnPedidos).setOnClickListener(v ->
                startActivity(new Intent(this, PedidosActivity.class)));
        reloadLocal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadLocal();
        syncNow(false);
        pollHandler.removeCallbacks(pollTick);
        pollHandler.postDelayed(pollTick, POLL_MS);
    }

    @Override
    protected void onPause() {
        pollHandler.removeCallbacks(pollTick);
        super.onPause();
    }

    private void pollOnce() {
        syncNow(false);
        pollHandler.postDelayed(pollTick, POLL_MS);
    }

    private void syncNow(boolean showError) {
        if (syncing) return;
        syncing = true;
        new Thread(() -> {
            try {
                List<CatalogModels.MesaDto> remote = repo.api().mesas();
                repo.replaceMesas(remote);
                runOnUiThread(() -> {
                    setOnline(true, null);
                    reloadLocal();
                });
            } catch (Exception e) {
                String msg = PosApi.friendlyError(e);
                runOnUiThread(() -> {
                    setOnline(false, msg);
                    if (showError) {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                syncing = false;
            }
        }).start();
    }

    private void reloadLocal() {
        new Thread(() -> {
            List<MesaEntity> loaded = repo.dao().mesas();
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
        for (MesaEntity m : all) {
            String status = m.status == null ? "livre" : m.status;
            if (!"all".equals(filter) && !filter.equalsIgnoreCase(status)) continue;
            String hay = ((m.number == null ? "" : m.number) + " " +
                    (m.name == null ? "" : m.name) + " " +
                    (m.contactName == null ? "" : m.contactName)).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            shown.add(m);
        }
        adapter.notifyDataSetChanged();
    }

    private void setOnline(boolean connected, String message) {
        online = connected;
        ConnectionWatch.bindBanner(this, connected, message);
    }

    private void openMesa(MesaEntity m) {
        if (!online) {
            Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ocupada = "ocupada".equalsIgnoreCase(m.status);
        Intent i = new Intent(this, ocupada ? OrderActivity.class : OccupyActivity.class);
        i.putExtra("mesaId", m.id);
        i.putExtra("tableNumber", OccupyActivity.numericTable(m));
        startActivity(i);
    }

    private void showTrocarNomeDialog(MesaEntity m) {
        if (!online) {
            Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trocar_nome, null);
        TextInputEditText input = dialogView.findViewById(R.id.inputName);
        String current = m.contactName == null || m.contactName.isEmpty() ? "Cliente" : m.contactName;
        input.setText(current);
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Trocar nome — Mesa " + OccupyActivity.numericTable(m))
                .setView(dialogView)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Informe o nome do cliente", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    salvarNomeCliente(m, name);
                })
                .show();
    }

    private void salvarNomeCliente(MesaEntity m, String name) {
        new Thread(() -> {
            try {
                String saved = repo.api().trocarNomeContato(m.id, name);
                m.contactName = saved;
                m.status = "ocupada";
                repo.dao().upsertMesa(m);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Nome atualizado", Toast.LENGTH_SHORT).show();
                    syncNow(false);
                });
            } catch (Exception ex) {
                runOnUiThread(() ->
                        Toast.makeText(this, PosApi.friendlyError(ex), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    class MesaAdapter extends RecyclerView.Adapter<MesaAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mesa, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MesaEntity m = shown.get(position);
            boolean ocupada = "ocupada".equalsIgnoreCase(m.status);
            String numero = OccupyActivity.numericTable(m);
            if (numero.isEmpty()) {
                numero = m.number != null ? m.number : "";
            }
            holder.numero.setText(numero);
            if (!ocupada) {
                holder.status.setImageResource(R.drawable.ic_mesa_livre);
            } else if (m.totalValue > 0) {
                holder.status.setImageResource(R.drawable.ic_mesa_ocupada);
            } else {
                holder.status.setImageResource(R.drawable.ic_relogio);
            }
            holder.numero.setBackgroundResource(
                    ocupada ? R.drawable.bg_mesa_bar_ocupada : R.drawable.bg_mesa_footer_livre);
            if (ocupada) {
                String cliente = m.contactName == null || m.contactName.isEmpty() ? "Ocupada" : m.contactName;
                holder.cliente.setVisibility(View.VISIBLE);
                holder.cliente.setText(cliente);
                holder.total.setVisibility(View.VISIBLE);
                holder.total.setText("R$ " + String.format(Locale.US, "%.2f", m.totalValue));
                holder.card.setStrokeColor(0xFF4B5563);
            } else {
                holder.cliente.setVisibility(View.GONE);
                holder.total.setVisibility(View.GONE);
                holder.card.setStrokeColor(0xFFD1D5DB);
            }
            holder.itemView.setOnClickListener(v -> openMesa(m));
            if (ocupada) {
                holder.itemView.setOnLongClickListener(v -> {
                    showTrocarNomeDialog(m);
                    return true;
                });
            } else {
                holder.itemView.setOnLongClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            ImageView status;
            TextView cliente, numero, total;
            VH(View v) {
                super(v);
                card = v.findViewById(R.id.cardMesa);
                status = v.findViewById(R.id.imgStatus);
                cliente = v.findViewById(R.id.txtCliente);
                numero = v.findViewById(R.id.txtNumero);
                total = v.findViewById(R.id.txtTotal);
            }
        }
    }
}
