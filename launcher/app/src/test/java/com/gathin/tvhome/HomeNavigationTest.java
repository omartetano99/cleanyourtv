package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.os.UserManager;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-xhdpi")
public class HomeNavigationTest {
    ActivityController<MainActivity> controller;
    MainActivity activity;
    @Before public void setup() throws Exception {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getSystemService(UserManager.class)).setUserUnlocked(true);
        controller=Robolectric.buildActivity(MainActivity.class).create().start(); activity=controller.get();
        set("resumed",true);set("feedRowData",new ArrayList<FeedLoader.Row>());set("cinemanaRowData",new ArrayList<FeedLoader.Row>());
        set("stremioRowData",new ArrayList<FeedLoader.Row>());
        activity.getSharedPreferences("tvhome",0).edit().clear().commit();
    }
    @After public void cleanup() { if(controller!=null) controller.stop().destroy(); }
    @Test public void addingAndReorderingRowsReusesExistingRecyclerViews() throws Exception {
        FeedLoader.Row one=row("one"),two=row("two"),three=row("three");
        set("providerRowData",Arrays.asList(one,two));render();
        View original=((LinearLayout)activity.findViewById(R.id.videoRows)).getChildAt(0);
        set("providerRowData",Arrays.asList(three,two,one));render();
        assertSame(original,((LinearLayout)activity.findViewById(R.id.videoRows)).getChildAt(2));
        List<VideoRowAdapter> adapters=(List<VideoRowAdapter>)get("videoRowAdapters");
        Field index=VideoRowAdapter.class.getDeclaredField("rowIndex");index.setAccessible(true);assertEquals(2,index.getInt(adapters.get(2)));
    }
    @Test public void historyAndStremioCanBeHiddenAndOrderedIndependently() throws Exception {
        FeedLoader.Row history=row(CinemanaHistory.KEY),stremio=row("stremio|movie|top"),provider=row("provider");
        set("providerRowData",Arrays.asList(provider));set("cinemanaRowData",Arrays.asList(history));set("stremioRowData",Arrays.asList(stremio));
        RowOrder.move(activity.getSharedPreferences("tvhome",0),Arrays.asList(provider,history,stremio),stremio.key,0);render();
        assertEquals(stremio.key,((List<FeedLoader.Row>)get("visibleRows")).get(0).key);
        activity.getSharedPreferences("tvhome",0).edit().putStringSet("hidden_rows",new HashSet<>(Arrays.asList(history.key))).commit();render();
        assertEquals(2,((List<FeedLoader.Row>)get("visibleRows")).size());
    }
    @Test public void headerHasDedicatedAppLibraryAndPrivateSettings() {
        assertTrue(activity.findViewById(R.id.btnAllApps).isFocusable());
        activity.findViewById(R.id.btnAllApps).performClick();
        assertEquals(AllAppsActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        activity.findViewById(R.id.btnSettings).performClick();
        assertEquals(HomeSettingsActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void idleTimerIsRemovedWhenWindowLosesFocus() throws Exception {
        activity.onWindowFocusChanged(true);assertTrue((boolean)get("windowFocused"));
        activity.onWindowFocusChanged(false);assertFalse((boolean)get("windowFocused"));
        android.os.Handler handler=(android.os.Handler)get("handler");
        assertFalse(handler.hasCallbacks((Runnable)get("idleScreen")));
    }
    private FeedLoader.Row row(String key) { FeedLoader.Row row=new FeedLoader.Row();row.key=key;row.title=key;FeedLoader.Video video=new FeedLoader.Video();video.title="Example";video.url="https://example.invalid/"+key;row.videos.add(video);return row; }
    private void render() throws Exception { Method m=MainActivity.class.getDeclaredMethod("renderContentRows");m.setAccessible(true);m.invoke(activity); }
    private void set(String key,Object value) throws Exception { Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(activity,value); }
    private Object get(String key) throws Exception { Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(activity); }
}
