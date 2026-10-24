package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Bounded HTTPS responses, atomic cache replacement and explicit freshness. Worker-thread only. */
final class JsonCache {
    static final class Result { final JSONObject json; final long fetchedAt; final boolean stale;
        Result(JSONObject j, long t, boolean s) { json=j; fetchedAt=t; stale=s; }
    }
    static Result get(Context c, String url, long ttl) throws Exception {
        if (!url.startsWith("https://")) throw new IOException("HTTPS required");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder(); for (byte b : digest) hash.append(String.format("%02x", b));
        File dir = new File(c.getCacheDir(), "ambient-feeds"); dir.mkdirs();
        File file = new File(dir, hash + ".json"); long now = System.currentTimeMillis();
        if (file.isFile() && now - file.lastModified() < ttl) return cached(file, false);
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            for (int redirects = 0; ; redirects++) {
                connection = (HttpURLConnection) target.openConnection(); connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(4000); connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "TvHome/3.0 (personal TV launcher)");
                int status = connection.getResponseCode();
                if (status == 200) break;
                if (redirects >= 3 || (status != 301 && status != 302 && status != 303 && status != 307 && status != 308)) throw new IOException("Service unavailable");
                String location = connection.getHeaderField("Location");
                if (location == null) throw new IOException("Missing redirect");
                URL next = new URL(target, location);
                if (!"https".equals(next.getProtocol())) throw new IOException("Insecure redirect");
                connection.disconnect(); target = next;
            }
            JSONObject json; try (InputStream in = connection.getInputStream()) { json = parse(read(in)); }
            validate(url, json);
            // Avoid growing the cache indefinitely as daily fixture URLs change.
            File[] files = dir.listFiles(); if (files != null && files.length > 80) {
                java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
                for (int i=0; i<files.length-60; i++) files[i].delete();
            }
            File temp = File.createTempFile("feed-", ".tmp", dir);
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(json.toString().getBytes(StandardCharsets.UTF_8)); }
            if (!temp.renameTo(file)) temp.delete();
            return new Result(json, now, false);
        } catch (Exception e) { if (file.isFile()) return cached(file, true); throw e; }
        finally { if (connection != null) connection.disconnect(); }
    }
    private static JSONObject parse(String text) throws Exception {
        return text.trim().startsWith("[") ? new JSONObject().put("items", new org.json.JSONArray(text)) : new JSONObject(text);
    }
    private static void validate(String url, JSONObject json) throws Exception {
        if (url.contains("/forecast?")) AmbientData.weather(json);
        else if (url.contains("/route/v1/")) AmbientData.route(json);
        else if (url.contains("/scoreboard")) json.getJSONArray("events");
        else if (url.contains("/catalog/")) json.getJSONArray("metas");
        else if (url.contains("/video_title_search/")) json.getJSONArray("items");
    }
    private static Result cached(File file, boolean stale) throws Exception {
        try (InputStream in = new FileInputStream(file)) { return new Result(parse(read(in)), file.lastModified(), stale); }
    }
    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] bytes = new byte[8192]; int n;
        while ((n=in.read(bytes)) != -1) { if (out.size()+n > 3*1024*1024) throw new IOException("Response too large"); out.write(bytes,0,n); }
        return out.toString("UTF-8");
    }
}
