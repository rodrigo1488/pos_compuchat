package com.compumais.poscompuchat;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "pos_prefs";

    public static SharedPreferences get(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String agentUrl(Context ctx) {
        return get(ctx).getString("agent_url", "");
    }

    public static String token(Context ctx) {
        return get(ctx).getString("pos_token", "");
    }

    public static void saveAgent(Context ctx, String url, String token) {
        get(ctx).edit().putString("agent_url", url.trim()).putString("pos_token", token.trim()).apply();
    }

    public static boolean isConfigured(Context ctx) {
        return get(ctx).contains("agent_url") && agentUrl(ctx).startsWith("http");
    }

    public static int userId(Context ctx) {
        return get(ctx).getInt("user_id", 0);
    }

    public static String userName(Context ctx) {
        return get(ctx).getString("user_name", "");
    }

    public static void saveUser(Context ctx, int id, String name) {
        get(ctx).edit().putInt("user_id", id).putString("user_name", name).apply();
    }

    public static void clearUser(Context ctx) {
        get(ctx).edit().remove("user_id").remove("user_name").apply();
    }
}
