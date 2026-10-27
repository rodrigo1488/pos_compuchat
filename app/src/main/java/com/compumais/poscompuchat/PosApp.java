package com.compumais.poscompuchat;

import android.app.Application;

import com.compumais.poscompuchat.data.AppDatabase;

public class PosApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.get(this);
        AppUpdater.iniciar(this);
    }
}
