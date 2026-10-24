package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30, qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SmartHomeVisualTest {
    @Test public void ambientLayoutFitsTelevisionAndRenders() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        AmbientView view=new AmbientView(c);
        AmbientData.Weather weather=new AmbientData.Weather(); weather.temperature=23; weather.code=0;
        view.weather(weather,"Updated 23 Oct 22:00");
        view.league(0,"Home club  v  Away club · Sat 24 Oct, 19:30\nSecond club  v  Another club · Sat 24 Oct, 22:00\nUpdated 23 Oct 22:00");
        view.league(1,"Home club  v  Away club · Sat 24 Oct, 20:00\nUpdated 23 Oct 22:00");
        view.league(2,"Home club  v  Away club · Wed 28 Oct, 22:00\nUpdated 23 Oct 22:00");
        view.route("≈ 15 min · 10.0 km\nNo live traffic · Updated 23 Oct 22:00");
        render(view,"ambient-preview.png");
        assertTextFits(view);
    }
    @Test public void settingsHaveRemoteTargetsAndRender() throws Exception {
        try(org.robolectric.android.controller.ActivityController<HomeSettingsActivity> controller=Robolectric.buildActivity(HomeSettingsActivity.class).create().start()) {
            HomeSettingsActivity activity=controller.get(); View content=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            render(content,"settings-preview.png");
            assertTrue(countFocusable(content)>=12);
        }
    }
    @Test public void homeHeaderFitsAndFeaturedBannersStayAboveDock() throws Exception {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getSystemService(android.os.UserManager.class)).setUserUnlocked(true);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start()) {
            MainActivity a=controller.get();
            java.lang.reflect.Field resumed=MainActivity.class.getDeclaredField("resumed");resumed.setAccessible(true);resumed.set(a,true);
            FeedLoader.Row banners=new FeedLoader.Row();banners.key=CinemanaRows.KEY;banners.title="Cinemana 4K";
            for(int i=0;i<3;i++) {FeedLoader.Video v=new FeedLoader.Video();v.title="Preview film "+(i+1);v.pkg=CinemanaResume.PACKAGE;v.url="cinemana-banner://video/"+i;v.bannerIndex=i;v.rating="8.1";v.genres="Drama · Adventure";banners.videos.add(v);}
            java.lang.reflect.Field data=MainActivity.class.getDeclaredField("cinemanaRowData");data.setAccessible(true);data.set(a,java.util.Arrays.asList(banners,CinemanaHistory.row(a,null)));
            java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderContentRows");render.setAccessible(true);render.invoke(a);
            a.findViewById(R.id.topBar).animate().cancel();a.findViewById(R.id.topBar).setAlpha(1);
            View root=a.findViewById(R.id.root);render(root,"home-layout-preview.png");
            View identity=a.findViewById(R.id.identity),buttons=a.findViewById(R.id.topButtons),clock=a.findViewById(R.id.headerClock);
            assertTrue("Header overlaps identity",buttons.getLeft()>=identity.getRight());
            assertTrue("Header overlaps clock",buttons.getRight()<=clock.getLeft());
            assertTrue(a.findViewById(R.id.cinemanaShelf).getBottom()<=a.findViewById(R.id.appsCaption).getTop());
        }
    }
    private int countFocusable(View view) { int count=view.isFocusable()?1:0; if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) count+=countFocusable(((ViewGroup)view).getChildAt(i)); return count; }
    private void assertTextFits(View view) {
        if(view instanceof TextView) { TextView t=(TextView)view; if(t.getLayout()!=null) assertTrue("Text clipped: "+t.getText(),t.getLayout().getHeight()<=t.getHeight()-t.getPaddingTop()-t.getPaddingBottom()); }
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View child=((ViewGroup)view).getChildAt(i); assertTrue("Vertical overflow "+child,child.getBottom()<=view.getHeight()); assertTextFits(child); }
    }
    private void render(View view,String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));view.layout(0,0,1920,1080);
        Bitmap bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        File dir=new File("build/reports/smart-home");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);} bitmap.recycle();
    }
}
