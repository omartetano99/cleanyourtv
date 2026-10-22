package com.gathin.tvhome;

import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Stable keys preserve the user's order through refreshes and temporarily missing providers. */
final class RowOrder {
    static List<String> read(SharedPreferences prefs) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("row_order", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String key = array.optString(i);
                if (!key.isEmpty() && !result.contains(key)) result.add(key);
            }
        } catch (Exception ignored) { }
        return result;
    }
    static void sort(List<FeedLoader.Row> rows, SharedPreferences prefs) {
        List<String> order = read(prefs);
        rows.sort((a, b) -> Integer.compare(rank(order, a.key), rank(order, b.key)));
    }
    private static int rank(List<String> order, String key) {
        int index = order.indexOf(key);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
    static void move(SharedPreferences prefs, List<FeedLoader.Row> rows, String key, int target) {
        List<String> order = read(prefs);
        for (FeedLoader.Row row : rows) if (!order.contains(row.key)) order.add(row.key);
        List<String> visible = new ArrayList<>();
        for (FeedLoader.Row row : rows) visible.add(row.key);
        if (!visible.remove(key)) return;
        visible.add(Math.max(0, Math.min(target, visible.size())), key);
        // Reorder present keys in their existing slots; absent/hidden providers retain their slots.
        int cursor = 0;
        for (int i = 0; i < order.size(); i++) if (visible.contains(order.get(i))) order.set(i, visible.get(cursor++));
        prefs.edit().putString("row_order", new JSONArray(order).toString()).apply();
    }
}
