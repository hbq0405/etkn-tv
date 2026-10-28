package xyz.etkn.tv;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Only the in-emulator synthetic service is used, including real Media3 track selection. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class DetailFlowTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private DetailActivity detail;
    private PlayerActivity playback;
    private void onMain(Runnable action){instrumentation.runOnMainSync(action);}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){boolean[] value={false};onMain(()->value[0]=condition.getAsBoolean());if(value[0])return;Thread.sleep(100);}fail("Timed out waiting for detail/playback state");}
    private void settle(){instrumentation.waitForIdleSync();SystemClock.sleep(450);instrumentation.waitForIdleSync();}
    private Object field(Object owner,String name){try{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    private List<View> descendants(View root){List<View> views=new ArrayList<>();views.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)views.addAll(descendants(((ViewGroup)root).getChildAt(i)));return views;}
    private Button button(String prefix){for(View view:descendants(detail.content))if(view instanceof Button&&((Button)view).getText().toString().startsWith(prefix))return (Button)view;return null;}
    private void open(Fixture server,String id)throws Exception{
        new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"detail-fixture","详情测试","synthetic-test-session");
        detail=(DetailActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),DetailActivity.class).putExtra("id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->button("播放")!=null&&button("播放").isEnabled()||button("继续播放")!=null);settle();
        assertTrue("初始焦点应为播放/继续播放",detail.getCurrentFocus()==field(detail,"playButton"));
        assertEquals(0,((ScrollView)field(detail,"scroll")).getScrollY());
        assertNull(button("返回"));assertNull(button("媒体详情"));
    }
    private void choose(String prefix,int down){Button button=button(prefix);assertNotNull(button);onMain(button::performClick);settle();for(int i=0;i<down;i++)instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();}
    private void play()throws Exception{
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);
        onMain(()->((Button)field(detail,"playButton")).performClick());playback=(PlayerActivity)monitor.waitForActivityWithTimeout(10000);instrumentation.removeMonitor(monitor);assertNotNull(playback);
        await(()->field(playback,"player")!=null&&((ExoPlayer)field(playback,"player")).getPlaybackState()==Player.STATE_READY);
        onMain(()->{ExoPlayer player=(ExoPlayer)field(playback,"player");player.pause();for(Tracks.Group group:player.getCurrentTracks().getGroups())for(int i=0;i<group.length;i++){Format format=group.getTrackFormat(i);android.util.Log.i("DetailTracks","type="+group.getType()+" id="+format.id+" label="+format.label+" selected="+group.isTrackSelected(i)+" supported="+group.isTrackSupported(i));}});settle();
    }
    private String selected(int type){ExoPlayer player=(ExoPlayer)field(playback,"player");for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i))return PlaybackSelection.localTrackId(group.getTrackFormat(i).id);return "";}
    private void closePlayer(){if(playback!=null){onMain(()->playback.finish());settle();playback=null;}}
    private void cleanup(){closePlayer();if(detail!=null){onMain(()->detail.finish());detail=null;}new SessionStore(instrumentation.getTargetContext()).clear();}
    private void screenshot(String name)throws Exception{File dir=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"detail-regression");assertTrue(dir.isDirectory()||dir.mkdirs());Bitmap image=instrumentation.getUiAutomation().takeScreenshot();try(FileOutputStream stream=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}image.recycle();}

    @Test public void movieChoicesReachRealPlayerAndSurviveSourceReordering()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie");assertEquals(View.VISIBLE,button("版本").getVisibility());
            assertTrue(descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&view.isShown()&&((TextView)view).getText().toString().equals("合成电影")));
            screenshot("movie.png");choose("版本",1);choose("音轨",2);choose("字幕",3);
            assertTrue(button("音轨").getText().toString().contains("音轨二"));assertTrue(button("字幕").getText().toString().contains("内封二"));screenshot("movie-choices.png");
            play();assertEquals("source-b",playback.getIntent().getStringExtra("sourceId"));assertEquals(4,playback.getIntent().getIntExtra("audioIndex",-1));assertEquals(8,playback.getIntent().getIntExtra("subtitleIndex",-1));
            await(()->"2".equals(selected(C.TRACK_TYPE_AUDIO))&&"4".equals(selected(C.TRACK_TYPE_TEXT)));
            assertEquals("source-b",((JSONObject)field(playback,"source")).getString("Id"));closePlayer();
            assertNull("客户端只能访问 TV 接口",server.unexpectedPath);assertTrue(server.reports>0);
        }finally{cleanup();}
    }
    @Test public void singleVersionHidesSelectorAndExternalSubtitleOrOffTakeEffect()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"movie-single");assertEquals(View.GONE,button("版本").getVisibility());
            await(()->descendants(detail.content).stream().anyMatch(view->view instanceof TextView&&((TextView)view).getText().toString().equals("合成电影")&&view.getVisibility()==View.GONE));
            choose("字幕",4);play();await(()->PlaybackSelection.externalId(12).equals(selected(C.TRACK_TYPE_TEXT)));closePlayer();
            // The checked external subtitle is fourth; move up to the off entry.
            onMain(()->button("字幕").performClick());settle();for(int i=0;i<3;i++)instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);settle();
            play();onMain(()->assertTrue(((ExoPlayer)field(playback,"player")).getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)));
        }finally{cleanup();}
    }
    @Test public void singleSeasonUsesNumberMatrixAndKeepsFocusedEpisodePreviewVisible()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"series-one");assertNull(button("第 1 季"));assertTrue("分集必须显式请求版本和轨道字段",((JSONArray)field(detail,"sources")).length()>0);assertEquals("继续播放",((Button)field(detail,"playButton")).getText().toString());screenshot("single-season-entry.png");
            Button episode=button("第 2 集");assertNotNull(episode);onMain(episode::requestFocus);settle();
            assertTrue(((TextView)field(detail,"episodeTitle")).getText().toString().contains("故事 2"));assertEquals("第 2 集的合成简介",((TextView)field(detail,"episodeOverview")).getText().toString());
            Rect preview=new Rect();assertTrue(((View)field(detail,"preview")).getGlobalVisibleRect(preview));assertTrue(preview.height()>=detail.dp(140));
            assertTrue(server.imagePaths.stream().anyMatch(path->path.contains("/Items/episode-1-2/Images/Primary")));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle();assertEquals(button("第 3 集"),detail.getCurrentFocus());
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);settle();int columns=Math.max(3,(detail.getResources().getConfiguration().screenWidthDp-84)/112);assertEquals(button("第 "+(3+columns)+" 集"),detail.getCurrentFocus());
            Button last=button("第 24 集");onMain(last::requestFocus);settle();assertTrue(last.getGlobalVisibleRect(new Rect()));assertTrue(((View)field(detail,"preview")).getGlobalVisibleRect(preview));assertTrue(preview.height()>=detail.dp(140));screenshot("single-season-matrix.png");
        }finally{cleanup();}
    }
    @Test public void multipleSeasonsRemainSelectableAndRefreshMatrix()throws Exception{
        try(Fixture server=new Fixture()){
            open(server,"series-many");assertNotNull(button("第 1 季"));assertNotNull(button("第 2 季"));assertNull(button("第 24 集"));
            onMain(()->button("第 1 季").performClick());settle();assertNotNull(button("第 24 集"));onMain(()->button("第 1 集").requestFocus());settle();screenshot("multi-season.png");
            onMain(()->button("第 2 季").performClick());settle();assertNull(button("第 24 集"));
            assertTrue(((TextView)field(detail,"episodeTitle")).getText().toString().contains("故事 3"));
        }finally{cleanup();}
    }

    private final class Fixture implements AutoCloseable{
        final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        final byte[] image,media;
        final List<String> imagePaths=Collections.synchronizedList(new ArrayList<>());
        volatile boolean closed;volatile String unexpectedPath;volatile int reports;
        Fixture()throws Exception{
            Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(38,60,84));Paint paint=new Paint();paint.setColor(Color.rgb(95,140,155));canvas.drawCircle(750,180,220,paint);paint.setColor(Color.rgb(205,155,85));canvas.drawRect(620,300,900,500,paint);ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,output);bitmap.recycle();image=output.toByteArray();
            try(InputStream stream=instrumentation.getContext().getAssets().open("detail-tracks.mkv")){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)data.write(buffer,0,n);media=data.toByteArray();}
            Thread thread=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception error){if(!closed)android.util.Log.e("DetailFixture","request",error);}});worker.setDaemon(true);worker.start();}catch(IOException ignored){if(closed)return;}},"detail-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject stream(String type,int index,String title)throws Exception{return new JSONObject().put("Type",type).put("Index",index).put("DisplayTitle",title).put("Language","chi").put("Codec",type.equals("Audio")?"pcm_s16le":"subrip");}
        JSONArray sources(boolean multi)throws Exception{
            JSONArray streams=new JSONArray().put(stream("Audio",0,"音轨一").put("IsDefault",true)).put(stream("Audio",4,"音轨二")).put(stream("Subtitle",7,"内封一")).put(stream("Subtitle",8,"内封二")).put(stream("Subtitle",12,"外挂字幕").put("IsExternal",true).put("Codec","webvtt").put("DeliveryUrl","/api/tv/subtitle.vtt"));
            JSONArray sources=new JSONArray().put(new JSONObject().put("Id","source-a").put("Name","1080P").put("DirectStreamUrl","/api/tv/media.mkv").put("MediaStreams",streams).put("DefaultSubtitleStreamIndex",-1));
            if(multi)sources.put(new JSONObject(sources.getJSONObject(0).toString()).put("Id","source-b").put("Name","4K 版本"));return sources;
        }
        JSONObject base(String id,String type)throws Exception{return new JSONObject().put("Id",id).put("Type",type).put("Name",type.equals("Movie")?"合成电影":"合成剧集").put("Overview","这是用于详情页验收的合成简介。遥控器可选择播放版本、音轨、字幕，也可浏览演员与分集预览。").put("ProductionYear",2026).put("TMDbRating",7.6).put("DoubanRating",8.7).put("OfficialRating","PG-13").put("RunTimeTicks",95L*600000000).put("Genres",new JSONArray().put("剧情").put("悬疑")).put("Tags",new JSONArray().put("悬疑").put("经典")).put("People",new JSONArray().put(new JSONObject().put("Id","person-1").put("Name","合成演员").put("Type","Actor").put("Role","主演")));}
        JSONObject response(String path)throws Exception{
            if(path.contains("/Sessions/")){reports++;return new JSONObject();}
            if(path.contains("/PlaybackInfo")){JSONArray original=sources(!path.contains("movie-single")),reversed=new JSONArray();for(int i=original.length()-1;i>=0;i--)reversed.put(original.get(i));return new JSONObject().put("MediaSources",reversed).put("PlaySessionId","synthetic-play");}
            if(path.contains("/Seasons")){JSONArray seasons=new JSONArray().put(new JSONObject().put("Id","season-1").put("Name","第 1 季"));if(path.contains("series-many"))seasons.put(new JSONObject().put("Id","season-2").put("Name","第 2 季"));return new JSONObject().put("Items",seasons);}
            if(path.contains("/Episodes")){JSONArray episodes=new JSONArray();int seasons=path.contains("series-many")?2:1;for(int s=1;s<=seasons;s++)for(int e=1;e<=(s==1?24:12);e++)episodes.put(new JSONObject().put("Id","episode-"+s+"-"+e).put("Type","Episode").put("Name","故事 "+e).put("SeriesName","合成剧集").put("SeasonId","season-"+s).put("ParentIndexNumber",s).put("IndexNumber",e).put("Overview","第 "+e+" 集的合成简介").put("ParentBackdropItemId","series-parent").put("MediaSources",sources(false)).put("UserData",new JSONObject().put("PlaybackPositionTicks",s==seasons&&e==3?30000000:0)));if(!path.contains("Fields=MediaSources,MediaStreams,Overview"))for(int i=0;i<episodes.length();i++)episodes.getJSONObject(i).remove("MediaSources");return new JSONObject().put("Items",episodes);}
            String id=path.substring(path.lastIndexOf('/')+1).split("\\?")[0];boolean series=id.startsWith("series");JSONObject item=base(id,series?"Series":"Movie");if(!series)item.put("MediaSources",sources(!id.equals("movie-single")));return item;
        }
        void serve(Socket client)throws Exception{
            try(client){BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));String line=reader.readLine();if(line==null)return;String path=line.split(" ")[1];int range=0;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes="))range=Integer.parseInt(line.substring(13).split("-")[0]);
                if(!path.startsWith("/api/tv/"))unexpectedPath=path;
                boolean picture=path.contains("/Images/"),video=path.startsWith("/api/tv/media.mkv"),subtitle=path.contains("subtitle.vtt"),missing=path.contains("/Images/Logo")&&!path.contains("movie-single");if(picture)imagePaths.add(path);
                byte[] body=video?media:subtitle?"WEBVTT\n\n00:00:00.000 --> 00:00:20.000\nExternal synthetic subtitle\n".getBytes(StandardCharsets.UTF_8):picture?image:response(path).toString().getBytes(StandardCharsets.UTF_8);
                String code=missing?"404 Not Found":range>0?"206 Partial Content":"200 OK";int start=video?range:0;
                String headers="HTTP/1.1 "+code+"\r\nContent-Type: "+(video?"video/x-matroska":subtitle?"text/vtt":picture?"image/jpeg":"application/json")+"\r\nContent-Length: "+(body.length-start)+"\r\n"+(range>0?"Content-Range: bytes "+start+"-"+(body.length-1)+"/"+body.length+"\r\n":"")+"Connection: close\r\n\r\n";
                OutputStream output=client.getOutputStream();output.write(headers.getBytes(StandardCharsets.US_ASCII));output.write(body,start,body.length-start);
            }
        }
        @Override public void close()throws IOException{closed=true;socket.close();}
    }
}
