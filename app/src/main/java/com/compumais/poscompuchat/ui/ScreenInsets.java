package com.compumais.poscompuchat.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

final class ScreenInsets {
    private ScreenInsets() {}

    static void apply(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }
        View root = ((ViewGroup) content).getChildAt(0);
        if (root == null) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        boolean night = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat bars =
                new WindowInsetsControllerCompat(activity.getWindow(), root);
        bars.setAppearanceLightStatusBars(!night);
        bars.setAppearanceLightNavigationBars(!night);

        final int padL = root.getPaddingLeft();
        final int padT = root.getPaddingTop();
        final int padR = root.getPaddingRight();
        final int padB = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padL + sys.left, padT + sys.top, padR + sys.right, padB + sys.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
