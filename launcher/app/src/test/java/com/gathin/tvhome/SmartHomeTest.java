package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30, qualifiers="w960dp-h540dp-land-xhdpi")
public class SmartHomeTest {
    private Context context;
    @Before public void setup() { context=RuntimeEnvironment.getApplication(); CinemanaHistory.clear(context); AmbientSettings.prefs(context).edit().clear().commit(); PlaybackRecovery.prefs(context).edit().clear().commit(); }
    @Test public void coordinatesValidateAndRouteUsesLongitudeFirst() {
        assertNull(AmbientSettings.coordinates("NaN,4")); assertNull(AmbientSettings.coordinates("91,5")); assertNull(AmbientSettings.coordinates("12,181"));
        assertNull(AmbientSettings.coordinates("12,3,4")); assertNull(AmbientSettings.routeUrl("","3,4"));
        assertEquals("https://router.project-osrm.org/route/v1/driving/44.0,33.0;45.0,34.0?overview=false", AmbientSettings.routeUrl("33,44","34,45"));
        assertEquals("",AmbientSettings.origin(context));
    }
    @Test public void weatherUsesSunriseAndDoesNotReuseYesterdaysNightFlag() throws Exception {
        AmbientData.Weather weather=AmbientData.weather(new JSONObject("{current:{temperature_2m:23.6,weather_code:0,is_day:0},daily:{sunrise:['2026-10-23T06:13'],sunset:['2026-10-23T17:19']}}"));
        assertFalse(AmbientData.night(weather,ZonedDateTime.parse("2026-10-23T12:00:00+03:00[Asia/Baghdad]")));
        assertTrue(AmbientData.night(weather,ZonedDateTime.parse("2026-10-23T17:19:00+03:00[Asia/Baghdad]")));
        assertFalse(AmbientData.night(weather,ZonedDateTime.parse("2026-10-24T12:00:00+03:00[Asia/Baghdad]")));
        assertEquals("Rain",AmbientData.description(61)); assertEquals("Fog",AmbientData.description(45));
    }
    @Test public void malformedWeatherAndNoRouteAreNotInvented() throws Exception {
        try { AmbientData.weather(new JSONObject("{current:{weather_code:0}}")); fail(); } catch(org.json.JSONException expected) { }
        try { AmbientData.route(new JSONObject("{code:'NoRoute',routes:[]}")); fail(); } catch(org.json.JSONException expected) { }
        assertArrayEquals(new double[]{600,4200},AmbientData.route(new JSONObject("{code:'Ok',routes:[{duration:600,distance:4200}]}")),0.01);
    }
    @Test public void fixtureParsingSkipsBadEventsAndConvertsToBaghdad() throws Exception {
        JSONObject json=new JSONObject("{events:[{date:'2026-10-24T16:30Z',competitions:[{status:{type:{state:'pre'}},competitors:[{homeAway:'away',team:{shortDisplayName:'Away'}},{homeAway:'home',team:{shortDisplayName:'Home'}}]}]},{date:'invalid'}]}");
        List<AmbientData.Match> matches=AmbientData.matches(json); assertEquals(1,matches.size());
        assertTrue(AmbientData.matchLine(matches.get(0)).contains("19:30")); assertTrue(AmbientData.matchLine(matches.get(0)).startsWith("Home  v  Away"));
        assertEquals(1,AmbientData.relevant(matches,Instant.parse("2026-10-23T12:00:00Z")).size());
        assertTrue(AmbientData.relevant(matches,Instant.parse("2026-10-26T12:00:00Z")).isEmpty());
    }
    @Test public void liveGamesComeBeforeUpcomingAndOldResultsAreExcluded() {
        AmbientData.Match live=new AmbientData.Match(); live.start=Instant.parse("2026-10-23T19:00:00Z");live.state="in";
        AmbientData.Match old=new AmbientData.Match();old.start=Instant.parse("2026-10-21T19:00:00Z");old.state="post";
        AmbientData.Match next=new AmbientData.Match();next.start=Instant.parse("2026-10-24T19:00:00Z");next.state="pre";
        List<AmbientData.Match> relevant=AmbientData.relevant(Arrays.asList(old,next,live),Instant.parse("2026-10-23T20:00:00Z"));
        assertEquals(Arrays.asList(live,next),relevant);
    }
    @Test public void nextMatchdayHandlesCalendarOrderAndNoSchedule() throws Exception {
        assertEquals("20261025",AmbientData.nextDate(new JSONObject("{leagues:[{calendar:['2026-10-28T07:00Z','2026-10-21T07:00Z','2026-10-25T07:00Z']}] }"),LocalDate.of(2026,10,23)));
        assertNull(AmbientData.nextDate(new JSONObject("{}"),LocalDate.now()));
    }
    @Test public void busyAndTransitionalMediaStatesBlockIdle() {
        for(int s:new int[]{PlaybackState.STATE_PLAYING,PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING,PlaybackState.STATE_FAST_FORWARDING,PlaybackState.STATE_REWINDING,PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM}) assertTrue(PlaybackGuard.busyState(s));
        assertFalse(PlaybackGuard.busyState(PlaybackState.STATE_PAUSED)); assertFalse(PlaybackGuard.busyState(PlaybackState.STATE_STOPPED));
    }
    @Test public void cinemetaDeduplicatesAndRestrictsLinksToStremioIds() throws Exception {
        FeedLoader.Row row=StremioRows.parse(new JSONObject("{metas:[{id:'tt123',name:'Film',background:'https://example.com/a',imdbRating:'8.1',genres:['Drama','Crime']},{id:'tt123',name:'Duplicate'},{id:'intent://bad',name:'Unsafe'},{id:'tt456',name:'Second',background:'http://example.com/b'}]}"),"movie","top","Popular films");
        assertEquals(2,row.videos.size()); assertEquals("stremio:///detail/movie/tt123",row.videos.get(0).url);
        assertEquals(StremioRows.PACKAGE,row.videos.get(0).pkg); assertEquals("8.1",row.videos.get(0).rating); assertNull(row.videos.get(1).thumb);
    }
    @Test public void historyImportsWithoutOverwritingProgressAndIsBounded() {
        CinemanaHistory.remember(context,"Watched",null,123000,900000,true);
        CinemanaHistory.remember(context,"Watched",null,-1,0,false);
        FeedLoader.Row row=CinemanaHistory.row(context,null);assertEquals(1,row.videos.size());assertEquals(123000,row.videos.get(0).positionMs);
        for(int i=0;i<70;i++) CinemanaHistory.remember(context,"Title "+i,null,100,10000,true);
        assertEquals(50,CinemanaHistory.row(context,null).videos.size());
        CinemanaHistory.clear(context);assertEquals("cinemana-history://import",CinemanaHistory.row(context,null).videos.get(0).url);
    }
    @Test public void historyPreservesImportedOrderAndRejectsAmbiguousCoverMatches() throws Exception {
        CinemanaHistory.remember(context,"First",null,-1,0,false);
        CinemanaHistory.remember(context,"Second",null,-1,0,false);
        CinemanaHistory.remember(context,"Older page",null,-1,0,false);
        assertEquals("First",CinemanaHistory.row(context,null).videos.get(0).title);
        CinemanaHistory.remember(context,"Second",null,10000,900000,true);
        assertEquals("Second",CinemanaHistory.row(context,null).videos.get(0).title);
        assertNull(CinemanaHistory.uniqueMatch(new org.json.JSONArray("[{ar_title:'Carrie',year:'1976'},{en_title:'Carrie',year:'2013'}]"),"Carrie"));
        assertNotNull(CinemanaHistory.uniqueMatch(new org.json.JSONArray("[{ar_title:'Only match'}]"),"Only match"));
    }
    @Test public void historyExtrapolatesProgressAndKeepsItDuringRestore() {
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(20));
        long now=android.os.SystemClock.elapsedRealtime();
        MediaMetadata meta=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Movie").putLong(MediaMetadata.METADATA_KEY_DURATION,900000).build();
        CinemanaHistory.capture(context,meta,new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,10000,1,now-5000).build());
        assertEquals(15000,CinemanaHistory.row(context,null).videos.get(0).positionMs);
        PlaybackRecovery.prefs(context).edit().putLong("cinemana_restore_until",System.currentTimeMillis()+60000).commit();
        CinemanaHistory.capture(context,meta,new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0).build());
        assertEquals(15000,CinemanaHistory.row(context,null).videos.get(0).positionMs);
    }
    @Test public void historyOnlyRecordsActualPlaybackNotBufferingMetadata() {
        MediaMetadata metadata=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Movie").putLong(MediaMetadata.METADATA_KEY_DURATION,900000).build();
        CinemanaHistory.capture(context,metadata,new PlaybackState.Builder().setState(PlaybackState.STATE_BUFFERING,100,1).build());
        assertEquals("cinemana-history://import",CinemanaHistory.row(context,null).videos.get(0).url);
        CinemanaHistory.capture(context,metadata,new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,10000,1).build());
        assertEquals("Movie",CinemanaHistory.row(context,null).videos.get(0).title);assertEquals(10000,CinemanaHistory.row(context,null).videos.get(0).positionMs);
    }
}
