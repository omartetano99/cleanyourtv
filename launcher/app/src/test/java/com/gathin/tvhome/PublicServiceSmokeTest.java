package com.gathin.tvhome;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

/** Optional live contract check. Enable with TVHOME_LIVE_SMOKE=1; normal tests stay deterministic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class PublicServiceSmokeTest {
    @Test public void freeProvidersWorkThroughTheActualHttpClient() throws Exception {
        assumeTrue("1".equals(System.getenv("TVHOME_LIVE_SMOKE")));
        android.content.Context c=RuntimeEnvironment.getApplication();
        for(String type:new String[]{"movie","series"}) for(String catalogue:new String[]{"top","year"}) {
            String path=catalogue.equals("year")?catalogue+"/genre="+java.time.Year.now().getValue():catalogue;
            JsonCache.Result data=JsonCache.get(c,"https://v3-cinemeta.strem.io/catalog/"+type+"/"+path+".json",0);
            assertFalse("Stremio "+type+" "+catalogue,StremioRows.parse(data.json,type,catalogue,"Test").videos.isEmpty());
        }
        AmbientData.weather(JsonCache.get(c,"https://api.open-meteo.com/v1/forecast?latitude=33.3152&longitude=44.3661&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&timezone=Asia%2FBaghdad&forecast_days=1",0).json);
        for(String league:new String[]{"eng.1","esp.1","uefa.champions"})
            AmbientData.matches(JsonCache.get(c,"https://site.api.espn.com/apis/site/v2/sports/soccer/"+league+"/scoreboard",0).json);
        AmbientData.route(JsonCache.get(c,AmbientSettings.routeUrl("33.3152,44.3661","33.303040,44.371053"),0).json);
    }
}
