package com.compumais.poscompuchat;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.compumais.poscompuchat.ui.ConfigActivity;
import com.compumais.poscompuchat.ui.LoginActivity;
import com.compumais.poscompuchat.ui.MesasActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.isConfigured(this)) {
            startActivity(new Intent(this, ConfigActivity.class));
        } else if (Prefs.userId(this) <= 0) {
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            startActivity(new Intent(this, MesasActivity.class));
        }
        finish();
    }
}
