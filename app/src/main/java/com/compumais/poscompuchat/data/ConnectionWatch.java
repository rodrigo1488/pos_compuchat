package com.compumais.poscompuchat.data;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.compumais.poscompuchat.R;

import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionWatch {
    public interface Listener {
        void onStatus(boolean online, String message);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final PosApi api;
    private final long intervalMs;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private Listener listener;
    private boolean running;
    private final java.util.concurrent.atomic.AtomicInteger failStreak = new java.util.concurrent.atomic.AtomicInteger();

    public ConnectionWatch(Context ctx, long intervalMs) {
        this.api = new PosApi(ctx);
        this.intervalMs = intervalMs;
    }

    public void start(Listener listener) {
        this.listener = listener;
        running = true;
        main.removeCallbacks(tick);
        checkNow();
    }

    public void stop() {
        running = false;
        listener = null;
        main.removeCallbacks(tick);
    }

    public void checkNow() {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            boolean ok;
            String msg = null;
            try {
                api.ping();
                ok = true;
            } catch (Exception e) {
                ok = false;
                msg = PosApi.friendlyError(e);
            }
            if (ok) {
                failStreak.set(0);
            } else {
                failStreak.incrementAndGet();
            }
            boolean notify = ok || failStreak.get() >= 2;
            final boolean online = ok;
            final String message = msg;
            main.post(() -> {
                inFlight.set(false);
                if (listener != null && notify) {
                    listener.onStatus(online, message);
                }
                if (running) {
                    main.removeCallbacks(tick);
                    main.postDelayed(tick, intervalMs);
                }
            });
        }).start();
    }

    private final Runnable tick = this::checkNow;

    public static void bindBanner(Activity activity, boolean online, String message) {
        if (activity == null) {
            return;
        }
        View banner = activity.findViewById(R.id.bannerOffline);
        TextView txt = activity.findViewById(R.id.txtOffline);
        if (banner == null) {
            return;
        }
        banner.setVisibility(online ? View.GONE : View.VISIBLE);
        if (txt != null && !online) {
            txt.setText(message == null || message.isEmpty()
                    ? "Sem conexão com o servidor"
                    : message);
        }
    }
}
