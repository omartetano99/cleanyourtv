package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** A persistent header and one vertically scrolling collection of remote-friendly shelves. */
public class MainActivity extends Activity implements AppAdapter.TileListener {
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private TextView greeting, btnWifi, contentStatus;
    private float density;
    private ChannelRowAdapter channelAdapter;
    private RecyclerView channelsRow;
    private View channelBlockView;
    private ScrollView videoWrap;
    private LinearLayout videoRowsContainer, cinemanaShelf;
    private final List<View> videoRowViews = new ArrayList<>();
    private FeedLoader feedLoader;
    private ProviderRows providerRows;
    private CinemanaRows cinemanaRows;
    private StremioRows stremioRows;
    private List<FeedLoader.Row> stremioRowData;
    private boolean windowFocused;
    private final Runnable idleScreen = () -> {
        if (this.resumed && windowFocused && getSystemService(android.os.PowerManager.class).isInteractive() && !this.recoveryPending && AmbientSettings.idleMillis(this) > 0) {
            if (PlaybackGuard.canIdle(this)) startActivity(new Intent(this, AmbientActivity.class));
            else resetIdleTimer();
        }
    };
    private List<FeedLoader.Row> cinemanaRowData;
    private List<FeedLoader.Row> feedRowData, providerRowData;
    private final List<FeedLoader.Row> visibleRows = new ArrayList<>();
    private final List<VideoRowAdapter> videoRowAdapters = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed, restoring, pendingVideoRestore, observerRegistered;
    private int refreshGeneration;
    private boolean recoveryPending;
    private boolean waitingForUnlock;
    private android.content.BroadcastReceiver unlockReceiver;
    private final Runnable resumePlayback = () -> {
        recoveryPending = false;
        if (resumed && getSystemService(android.os.PowerManager.class).isInteractive() && !PlaybackRecovery.resume(this)) PlaybackRecovery.stayHome(this);
    };
    private String renderedFingerprint;
    private String focusZone = "apps", lastAppKey, lastRowKey, lastVideoKey;
    private int lastAppIndex, lastVideoIndex;
    private final Runnable providerRefresh = this::refreshContentRows;
    private final ContentObserver contentObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            handler.removeCallbacks(providerRefresh);
            handler.postDelayed(providerRefresh, 700);
        }
    };
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network network) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onLost(@NonNull Network network) { handler.post(MainActivity.this::updateWifiLabel); }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            updateGreeting();
            updateWifiLabel();
            refreshContentRows();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Vendor TvBoot resolves HOME before credential storage is unlocked.
        // Show a real window immediately, but never touch normal preferences until unlock.
        if (!getSystemService(android.os.UserManager.class).isUserUnlocked()) {
            waitingForUnlock = true;
            setContentView(R.layout.home_starting);
            unlockReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) { recreate(); }
            };
            registerReceiver(unlockReceiver, new android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if (getSystemService(android.os.UserManager.class).isUserUnlocked()) recreate();
            return;
        }
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();
        root = findViewById(R.id.root);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        contentStatus = findViewById(R.id.contentStatus);
        videoWrap = findViewById(R.id.videoRowsWrap);
        videoRowsContainer = findViewById(R.id.videoRows);
        cinemanaShelf = findViewById(R.id.cinemanaShelf);
        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        grid.setItemAnimator(null);
        grid.setNestedScrollingEnabled(false);
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);
        feedLoader = new FeedLoader(this);
        providerRows = new ProviderRows();
        cinemanaRows = new CinemanaRows(this);
        stremioRows = new StremioRows(this);
        focusZone = rowsPrefs().getString("focus_zone", "apps");
        lastAppKey = rowsPrefs().getString("focus_app", null);
        lastRowKey = rowsPrefs().getString("focus_row", null);
        lastVideoKey = rowsPrefs().getString("focus_video", null);
        lastVideoIndex = rowsPrefs().getInt("focus_lane", 0);

        View inputs = findViewById(R.id.btnInputs), settings = findViewById(R.id.btnSettings), rows = findViewById(R.id.btnRows);
        inputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> {
            Intent network = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            if (network.resolveActivity(getPackageManager()) == null) network = new Intent(Settings.ACTION_WIFI_SETTINGS);
            open(network);
        });
        settings.setOnClickListener(v -> open(new Intent(this, HomeSettingsActivity.class)));
        View allApps = findViewById(R.id.btnAllApps);
        allApps.setOnClickListener(v -> open(new Intent(this, AllAppsActivity.class)));
        rows.setOnClickListener(v -> showRowsManager());
        View[] buttons = {inputs, btnWifi, allApps, rows, settings};
        for (int j = 0; j < buttons.length; j++) {
            final int index = j;
            buttons[j].setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = Math.max(0, Math.min(buttons.length - 1, index + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    buttons[next].requestFocus();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (bannerRowIndex() >= 0) focusVideo(bannerRowIndex(), lastVideoIndex);
                    else if (adapter.getItemCount() > 0) focusTile(lastAppIndex);
                    else if (!videoRowAdapters.isEmpty()) focusVideo(0, 0);
                    else if (channelAdapter.getItemCount() > 0) focusChannel(0);
                    return true;
                }
                return false;
            });
            buttons[j].setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(160).start();
                if (focused && !restoring) { focusZone = "top"; videoWrap.smoothScrollTo(0, 0); }
            });
        }
        channelBlockView = findViewById(R.id.channelRow);
        channelsRow = channelBlockView.findViewById(R.id.channels);
        channelAdapter = new ChannelRowAdapter(this, new ChannelRowAdapter.Listener() {
            @Override public void onChannelFocused(View card, View itemView, String key) {
                if (!restoring) focusZone = "channels";
                revealShelf(channelBlockView);
            }
            @Override public boolean onKey(int key, int index) {
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (firstRegularRow() >= 0) focusVideo(videoRowAdapters.size() - 1, index);
                    else focusTile(lastAppIndex);
                    return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_DOWN;
            }
        });
        channelsRow.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        channelsRow.setNestedScrollingEnabled(false);
        channelsRow.setItemAnimator(null);
        channelsRow.setAdapter(channelAdapter);
        channelAdapter.setRow(channelsRow);
        channelBlockView.setVisibility(channelAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
        findViewById(R.id.topBar).setAlpha(0f);
        findViewById(R.id.topBar).animate().alpha(1f).setDuration(320).start();
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForUnlock) return;
        resumed = true;
        resetIdleTimer();
        PlaybackRecovery.homeVisible = true;
        PlaybackRecovery.initialize(this);
        if (getSystemService(android.os.PowerManager.class).isInteractive()) {
            recoveryPending = PlaybackRecovery.due(this);
            if (recoveryPending) {
                Toast.makeText(this, R.string.resuming_playback, Toast.LENGTH_LONG).show();
                handler.postDelayed(resumePlayback, 2000);
            } else PlaybackRecovery.stayHome(this);
        }
        pendingVideoRestore = "video".equals(focusZone);
        final String returnApp = lastAppKey;
        adapter.reload(() -> {
            if (!resumed || pendingVideoRestore || !"apps".equals(focusZone)) return;
            int position = adapter.positionOf(lastAppKey);
            focusTile(position >= 0 ? position : lastAppIndex);
        });
        updateGreeting();
        updateWifiLabel();
        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, MINUTE_MS);
        if (!observerRegistered) {
            try {
                getContentResolver().registerContentObserver(TvContract.Channels.CONTENT_URI, true, contentObserver);
                getContentResolver().registerContentObserver(TvContract.PreviewPrograms.CONTENT_URI, true, contentObserver);
                getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, contentObserver);
                observerRegistered = true;
            } catch (SecurityException e) {
                getContentResolver().unregisterContentObserver(contentObserver);
            }
        }
        if (!pendingVideoRestore) grid.post(() -> {
            int position = adapter.positionOf(returnApp);
            focusTile(position >= 0 ? position : lastAppIndex);
        });
        refreshContentRows();
    }

    @Override protected void onPause() {
        super.onPause();
        if (waitingForUnlock) return;
        resumed = false;
        handler.removeCallbacks(idleScreen);
        PlaybackRecovery.homeVisible = false;
        handler.removeCallbacks(resumePlayback);
        recoveryPending = false;
        ++refreshGeneration;
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(providerRefresh);
        if (observerRegistered) { getContentResolver().unregisterContentObserver(contentObserver); observerRegistered = false; }
        rowsPrefs().edit().putString("focus_zone", focusZone).putString("focus_app", lastAppKey)
            .putString("focus_row", lastRowKey).putString("focus_video", lastVideoKey).putInt("focus_lane", lastVideoIndex).apply();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (unlockReceiver != null) unregisterReceiver(unlockReceiver);
        if (waitingForUnlock) return;
        handler.removeCallbacksAndMessages(null);
        adapter.close();
        feedLoader.close();
        providerRows.close();
        cinemanaRows.close();
        stremioRows.close();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    private void resetIdleTimer() {
        handler.removeCallbacks(idleScreen);
        if (!waitingForUnlock && resumed && windowFocused && AmbientSettings.idleMillis(this) > 0)
            handler.postDelayed(idleScreen, AmbientSettings.idleMillis(this));
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); windowFocused = focused;
        if (!waitingForUnlock) resetIdleTimer();
    }
    @Override public void onUserInteraction() { super.onUserInteraction(); if (!waitingForUnlock) resetIdleTimer(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!waitingForUnlock && event.getAction() == KeyEvent.ACTION_DOWN) resetIdleTimer();
        if (recoveryPending && event.getAction() == KeyEvent.ACTION_DOWN) {
            handler.removeCallbacks(resumePlayback);
            recoveryPending = false;
            PlaybackRecovery.stayHome(this);
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() { if (!waitingForUnlock) focusTile(lastAppIndex); }

    @Override public void onRequestPermissionsResult(int code, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 42) refreshContentRows();
    }

    @Override public void onTileFocused(int position, View card) {
        if (restoring || pendingVideoRestore) return;
        focusZone = "apps";
        pendingVideoRestore = false;
        lastAppIndex = position;
        lastAppKey = adapter.keyAt(position);
        revealApps();
    }

    @Override public boolean onTileVerticalKey(int position, int key) {
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (bannerRowIndex() >= 0) focusVideo(bannerRowIndex(), 0);
            else findViewById(R.id.btnInputs).requestFocus();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (firstRegularRow() >= 0) focusVideo(firstRegularRow(), Math.min(position, 3));
            else if (channelBlockView.getVisibility() == View.VISIBLE) focusChannel(position);
            return true;
        }
        return false;
    }

    private void focusTile(int position) {
        pendingVideoRestore = false;
        focusZone = "apps";
        if (adapter.getItemCount() == 0) { findViewById(R.id.btnInputs).requestFocus(); return; }
        FocusRequests.focus(grid, Math.max(0, Math.min(position, adapter.getItemCount() - 1)));
        revealApps();
    }
    private void focusChannel(int index) {
        if (channelAdapter.getItemCount() > 0) FocusRequests.focus(channelsRow, Math.max(0, Math.min(index, channelAdapter.getItemCount() - 1)));
    }
    private int bannerRowIndex() {
        for (int i = 0; i < visibleRows.size(); i++) if (CinemanaRows.KEY.equals(visibleRows.get(i).key)) return i;
        return -1;
    }
    private int firstRegularRow() {
        for (int i = 0; i < visibleRows.size(); i++) if (!CinemanaRows.KEY.equals(visibleRows.get(i).key)) return i;
        return -1;
    }
    private void revealApps() {
        if (bannerRowIndex() < 0) { revealShelf(findViewById(R.id.appsCaption)); return; }
        if (restoring) return;
        Rect rect = new Rect(0, 0, grid.getWidth(), grid.getHeight());
        ((ViewGroup) videoWrap.getChildAt(0)).offsetDescendantRectToMyCoords(grid, rect);
        // Keep the banners in view whenever the dock fits beneath them.
        videoWrap.smoothScrollTo(0, Math.max(0, rect.bottom - videoWrap.getHeight() + (int) (8 * density)));
    }
    private void revealShelf(View shelf) {
        if (restoring || shelf == null) return;
        Rect rect = new Rect(0, 0, shelf.getWidth(), shelf.getHeight());
        ((ViewGroup) videoWrap.getChildAt(0)).offsetDescendantRectToMyCoords(shelf, rect);
        int target = Math.max(0, rect.top - (int) (8 * density));
        videoWrap.smoothScrollTo(0, target);
    }
    private final VideoRowAdapter.Listener videoListener = new VideoRowAdapter.Listener() {
        @Override public void onVideoFocused(View card, View itemView, int rowIndex, int lane) {
            if (restoring || rowIndex < 0 || rowIndex >= visibleRows.size()) return;
            focusZone = "video";
            pendingVideoRestore = false;
            lastRowKey = visibleRows.get(rowIndex).key;
            lastVideoIndex = lane;
            lastVideoKey = videoRowAdapters.get(rowIndex).keyAt(lane);
            revealShelf(videoRowViews.get(rowIndex));
        }
        @Override public boolean onKey(int rowIndex, int lane, int key) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (rowIndex == bannerRowIndex()) findViewById(R.id.btnInputs).requestFocus();
                else if (rowIndex == firstRegularRow()) focusTile(lastAppIndex);
                else if (rowIndex > 0) focusVideo(rowIndex - 1, lane);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (rowIndex == bannerRowIndex()) focusTile(lastAppIndex);
                else if (rowIndex + 1 < videoRowAdapters.size()) focusVideo(rowIndex + 1, lane);
                else if (channelBlockView.getVisibility() == View.VISIBLE) focusChannel(lane);
                return true;
            }
            return false;
        }
    };
    private void focusVideo(int index, int lane) {
        if (index < 0 || index >= videoRowAdapters.size()) return;
        videoRowAdapters.get(index).focusAt(lane);
        revealShelf(videoRowViews.get(index));
    }

    private void refreshContentRows() {
        if (!resumed) return;
        final int generation = ++refreshGeneration;
        final boolean[] loaded = {false, false};
        providerRows.load(this, rows -> {
            if (!resumed || generation != refreshGeneration) return;
            providerRowData = rows; loaded[0] = true;
            if (loaded[1]) renderContentRows();
        });
        cinemanaRows.load(rows -> {
            if (!resumed || generation != refreshGeneration) return;
            cinemanaRowData = rows;
            renderContentRows();
        });
        stremioRows.load(rows -> {
            if (!resumed || generation != refreshGeneration) return;
            stremioRowData = rows;
            renderContentRows();
        });
        List<String> specs = new ArrayList<>();
        for (String s : getResources().getStringArray(R.array.content_rows)) if (s != null && !s.trim().isEmpty()) specs.add(s.trim());
        feedLoader.load(specs, rows -> {
            if (!resumed || generation != refreshGeneration) return;
            feedRowData = rows; loaded[1] = true;
            if (loaded[0]) renderContentRows();
        });
    }
    private void renderContentRows() {
        if (!resumed || isDestroyed()) return;
        java.util.Set<String> hidden = hiddenRows();
        List<FeedLoader.Row> merged = new ArrayList<>();
        if (providerRowData != null) for (FeedLoader.Row r : providerRowData) if (!hidden.contains(r.key) && !r.videos.isEmpty()) merged.add(r);
        if (feedRowData != null) for (FeedLoader.Row r : feedRowData) if (!hidden.contains(r.key) && !r.videos.isEmpty()) merged.add(r);
        if (cinemanaRowData != null) for (FeedLoader.Row r : cinemanaRowData) if (!hidden.contains(r.key) && !r.videos.isEmpty()) merged.add(r);
        if (stremioRowData != null) for (FeedLoader.Row r : stremioRowData) if (!hidden.contains(r.key) && !r.videos.isEmpty()) merged.add(r);
        RowOrder.sort(merged, rowsPrefs());
        // Cinemana is the featured shelf above the apps dock; other shelves keep their saved order.
        for (int i = 0; i < merged.size(); i++) if (CinemanaRows.KEY.equals(merged.get(i).key)) {
            merged.add(0, merged.remove(i)); break;
        }
        StringBuilder signature = new StringBuilder();
        for (FeedLoader.Row r : merged) {
            signature.append(r.key).append('|').append(r.title);
            for (FeedLoader.Video v : r.videos) signature.append('\n').append(v.identity()).append('|').append(v.title).append('|').append(v.thumb).append('|').append(v.positionMs).append('|').append(v.durationMs).append('|').append(v.rating).append('|').append(v.genres).append('|').append(v.bannerIndex);
        }
        String fingerprint = signature.toString();
        boolean restoreVideo = pendingVideoRestore || "video".equals(focusZone);
        String rowKey = lastRowKey, videoKey = lastVideoKey;
        int lane = lastVideoIndex;
        boolean changed = !fingerprint.equals(renderedFingerprint);
        boolean sameOrder = merged.size() == visibleRows.size();
        for (int i = 0; sameOrder && i < merged.size(); i++)
            sameOrder = java.util.Objects.equals(merged.get(i).key, visibleRows.get(i).key);
        if (changed && sameOrder) {
            restoring = true;
            for (int i = 0; i < merged.size(); i++) {
                FeedLoader.Row row = merged.get(i);
                View view = videoRowViews.get(i);
                boolean featured = CinemanaRows.KEY.equals(row.key);
                ((TextView) view.findViewById(R.id.rowCaption)).setText(featured ? "Cinemana 4K" : row.title);
                if (!featured) ((TextView) view.findViewById(R.id.rowSource)).setText(
                    getString(R.string.row_source_count, appLabel(row.videos.get(0).pkg), row.videos.size()));
                videoRowAdapters.get(i).setItems(row.videos);
            }
            visibleRows.clear(); visibleRows.addAll(merged);
            restoring = false;
            renderedFingerprint = fingerprint;
        } else if (changed) {
            java.util.Map<String, android.os.Parcelable> scrollStates = new java.util.HashMap<>();
            for (int i = 0; i < visibleRows.size(); i++) {
                RecyclerView rv = videoRowViews.get(i).findViewById(R.id.videoCards);
                scrollStates.put(visibleRows.get(i).key, rv.getLayoutManager().onSaveInstanceState());
            }
            java.util.Map<String, View> reusableViews = new java.util.HashMap<>();
            java.util.Map<String, VideoRowAdapter> reusableAdapters = new java.util.HashMap<>();
            for (int i = 0; i < visibleRows.size(); i++) {
                reusableViews.put(visibleRows.get(i).key, videoRowViews.get(i));
                reusableAdapters.put(visibleRows.get(i).key, videoRowAdapters.get(i));
            }
            restoring = true;
            videoRowsContainer.removeAllViews();
            cinemanaShelf.removeAllViews(); videoRowViews.clear();
            videoRowAdapters.clear(); visibleRows.clear();
            for (FeedLoader.Row r : merged) {
                int index = visibleRows.size();
                View rowView = reusableViews.get(r.key);
                boolean newRow = rowView == null;
                if (newRow) rowView = getLayoutInflater().inflate(R.layout.video_row, videoRowsContainer, false);
                boolean cinemanaBanner = CinemanaRows.KEY.equals(r.key);
                ((TextView) rowView.findViewById(R.id.rowCaption)).setText(cinemanaBanner ? "Cinemana 4K" : r.title);
                String source = r.videos.get(0).pkg;
                ((TextView) rowView.findViewById(R.id.rowSource)).setText(getString(R.string.row_source_count, appLabel(source), r.videos.size()));
                rowView.findViewById(R.id.rowSource).setVisibility(cinemanaBanner ? View.GONE : View.VISIBLE);
                RecyclerView cards = rowView.findViewById(R.id.videoCards);
                VideoRowAdapter a = reusableAdapters.get(r.key);
                if (newRow) {
                    cards.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
                    cards.setNestedScrollingEnabled(false);
                    cards.setItemAnimator(null);
                    a = new VideoRowAdapter(this, cards, index, videoListener);
                    cards.setAdapter(a);
                    cards.addItemDecoration(new RecyclerView.ItemDecoration() {
                    @Override public void getItemOffsets(@NonNull Rect out, @NonNull View v, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                        out.right = (int) (18 * density);
                    }
                    });
                }
                a.setRowIndex(index); a.setItems(r.videos);
                if (cinemanaBanner) cinemanaShelf.addView(rowView);
                else videoRowsContainer.addView(rowView);
                videoRowViews.add(rowView);
                visibleRows.add(r); videoRowAdapters.add(a);
                android.os.Parcelable saved = scrollStates.get(r.key);
                if (saved != null) cards.getLayoutManager().onRestoreInstanceState(saved);
            }
            restoring = false;
            renderedFingerprint = fingerprint;
            android.util.Log.i("TvHome", "Rendered " + merged.size() + " content rows");
        }
        boolean noRows = merged.isEmpty();
        contentStatus.setVisibility(noRows ? View.VISIBLE : View.GONE);
        contentStatus.setText(checkSelfPermission("android.permission.READ_TV_LISTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED
            ? R.string.permission_rows_hint : (!hidden.isEmpty() ? R.string.hidden_rows_hint : R.string.empty_rows));
        channelBlockView.setVisibility(noRows && channelAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
        if (restoreVideo && (changed || pendingVideoRestore)) {
            pendingVideoRestore = false;
            int index = -1;
            for (int i = 0; i < visibleRows.size(); i++) if (java.util.Objects.equals(visibleRows.get(i).key, rowKey)) { index = i; break; }
            if (index >= 0) {
                int pos = videoRowAdapters.get(index).positionOf(videoKey);
                focusVideo(index, pos >= 0 ? pos : lane);
            } else focusTile(lastAppIndex);
        }
    }
    private String appLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return getString(R.string.feed_source);
        try { return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))); }
        catch (Exception e) { return "Video app"; }
    }
    private android.content.SharedPreferences rowsPrefs() {
        return getSharedPreferences("tvhome", MODE_PRIVATE);
    }

    private java.util.Set<String> hiddenRows() {
        java.util.Set<String> stored = rowsPrefs().getStringSet("hidden_rows", null);
        return stored == null ? new java.util.HashSet<>() : new java.util.HashSet<>(stored);
    }

    /** Remote-friendly ordering: select a shelf, then move or hide it. */
    private void showRowsManager() { showRowsManager(null); }
    private void showRowsManager(String selectedKey) {
        List<FeedLoader.Row> rows = new ArrayList<>();
        if (providerRowData != null) rows.addAll(providerRowData);
        if (feedRowData != null) rows.addAll(feedRowData);
        if (cinemanaRowData != null) rows.addAll(cinemanaRowData);
        if (stremioRowData != null) rows.addAll(stremioRowData);
        RowOrder.sort(rows, rowsPrefs());
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.nothing_published, Toast.LENGTH_SHORT).show(); return;
        }
        String[] labels = new String[rows.size()];
        java.util.Set<String> hidden = hiddenRows();
        int selected = 0;
        for (int i = 0; i < rows.size(); i++) {
            FeedLoader.Row row = rows.get(i);
            labels[i] = (CinemanaRows.KEY.equals(row.key) ? "Above apps · " : (i + 1) + ".  ") + row.title + (hidden.contains(row.key) ? "  ·  " + getString(R.string.row_hidden) : "");
            if (row.key.equals(selectedKey)) selected = i;
        }
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.rows_order_title)
            .setItems(labels, (d, index) -> showRowActions(rows, index))
            .setPositiveButton(android.R.string.ok, null).create();
        final int focus = selected;
        dialog.setOnShowListener(d -> { dialog.getListView().setSelection(focus); dialog.getListView().requestFocus(); });
        dialog.show();
    }
    private void showRowActions(List<FeedLoader.Row> rows, int index) {
        FeedLoader.Row row = rows.get(index);
        List<String> actions = new ArrayList<>();
        List<Integer> destinations = new ArrayList<>();
        if (!CinemanaRows.KEY.equals(row.key) && index > 0) {
            actions.add(getString(R.string.row_move_first)); destinations.add(0);
            if (index > 1) { actions.add(getString(R.string.row_move_up)); destinations.add(index - 1); }
        }
        if (!CinemanaRows.KEY.equals(row.key) && index < rows.size() - 1) { actions.add(getString(R.string.row_move_down)); destinations.add(index + 1); }
        actions.add(getString(hiddenRows().contains(row.key) ? R.string.row_show : R.string.row_hide)); destinations.add(-1);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(row.title)
            .setItems(actions.toArray(new String[0]), (d, which) -> {
                int target = destinations.get(which);
                if (target < 0) {
                    java.util.Set<String> hidden = hiddenRows();
                    if (!hidden.remove(row.key)) hidden.add(row.key);
                    rowsPrefs().edit().putStringSet("hidden_rows", hidden).apply();
                } else RowOrder.move(rowsPrefs(), rows, row.key, target);
                renderContentRows();
                showRowsManager(row.key);
            }).setNegativeButton(android.R.string.cancel, (d, which) -> showRowsManager(row.key)).show();
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(java.util.Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(this, InputActivity.class).putExtra("input_id", id).putExtra("input_label", String.valueOf(info.loadLabel(this)))));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null && tunerId != null) {
            liveTv.putExtra(TvInputInfo.EXTRA_INPUT_ID, tunerId);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
