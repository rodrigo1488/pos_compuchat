package com.compumais.poscompuchat;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppUpdater implements Application.ActivityLifecycleCallbacks {
    public interface Aviso {
        void mensagem(String texto);
    }
    private static final String TAG = "AppUpdate";
    private static final String PREFS = "utility_update";
    private static final String KEY_SHA = "sha256";
    private static final String KEY_VERSION = "version";
    private static final String MIME_APK = "application/vnd.android.package-archive";
    private static final int CONEXAO_MS = 15_000;
    private static final int LEITURA_MANIFESTO = 20_000;
    private static final int LEITURA_APK = 300_000;

    private static AppUpdater instancia;

    private final Application app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean emAndamento = new AtomicBoolean(false);
    private String hashCanal;
    private Activity resumed;
    private File apkPendente;
    private AlertDialog dialogo;
    private Activity dialogoActivity;
    private boolean aguardandoPermissao;

    private AppUpdater(Application app) {
        this.app = app;
    }

    public static void iniciar(Application app) {
        String hash = AtualizacaoConfig.HASH.trim().toLowerCase(Locale.US);
        if (hash.isEmpty()) return;
        AppUpdater updater = new AppUpdater(app);
        updater.hashCanal = hash;
        instancia = updater;
        app.registerActivityLifecycleCallbacks(updater);
        updater.consultar(false, null);
    }

    public static void verificarManual(Activity activity, Aviso aviso) {
        AppUpdater updater = instancia;
        if (updater == null || updater.hashCanal == null || updater.hashCanal.isEmpty()) {
            aviso.mensagem("Atualização não configurada neste aplicativo.");
            return;
        }
        updater.resumed = activity;
        updater.consultar(true, aviso);
    }

    private void consultar(boolean manual, Aviso aviso) {
        if (!emAndamento.compareAndSet(false, true)) {
            if (manual && aviso != null) {
                mainHandler.post(() -> aviso.mensagem("Verificação já em andamento."));
            }
            return;
        }
        if (manual && aviso != null) {
            mainHandler.post(() -> aviso.mensagem("Verificando versão…"));
        }
        Thread thread = new Thread(() -> {
            try {
                File arquivo = preparar(hashCanal);
                mainHandler.post(() -> {
                    emAndamento.set(false);
                    if (arquivo != null) {
                        apkPendente = arquivo;
                        mostrarSePossivel();
                        if (aviso != null) {
                            aviso.mensagem("Há uma versão nova pronta para instalar.");
                        }
                    } else if (manual && aviso != null) {
                        aviso.mensagem("Este aplicativo já está na versão mais recente.");
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Falha ao consultar atualização", e);
                mainHandler.post(() -> {
                    emAndamento.set(false);
                    if (manual && aviso != null) {
                        aviso.mensagem("Não foi possível consultar a atualização.");
                    }
                });
            }
        }, "app-update");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumed = activity;
        if (aguardandoPermissao) {
            aguardandoPermissao = false;
            if (apkPendente != null && podeInstalar(activity)) {
                abrirInstalador(activity, apkPendente);
                return;
            }
        }
        mostrarSePossivel();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (resumed == activity) resumed = null;
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (resumed == activity) resumed = null;
        if (dialogoActivity == activity && dialogo != null) {
            dialogo.setOnCancelListener(null);
            dialogo.dismiss();
            dialogo = null;
            dialogoActivity = null;
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    private void mostrarSePossivel() {
        if (apkPendente == null || dialogo != null) return;
        Activity activity = resumed;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Nova versão")
                .setMessage("Há uma versão nova deste aplicativo. Deseja instalar agora?")
                .setPositiveButton("Instalar", (d, w) -> instalar(activity))
                .setNegativeButton("Agora não", (d, w) -> adiar())
                .setOnCancelListener(d -> adiar())
                .create();
        dialogo = dialog;
        dialogoActivity = activity;
        dialog.show();
    }

    private void adiar() {
        apkPendente = null;
        dialogo = null;
        dialogoActivity = null;
    }

    private void instalar(Activity activity) {
        File arquivo = apkPendente;
        dialogo = null;
        dialogoActivity = null;
        if (arquivo == null) return;
        if (!podeInstalar(activity)) {
            aguardandoPermissao = true;
            Intent destino = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            try {
                activity.startActivity(destino);
            } catch (Exception e) {
                aguardandoPermissao = false;
                Log.w(TAG, "Não abriu a permissão de instalação", e);
                mostrarSePossivel();
            }
            return;
        }
        abrirInstalador(activity, arquivo);
    }

    private void abrirInstalador(Activity activity, File arquivo) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", arquivo);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MIME_APK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("", uri));
            activity.startActivity(intent);
            apkPendente = null;
        } catch (Exception e) {
            Log.w(TAG, "Não abriu o instalador", e);
            mostrarSePossivel();
        }
    }

    private File preparar(String hash) throws Exception {
        android.content.SharedPreferences prefs = app.getSharedPreferences(PREFS, Application.MODE_PRIVATE);
        int versaoLocal = prefs.getInt(KEY_VERSION, 0);
        String shaGuardado = prefs.getString(KEY_SHA, "");
        if (shaGuardado == null) shaGuardado = "";
        Manifesto manifesto = baixarManifesto(hash, versaoLocal);
        if (manifesto == null || manifesto.sha256.isEmpty()) {
            throw new IOException("Não foi possível consultar a atualização.");
        }
        if (shaGuardado.equalsIgnoreCase(manifesto.sha256)) return null;

        File instalado = new File(app.getApplicationInfo().sourceDir);
        if (sha256(instalado).equalsIgnoreCase(manifesto.sha256)) {
            gravarAplicado(manifesto);
            return null;
        }
        return baixarSeNecessario(manifesto);
    }

    private void gravarAplicado(Manifesto manifesto) {
        app.getSharedPreferences(PREFS, Application.MODE_PRIVATE)
                .edit()
                .putString(KEY_SHA, manifesto.sha256.toLowerCase(Locale.US))
                .putInt(KEY_VERSION, manifesto.version)
                .apply();
    }

    private File baixarSeNecessario(Manifesto manifesto) throws Exception {
        File pasta = new File(app.getFilesDir(), "updates");
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Não foi possível salvar a atualização.");
        }
        File destino = new File(pasta, "update.apk");
        if (destino.isFile() && sha256(destino).equalsIgnoreCase(manifesto.sha256)) return destino;

        File parcial = new File(pasta, "update.apk.part");
        try {
            baixarArquivo(manifesto.downloadUrl, parcial);
            if (!sha256(parcial).equalsIgnoreCase(manifesto.sha256)) {
                parcial.delete();
                throw new IOException("O arquivo baixado não confere.");
            }
            if (destino.exists() && !destino.delete()) {
                throw new IOException("Não foi possível salvar a atualização.");
            }
            if (!parcial.renameTo(destino)) {
                copiar(parcial, destino);
                parcial.delete();
            }
            if (!sha256(destino).equalsIgnoreCase(manifesto.sha256)) {
                destino.delete();
                throw new IOException("O arquivo baixado não confere.");
            }
            return destino;
        } catch (Exception e) {
            parcial.delete();
            throw e;
        }
    }

    private Manifesto baixarManifesto(String hash, int versaoLocal) throws Exception {
        String endereco = base() + "/utilitarios/atualizacao/" + hash + "?version=" + versaoLocal;
        HttpURLConnection conn = abrir(endereco, LEITURA_MANIFESTO, "application/json");
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            String corpo = lerTexto(conn.getInputStream());
            JSONObject json = new JSONObject(corpo);
            String sha = json.optString("sha256").trim();
            int versao = json.optInt("version", 0);
            String download = json.optString("download_url").trim();
            if (download.isEmpty()) {
                download = base() + "/utilitarios/arquivo/hash/" + hash;
            }
            return new Manifesto(versao, sha, download);
        } finally {
            conn.disconnect();
        }
    }

    private void baixarArquivo(String endereco, File destino) throws Exception {
        HttpURLConnection conn = abrir(endereco, LEITURA_APK, "*/*");
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            try (InputStream entrada = conn.getInputStream();
                 FileOutputStream saida = new FileOutputStream(destino)) {
                byte[] buffer = new byte[8192];
                int lido;
                while ((lido = entrada.read(buffer)) >= 0) {
                    saida.write(buffer, 0, lido);
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection abrir(String endereco, int leituraMs, String accept) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endereco).openConnection();
        conn.setConnectTimeout(CONEXAO_MS);
        conn.setReadTimeout(leituraMs);
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept", accept);
        conn.setRequestProperty("User-Agent", "ComputicketAppUpdate");
        return conn;
    }

    private static String lerTexto(InputStream entrada) throws IOException {
        try (InputStream in = entrada) {
            byte[] buffer = new byte[4096];
            java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
            int lido;
            while ((lido = in.read(buffer)) >= 0) {
                saida.write(buffer, 0, lido);
            }
            return saida.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void copiar(File origem, File destino) throws IOException {
        try (InputStream in = new FileInputStream(origem);
             FileOutputStream out = new FileOutputStream(destino)) {
            byte[] buffer = new byte[8192];
            int lido;
            while ((lido = in.read(buffer)) >= 0) {
                out.write(buffer, 0, lido);
            }
        }
    }

    static String sha256(File arquivo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(arquivo)) {
            byte[] buffer = new byte[8192];
            int lido;
            while ((lido = in.read(buffer)) >= 0) {
                if (lido > 0) digest.update(buffer, 0, lido);
            }
        }
        byte[] raw = digest.digest();
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static boolean podeInstalar(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    private static String base() {
        String valor = AtualizacaoConfig.BASE.trim();
        while (valor.endsWith("/")) valor = valor.substring(0, valor.length() - 1);
        return valor;
    }

    private static final class Manifesto {
        final int version;
        final String sha256;
        final String downloadUrl;

        Manifesto(int version, String sha256, String downloadUrl) {
            this.version = version;
            this.sha256 = sha256;
            this.downloadUrl = downloadUrl;
        }
    }
}
