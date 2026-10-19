package com.compumais.poscompuchat.data;

import android.content.Context;

import com.compumais.poscompuchat.Prefs;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PosApi {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final OkHttpClient pingClient;
    private final Gson gson = new Gson();
    private final Context ctx;

    public PosApi(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.pingClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .build();
    }

    public void ping() throws IOException {
        ping(Prefs.agentUrl(ctx), Prefs.token(ctx));
    }

    public void ping(String baseUrl, String token) throws IOException {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IOException("Servidor não configurado");
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        if (token != null && !token.trim().isEmpty()) {
            Request.Builder b = new Request.Builder().url(base + "/pos/health").get();
            b.header("X-Pos-Token", token.trim());
            try (Response resp = pingClient.newCall(b.build()).execute()) {
                int code = resp.code();
                if (code == 401 || code == 403) {
                    throw new IOException("HTTP " + code);
                }
                if (code <= 0 || code >= 500) {
                    throw new IOException("HTTP " + code);
                }
            }
            return;
        }
        try (Response resp = pingClient.newCall(
                new Request.Builder().url(base + "/health").get().build()).execute()) {
            if (resp.code() <= 0 || resp.code() >= 500) {
                throw new IOException("HTTP " + resp.code());
            }
        }
    }

    public static String friendlyError(Throwable error) {
        if (error == null) {
            return "Sem conexão com o servidor";
        }
        String raw = error.getMessage() == null ? "" : error.getMessage();
        String lower = raw.toLowerCase();
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException) {
                return "Servidor não respondeu. Confira se o Agent está ligado.";
            }
            if (cause instanceof java.net.ConnectException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof java.net.NoRouteToHostException) {
                return "Sem conexão com o servidor. Verifique o IP e a rede.";
            }
            cause = cause.getCause();
        }
        if (lower.contains("failed to connect")
                || lower.contains("unable to resolve")
                || lower.contains("econnrefused")
                || lower.contains("network is unreachable")
                || lower.contains("software caused connection abort")) {
            return "Sem conexão com o servidor. Verifique o IP e a rede.";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "Servidor não respondeu. Confira se o Agent está ligado.";
        }
        if (raw.contains("HTTP 401") || raw.contains("HTTP 403") || lower.contains("unauthorized")) {
            return "Token do POS inválido. Ajuste nas configurações.";
        }
        if (raw.contains("Servidor não configurado")) {
            return raw;
        }
        return "Falha ao falar com o servidor" + (raw.isEmpty() ? "." : ": " + raw);
    }

    private Request.Builder req(String path) {
        String base = Prefs.agentUrl(ctx).replaceAll("/+$", "");
        Request.Builder b = new Request.Builder().url(base + path);
        String token = Prefs.token(ctx);
        if (token != null && !token.isEmpty()) {
            b.header("X-Pos-Token", token);
        }
        return b;
    }

    private String body(Request request) throws IOException {
        try (Response resp = client.newCall(request).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " " + raw);
            }
            return raw;
        }
    }

    public CatalogModels.Catalog sync(boolean refresh) throws IOException {
        String path = "/pos/sync" + (refresh ? "?refresh=1" : "");
        String raw = body(req(path).get().build());
        return gson.fromJson(raw, CatalogModels.Catalog.class);
    }

    public void login(int userId, String pin) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("userId", userId);
        o.addProperty("pin", pin == null ? "" : pin);
        body(req("/pos/login").post(RequestBody.create(o.toString(), JSON)).build());
    }

    public List<CatalogModels.MesaDto> mesas() throws IOException {
        String raw = body(req("/pos/mesas").get().build());
        JsonObject o = gson.fromJson(raw, JsonObject.class);
        CatalogModels.Catalog c = new CatalogModels.Catalog();
        if (o.has("mesas")) {
            CatalogModels.MesaDto[] arr = gson.fromJson(o.get("mesas"), CatalogModels.MesaDto[].class);
            if (arr != null) {
                for (CatalogModels.MesaDto m : arr) c.mesas.add(m);
            }
        }
        return c.mesas;
    }

    public void ocupar(int mesaId, String customerName, int userId) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("customerName", customerName);
        o.addProperty("userId", userId);
        body(req("/pos/mesas/" + mesaId + "/ocupar").post(RequestBody.create(o.toString(), JSON)).build());
    }

    public String trocarNomeContato(int mesaId, String customerName) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("customerName", customerName == null ? "" : customerName);
        String raw = body(req("/pos/mesas/" + mesaId + "/contato-nome")
                .put(RequestBody.create(o.toString(), JSON)).build());
        JsonObject resp = gson.fromJson(raw, JsonObject.class);
        if (resp != null && resp.has("contactName") && !resp.get("contactName").isJsonNull()) {
            return resp.get("contactName").getAsString();
        }
        return customerName;
    }

    public CatalogModels.OrderResult sendOrder(int mesaId, String tableNumber, String customerName,
                            String garcomName, List<CatalogModels.CartLine> lines, String clientOrderId) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("clientOrderId", clientOrderId == null || clientOrderId.isEmpty()
                ? java.util.UUID.randomUUID().toString() : clientOrderId);
        o.addProperty("tableId", mesaId);
        o.addProperty("tableNumber", tableNumber);
        o.addProperty("customerName", customerName);
        o.addProperty("garcomName", garcomName);
        o.addProperty("orderType", "mesa");
        JsonArray items = new JsonArray();
        for (CatalogModels.CartLine line : lines) {
            JsonObject tree = gson.toJsonTree(line).getAsJsonObject();
            tree.addProperty("addonsTotal", line.addonsTotal());
            items.add(tree);
        }
        o.add("menuItems", items);
        String raw = body(req("/pos/orders").post(RequestBody.create(o.toString(), JSON)).build());
        CatalogModels.OrderResult result = gson.fromJson(raw, CatalogModels.OrderResult.class);
        return result == null ? new CatalogModels.OrderResult() : result;
    }

    public CatalogModels.ContaResumo conta(String tableNumber) throws IOException {
        String raw = body(req("/pos/conta?numeromesa=" + java.net.URLEncoder.encode(tableNumber, "UTF-8")).get().build());
        CatalogModels.ContaResumo resumo = gson.fromJson(raw, CatalogModels.ContaResumo.class);
        return resumo == null ? new CatalogModels.ContaResumo() : resumo;
    }

    public CatalogModels.PedidoDia pedidos() throws IOException {
        String raw = body(req("/pos/pedidos").get().build());
        CatalogModels.PedidoDia dia = gson.fromJson(raw, CatalogModels.PedidoDia.class);
        if (dia == null) {
            dia = new CatalogModels.PedidoDia();
        }
        if (dia.itens == null) {
            dia.itens = new java.util.ArrayList<>();
        }
        if (dia.printers == null) {
            dia.printers = new java.util.ArrayList<>();
        }
        return dia;
    }

    public void marcarEntregue(int itemId, boolean entregue) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("entregue", entregue);
        body(req("/pos/pedidos/" + itemId + "/entregue")
                .post(RequestBody.create(o.toString(), JSON)).build());
    }

    public CatalogModels.CancelFilaResult cancelarFila(String deviceId) throws IOException {
        JsonObject o = new JsonObject();
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            o.addProperty("deviceId", deviceId.trim());
        }
        String raw = body(req("/pos/printers/cancel-queue")
                .post(RequestBody.create(o.toString(), JSON)).build());
        CatalogModels.CancelFilaResult result = gson.fromJson(raw, CatalogModels.CancelFilaResult.class);
        return result == null ? new CatalogModels.CancelFilaResult() : result;
    }

    public void downloadMedia(String imageId, File dest) throws IOException {
        Request request = req("/pos/media/" + imageId).get().build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("HTTP " + resp.code());
            }
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(dest.getAbsolutePath() + ".tmp");
            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            if (dest.exists()) {
                dest.delete();
            }
            if (!tmp.renameTo(dest)) {
                throw new IOException("rename_failed");
            }
        }
    }
}
