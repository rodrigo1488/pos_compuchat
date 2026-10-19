package com.compumais.poscompuchat.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ImageStore {
    private static final String PREFS = "pos_image_hashes";

    public static File dir(Context ctx) {
        File folder = new File(ctx.getApplicationContext().getFilesDir(), "pos_media");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File file(Context ctx, String imageId) {
        if (imageId == null || imageId.trim().isEmpty()) return null;
        return new File(dir(ctx), imageId.trim());
    }

    public interface ImageProgress {
        void onImage(int done, int total);
    }

    public static void sync(Context ctx, PosApi api, List<CatalogModels.ImageDto> images) {
        sync(ctx, api, images, null);
    }

    public static void sync(Context ctx, PosApi api, List<CatalogModels.ImageDto> images, ImageProgress progress) {
        if (images == null) return;
        SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        int total = 0;
        for (CatalogModels.ImageDto img : images) {
            if (img != null && img.id != null && !img.id.trim().isEmpty()) {
                total++;
            }
        }
        int done = 0;
        for (CatalogModels.ImageDto img : images) {
            if (img == null || img.id == null || img.id.trim().isEmpty()) continue;
            String id = img.id.trim();
            String hash = img.hash == null ? "" : img.hash;
            File dest = file(ctx, id);
            String prev = prefs.getString(id, "");
            if (dest == null || !dest.isFile() || !hash.equals(prev)) {
                try {
                    api.downloadMedia(id, dest);
                    editor.putString(id, hash);
                } catch (IOException ignored) {
                }
            }
            done++;
            if (progress != null) {
                progress.onImage(done, total);
            }
        }
        editor.apply();
    }

    public static void bind(ImageView view, Context ctx, String imageId) {
        File f = file(ctx, imageId);
        if (f == null || !f.isFile()) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        view.setImageBitmap(bmp);
    }
}
