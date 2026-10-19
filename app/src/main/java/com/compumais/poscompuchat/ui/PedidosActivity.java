package com.compumais.poscompuchat.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.SyncRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class PedidosActivity extends AppCompatActivity {
    private static final long POLL_MS = 5000;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Row> rows = new ArrayList<>();
    private final List<CatalogModels.PedidoItem> all = new ArrayList<>();
    private final List<CatalogModels.PedidoPrinter> printers = new ArrayList<>();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTick = this::pollOnce;
    private PedidoAdapter adapter;
    private SyncRepository repo;
    private ConnectionWatch connectionWatch;
    private boolean groupByMesa;
    private boolean pendentes = true;
    private volatile boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pedidos);
        ScreenInsets.apply(this);
        repo = new SyncRepository(this);
        connectionWatch = new ConnectionWatch(this, 8000);
        RecyclerView list = findViewById(R.id.listPedidos);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PedidoAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        CheckBox chk = findViewById(R.id.chkAgruparMesa);
        chk.setOnCheckedChangeListener((b, checked) -> {
            groupByMesa = checked;
            rebuild();
        });
        RadioGroup rg = findViewById(R.id.filterPedidos);
        rg.setOnCheckedChangeListener((g, id) -> {
            pendentes = id != R.id.filterTodosPedidos;
            rebuild();
        });
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectionWatch != null) {
            connectionWatch.start(this::onConnectionStatus);
        }
        reload();
        pollHandler.removeCallbacks(pollTick);
        pollHandler.postDelayed(pollTick, POLL_MS);
    }

    @Override
    protected void onPause() {
        pollHandler.removeCallbacks(pollTick);
        if (connectionWatch != null) {
            connectionWatch.stop();
        }
        super.onPause();
    }

    private void onConnectionStatus(boolean connected, String message) {
        ConnectionWatch.bindBanner(this, connected, message);
    }

    private void pollOnce() {
        reload();
        pollHandler.postDelayed(pollTick, POLL_MS);
    }

    private void reload() {
        if (loading) {
            return;
        }
        loading = true;
        new Thread(() -> {
            try {
                CatalogModels.PedidoDia dia = repo.api().pedidos();
                runOnUiThread(() -> {
                    all.clear();
                    all.addAll(dia.itens);
                    printers.clear();
                    printers.addAll(dia.printers);
                    rebuild();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (all.isEmpty()) {
                        Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                loading = false;
            }
        }).start();
    }

    private void rebuild() {
        rows.clear();
        List<CatalogModels.PedidoPrinter> sections = new ArrayList<>(printers);
        if (sections.isEmpty()) {
            CatalogModels.PedidoPrinter fallback = new CatalogModels.PedidoPrinter();
            fallback.deviceId = "";
            fallback.name = "Pedidos";
            sections.add(fallback);
        }
        for (CatalogModels.PedidoPrinter printer : sections) {
            List<CatalogModels.PedidoItem> bucket = new ArrayList<>();
            for (CatalogModels.PedidoItem it : all) {
                if (pendentes && it.entregue) {
                    continue;
                }
                if (samePrinter(printer, it)) {
                    bucket.add(it);
                }
            }
            rows.add(Row.printerHeader(printer));
            if (bucket.isEmpty()) {
                continue;
            }
            if (groupByMesa) {
                LinkedHashMap<Integer, List<CatalogModels.PedidoItem>> byMesa = new LinkedHashMap<>();
                for (CatalogModels.PedidoItem it : bucket) {
                    List<CatalogModels.PedidoItem> list = byMesa.get(it.numeromesa);
                    if (list == null) {
                        list = new ArrayList<>();
                        byMesa.put(it.numeromesa, list);
                    }
                    list.add(it);
                }
                for (Integer mesa : byMesa.keySet()) {
                    rows.add(Row.header("Mesa " + mesa));
                    for (CatalogModels.PedidoItem it : byMesa.get(mesa)) {
                        rows.add(Row.item(it));
                    }
                }
            } else {
                for (CatalogModels.PedidoItem it : bucket) {
                    rows.add(Row.item(it));
                }
            }
        }
        adapter.notifyDataSetChanged();
        boolean emptyItems = true;
        for (Row row : rows) {
            if (row.item != null) {
                emptyItems = false;
                break;
            }
        }
        TextView empty = findViewById(R.id.txtPedidosEmpty);
        empty.setText(pendentes ? "Nenhum pedido pendente" : "Nenhum pedido no dia");
        empty.setVisibility(emptyItems ? View.VISIBLE : View.GONE);
        findViewById(R.id.listPedidos).setVisibility(View.VISIBLE);
    }

    private static boolean samePrinter(CatalogModels.PedidoPrinter printer, CatalogModels.PedidoItem it) {
        String a = printer.deviceId == null ? "" : printer.deviceId;
        String b = it.printerDeviceId == null ? "" : it.printerDeviceId;
        if (!a.isEmpty() || !b.isEmpty()) {
            return a.equalsIgnoreCase(b);
        }
        String na = printer.name == null ? "" : printer.name;
        String nb = it.printerName == null ? "" : it.printerName;
        return na.equalsIgnoreCase(nb);
    }

    private void entregar(CatalogModels.PedidoItem item) {
        if (item == null || item.entregue) {
            return;
        }
        item.entregue = true;
        rebuild();
        new Thread(() -> {
            try {
                repo.api().marcarEntregue(item.id, true);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    item.entregue = false;
                    rebuild();
                    Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void confirmCancelFila(CatalogModels.PedidoPrinter printer) {
        String name = printer == null || printer.name == null || printer.name.isEmpty()
                ? "esta impressora" : printer.name;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cancelar fila")
                .setMessage("Descartar os cupons ainda na memória de " + name + "?")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Limpar fila", (d, w) ->
                        cancelFila(printer == null ? "" : printer.deviceId))
                .show();
    }

    private void cancelFila(String deviceId) {
        new Thread(() -> {
            try {
                CatalogModels.CancelFilaResult result = repo.api().cancelarFila(deviceId);
                runOnUiThread(() -> {
                    String msg = formatCancelResult(result);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_LONG).show());
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

    static class Row {
        final int type;
        final String title;
        final CatalogModels.PedidoItem item;
        final CatalogModels.PedidoPrinter printer;

        private Row(int type, String title, CatalogModels.PedidoItem item, CatalogModels.PedidoPrinter printer) {
            this.type = type;
            this.title = title;
            this.item = item;
            this.printer = printer;
        }

        static Row header(String title) {
            return new Row(TYPE_HEADER, title, null, null);
        }

        static Row printerHeader(CatalogModels.PedidoPrinter printer) {
            String name = printer == null || printer.name == null || printer.name.isEmpty()
                    ? "Impressora" : printer.name;
            return new Row(TYPE_HEADER, name, null, printer);
        }

        static Row item(CatalogModels.PedidoItem item) {
            return new Row(TYPE_ITEM, null, item, null);
        }
    }

    class PedidoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(inf.inflate(R.layout.item_pedido_header, parent, false));
            }
            return new ItemVH(inf.inflate(R.layout.item_pedido, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderVH) {
                HeaderVH vh = (HeaderVH) holder;
                vh.title.setText(row.title);
                if (row.printer != null) {
                    vh.cancel.setVisibility(View.VISIBLE);
                    vh.cancel.setOnClickListener(v -> confirmCancelFila(row.printer));
                } else {
                    vh.cancel.setVisibility(View.GONE);
                    vh.cancel.setOnClickListener(null);
                }
                return;
            }
            CatalogModels.PedidoItem it = row.item;
            ItemVH vh = (ItemVH) holder;
            double qtd = it.quantidade;
            String qtdTxt = qtd == (long) qtd ? String.valueOf((long) qtd)
                    : String.format(Locale.US, "%.2f", qtd);
            vh.nome.setText(qtdTxt + "x  " + it.nomeproduto);
            String meta = "Mesa " + it.numeromesa;
            if (it.hora != null && !it.hora.isEmpty()) {
                meta += "  ·  " + it.hora;
            }
            if (!groupByMesa && it.cliente != null && !it.cliente.isEmpty()) {
                meta += "  ·  " + it.cliente;
            }
            vh.meta.setText(meta);
            if (it.observacao != null && !it.observacao.isEmpty()) {
                vh.obs.setVisibility(View.VISIBLE);
                vh.obs.setText(it.observacao);
            } else {
                vh.obs.setVisibility(View.GONE);
            }
            vh.btn.setEnabled(!it.entregue);
            vh.btn.setText(it.entregue ? "Entregue" : "Entregar");
            vh.btn.setOnClickListener(v -> entregar(it));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class HeaderVH extends RecyclerView.ViewHolder {
            TextView title;
            MaterialButton cancel;
            HeaderVH(View v) {
                super(v);
                title = v.findViewById(R.id.txtPedidoHeader);
                cancel = v.findViewById(R.id.btnCancelFila);
            }
        }

        class ItemVH extends RecyclerView.ViewHolder {
            TextView nome, meta, obs;
            MaterialButton btn;
            ItemVH(View v) {
                super(v);
                nome = v.findViewById(R.id.txtPedidoNome);
                meta = v.findViewById(R.id.txtPedidoMeta);
                obs = v.findViewById(R.id.txtPedidoObs);
                btn = v.findViewById(R.id.btnEntregar);
            }
        }
    }
}
