package xyz.etkn.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ScrollView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 在真实首页和 Android 焦点滚动路径上验收；只连接模拟器内的合成媒体服务。 */
public class HomeFocusTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private HomeActivity home;
    private File evidence;
    private void await(BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){boolean[] ready={false};instrumentation.runOnMainSync(()->ready[0]=condition.getAsBoolean());if(ready[0]){settle();return;}Thread.sleep(100);}fail("等待媒体库状态超时");}
    @SuppressWarnings("unchecked") private View library(String id){return ((java.util.Map<String,View>)field(HomeActivity.class,"libraryCards")).get(id);}
    private void call(String name,Class<?>[] types,Object... args){instrumentation.runOnMainSync(()->{try{java.lang.reflect.Method method=HomeActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(home,args);}catch(Exception error){throw new AssertionError(error);}});}

    @Test public void continueWatchingClickOpensPlayerDirectly()throws Exception{
        PlayerActivity playback=null;
        Instrumentation.ActivityMonitor details=instrumentation.addMonitor(DetailActivity.class.getName(),null,true);
        Instrumentation.ActivityMonitor player=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        try(Fixture server=new Fixture()){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"resume-fixture","直接续播测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->field(HomeActivity.class,"resumeFirst")!=null);
            instrumentation.runOnMainSync(()->((View)field(HomeActivity.class,"resumeFirst")).performClick());
            playback=(PlayerActivity)player.waitForActivityWithTimeout(8000);assertNotNull(playback);assertEquals("item-0",playback.getIntent().getStringExtra("id"));assertTrue(playback.getIntent().getBooleanExtra("resume",false));assertEquals(0,details.getHits());
        }finally{instrumentation.removeMonitor(details);instrumentation.removeMonitor(player);if(playback!=null){PlayerActivity current=playback;instrumentation.runOnMainSync(current::finish);}if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void heroLoadsWhileCardImagesAreBlockedAndResumeSitsAtBottom()throws Exception{
        try(Fixture server=new Fixture()){
            server.holdCardImages=true;
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"hero-fixture","首屏测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            assertTrue("背景图不能排在卡片图片之后",server.heroLoaded.await(8,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("卡片请求仍被阻塞",1L,server.cardGate.getCount());
            await(()->field(HomeActivity.class,"resumeFirst")!=null);
            instrumentation.runOnMainSync(()->((View)field(HomeActivity.class,"resumeFirst")).requestFocus());settle();
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"home-performance");assertTrue(evidence.isDirectory()||evidence.mkdirs());saveScreenshot("compact-resume.png");
            instrumentation.runOnMainSync(()->{
                View card=home.getCurrentFocus();assertEquals(home.dp(192),card.getWidth());assertEquals(home.dp(150),card.getHeight());
                int[] location=new int[2];card.getLocationOnScreen(location);int bottom=Math.round(location[1]+card.getHeight()*card.getScaleY());
                int screen=home.getWindow().getDecorView().getHeight();
                assertTrue("聚焦卡片须完整保留在底部安全区，bottom="+bottom+" screen="+screen,bottom<=screen&&screen-bottom<=home.dp(24));
            });
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void cachedHomeAppearsBeforeNetworkAndServerEventRefreshesWithoutStealingFocus()throws Exception{
        try(Fixture server=new Fixture()){
            SessionStore session=new SessionStore(instrumentation.getTargetContext());session.save(server.origin(),"cache-fixture","缓存测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->cards().size()==90&&home.getCurrentFocus()!=null);assertTrue(server.eventsConnected.await(5,java.util.concurrent.TimeUnit.SECONDS));
            instrumentation.runOnMainSync(()->home.finish());settle();server.holdResume=true;server.holdFirstLatest=true;
            long started=android.os.SystemClock.elapsedRealtime();
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->cards().size()==90&&home.getCurrentFocus()!=null);
            android.util.Log.i("HomePerformance","cached interactive ms="+(android.os.SystemClock.elapsedRealtime()-started));
            assertEquals("缓存应在续播请求结束前展示",1L,server.resumeGate.getCount());assertEquals(1L,server.latestGate.getCount());
            instrumentation.runOnMainSync(()->library("library-2").requestFocus());
            server.resumeGate.countDown();server.latestGate.countDown();
            await(()->((java.util.Set<?>)field(HomeActivity.class,"pendingShelves")).isEmpty());
            server.changed=true;server.updateGate.countDown();
            await(()->cards().stream().anyMatch(card->((android.widget.TextView)((ViewGroup)card).getChildAt(1)).getText().toString().contains("更新后的影片")));
            assertSame("后台刷新不能抢走其它片架的焦点",library("library-2"),home.getCurrentFocus());
            assertEquals("服务端更新后不能重复追加片架",90,cards().size());
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void slowLibraryDoesNotBlockOtherShelvesOrInitialFocus()throws Exception{
        try(Fixture server=new Fixture()){
            server.holdFirstLatest=true;server.holdResume=true;
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"slow-fixture","首页延迟测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->library("library-2")!=null&&home.getCurrentFocus()==library("library-0")&&cards().size()>=18);
            assertEquals("媒体库列表只能请求一次",1,server.viewCalls);assertEquals(1L,server.latestGate.getCount());assertEquals(1L,server.resumeGate.getCount());
            View focused=home.getCurrentFocus();server.latestGate.countDown();server.resumeGate.countDown();await(()->cards().size()==90);assertEquals("后续加载不可抢走焦点",focused,home.getCurrentFocus());
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void librarySortingPersistsIndependentlyAndBackRestoresTheLibraryCard()throws Exception{
        try(Fixture server=new Fixture()){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"sort-fixture","排序测试","synthetic-test-session");
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->library("library-2")!=null&&home.getCurrentFocus()!=null);
            instrumentation.runOnMainSync(()->library("library-2").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);
            call("setSort",new Class<?>[]{String.class,String.class},"DoubanRating","Descending");await(()->server.lastListingPath.contains("SortBy=DoubanRating"));
            key(KeyEvent.KEYCODE_BACK);await(()->library("library-2")!=null&&home.getCurrentFocus()==library("library-2"));
            instrumentation.runOnMainSync(()->library("library-3").performClick());await(()->"library-3".equals(field(HomeActivity.class,"parent"))&&posterGrid()!=null&&posterGrid().getChildCount()==60);
            assertEquals("SortName",field(HomeActivity.class,"sortBy"));call("setSort",new Class<?>[]{String.class,String.class},"ProductionYear","Ascending");
            await(()->server.lastListingPath.contains("SortBy=ProductionYear"));instrumentation.runOnMainSync(()->home.finish());settle();
            home=(HomeActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await(()->library("library-2")!=null);
            instrumentation.runOnMainSync(()->library("library-2").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);assertEquals("DoubanRating",field(HomeActivity.class,"sortBy"));assertEquals("Descending",field(HomeActivity.class,"sortOrder"));
            key(KeyEvent.KEYCODE_BACK);await(()->library("library-2")!=null);call("loadLibraries",new Class<?>[]{});await(()->"library".equals(field(HomeActivity.class,"mode"))&&library("library-3")!=null);
            instrumentation.runOnMainSync(()->library("library-3").performClick());await(()->posterGrid()!=null&&posterGrid().getChildCount()==60);assertEquals("ProductionYear",field(HomeActivity.class,"sortBy"));
            key(KeyEvent.KEYCODE_BACK);await(()->"library".equals(field(HomeActivity.class,"mode"))&&home.getCurrentFocus()==library("library-3"));
        }finally{if(home!=null)instrumentation.runOnMainSync(()->home.finish());new SessionStore(instrumentation.getTargetContext()).clear();}
    }

    @Test public void focusedArtworkRemainsVisibleAcrossShelvesAndRowEdges() throws Exception {
        try(Fixture server=new Fixture()) {
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"focus-regression");
            assertTrue(evidence.isDirectory()||evidence.mkdirs());
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"focus-fixture","焦点回归测试","synthetic-test-session");
            Intent intent=new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            home=(HomeActivity)instrumentation.startActivitySync(intent);
            long deadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<deadline){
                boolean[] ready={false};
                instrumentation.runOnMainSync(()->ready[0]=cards().size()==90&&home.getCurrentFocus()!=null);
                if(ready[0])break;
                Thread.sleep(100);
            }
            assertEquals("合成首页应加载完整的继续观看、媒体库及六个最新片架",90,cards().size());
            settle();check("initial");
            for(int i=0;i<7;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);check("down-"+i);}
            for(int i=0;i<11;i++){key(KeyEvent.KEYCODE_DPAD_RIGHT);check("right-"+i);}
            for(int i=0;i<11;i++){key(KeyEvent.KEYCODE_DPAD_LEFT);check("left-"+i);}
            for(int i=0;i<7;i++){key(KeyEvent.KEYCODE_DPAD_UP);check("up-"+i);}
            assertNull("首页请求应全部使用独立 TV API",server.unexpectedPath);
        } finally {
            if(home!=null)instrumentation.runOnMainSync(()->home.finish());
            new SessionStore(instrumentation.getTargetContext()).clear();
        }
    }

    @Test public void libraryPosterWallSortsAndLoadsMoreFromTheTop() throws Exception {
        try(Fixture server=new Fixture()) {
            evidence=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"poster-wall-regression");
            assertTrue(evidence.isDirectory()||evidence.mkdirs());
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"poster-fixture","海报墙测试","synthetic-test-session");
            Intent intent=new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            home=(HomeActivity)instrumentation.startActivitySync(intent);
            long deadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<deadline&&cards().size()<90)Thread.sleep(100);
            assertEquals(90,cards().size());
            View library=null;
            for(View card:cards())if(((android.widget.TextView)((ViewGroup)card).getChildAt(1)).getText().toString().startsWith("媒体库 ")){library=card;break;}
            assertNotNull("首页应提供媒体库卡片",library);
            View selected=library;
            ScrollView scroll=(ScrollView)field(HomeActivity.class,"homeScroll");
            instrumentation.runOnMainSync(()->{scroll.scrollTo(0,360);selected.performClick();});
            long itemsDeadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<itemsDeadline&&posterGrid().getChildCount()<60)Thread.sleep(100);
            assertEquals("首批海报应懒加载 60 项",60,posterGrid().getChildCount());
            assertEquals("进入海报墙应回到顶部",0,scroll.getScrollY());
            String meta=((android.widget.TextView)field(HomeActivity.class,"libraryMeta")).getText().toString();
            assertTrue(meta.contains("120 项")&&meta.contains("混合")&&meta.contains("实体库"));
            Rect header=new Rect();assertTrue(((View)field(HomeActivity.class,"libraryMeta")).getGlobalVisibleRect(header));
            saveScreenshot("poster-wall-entry.png");
            View sortButton=findSort((View)field(TvActivity.class,"content"));assertNotNull(sortButton);
            instrumentation.runOnMainSync(sortButton::performClick);settle();
            for(int i=0;i<14;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_CENTER);
            long sortDeadline=System.currentTimeMillis()+20000;
            while(System.currentTimeMillis()<sortDeadline&&!server.lastListingPath.contains("SortBy=DoubanRating"))Thread.sleep(100);
            assertTrue("豆瓣评分排序须走 TV 专属参数",server.lastListingPath.contains("SortBy=DoubanRating"));
            for(int i=0;i<5&&posterGrid().getChildCount()<120;i++){
                instrumentation.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));Thread.sleep(500);
            }
            long moreDeadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<moreDeadline&&posterGrid().getChildCount()<120)Thread.sleep(100);
            assertEquals("滚动到底部应追加下一批而不移除首批",120,posterGrid().getChildCount());
            int completedCalls=server.listingCalls;
            instrumentation.runOnMainSync(()->scroll.fullScroll(View.FOCUS_UP));settle();
            instrumentation.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));settle();
            assertEquals("全部加载后滚动不能重复请求",completedCalls,server.listingCalls);
            View first=posterGrid().getChildAt(0);instrumentation.runOnMainSync(()->{first.requestFocus();scroll.scrollTo(0,0);});settle();
            Rect visible=new Rect();((ViewGroup)first).getChildAt(0).getGlobalVisibleRect(visible);
            assertFalse("海报焦点图应完整显示",visible.isEmpty());
            View art=((ViewGroup)first).getChildAt(0);int[] location=new int[2];art.getLocationOnScreen(location);
            assertEquals("第一行聚焦海报顶部不能被裁切",location[1],visible.top,1);
            assertEquals("第一行聚焦海报高度必须完整",Math.round(art.getHeight()*first.getScaleY()),visible.height(),2);
            Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            try(FileOutputStream file=new FileOutputStream(new File(evidence,"poster-wall.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();
        } finally {
            if(home!=null)instrumentation.runOnMainSync(()->home.finish());
            new SessionStore(instrumentation.getTargetContext()).clear();
        }
    }

    private void saveScreenshot(String name)throws Exception{
        Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        try(FileOutputStream file=new FileOutputStream(new File(evidence,name))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();
    }
    @SuppressWarnings("unchecked") private List<View> cards(){
        try{java.lang.reflect.Field field=HomeActivity.class.getDeclaredField("homeCards");field.setAccessible(true);return (List<View>)field.get(home);}
        catch(Exception error){throw new AssertionError(error);}
    }
    private Object field(Class<?> owner,String name){try{java.lang.reflect.Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(home);}catch(Exception error){throw new AssertionError(error);}}
    private GridLayout posterGrid(){return (GridLayout)field(HomeActivity.class,"posterGrid");}
    private View findSort(View view){if(view instanceof android.widget.Button&&((android.widget.Button)view).getText().toString().startsWith("排序 ·"))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findSort(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void key(int key){instrumentation.sendKeyDownUpSync(key);settle();}
    private void settle(){instrumentation.waitForIdleSync();android.os.SystemClock.sleep(400);instrumentation.waitForIdleSync();}
    private void check(String step)throws Exception{
        String[] error={null};
        instrumentation.runOnMainSync(()->{
            View card=home.getCurrentFocus();
            if(!cards().contains(card)){error[0]="焦点离开片架："+card;return;}
            View artwork=((ViewGroup)card).getChildAt(0);
            int[] location=new int[2];artwork.getLocationOnScreen(location);
            Rect visible=new Rect();artwork.getGlobalVisibleRect(visible);
            int width=Math.round(artwork.getWidth()*card.getScaleX()),height=Math.round(artwork.getHeight()*card.getScaleY());
            String geometry=step+" card="+cards().indexOf(card)+" expected="+location[0]+","+location[1]+","+width+","+height+" visible="+visible;
            android.util.Log.i("HomeFocusTest",geometry);
            if(visible.top>location[1]+1||visible.left>location[0]+1||visible.width()<width-2||visible.height()<height-2){
                error[0]=geometry;
                View ancestor=artwork;
                while(ancestor!=null){
                    Rect bounds=new Rect();ancestor.getGlobalVisibleRect(bounds);
                    android.util.Log.i("HomeFocusTest","ancestor="+ancestor.getClass().getSimpleName()+" visible="+bounds+" size="+ancestor.getWidth()+"x"+ancestor.getHeight()+" padding="+ancestor.getPaddingLeft()+","+ancestor.getPaddingTop()+" scroll="+ancestor.getScrollX()+","+ancestor.getScrollY()+" clip="+(ancestor instanceof ViewGroup?((ViewGroup)ancestor).getClipChildren()+","+((ViewGroup)ancestor).getClipToPadding():"n/a"));
                    ancestor=ancestor.getParent() instanceof View?(View)ancestor.getParent():null;
                }
            }
        });
        if(error[0]!=null||step.equals("initial")||step.equals("down-2")||step.equals("up-3")){
            Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            try(FileOutputStream file=new FileOutputStream(new File(evidence,step+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}
            screenshot.recycle();
        }
        assertNull("聚焦图片被裁切："+step+" "+error[0],error[0]);
    }

    private static final class Fixture implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        final byte[] image;
        volatile boolean closed;
        volatile String unexpectedPath;
        volatile String lastListingPath="";
        volatile int listingCalls;
        volatile boolean holdFirstLatest,holdResume,holdCardImages,changed;
        final java.util.concurrent.CountDownLatch cardGate=new java.util.concurrent.CountDownLatch(1),heroLoaded=new java.util.concurrent.CountDownLatch(1),updateGate=new java.util.concurrent.CountDownLatch(1),eventsConnected=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch latestGate=new java.util.concurrent.CountDownLatch(1),resumeGate=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger views=new java.util.concurrent.atomic.AtomicInteger();
        volatile int viewCalls;
        Fixture()throws Exception{
            Bitmap bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(45,80,110));
            Paint paint=new Paint();paint.setColor(Color.rgb(225,150,70));canvas.drawRect(0,0,640,18,paint);canvas.drawRect(0,342,640,360,paint);
            paint.setColor(Color.WHITE);paint.setTextSize(40);canvas.drawText("TV Focus Fixture",100,190,paint);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes);bitmap.recycle();image=bytes.toByteArray();
            Thread thread=new Thread(()->{while(!closed){try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception ignored){}},"focus-request");worker.setDaemon(true);worker.start();}catch(Exception ignored){if(closed)return;}}},"focus-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        void serve(Socket client)throws Exception{
            try(client){
                BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
                String request=reader.readLine();if(request==null)return;String path=request.split(" ")[1];while(true){String line=reader.readLine();if(line==null||line.isEmpty())break;}
                if(path.endsWith("/Home/Events")){
                    OutputStream output=client.getOutputStream();output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: home\ndata: {\"revision\":\"v1\"}\n\n".getBytes(StandardCharsets.UTF_8));output.flush();eventsConnected.countDown();
                    updateGate.await(30,java.util.concurrent.TimeUnit.SECONDS);if(!closed){output.write("event: home\ndata: {\"revision\":\"v2\"}\n\n".getBytes(StandardCharsets.UTF_8));output.flush();while(!closed){Thread.sleep(1000);output.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));output.flush();}}return;
                }
                if(path.contains("maxWidth=1280"))heroLoaded.countDown();
                if(holdCardImages&&path.contains("/Images/")&&!path.contains("maxWidth=1280"))cardGate.await(20,java.util.concurrent.TimeUnit.SECONDS);
                if(path.endsWith("/Views"))viewCalls=views.incrementAndGet();
                if(holdFirstLatest&&path.contains("/Items/Latest?ParentId=library-0&"))latestGate.await(25,java.util.concurrent.TimeUnit.SECONDS);
                if(holdResume&&path.contains("/Items/Resume?"))resumeGate.await(25,java.util.concurrent.TimeUnit.SECONDS);
                if(!path.startsWith("/api/tv/"))unexpectedPath=path; if(path.contains("/Users/")&&path.contains("/Items?")){lastListingPath=path;listingCalls++;}
                boolean picture=path.contains("/Images/");
                byte[] body=picture?image:response(path).toString().getBytes(StandardCharsets.UTF_8);
                OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+(picture?"image/jpeg":"application/json")+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);
            }
        }
        JSONObject response(String path)throws Exception{
            if(path.contains("check_status"))return new JSONObject().put("user",new JSONObject().put("can_access_discovery",false));
            if(path.contains("/Users/Me"))return new JSONObject();
            if(path.contains("/Users/")&&path.contains("/Items?")){
                int start=0;java.util.regex.Matcher match=java.util.regex.Pattern.compile("[?&]StartIndex=(\\d+)").matcher(path);if(match.find())start=Integer.parseInt(match.group(1));
                JSONArray page=new JSONArray();for(int i=start;i<Math.min(start+60,120);i++)page.put(new JSONObject().put("Id","item-"+i).put("Name","海报影片 "+i).put("Type",i%2==0?"Series":"Movie").put("ProductionYear",2020+i%5).put("RecursiveItemCount",12).put("TMDbRating",7.1+(i%10)/10.0).put("DoubanRating",8.1+(i%10)/10.0).put("UserData",new JSONObject().put("UnplayedItemCount",3)).put("ImageTags",new JSONObject().put("Primary","poster")));
                return new JSONObject().put("Items",page).put("TotalRecordCount",120).put("EpisodeCountMode","unplayed");
            }
            boolean libraries=path.endsWith("/Views");JSONArray items=new JSONArray();
            for(int i=0;i<(libraries?6:12);i++)items.put(new JSONObject().put("Id",(libraries?"library-":"item-")+i).put("Name",(libraries?"媒体库 ":changed?"更新后的影片 ":"测试影片 ")+(i+1)).put("Type",libraries?"CollectionFolder":"Movie").put("CollectionType",libraries?"mixed":"").put("SourceType",libraries?"path":"").put("RunTimeTicks",1000).put("UserData",new JSONObject().put("PlaybackPositionTicks",300)));
            return new JSONObject().put("Items",items);
        }
        @Override public void close()throws IOException{closed=true;latestGate.countDown();resumeGate.countDown();cardGate.countDown();updateGate.countDown();socket.close();}
    }
}
