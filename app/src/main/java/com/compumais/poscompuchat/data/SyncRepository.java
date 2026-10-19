package com.compumais.poscompuchat.data;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SyncRepository {
    private final Context ctx;
    private final AppDatabase db;
    private final PosApi api;
    private final Gson gson = new Gson();

    public SyncRepository(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = AppDatabase.get(ctx);
        this.api = new PosApi(ctx);
    }

    public PosApi api() {
        return api;
    }

    public PosDao dao() {
        return db.dao();
    }

    public CatalogModels.Catalog sync(boolean refresh) throws IOException {
        return sync(refresh, null);
    }

    public CatalogModels.Catalog sync(boolean refresh, SyncProgress progress) throws IOException {
        report(progress, 4, "Conectando ao servidor…");
        CatalogModels.Catalog catalog = api.sync(refresh);
        if (catalog == null || catalog.unchanged) {
            report(progress, 100, "Catálogo já está atualizado");
            return catalog;
        }
        int users = catalog.users == null ? 0 : catalog.users.size();
        int mesas = catalog.mesas == null ? 0 : catalog.mesas.size();
        int products = catalog.products == null ? 0 : catalog.products.size();
        report(progress, 22, "Catálogo recebido");
        report(progress, 30, "Salvando " + users + " usuários…");
        persist(catalog);
        report(progress, 42, mesas + " mesas e " + products + " produtos salvos");
        int images = catalog.images == null ? 0 : catalog.images.size();
        if (images == 0) {
            report(progress, 100, "Sincronização concluída");
            return catalog;
        }
        report(progress, 45, "Baixando imagens…");
        ImageStore.sync(ctx, api, catalog.images, (done, total) -> {
            int pct = total <= 0 ? 95 : 45 + (done * 54 / total);
            report(progress, Math.min(99, pct), "Imagens " + done + " de " + total);
        });
        report(progress, 100, "Sincronização concluída");
        return catalog;
    }

    private static void report(SyncProgress progress, int percent, String message) {
        if (progress != null) {
            progress.onProgress(Math.max(0, Math.min(100, percent)), message);
        }
    }

    public void persist(CatalogModels.Catalog catalog) {
        List<UserEntity> users = new ArrayList<>();
        if (catalog.users != null) {
            for (CatalogModels.UserDto u : catalog.users) {
                UserEntity e = new UserEntity();
                e.id = u.id;
                e.name = u.name;
                e.hasPin = u.pin;
                e.profile = u.profile;
                users.add(e);
            }
        }
        List<MesaEntity> mesas = new ArrayList<>();
        if (catalog.mesas != null) {
            for (CatalogModels.MesaDto m : catalog.mesas) {
                mesas.add(toMesa(m));
            }
        }
        java.util.Map<String, Integer> addonByGrupo = new java.util.HashMap<>();
        if (catalog.grupoAddOn != null) {
            for (CatalogModels.GrupoAddOnDto a : catalog.grupoAddOn) {
                if (a != null && a.grupo != null && !a.grupo.isEmpty() && a.addOnGroupId > 0) {
                    addonByGrupo.put(a.grupo, a.addOnGroupId);
                }
            }
        }
        List<ProductEntity> products = new ArrayList<>();
        if (catalog.products != null) {
            for (CatalogModels.ProductDto p : catalog.products) {
                if ((p.addOnGroupId == null || p.addOnGroupId <= 0) && p.grupo != null) {
                    Integer fromGrupo = addonByGrupo.get(p.grupo);
                    if (fromGrupo != null) {
                        p.addOnGroupId = fromGrupo;
                    }
                }
                ProductEntity e = new ProductEntity();
                e.id = p.id;
                e.name = p.name;
                e.grupo = p.grupo == null ? "Outros" : p.grupo;
                e.payloadJson = gson.toJson(p);
                products.add(e);
            }
        }
        List<GroupEntity> groups = new ArrayList<>();
        if (catalog.groups != null) {
            for (CatalogModels.AddOnGroupDto g : catalog.groups) {
                GroupEntity e = new GroupEntity();
                e.id = g.id;
                e.payloadJson = gson.toJson(g);
                groups.add(e);
            }
        }
        db.runInTransaction(() -> {
            db.dao().clearUsers();
            db.dao().clearMesas();
            db.dao().clearProducts();
            db.dao().clearGroups();
            if (!users.isEmpty()) db.dao().insertUsers(users);
            if (!mesas.isEmpty()) db.dao().insertMesas(mesas);
            if (!products.isEmpty()) db.dao().insertProducts(products);
            if (!groups.isEmpty()) db.dao().insertGroups(groups);
            MetaEntity meta = new MetaEntity();
            meta.id = 1;
            meta.catalogVersion = catalog.catalogVersion;
            meta.updatedAt = catalog.updatedAt;
            db.dao().upsertMeta(meta);
        });
    }

    public void upsertMesa(CatalogModels.MesaDto m) {
        db.dao().upsertMesa(toMesa(m));
    }

    public void replaceMesas(List<CatalogModels.MesaDto> remote) {
        List<MesaEntity> mesas = new ArrayList<>();
        if (remote != null) {
            for (CatalogModels.MesaDto m : remote) {
                mesas.add(toMesa(m));
            }
        }
        db.runInTransaction(() -> {
            db.dao().clearMesas();
            if (!mesas.isEmpty()) {
                db.dao().insertMesas(mesas);
            }
        });
    }

    public CatalogModels.ProductDto product(int id) {
        ProductEntity e = db.dao().product(id);
        if (e == null) return null;
        return gson.fromJson(e.payloadJson, CatalogModels.ProductDto.class);
    }

    public CatalogModels.AddOnGroupDto group(int id) {
        for (GroupEntity e : db.dao().groups()) {
            if (e.id == id) {
                return gson.fromJson(e.payloadJson, CatalogModels.AddOnGroupDto.class);
            }
        }
        return null;
    }

    public List<CatalogModels.AddOnGroupDto> groups() {
        List<CatalogModels.AddOnGroupDto> out = new ArrayList<>();
        for (GroupEntity e : db.dao().groups()) {
            CatalogModels.AddOnGroupDto g = gson.fromJson(e.payloadJson, CatalogModels.AddOnGroupDto.class);
            if (g != null) {
                out.add(g);
            }
        }
        return out;
    }

    private static MesaEntity toMesa(CatalogModels.MesaDto m) {
        MesaEntity e = new MesaEntity();
        e.id = m.id;
        e.number = m.number;
        e.name = m.name;
        e.type = m.type;
        e.status = m.status;
        e.contactName = m.contactName;
        e.totalValue = m.totalValue;
        e.displayOrder = m.displayOrder;
        e.section = m.section;
        return e;
    }
}
