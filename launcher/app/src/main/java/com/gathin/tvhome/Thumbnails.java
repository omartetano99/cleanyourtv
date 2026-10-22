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

/** Bounded image cache; a unique bind token prevents recycled cards receiving stale artwork. */
final class Thumbnails {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(12 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 12)) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final java.util.concurrent.ThreadPoolExecutor EXEC = new java.util.concurrent.ThreadPoolExecutor(
        2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static void clear(ImageView view) {
        if (view.getTag() instanceof Request) {
            Request old = (Request) view.getTag(); old.cancelled = true; EXEC.remove(old);
        }
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
        Bitmap cached = CACHE.get(cacheKey);
        if (cached != null) { view.setTag(new Object()); view.setImageBitmap(cached); return; }
        Request request = new Request(view, url, cacheKey, banner);
        view.setTag(request);
        EXEC.execute(request);
    }
    private static final class Request implements Runnable {
        final WeakReference<ImageView> target;
        final Context app;
        final String url, cacheKey;
        final boolean banner;
        volatile boolean cancelled;
        Request(ImageView view, String url, String cacheKey, boolean banner) {
            target = new WeakReference<>(view); app = view.getContext().getApplicationContext();
            this.url = url; this.cacheKey = cacheKey; this.banner = banner;
        }
        @Override public void run() {
            // Fast D-pad navigation must not leave obsolete downloads ahead of visible artwork.
            if (cancelled || target.get() == null) return;
            Bitmap bitmap = CACHE.get(cacheKey);
            if (bitmap == null) bitmap = fetch(app, url, banner);
            if (bitmap == null) return;
            CACHE.put(cacheKey, bitmap);
            if (cancelled) return;
            bitmap.prepareToDraw();
            final Bitmap result = bitmap;
            MAIN.post(() -> {
                ImageView view = target.get();
                if (!cancelled && view != null && view.getTag() == this) {
                    view.setAlpha(0f); view.setImageBitmap(result);
                    view.animate().alpha(1f).setDuration(140).start();
                }
            });
        }
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
