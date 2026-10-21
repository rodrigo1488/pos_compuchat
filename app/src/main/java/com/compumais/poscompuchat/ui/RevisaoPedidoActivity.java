package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.compumais.poscompuchat.Prefs;
import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.ProductEntity;
import com.compumais.poscompuchat.data.SyncRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RevisaoPedidoActivity extends AppCompatActivity implements CartListAdapter.Host {
    public static final String EXTRA_MESA_ID = "mesaId";
    public static final String EXTRA_TABLE_NUMBER = "tableNumber";
    public static final String EXTRA_CUSTOMER = "customerName";
    public static final String EXTRA_HEADER = "header";
    public static final String EXTRA_CART_JSON = "cartJson";

    private static final Type CART_TYPE = new TypeToken<ArrayList<CatalogModels.CartLine>>() {}.getType();

    private final List<CatalogModels.CartLine> cart = new ArrayList<>();
    private final List<CatalogModels.ProductDto> products = new ArrayList<>();
    private final Gson gson = new Gson();
    private CartListAdapter adapter;
    private SyncRepository repo;
    private ConnectionWatch connectionWatch;
    private boolean online = true;
    private int mesaId;
    private String tableNumber;
    private String customerName;
    private String pendingOrderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revisao_pedido);
        ScreenInsets.apply(this);
        repo = new SyncRepository(this);
        connectionWatch = new ConnectionWatch(this, 8000);
        mesaId = getIntent().getIntExtra(EXTRA_MESA_ID, 0);
        tableNumber = getIntent().getStringExtra(EXTRA_TABLE_NUMBER);
        customerName = getIntent().getStringExtra(EXTRA_CUSTOMER);
        String header = getIntent().getStringExtra(EXTRA_HEADER);
        TextView headerView = findViewById(R.id.txtOrderHeader);
        if (header != null && !header.isEmpty()) {
            headerView.setText(header);
        }
        List<CatalogModels.CartLine> incoming = parseCart(getIntent().getStringExtra(EXTRA_CART_JSON));
        cart.addAll(incoming);

        RecyclerView list = findViewById(R.id.listCart);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartListAdapter(cart, this);
        list.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> returnCartToOrder());
        findViewById(R.id.btnDineIn).setOnClickListener(v -> sendOrder(false));
        findViewById(R.id.btnTakeaway).setOnClickListener(v -> sendOrder(true));
        findViewById(R.id.btnOrderStatusOk).setOnClickListener(v -> goBackToMesas());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (findViewById(R.id.panelOrderStatus).getVisibility() == View.VISIBLE
                        && findViewById(R.id.btnOrderStatusOk).getVisibility() == View.VISIBLE) {
                    goBackToMesas();
                    return;
                }
                if (findViewById(R.id.panelOrderStatus).getVisibility() == View.VISIBLE) {
                    return;
                }
                returnCartToOrder();
            }
        });
        refreshTotals();
        loadProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectionWatch != null) {
            connectionWatch.start(this::onConnectionStatus);
        }
    }

    @Override
    protected void onPause() {
        if (connectionWatch != null) {
            connectionWatch.stop();
        }
        super.onPause();
    }

    private void onConnectionStatus(boolean connected, String message) {
        online = connected;
        ConnectionWatch.bindBanner(this, connected, message);
        setSendButtonsEnabled(connected && !cart.isEmpty());
    }

    private void setSendButtonsEnabled(boolean enabled) {
        View dineIn = findViewById(R.id.btnDineIn);
        View takeaway = findViewById(R.id.btnTakeaway);
        if (dineIn != null) dineIn.setEnabled(enabled);
        if (takeaway != null) takeaway.setEnabled(enabled);
    }

    private void loadProducts() {
        new Thread(() -> {
            List<ProductEntity> rows = repo.dao().products();
            List<CatalogModels.ProductDto> parsed = new ArrayList<>();
            for (ProductEntity e : rows) {
                CatalogModels.ProductDto p = gson.fromJson(e.payloadJson, CatalogModels.ProductDto.class);
                if (p != null) {
                    parsed.add(p);
                }
            }
            runOnUiThread(() -> {
                products.clear();
                products.addAll(parsed);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    static List<CatalogModels.CartLine> parseCart(String json) {
        List<CatalogModels.CartLine> parsed = new Gson().fromJson(json, CART_TYPE);
        return parsed == null ? new ArrayList<>() : parsed;
    }

    private void refreshTotals() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        double total = 0;
        for (CatalogModels.CartLine line : cart) {
            total += line.lineTotal();
        }
        TextView txt = findViewById(R.id.txtCartTotal);
        txt.setText("Total  R$ " + String.format(Locale.US, "%.2f", total));
        setSendButtonsEnabled(online && !cart.isEmpty());
    }

    @Override
    public CatalogModels.ProductDto findProduct(int id) {
        for (CatalogModels.ProductDto p : products) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void onEditObservation(CatalogModels.CartLine line) {
        CartObservation.show(this, line, false, this::refreshTotals);
    }

    @Override
    public void onRemoveLine(int index) {
        if (index < 0 || index >= cart.size()) {
            return;
        }
        cart.remove(index);
        refreshTotals();
        if (cart.isEmpty()) {
            Toast.makeText(this, "Sacola vazia. Volte para adicionar itens.", Toast.LENGTH_SHORT).show();
        }
    }

    private void returnCartToOrder() {
        Intent data = new Intent();
        data.putExtra(EXTRA_CART_JSON, gson.toJson(cart));
        setResult(RESULT_OK, data);
        finish();
    }

    private void sendOrder(boolean takeaway) {
        if (!online) {
            Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cart.isEmpty()) {
            Toast.makeText(this, "Adicione itens", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingOrderId == null) {
            pendingOrderId = java.util.UUID.randomUUID().toString();
        }
        setSendButtonsEnabled(false);
        showOrderSending(takeaway);
        String table = tableNumber == null ? "" : tableNumber;
        String customer = customerName == null || customerName.isEmpty() ? "Cliente" : customerName;
        String fulfillment = takeaway ? "pickup" : "dine_in";
        new Thread(() -> {
            try {
                CatalogModels.OrderResult result = repo.api().sendOrder(
                        mesaId, table, customer, Prefs.userName(this),
                        new ArrayList<>(cart), pendingOrderId, fulfillment);
                runOnUiThread(() -> showOrderDone(result));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideOrderStatus();
                    setSendButtonsEnabled(online && !cart.isEmpty());
                    Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showOrderSending(boolean takeaway) {
        View panel = findViewById(R.id.panelOrderStatus);
        panel.setVisibility(View.VISIBLE);
        findViewById(R.id.progressOrder).setVisibility(View.VISIBLE);
        findViewById(R.id.imgOrderStatus).setVisibility(View.GONE);
        findViewById(R.id.btnOrderStatusOk).setVisibility(View.GONE);
        findViewById(R.id.txtOrderStatusSub).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.txtOrderStatusTitle)).setText(
                takeaway ? "Enviando para levar…" : "Enviando consumo no local…"
        );
    }

    private void showOrderDone(CatalogModels.OrderResult result) {
        pendingOrderId = null;
        cart.clear();
        refreshTotals();
        View panel = findViewById(R.id.panelOrderStatus);
        panel.setVisibility(View.VISIBLE);
        findViewById(R.id.progressOrder).setVisibility(View.GONE);
        ImageView icon = findViewById(R.id.imgOrderStatus);
        icon.setVisibility(View.VISIBLE);
        TextView title = findViewById(R.id.txtOrderStatusTitle);
        TextView sub = findViewById(R.id.txtOrderStatusSub);
        View ok = findViewById(R.id.btnOrderStatusOk);
        ok.setVisibility(View.VISIBLE);
        boolean printed = result == null || result.printed == null || result.printed;
        if (printed) {
            icon.setImageResource(R.drawable.ic_check);
            title.setText("Pedido concluído");
            sub.setVisibility(View.GONE);
        } else {
            icon.setImageResource(R.drawable.ic_alert);
            title.setText("Pedido concluído, mas não impresso");
            sub.setVisibility(View.VISIBLE);
            String err = result == null || result.printError == null || result.printError.isEmpty()
                    ? "O pedido foi gravado no Uniplus. Verifique o papel da impressora."
                    : result.printError;
            sub.setText(err);
        }
    }

    private void hideOrderStatus() {
        findViewById(R.id.panelOrderStatus).setVisibility(View.GONE);
    }

    private void goBackToMesas() {
        Intent i = new Intent(this, MesasActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
