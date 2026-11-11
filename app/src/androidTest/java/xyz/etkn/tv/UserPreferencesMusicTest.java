package xyz.etkn.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Loopback fixture only; run in a separately signed application data area. */
public class UserPreferencesMusicTest {
    final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    final Context context=instrumentation.getTargetContext();
    TvActivity host;
    void main(Runnable action){instrumentation.runOnMainSync(action);}
    void await(BooleanSupplier ready)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){boolean[] result={false};main(()->result[0]=ready.getAsBoolean());if(result[0])return;SystemClock.sleep(80);}fail("Timed out waiting for fixture behavior");}
    void setup(Fixture fixture)throws Exception{
        context.getSharedPreferences("session",0).edit().clear().commit();
        context.getSharedPreferences("appearance",0).edit().clear().commit();
        SessionStore.endTemporarySession();LoginGate.satisfy();TvAppearance.current=new JSONObject();TvAppearance.saving=false;
        SessionStore session=new SessionStore(context);session.saveSession(fixture.origin(),"fixture","音乐测试","loopback-only",SessionStore.PERSIST_FULL);
        context.getSharedPreferences("session",0).edit().putBoolean("mediaEntryVerified",true).commit();
    }
    void close(){if(host!=null)main(()->{MusicPlayback.stop();host.finish();});SessionStore.endTemporarySession();}
    void call(String name,Class<?>[] types,Object... args)throws Exception{java.lang.reflect.Method method=host.getClass().getDeclaredMethod(name,types);method.setAccessible(true);main(()->{try{method.invoke(host,args);}catch(Exception error){throw new RuntimeException(error);}});}
    Object field(String name)throws Exception{java.lang.reflect.Field field=host.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(host);}
    void screenshot(String name)throws Exception{SystemClock.sleep(700);File directory=new File(context.getExternalFilesDir(null),"preferences-music");assertTrue(directory.isDirectory()||directory.mkdirs());android.graphics.Bitmap frame=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(frame);try(FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))){frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}frame.recycle();}

    @Test public void sharedLockExcludesAdministratorChoiceAndKeepsCurrentSession()throws Exception{
        try(Fixture fixture=new Fixture()){
            setup(fixture);host=(SettingsActivity)instrumentation.startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->TvAppearance.current.optInt("card_size",-1)==2);
            fixture.preferences.put("card_size",4).put("shadow_color","#bb3366");
            await(()->new TvPreferences(context,host.api).imageSize()==4);
            call("saveClient",new Class<?>[]{String.class,Object.class},"shadow_color","#123456");
            await(()->"#123456".equals(TvAppearance.current.optString("shadow_color"))&&!TvAppearance.saving);
            assertEquals("#123456",fixture.preferences.getString("shadow_color"));
            call("changeThemeLock",new Class<?>[]{boolean.class},true);
            await(()->TvAppearance.locked(context,fixture.origin())&&!TvAppearance.saving);
            fixture.preferences.put("card_size",0).put("shadow_color","#ff0000");
            fixture.remember=false;
            await(()->!host.api.session.rememberAdmin(fixture.origin(),"fixture")&&host.api.session.persist()==SessionStore.PERSIST_ACCOUNT);
            assertEquals("同步关闭保存不能退出当前会话","loopback-only",host.api.session.token());
            assertEquals(4,new TvPreferences(context,host.api).imageSize());
            List<?> controls=(List<?>)field("sharedControls");for(Object control:controls)assertFalse(((View)control).isEnabled());
            host.api.get("/api/tv/Users/fixture/Items/Latest");
            assertTrue(fixture.lockedHeader.contains("jpn"));
            call("saveClient",new Class<?>[]{String.class,Object.class},"card_size",1);
            assertEquals(0,fixture.preferences.getInt("card_size"));
            screenshot("locked-preferences");
            call("changeThemeLock",new Class<?>[]{boolean.class},false);
            await(()->!TvAppearance.locked(context,fixture.origin())&&new TvPreferences(context,host.api).imageSize()==0);
            // Reuse the pre-existing password gate after clearing only persisted credentials.
            assertEquals(SessionStore.PERSIST_ACCOUNT,host.api.savedAccountPolicy("fixture"));
            SessionStore.endTemporarySession();
            try{host.api.session.activate("fixture");fail("Password should be required");}catch(Exception expected){assertTrue(expected.getMessage().contains("密码"));}
        }finally{close();}
    }

    @Test public void musicPagePlaysAudioPaginatesAndReportsCompletion()throws Exception{
        try(Fixture fixture=new Fixture()){
            setup(fixture);host=(MusicActivity)instrumentation.startActivitySync(new Intent(context,MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->find(host.content,"最近听的")!=null&&find(host.content,"最近常听")!=null);
            screenshot("music-recommended");
            List<JSONObject> tracks=List.of(fixture.track(1),fixture.track(2));
            MusicPlayback[] playback={null};main(()->{playback[0]=MusicPlayback.get(context);playback[0].play(tracks,tracks.get(0),"/api/tv/Users/fixture/Music?Tab=songs",3);});
            host=(MusicPlayerActivity)instrumentation.startActivitySync(new Intent(context,MusicPlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->playback[0].playing()&&playback[0].position()>250&&playback[0].queue.items.size()==3);
            assertTrue(fixture.audioRequests>0);
            View pause=(View)field("pause"),mode=(View)field("mode");Rect rect=new Rect();
            assertTrue(pause.getGlobalVisibleRect(rect));assertEquals(pause.getHeight(),rect.height());
            assertTrue(mode.getGlobalVisibleRect(rect));assertEquals("播放选项必须完整可见",mode.getHeight(),rect.height());
            main(pause::requestFocus);instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            await(()->!playback[0].playing());instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            await(()->playback[0].playing());
            screenshot("music-player");
            main(()->playback[0].seek(7600));
            await(()->playback[0].queue.index==1&&playback[0].playing());
            await(()->fixture.completed>0);
            main(()->playback[0].remove(0));assertEquals(0,playback[0].queue.index);
            main(()->playback[0].mode(3));main(()->playback[0].seek(7600));
            await(()->fixture.completed>=2&&playback[0].position()<5000&&playback[0].playing());
            assertEquals(0,playback[0].queue.index);
            main(MusicPlayback::stop);await(()->fixture.stops>=3);
        }finally{close();}
    }
    TextView find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView result=find(group.getChildAt(i),text);if(result!=null)return result;}}return null;}

    static final class Fixture implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));
        final ExecutorService workers=Executors.newCachedThreadPool();
        volatile boolean running=true,remember=true;volatile int audioRequests,completed,stops;volatile String lockedHeader="";
        final JSONObject preferences=new JSONObject("{\"revision\":\"fixture\",\"configured\":true,\"mode\":\"dark\",\"theme_color\":\"#55c7b0\",\"shadow_color\":\"#000000\",\"shadow_strength\":1,\"background_blur\":0,\"glass_opacity\":0.7,\"background_url\":\"\",\"card_size\":2,\"show_home_libraries\":true,\"home_carousel\":true,\"skip_mode\":0,\"playback_preferences\":{\"preferences\":{\"audio_lang_priority\":[\"jpn\"],\"subtitle_priority\":[\"chs\"]},\"global_preferences\":{},\"effective_preferences\":{\"audio_lang_priority\":[\"jpn\"],\"subtitle_priority\":[\"chs\"]},\"hide_watched_latest\":true}}");
        final byte[] audio=tone();
        Fixture()throws Exception{workers.execute(()->{while(running)try{Socket client=socket.accept();workers.execute(()->serve(client));}catch(IOException ignored){}});}
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject track(int id)throws JSONException{return new JSONObject().put("Id","item-"+id).put("Type","Audio").put("Name","歌曲 "+id).put("Album","测试专辑").put("AlbumKey","album").put("Artists",new JSONArray().put("测试歌手")).put("RunTimeTicks",80000000);}
        void serve(Socket client){try(client){BufferedInputStream input=new BufferedInputStream(client.getInputStream());ByteArrayOutputStream headerBytes=new ByteArrayOutputStream();int current,tail=0;while((current=input.read())!=-1){headerBytes.write(current);tail=(tail<<8)|current;if(tail==0x0d0a0d0a)break;}
            String headers=headerBytes.toString(StandardCharsets.UTF_8.name()),first=headers.split("\r\n")[0];String[] request=first.split(" ");String method=request[0],path=request[1];int length=0;for(String header:headers.split("\r\n")){if(header.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.split(":",2)[1].trim());if(header.toLowerCase(Locale.ROOT).startsWith("x-etkn-locked-preferences:"))lockedHeader=header.split(":",2)[1].trim();}
            String body=new String(input.readNBytes(length),StandardCharsets.UTF_8);JSONObject result=new JSONObject();byte[] data;String type="application/json";int status=200,start=0,end=audio.length-1;
            if(path.startsWith("/audio.wav")){audioRequests++;type="audio/wav";for(String header:headers.split("\r\n"))if(header.toLowerCase(Locale.ROOT).startsWith("range:")){String[] range=header.split("=",2)[1].trim().split("-",-1);start=Integer.parseInt(range[0]);if(!range[1].isEmpty())end=Math.min(end,Integer.parseInt(range[1]));status=206;}data=Arrays.copyOfRange(audio,start,end+1);}
            else{
                if(path.startsWith("/api/tv/Users/Me/ClientPreferences")){if(method.equals("PUT")){JSONObject update=new JSONObject(body);Iterator<String> keys=update.keys();while(keys.hasNext()){String key=keys.next();preferences.put(key,update.get(key));}}result=new JSONObject(preferences.toString());}
                else if(path.startsWith("/api/tv/Users/Me/AccountPreferences")){if(method.equals("PUT"))remember=new JSONObject(body).getBoolean("remember_admin_account");result.put("remember_admin_account",remember);}
                else if(path.equals("/api/tv/Users/Me"))result.put("Id","fixture").put("Name","音乐测试").put("Policy",new JSONObject().put("IsAdministrator",true));
                else if(path.contains("PlaybackInfo"))result.put("MediaSources",new JSONArray().put(new JSONObject().put("Id","version-1").put("DirectStreamUrl","/audio.wav").put("Container","wav"))).put("PlaySessionId",UUID.randomUUID().toString());
                else if(path.contains("/Sessions/Playing")){if(path.endsWith("/Stopped")){stops++;if(new JSONObject(body).optBoolean("PlayedToCompletion"))completed++;}}
                else if(path.contains("/Music?")){if(path.contains("Tab=recommended"))result.put("Latest",new JSONArray()).put("Recent",new JSONArray().put(track(1))).put("Frequent",new JSONArray().put(track(2)));else result.put("Items",new JSONArray().put(track(3))).put("TotalRecordCount",3);}
                else if(path.contains("/Images/")){status=404;}
                data=result.toString().getBytes(StandardCharsets.UTF_8);
            }
            String response="HTTP/1.1 "+status+" OK\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n"+(status==206?"Content-Range: bytes "+start+"-"+end+"/"+audio.length+"\r\n":"")+"\r\n";OutputStream output=client.getOutputStream();output.write(response.getBytes(StandardCharsets.UTF_8));output.write(data);output.flush();
        }catch(Exception ignored){}}
        static byte[] tone(){int count=16000*8;ByteBuffer wav=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN);wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+count*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(count*2);for(int i=0;i<count;i++)wav.putShort((short)(Math.sin(i*2*Math.PI*440/16000)*1200));return wav.array();}
        public void close()throws Exception{running=false;socket.close();workers.shutdownNow();}
    }
}
