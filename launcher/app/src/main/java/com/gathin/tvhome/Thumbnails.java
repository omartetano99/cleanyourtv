package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Bounded image cache; a unique bind token prevents recycled cards receiving stale artwork. */
final class Thumbnails {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(12 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 12)) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final Executor EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static void clear(ImageView view) {
        view.setTag(null); view.animate().cancel(); view.setAlpha(1f); view.setImageDrawable(null);
    }
    static void load(String url, ImageView view) {
        load(url, view, false);
    }
    static void loadBanner(String url, ImageView view) {
        load(url, view, true);
    }
    private static void load(String url, ImageView view, boolean banner) {
        clear(view);
        if (url == null || url.trim().isEmpty()) return;
        String cacheKey = banner ? url + "|banner" : url;
        Object token = new Object();
        view.setTag(token); // Also on cache hits: invalidates any previous asynchronous request.
        Bitmap cached = CACHE.get(cacheKey);
        if (cached != null) { view.setImageBitmap(cached); return; }
        WeakReference<ImageView> ref = new WeakReference<>(view);
        Context app = view.getContext().getApplicationContext();
        EXEC.execute(() -> {
            Bitmap bitmap = CACHE.get(cacheKey);
            if (bitmap == null) bitmap = fetch(app, url, banner);
            if (bitmap == null) return;
            CACHE.put(cacheKey, bitmap);
            final Bitmap result = bitmap;
            MAIN.post(() -> {
                ImageView target = ref.get();
                if (target != null && target.getTag() == token) {
                    target.setAlpha(0f); target.setImageBitmap(result);
                    target.animate().alpha(1f).setDuration(180).start();
                }
            });
        });
    }
    private static Bitmap fetch(Context context, String url, boolean banner) {
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse(url);
            InputStream stream;
            if ("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme())) {
                stream = context.getContentResolver().openInputStream(uri);
            } else if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(5000); connection.setReadTimeout(6000);
                connection.setRequestProperty("User-Agent", "TvHome/2.1");
                stream = connection.getInputStream();
            } else return null;
            if (stream == null) return null;
            byte[] bytes;
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 4 * 1024 * 1024) return null;
                    out.write(buffer, 0, n);
                }
                bytes = out.toByteArray();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = banner ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            opts.inSampleSize = 1;
            if (banner) {
                // Keep at least the on-screen banner resolution; rounding down to 506px
                // and then enlarging/cropping it caused visibly blurred artwork.
                while (bounds.outWidth / (opts.inSampleSize * 2) >= 960
                    && bounds.outHeight / (opts.inSampleSize * 2) >= 350) opts.inSampleSize *= 2;
            } else {
                while (bounds.outWidth / opts.inSampleSize > 960 || bounds.outHeight / opts.inSampleSize > 540) opts.inSampleSize *= 2;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (Exception e) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }
}
