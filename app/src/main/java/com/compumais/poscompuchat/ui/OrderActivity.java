package com.compumais.poscompuchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.ConnectionWatch;
import com.compumais.poscompuchat.data.ImageStore;
import com.compumais.poscompuchat.data.MesaEntity;
import com.compumais.poscompuchat.data.PosApi;
import com.compumais.poscompuchat.data.ProductEntity;
import com.compumais.poscompuchat.data.SyncRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderActivity extends AppCompatActivity {
    public static final String EXTRA_BROWSE_ONLY = "browseOnly";
    private final List<CatalogModels.ProductDto> allProducts = new ArrayList<>();
    private final List<CatalogModels.ProductDto> shown = new ArrayList<>();
    private final List<CatalogModels.CartLine> cart = new ArrayList<>();
    private final Map<Integer, Integer> pendingQty = new HashMap<>();
    private final Gson gson = new Gson();
    private SyncRepository repo;
    private ProductAdapter adapter;
    private int mesaId;
    private MesaEntity mesa;
    private String groupFilter = "Todos";
    private String search = "";
    private ConnectionWatch connectionWatch;
    private boolean online = true;
    private boolean browseOnly;
    private final ActivityResultLauncher<Intent> reviewLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                List<CatalogModels.CartLine> returned = RevisaoPedidoActivity.parseCart(
                        result.getData().getStringExtra(RevisaoPedidoActivity.EXTRA_CART_JSON));
                cart.clear();
                cart.addAll(returned);
                refreshCart();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);
        ScreenInsets.apply(this);
        mesaId = getIntent().getIntExtra("mesaId", 0);
        browseOnly = getIntent().getBooleanExtra(EXTRA_BROWSE_ONLY, false);
        repo = new SyncRepository(this);
        connectionWatch = new ConnectionWatch(this, 8000);
        RecyclerView list = findViewById(R.id.listProducts);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter();
        list.setAdapter(adapter);

        ((android.widget.TextView) findViewById(R.id.inputProductSearch)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                search = s == null ? "" : s.toString();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.btnSend).setOnClickListener(v -> openReview());
        findViewById(R.id.btnConta).setOnClickListener(v -> showConta());
        findViewById(R.id.btnTrocarNome).setOnClickListener(v -> showTrocarNomeDialog());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSacola).setOnClickListener(v -> {
            if (cart.isEmpty()) {
                Toast.makeText(this, "Sacola vazia", Toast.LENGTH_SHORT).show();
                return;
            }
            openReview();
        });
        refreshCart();
        applyBrowseMode();
        bootCatalog();
    }

    private void applyBrowseMode() {
        if (!browseOnly) {
            return;
        }
        findViewById(R.id.btnSacola).setVisibility(View.GONE);
        findViewById(R.id.txtSacolaCount).setVisibility(View.GONE);
        findViewById(R.id.panelFooter).setVisibility(View.GONE);
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
        View btnSend = findViewById(R.id.btnSend);
        if (btnSend != null) {
            btnSend.setEnabled(connected && !cart.isEmpty());
        }
        View btnConta = findViewById(R.id.btnConta);
        if (btnConta != null) {
            btnConta.setEnabled(connected && !browseOnly);
        }
    }

    private void bootCatalog() {
        new Thread(() -> {
            mesa = repo.dao().mesa(mesaId);
            List<ProductEntity> rows = repo.dao().products();
            List<CatalogModels.ProductDto> parsed = new ArrayList<>();
            LinkedHashSet<String> groups = new LinkedHashSet<>();
            groups.add("Todos");
            for (ProductEntity e : rows) {
                CatalogModels.ProductDto p = gson.fromJson(e.payloadJson, CatalogModels.ProductDto.class);
                if (p != null) {
                    parsed.add(p);
                    groups.add(p.grupo == null ? "Outros" : p.grupo);
                }
            }
            runOnUiThread(() -> {
                refreshOrderHeader();
                View btnTrocar = findViewById(R.id.btnTrocarNome);
                if (btnTrocar != null) {
                    btnTrocar.setVisibility(browseOnly ? View.GONE : View.VISIBLE);
                }
                allProducts.clear();
                allProducts.addAll(parsed);
                ChipGroup chips = findViewById(R.id.chipGroups);
                chips.removeAllViews();
                chips.setSingleSelection(true);
                chips.setSelectionRequired(true);
                for (String g : groups) {
                    Chip chip = new Chip(this);
                    chip.setId(View.generateViewId());
                    chip.setText(g);
                    chip.setCheckable(true);
                    chip.setTag(g);
                    chip.setChecked("Todos".equals(g));
                    chips.addView(chip);
                }
                chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                    if (checkedIds == null || checkedIds.isEmpty()) {
                        return;
                    }
                    View selected = group.findViewById(checkedIds.get(0));
                    if (selected == null || selected.getTag() == null) {
                        return;
                    }
                    groupFilter = String.valueOf(selected.getTag());
                    applyFilter();
                });
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        shown.clear();
        String q = search.toLowerCase(Locale.ROOT).trim();
        for (CatalogModels.ProductDto p : allProducts) {
            String g = p.grupo == null ? "Outros" : p.grupo;
            if (!"Todos".equals(groupFilter) && !groupFilter.equals(g)) continue;
            String hay = ((p.name == null ? "" : p.name) + " " + (p.description == null ? "" : p.description))
                    .toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            shown.add(p);
        }
        adapter.notifyDataSetChanged();
    }

    private String currentTableNumber() {
        String tableNumber = getIntent().getStringExtra("tableNumber");
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            tableNumber = OccupyActivity.numericTable(mesa);
        }
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            tableNumber = mesa != null && mesa.number != null ? mesa.number : "";
        }
        return tableNumber;
    }

    private void refreshOrderHeader() {
        TextView header = findViewById(R.id.txtOrderHeader);
        if (header == null) {
            return;
        }
        header.setText(browseOnly ? "Cardápio"
                : (mesa == null ? "Pedido" : OccupyActivity.label(mesa)
                + (mesa.contactName != null && !mesa.contactName.isEmpty()
                ? " · " + mesa.contactName : "")));
    }

    private void showTrocarNomeDialog() {
        if (browseOnly || mesaId == 0) {
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trocar_nome, null);
        TextInputEditText input = dialogView.findViewById(R.id.inputName);
        String current = mesa != null && mesa.contactName != null ? mesa.contactName : "";
        if (current.isEmpty()) {
            current = "Cliente";
        }
        input.setText(current);
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Trocar nome do cliente")
                .setView(dialogView)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Informe o nome do cliente", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    salvarNomeCliente(name);
                })
                .show();
    }

    private void salvarNomeCliente(String name) {
        findViewById(R.id.btnTrocarNome).setEnabled(false);
        new Thread(() -> {
            try {
                String saved = repo.api().trocarNomeContato(mesaId, name);
                if (mesa != null) {
                    mesa.contactName = saved;
                    mesa.status = "ocupada";
                    repo.dao().upsertMesa(mesa);
                } else {
                    mesa = repo.dao().mesa(mesaId);
                    if (mesa != null) {
                        mesa.contactName = saved;
                        mesa.status = "ocupada";
                        repo.dao().upsertMesa(mesa);
                    }
                }
                runOnUiThread(() -> {
                    refreshOrderHeader();
                    Toast.makeText(this, "Nome atualizado", Toast.LENGTH_SHORT).show();
                    findViewById(R.id.btnTrocarNome).setEnabled(true);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    Toast.makeText(this, PosApi.friendlyError(ex), Toast.LENGTH_LONG).show();
                    findViewById(R.id.btnTrocarNome).setEnabled(true);
                });
            }
        }).start();
    }

    private void refreshCart() {
        boolean hasItems = !browseOnly && !cart.isEmpty();
        View btnSend = findViewById(R.id.btnSend);
        if (btnSend != null) {
            btnSend.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            btnSend.setEnabled(online && hasItems);
        }
        TextView badge = findViewById(R.id.txtSacolaCount);
        if (badge != null) {
            if (hasItems) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(String.valueOf(cart.size()));
            } else {
                badge.setVisibility(View.GONE);
            }
        }
    }

    private void openReview() {
        if (browseOnly) {
            return;
        }
        if (cart.isEmpty()) {
            Toast.makeText(this, "Adicione itens", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView header = findViewById(R.id.txtOrderHeader);
        Intent i = new Intent(this, RevisaoPedidoActivity.class);
        i.putExtra(RevisaoPedidoActivity.EXTRA_MESA_ID, mesaId);
        i.putExtra(RevisaoPedidoActivity.EXTRA_TABLE_NUMBER, currentTableNumber());
        i.putExtra(RevisaoPedidoActivity.EXTRA_CUSTOMER,
                mesa == null || mesa.contactName == null || mesa.contactName.isEmpty()
                        ? "Cliente" : mesa.contactName);
        i.putExtra(RevisaoPedidoActivity.EXTRA_HEADER,
                header == null ? "" : String.valueOf(header.getText()));
        i.putExtra(RevisaoPedidoActivity.EXTRA_CART_JSON, gson.toJson(cart));
        reviewLauncher.launch(i);
    }

    private void showConta() {
        if (!online) {
            Toast.makeText(this, "Sem conexão com o servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        String tableNumber = currentTableNumber();
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            Toast.makeText(this, "Mesa sem número", Toast.LENGTH_SHORT).show();
            return;
        }
        findViewById(R.id.btnConta).setEnabled(false);
        new Thread(() -> {
            try {
                CatalogModels.ContaResumo resumo = repo.api().conta(tableNumber);
                runOnUiThread(() -> {
                    findViewById(R.id.btnConta).setEnabled(true);
                    showContaDialog(resumo);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    findViewById(R.id.btnConta).setEnabled(true);
                    Toast.makeText(this, PosApi.friendlyError(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showContaDialog(CatalogModels.ContaResumo resumo) {
        if (resumo == null || !resumo.open) {
            new AlertDialog.Builder(this)
                    .setTitle("Conta da mesa")
                    .setMessage("Conta ainda não aberta no Uniplus.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        View body = getLayoutInflater().inflate(R.layout.dialog_conta, null, false);
        TextView cliente = body.findViewById(R.id.txtContaCliente);
        if (resumo.cliente == null || resumo.cliente.isEmpty()) {
            cliente.setVisibility(View.GONE);
        } else {
            cliente.setText(resumo.cliente);
        }
        List<CatalogModels.ContaItem> itens = resumo.itens == null
                ? new ArrayList<>() : resumo.itens;
        RecyclerView list = body.findViewById(R.id.listConta);
        list.setLayoutManager(new LinearLayoutManager(this));
        ContaAdapter adapter = new ContaAdapter(itens);
        list.setAdapter(adapter);
        com.google.android.material.button.MaterialButton verMais = body.findViewById(R.id.btnContaVerMais);
        verMais.setOnClickListener(v -> {
            adapter.toggleDetalhes();
            verMais.setText(adapter.detalhesVisiveis() ? "Ver menos" : "Ver mais");
        });
        ((TextView) body.findViewById(R.id.txtContaTotal)).setText(money(resumo.valortotal));
        new AlertDialog.Builder(this)
                .setTitle("Conta da mesa " + resumo.numeromesa)
                .setView(body)
                .setPositiveButton("OK", null)
                .show();
    }

    private int pendingQuantity(int productId) {
        Integer q = pendingQty.get(productId);
        if (q == null || q < 1) return 1;
        return Math.min(q, 99);
    }

    private void rememberQuantity(int productId, int qty) {
        pendingQty.put(productId, Math.max(1, Math.min(99, qty)));
    }

    private void resetPendingQuantity(int productId) {
        pendingQty.put(productId, 1);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void addProduct(CatalogModels.ProductDto p, int quantity) {
        if (browseOnly) {
            Toast.makeText(this, "Abra uma mesa para lançar o pedido", Toast.LENGTH_SHORT).show();
            return;
        }
        int qty = Math.max(1, quantity);
        if (p.variations != null && !p.variations.isEmpty()
                && p.variations.get(0).options != null
                && !p.variations.get(0).options.isEmpty()) {
            pickOption(p, false, qty);
            return;
        }
        if (p.variablePrice) {
            askVariablePrice(p, null, qty);
            return;
        }
        maybeAddonsThenCart(p, null, p.value, qty);
    }

    private void pickOption(CatalogModels.ProductDto p, boolean half, int quantity) {
        CatalogModels.VariationDto v = p.variations.get(0);
        String[] labels = new String[v.options.size()];
        for (int i = 0; i < v.options.size(); i++) {
            labels[i] = v.options.get(i).label + "  R$ "
                    + String.format(Locale.US, "%.2f", v.options.get(i).value);
        }
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(labels, (d, which) -> {
                    CatalogModels.OptionDto opt = v.options.get(which);
                    if (half) {
                        startHalf(p, opt, quantity);
                    } else if (p.variablePrice) {
                        askVariablePrice(p, opt, quantity);
                    } else {
                        maybeAddonsThenCart(p, opt, opt.value, quantity);
                    }
                })
                .show();
    }

    private void askVariablePrice(CatalogModels.ProductDto p, CatalogModels.OptionDto opt, int quantity) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Valor unitário");
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        double val = Double.parseDouble(String.valueOf(input.getText()).replace(",", "."));
                        maybeAddonsThenCart(p, opt, val, quantity);
                    } catch (Exception e) {
                        Toast.makeText(this, "Valor inválido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void maybeAddonsThenCart(CatalogModels.ProductDto p, CatalogModels.OptionDto opt, double unit, int quantity) {
        CatalogModels.AddOnGroupDto group = resolveAddOnGroup(p);
        if (group == null) {
            pushLine(p, opt, unit, new ArrayList<>(), quantity);
            return;
        }
        pickAddons(group, selected -> pushLine(p, opt, unit, selected, quantity));
    }

    private CatalogModels.AddOnGroupDto resolveAddOnGroup(CatalogModels.ProductDto p) {
        if (p == null) {
            return null;
        }
        if (p.addOnGroupId != null && p.addOnGroupId > 0) {
            CatalogModels.AddOnGroupDto group = repo.group(p.addOnGroupId);
            if (group != null) {
                return group;
            }
        }
        if (p.grupo == null || p.grupo.isEmpty()) {
            return null;
        }
        for (CatalogModels.ProductDto other : allProducts) {
            if (other == null || other.addOnGroupId == null || other.addOnGroupId <= 0) {
                continue;
            }
            if (p.grupo.equals(other.grupo)) {
                CatalogModels.AddOnGroupDto group = repo.group(other.addOnGroupId);
                if (group != null) {
                    return group;
                }
            }
        }
        return null;
    }

    private interface AddonCb {
        void ok(List<CatalogModels.CartAddon> addons);
    }

    private void pickAddons(CatalogModels.AddOnGroupDto group, AddonCb cb) {
        List<AddonSection> sections = buildAddonSections(group);
        if (sections.isEmpty()) {
            cb.ok(new ArrayList<>());
            return;
        }

        View body = getLayoutInflater().inflate(R.layout.dialog_addons, null, false);
        TextView title = body.findViewById(R.id.txtAddonDialogTitle);
        title.setText(group.name != null && !group.name.isEmpty() ? group.name : "Adicionais");
        LinearLayout host = body.findViewById(R.id.listAddonSections);
        List<CheckBox> checks = new ArrayList<>();
        List<CatalogModels.AddOnItemDto> boundItems = new ArrayList<>();

        LayoutInflater inflater = getLayoutInflater();
        for (AddonSection section : sections) {
            View card = inflater.inflate(R.layout.item_addon_section, host, false);
            TextView sectionTitle = card.findViewById(R.id.txtAddonSection);
            if (sections.size() == 1 && section.name.equals(title.getText().toString())) {
                sectionTitle.setVisibility(View.GONE);
            } else {
                sectionTitle.setText(section.name);
            }
            LinearLayout itemsHost = card.findViewById(R.id.listAddonItems);
            for (CatalogModels.AddOnItemDto item : section.items) {
                View row = inflater.inflate(R.layout.item_addon, itemsHost, false);
                CheckBox chk = row.findViewById(R.id.chkAddon);
                TextView price = row.findViewById(R.id.txtAddonPrice);
                chk.setText(item.label);
                price.setText("R$ " + String.format(Locale.US, "%.2f", item.value));
                chk.setClickable(false);
                chk.setFocusable(false);
                row.setOnClickListener(v -> chk.toggle());
                checks.add(chk);
                boundItems.add(item);
                itemsHost.addView(row);
            }
            host.addView(card);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(body)
                .setPositiveButton("OK", (d, w) -> {
                    List<CatalogModels.CartAddon> selected = new ArrayList<>();
                    for (int i = 0; i < checks.size(); i++) {
                        if (!checks.get(i).isChecked()) continue;
                        CatalogModels.AddOnItemDto it = boundItems.get(i);
                        CatalogModels.CartAddon a = new CatalogModels.CartAddon();
                        a.addOnItemId = it.id;
                        a.label = it.label;
                        a.value = it.value;
                        a.idUniplus = it.idUniplus;
                        selected.add(a);
                    }
                    cb.ok(selected);
                });
        if (group.required) {
            builder.setNegativeButton("Cancelar", null);
        } else {
            builder.setNegativeButton("Sem adicionais", (d, w) -> cb.ok(new ArrayList<>()));
        }
        builder.show();
    }

    private static class AddonSection {
        final String name;
        final List<CatalogModels.AddOnItemDto> items;

        AddonSection(String name, List<CatalogModels.AddOnItemDto> items) {
            this.name = name;
            this.items = items;
        }
    }

    private List<AddonSection> buildAddonSections(CatalogModels.AddOnGroupDto group) {
        List<AddonSection> sections = new ArrayList<>();
        if (group.items != null && !group.items.isEmpty()) {
            String title = (group.subgroups != null && !group.subgroups.isEmpty())
                    ? "Adicionais"
                    : (group.name != null && !group.name.isEmpty() ? group.name : "Adicionais");
            sections.add(new AddonSection(title, group.items));
        }
        if (group.subgroups != null) {
            for (CatalogModels.AddOnSubgroupDto sg : group.subgroups) {
                if (sg.items == null || sg.items.isEmpty()) continue;
                String name = sg.name != null && !sg.name.isEmpty() ? sg.name : "Adicionais";
                sections.add(new AddonSection(name, sg.items));
            }
        }
        return sections;
    }

    private void pushLine(CatalogModels.ProductDto p, CatalogModels.OptionDto opt, double unit,
                          List<CatalogModels.CartAddon> addons, int quantity) {
        CatalogModels.CartLine line = new CatalogModels.CartLine();
        line.productId = p.id;
        line.productName = p.name + (opt != null ? " - " + opt.label : "");
        line.productValue = unit;
        line.quantity = Math.max(1, quantity);
        line.grupo = p.grupo;
        line.idUniplus = opt != null && opt.idUniplus != null ? opt.idUniplus : p.idUniplus;
        if (opt != null) {
            line.optionId = opt.id;
            line.optionLabel = opt.label;
        }
        line.addons = addons;
        cart.add(line);
        refreshCart();
        resetPendingQuantity(p.id);
        CartObservation.show(this, line, true, this::refreshCart);
    }

    private CatalogModels.ProductDto findProduct(int id) {
        for (CatalogModels.ProductDto p : allProducts) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    private static CatalogModels.OptionDto matchingOption(CatalogModels.ProductDto p,
                                                          CatalogModels.OptionDto baseOpt) {
        if (p == null || p.variations == null || p.variations.isEmpty()
                || p.variations.get(0).options == null
                || p.variations.get(0).options.isEmpty()) {
            return null;
        }
        List<CatalogModels.OptionDto> opts = p.variations.get(0).options;
        if (baseOpt != null && baseOpt.label != null) {
            for (CatalogModels.OptionDto o : opts) {
                if (baseOpt.label.equalsIgnoreCase(o.label)) {
                    return o;
                }
            }
        }
        return opts.get(0);
    }

    private static double flavorValue(CatalogModels.ProductDto p, CatalogModels.OptionDto baseOpt) {
        CatalogModels.OptionDto opt = matchingOption(p, baseOpt);
        return opt != null ? opt.value : (p == null ? 0 : p.value);
    }

    private static String money(double value) {
        return "R$ " + String.format(Locale.US, "%.2f", value);
    }

    private void startHalf(CatalogModels.ProductDto base, CatalogModels.OptionDto baseOpt, int quantity) {
        List<CatalogModels.ProductDto> candidates = new ArrayList<>();
        String wantGrupo = base.halfAndHalfGrupo;
        for (CatalogModels.ProductDto p : allProducts) {
            if (p.id == base.id) continue;
            if (!p.allowsHalfAndHalf) continue;
            if (wantGrupo != null && !wantGrupo.isEmpty() && !wantGrupo.equals(p.grupo)) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "Sem segundo sabor", Toast.LENGTH_SHORT).show();
            return;
        }
        View body = getLayoutInflater().inflate(R.layout.dialog_half_flavors, null, false);
        TextView hint = body.findViewById(R.id.txtHalfHint);
        double firstValue = baseOpt != null ? baseOpt.value : base.value;
        String size = baseOpt != null && baseOpt.label != null ? baseOpt.label : "";
        hint.setText("1º sabor: " + base.name + "  " + money(firstValue)
                + (size.isEmpty() ? "" : " · " + size));
        RecyclerView list = body.findViewById(R.id.listHalfFlavors);
        list.setLayoutManager(new LinearLayoutManager(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Segundo sabor")
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .create();
        list.setAdapter(new HalfFlavorAdapter(candidates, baseOpt, other -> {
            dialog.dismiss();
            finishHalf(base, baseOpt, other, quantity);
        }));
        dialog.show();
    }

    private void finishHalf(CatalogModels.ProductDto base, CatalogModels.OptionDto baseOpt,
                            CatalogModels.ProductDto other, int quantity) {
        CatalogModels.OptionDto otherOpt = matchingOption(other, baseOpt);
        double v1 = baseOpt != null ? baseOpt.value : base.value;
        double v2 = otherOpt != null ? otherOpt.value : other.value;
        String rule = base.halfAndHalfPriceRule == null ? "max" : base.halfAndHalfPriceRule;
        double unit;
        if ("average".equals(rule)) unit = (v1 + v2) / 2.0;
        else if ("fixed".equals(rule)) unit = v1;
        else unit = Math.max(v1, v2);

        CatalogModels.CartLine line = new CatalogModels.CartLine();
        line.type = "halfAndHalf";
        line.productId = base.id;
        line.productName = "Meio a meio: " + base.name + " / " + other.name;
        line.productValue = unit;
        line.quantity = Math.max(1, quantity);
        line.grupo = base.grupo;
        line.half1ProductId = base.id;
        line.half2ProductId = other.id;
        line.half1Name = base.name;
        line.half2Name = other.name;
        if (baseOpt != null) {
            line.half1OptionId = baseOpt.id;
            line.baseOptionId = baseOpt.id;
            line.optionLabel = baseOpt.label;
            line.idUniplus = baseOpt.idUniplus != null ? baseOpt.idUniplus : base.idUniplus;
        } else {
            line.idUniplus = base.idUniplus;
        }
        if (otherOpt != null) {
            line.half2OptionId = otherOpt.id;
        }
        CatalogModels.AddOnGroupDto group = resolveAddOnGroup(base);
        if (group == null) {
            group = resolveAddOnGroup(other);
        }
        if (group == null) {
            addHalfToCart(line, new ArrayList<>());
            return;
        }
        pickAddons(group, selected -> addHalfToCart(line, selected));
    }

    private void addHalfToCart(CatalogModels.CartLine line, List<CatalogModels.CartAddon> addons) {
        line.addons = addons == null ? new ArrayList<>() : addons;
        cart.add(line);
        refreshCart();
        resetPendingQuantity(line.productId);
        CartObservation.show(this, line, true, this::refreshCart);
    }

    class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CatalogModels.ProductDto p = shown.get(position);
            holder.name.setText(p.name);
            holder.price.setText("R$ " + String.format(Locale.US, "%.2f", p.value));
            ImageStore.bind(holder.image, holder.itemView.getContext(), p.imageId);
            int qty = pendingQuantity(p.id);
            holder.txtQty.setText(String.valueOf(qty));
            holder.boxQty.setVisibility(browseOnly ? View.GONE : View.VISIBLE);
            holder.btnQtyMinus.setEnabled(qty > 1);
            holder.btnQtyPlus.setOnClickListener(v -> {
                int next = Math.min(99, pendingQuantity(p.id) + 1);
                rememberQuantity(p.id, next);
                holder.txtQty.setText(String.valueOf(next));
                holder.btnQtyMinus.setEnabled(true);
            });
            holder.btnQtyMinus.setOnClickListener(v -> {
                int next = Math.max(1, pendingQuantity(p.id) - 1);
                rememberQuantity(p.id, next);
                holder.txtQty.setText(String.valueOf(next));
                holder.btnQtyMinus.setEnabled(next > 1);
            });
            holder.btnAdd.setOnClickListener(v -> addProduct(p, pendingQuantity(p.id)));
            holder.btnHalf.setVisibility(browseOnly || !p.allowsHalfAndHalf ? View.GONE : View.VISIBLE);
            holder.btnAdd.setVisibility(browseOnly ? View.GONE : View.VISIBLE);
            holder.btnHalf.setOnClickListener(v -> {
                if (browseOnly) {
                    return;
                }
                int halfQty = pendingQuantity(p.id);
                if (p.variations != null && !p.variations.isEmpty()
                        && p.variations.get(0).options != null
                        && !p.variations.get(0).options.isEmpty()) {
                    pickOption(p, true, halfQty);
                } else {
                    startHalf(p, null, halfQty);
                }
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView name, price, txtQty;
            View btnAdd, btnHalf, boxQty, btnQtyPlus, btnQtyMinus;
            VH(View v) {
                super(v);
                image = v.findViewById(R.id.imgProduct);
                name = v.findViewById(R.id.txtName);
                price = v.findViewById(R.id.txtPrice);
                btnAdd = v.findViewById(R.id.btnAdd);
                btnHalf = v.findViewById(R.id.btnHalf);
                boxQty = v.findViewById(R.id.boxQty);
                txtQty = v.findViewById(R.id.txtQty);
                btnQtyPlus = v.findViewById(R.id.btnQtyPlus);
                btnQtyMinus = v.findViewById(R.id.btnQtyMinus);
            }
        }
    }

    class HalfFlavorAdapter extends RecyclerView.Adapter<HalfFlavorAdapter.VH> {
        private final List<CatalogModels.ProductDto> items;
        private final CatalogModels.OptionDto baseOpt;
        private final HalfPick cb;

        HalfFlavorAdapter(List<CatalogModels.ProductDto> items, CatalogModels.OptionDto baseOpt, HalfPick cb) {
            this.items = items;
            this.baseOpt = baseOpt;
            this.cb = cb;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_half_flavor, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CatalogModels.ProductDto p = items.get(position);
            CatalogModels.OptionDto opt = matchingOption(p, baseOpt);
            holder.name.setText(p.name);
            String price = money(opt != null ? opt.value : p.value);
            if (opt != null && opt.label != null && !opt.label.isEmpty()) {
                price += "  ·  " + opt.label;
            }
            holder.price.setText(price);
            ImageStore.bind(holder.image, holder.itemView.getContext(), p.imageId);
            holder.itemView.setOnClickListener(v -> cb.ok(p));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView name, price;
            VH(View v) {
                super(v);
                image = v.findViewById(R.id.imgHalf);
                name = v.findViewById(R.id.txtHalfName);
                price = v.findViewById(R.id.txtHalfPrice);
            }
        }
    }

    class ContaAdapter extends RecyclerView.Adapter<ContaAdapter.VH> {
        private final List<CatalogModels.ContaItem> itens;
        private boolean detalhes;

        ContaAdapter(List<CatalogModels.ContaItem> itens) {
            this.itens = itens;
        }

        void toggleDetalhes() {
            detalhes = !detalhes;
            notifyDataSetChanged();
        }

        boolean detalhesVisiveis() {
            return detalhes;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conta, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CatalogModels.ContaItem it = itens.get(position);
            double qtd = it.quantidade;
            holder.qtd.setText(qtd == (long) qtd
                    ? String.valueOf((long) qtd)
                    : String.format(Locale.US, "%.2f", qtd));
            holder.produto.setText(contaNome(it.nomeproduto));
            holder.valor.setText(money(it.valortotal));
            String extra = contaDetalhe(it);
            if (detalhes && !extra.isEmpty()) {
                holder.detalhe.setVisibility(View.VISIBLE);
                holder.detalhe.setText(extra);
            } else {
                holder.detalhe.setVisibility(View.GONE);
            }
            holder.repeat.setOnClickListener(v -> repeatContaItem(it));
        }

        @Override
        public int getItemCount() {
            return itens.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView qtd, produto, valor, detalhe;
            ImageButton repeat;
            VH(View v) {
                super(v);
                qtd = v.findViewById(R.id.txtContaQtd);
                produto = v.findViewById(R.id.txtContaProduto);
                valor = v.findViewById(R.id.txtContaValor);
                detalhe = v.findViewById(R.id.txtContaDetalhe);
                repeat = v.findViewById(R.id.btnContaRepeat);
            }
        }
    }

    private static String contaDetalhe(CatalogModels.ContaItem it) {
        if (it == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (it.hora != null && !it.hora.trim().isEmpty()) {
            sb.append("Pedido às ").append(it.hora.trim());
        }
        if (it.precounitario > 0) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Unitário ").append(money(it.precounitario));
        }
        if (it.observacao != null && !it.observacao.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Obs: ").append(it.observacao.trim());
        }
        return sb.toString();
    }

    private static String contaNome(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("meio a meio:")) {
            return "1/2 " + name.substring(12).trim();
        }
        return name;
    }

    private void repeatContaItem(CatalogModels.ContaItem it) {
        if (browseOnly) {
            Toast.makeText(this, "Abra uma mesa para lançar o pedido", Toast.LENGTH_SHORT).show();
            return;
        }
        CatalogModels.CartLine line = cartLineFromConta(it);
        if (line == null) {
            Toast.makeText(this, "Não foi possível repetir este item", Toast.LENGTH_SHORT).show();
            return;
        }
        cart.add(line);
        refreshCart();
        Toast.makeText(this, "Adicionado à sacola: " + line.productName, Toast.LENGTH_SHORT).show();
    }

    private CatalogModels.CartLine cartLineFromConta(CatalogModels.ContaItem it) {
        if (it == null) {
            return null;
        }
        String codigo = it.codigoproduto == null ? "" : it.codigoproduto.trim();
        CatalogModels.ProductDto product = null;
        CatalogModels.OptionDto option = null;
        CatalogModels.AddOnItemDto addon = null;
        if (!codigo.isEmpty()) {
            product = findProductByCodigo(codigo);
            if (product != null) {
                option = findOptionByCodigo(product, codigo);
            } else {
                addon = findAddonByCodigo(codigo);
            }
        }
        if (product == null && addon == null) {
            product = findProductByContaName(it.nomeproduto);
            if (product != null) {
                option = matchingOptionByContaName(product, it.nomeproduto);
            }
        }

        CatalogModels.CartLine line = new CatalogModels.CartLine();
        line.quantity = 1;
        if (it.observacao != null) {
            line.observation = it.observacao.trim();
        }
        if (addon != null) {
            line.productName = addon.label;
            line.productValue = addon.value;
            line.idUniplus = addon.idUniplus;
            return line;
        }
        if (product != null) {
            line.productId = product.id;
            line.grupo = product.grupo;
            if (option != null) {
                line.optionId = option.id;
                line.optionLabel = option.label;
                line.productName = product.name + " - " + option.label;
                line.productValue = option.value;
                line.idUniplus = option.idUniplus != null ? option.idUniplus : product.idUniplus;
            } else {
                line.productName = product.name;
                line.productValue = product.value;
                line.idUniplus = product.idUniplus;
            }
            if (it.precounitario > 0 && product.variablePrice) {
                line.productValue = it.precounitario;
            }
            return line;
        }
        String nome = contaNome(it.nomeproduto);
        if (nome.isEmpty()) {
            return null;
        }
        line.productName = nome;
        if (it.precounitario > 0) {
            line.productValue = it.precounitario;
        } else if (it.quantidade > 0) {
            line.productValue = it.valortotal / it.quantidade;
        } else {
            line.productValue = it.valortotal;
        }
        line.idUniplus = codigo.isEmpty() ? null : codigo;
        return line;
    }

    private CatalogModels.ProductDto findProductByCodigo(String codigo) {
        for (CatalogModels.ProductDto p : allProducts) {
            if (p == null) continue;
            if (codigo.equals(p.idUniplus)) {
                return p;
            }
            if (p.variations == null) continue;
            for (CatalogModels.VariationDto v : p.variations) {
                if (v == null || v.options == null) continue;
                for (CatalogModels.OptionDto o : v.options) {
                    if (o != null && codigo.equals(o.idUniplus)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static CatalogModels.OptionDto findOptionByCodigo(CatalogModels.ProductDto p, String codigo) {
        if (p == null || p.variations == null || codigo.equals(p.idUniplus)) {
            return null;
        }
        for (CatalogModels.VariationDto v : p.variations) {
            if (v == null || v.options == null) continue;
            for (CatalogModels.OptionDto o : v.options) {
                if (o != null && codigo.equals(o.idUniplus)) {
                    return o;
                }
            }
        }
        return null;
    }

    private CatalogModels.AddOnItemDto findAddonByCodigo(String codigo) {
        for (CatalogModels.AddOnGroupDto g : repo.groups()) {
            if (g == null) continue;
            CatalogModels.AddOnItemDto found = addonWithCodigo(g.items, codigo);
            if (found != null) return found;
            if (g.subgroups == null) continue;
            for (CatalogModels.AddOnSubgroupDto sg : g.subgroups) {
                if (sg == null) continue;
                found = addonWithCodigo(sg.items, codigo);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static CatalogModels.AddOnItemDto addonWithCodigo(List<CatalogModels.AddOnItemDto> items, String codigo) {
        if (items == null) return null;
        for (CatalogModels.AddOnItemDto it : items) {
            if (it != null && codigo.equals(it.idUniplus)) {
                return it;
            }
        }
        return null;
    }

    private CatalogModels.ProductDto findProductByContaName(String raw) {
        String want = normalizeContaName(raw);
        if (want.isEmpty()) return null;
        CatalogModels.ProductDto exact = null;
        CatalogModels.ProductDto prefix = null;
        for (CatalogModels.ProductDto p : allProducts) {
            if (p == null || p.name == null) continue;
            String name = p.name.trim().toLowerCase(Locale.ROOT);
            if (name.equals(want)) {
                exact = p;
                break;
            }
            if (want.startsWith(name + " - ") || want.startsWith(name + " – ")) {
                prefix = p;
            }
        }
        return exact != null ? exact : prefix;
    }

    private static CatalogModels.OptionDto matchingOptionByContaName(CatalogModels.ProductDto p, String raw) {
        String want = normalizeContaName(raw);
        if (p == null || p.variations == null || want.isEmpty()) return null;
        for (CatalogModels.VariationDto v : p.variations) {
            if (v == null || v.options == null) continue;
            for (CatalogModels.OptionDto o : v.options) {
                if (o == null || o.label == null) continue;
                String labeled = (p.name + " - " + o.label).trim().toLowerCase(Locale.ROOT);
                if (want.equals(labeled) || want.endsWith(" - " + o.label.toLowerCase(Locale.ROOT))) {
                    return o;
                }
            }
        }
        return null;
    }

    private static String normalizeContaName(String raw) {
        String name = contaNome(raw);
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private interface HalfPick {
        void ok(CatalogModels.ProductDto other);
    }
}
