package xyz.etkn.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Real Android views and a loopback-only service; no production requests or acquisitions. */
public class DiscoveryFlowTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private DiscoveryActivity activity;
    private Fixture server;
    private SharedPreferences session;
    private Map<String,?> savedSession;

    @Before public void open()throws Exception{
        server=new Fixture();
        Context context=instrumentation.getTargetContext();
        session=context.getSharedPreferences("session",Context.MODE_PRIVATE);savedSession=session.getAll();
        assertTrue(session.edit().clear().commit());
        new SessionStore(context).save(server.origin(),"discovery-fixture","探索测试","synthetic-session");
        activity=(DiscoveryActivity)instrumentation.startActivitySync(new Intent(context,DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->field("posterGrid")!=null&&((GridLayout)field("posterGrid")).getChildCount()==1);
    }
    @After public void close()throws Exception{
        if(server!=null)server.release();
        if(activity!=null)onMain(activity::finish);
        instrumentation.waitForIdleSync();
        if(server!=null)server.close();
        if(session!=null&&savedSession!=null){
            SharedPreferences.Editor editor=session.edit().clear();
            for(Map.Entry<String,?> entry:savedSession.entrySet()){
                Object value=entry.getValue();
                if(value instanceof String)editor.putString(entry.getKey(),(String)value);
                else if(value instanceof Boolean)editor.putBoolean(entry.getKey(),(Boolean)value);
                else if(value instanceof Long)editor.putLong(entry.getKey(),(Long)value);
                else if(value instanceof Integer)editor.putInt(entry.getKey(),(Integer)value);
                else if(value instanceof Float)editor.putFloat(entry.getKey(),(Float)value);
                else if(value instanceof Set)editor.putStringSet(entry.getKey(),new HashSet<>((Set<String>)value));
            }
            assertTrue(editor.commit());
        }
    }
    @Test public void seasonsAttachOnceOnCachedAndNetworkDetails()throws Exception{
        JSONObject series=server.item("tv");
        onMain(()->{
            ((Map<String,JSONObject>)field("seriesCache")).put("10",server.seasons());
            invoke("enterDetail",new Class<?>[]{JSONObject.class},series);
            assertEquals(2,seasonRows());
            assertEquals(activity.dp(6),((ViewGroup.MarginLayoutParams)seasonRow(1).getLayoutParams()).topMargin);
            seasonRow(1).performClick();
            assertEquals(1,field("season"));
            activity.onBackPressed();
            assertEquals(2,seasonRows());
            activity.onBackPressed();
            ((Map<?,?>)field("seriesCache")).clear();
            invoke("enterDetail",new Class<?>[]{JSONObject.class},series);
        });
        await(()->seasonRows()==2);
        assertEquals(1,server.seasonRequests);
    }
    @Test public void movieResourcesRenderAllProvidersAndReopenWithoutDuplicateViews()throws Exception{
        for(int pass=0;pass<3;pass++){
            onMain(()->invoke("enterDetail",new Class<?>[]{JSONObject.class},server.item("movie")));
            onMain(()->invoke("enterCloud",new Class<?>[0]));
            await(()->activity.status.getText().toString().startsWith("搜索完成"));
            onMain(()->{
                assertEquals(4,activity.content.getChildCount());
                assertEquals(3,((ViewGroup)activity.content.getChildAt(1)).getChildCount());
                assertEquals(2,((ViewGroup)activity.content.getChildAt(2)).getChildCount());
                assertEquals(2,((ViewGroup)activity.content.getChildAt(3)).getChildCount());
                assertEquals(0,seasonRows());
                activity.onBackPressed();
                assertEquals("10",((JSONObject)field("selected")).optString("id"));
                activity.onBackPressed();
                assertSame(field("browseView"),activity.content.getChildAt(0));
            });
        }
        assertEquals(9,server.cloudRequests);
    }
    @Test public void lateMovieCallbacksCannotWriteIntoAnotherPage()throws Exception{
        server.delayCast=true;
        onMain(()->invoke("enterDetail",new Class<?>[]{JSONObject.class},server.item("movie")));
        assertTrue(server.castRequested.await(5,TimeUnit.SECONDS));
        onMain(()->invoke("enterCloud",new Class<?>[0]));
        await(()->activity.status.getText().toString().startsWith("搜索完成"));
        server.castGate.countDown();
        assertTrue(server.castSent.await(5,TimeUnit.SECONDS));
        // Let the real async callback run, then inspect the current view hierarchy.
        SystemClock.sleep(350);instrumentation.waitForIdleSync();
        onMain(()->assertEquals("迟到的演员回调不得追加到资源页",4,activity.content.getChildCount()));
        server.delayCloud=true;
        onMain(()->{activity.onBackPressed();invoke("enterCloud",new Class<?>[0]);});
        assertTrue(server.cloudRequested.await(5,TimeUnit.SECONDS));
        onMain(()->{activity.onBackPressed();activity.status.setText("详情页状态");});
        server.cloudGate.countDown();
        await(()->server.delayedCloudSent==3);
        SystemClock.sleep(350);instrumentation.waitForIdleSync();
        onMain(()->assertEquals("详情页状态",activity.status.getText().toString()));
    }
    private void onMain(Runnable action){
        Throwable[] failure={null};
        instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError("探索页面操作失败",failure[0]);
    }
    private Object field(String name){try{java.lang.reflect.Field f=DiscoveryActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception error){throw new AssertionError(error);}}
    private void invoke(String name,Class<?>[] types,Object...args){try{java.lang.reflect.Method m=DiscoveryActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(java.lang.reflect.InvocationTargetException error){throw new AssertionError(error.getCause());}catch(Exception error){throw new AssertionError(error);}}
    private void await(BooleanSupplier condition)throws Exception{
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until){boolean[] ready={false};onMain(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}
        fail("等待探索页面超时");
    }
    private int seasonRows(){int count=0;for(int i=0;i<activity.content.getChildCount();i++){View view=activity.content.getChildAt(i);if(view.isClickable()&&view instanceof ViewGroup)count++;}return count;}
    private View seasonRow(int number){int index=0;for(int i=0;i<activity.content.getChildCount();i++){View view=activity.content.getChildAt(i);if(view.isClickable()&&view instanceof ViewGroup&&++index==number)return view;}throw new AssertionError("季度不存在");}

    private static final class Fixture implements AutoCloseable{
        final ServerSocket socket=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
        final CountDownLatch castRequested=new CountDownLatch(1),castGate=new CountDownLatch(1),castSent=new CountDownLatch(1),cloudRequested=new CountDownLatch(1),cloudGate=new CountDownLatch(1);
        volatile boolean closed,delayCast,delayCloud;
        volatile int seasonRequests,cloudRequests,delayedCloudSent;
        Fixture()throws Exception{
            Thread accept=new Thread(()->{while(!closed)try{
                Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception error){if(!closed)android.util.Log.e("DiscoveryFixture","request",error);}});worker.setDaemon(true);worker.start();
            }catch(IOException error){if(closed)return;}},"discovery-fixture");accept.setDaemon(true);accept.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject item(String type){try{return new JSONObject().put("id","10").put("title","探索合成影片").put("media_type",type).put("overview","本地合成简介").put("poster_path","").put("backdrop_path","");}catch(Exception error){throw new AssertionError(error);}}
        JSONObject seasons(){try{return new JSONObject().put("seasons",new JSONArray().put(new JSONObject().put("season_number",1).put("name","第一季").put("episode_count",8)).put(new JSONObject().put("season_number",2).put("name","第二季").put("episode_count",6)));}catch(Exception error){throw new AssertionError(error);}}
        JSONObject response(String path)throws Exception{
            if(path.contains("check_status"))return new JSONObject().put("user",new JSONObject().put("can_access_discovery",true));
            if(path.contains("/query/"))return new JSONObject().put("results",new JSONArray().put(item("movie"))).put("total_pages",1);
            if(path.contains("/discover/series/")){seasonRequests++;return seasons();}
            if(path.contains("/credits"))return new JSONObject().put("cast",new JSONArray().put(new JSONObject().put("id","90").put("name","合成演员")));
            if(path.contains("-resources")){
                synchronized(this){cloudRequests++;}
                JSONArray resources=new JSONArray();
                JSONObject resource=new JSONObject().put("title","合成资源").put("resolution","1080p").put("video_codec","H265").put("size",1024000000).put("tag_labels",new JSONArray().put("一致版"));
                if(path.contains("shared-resources"))resource.put("versions",new JSONArray().put(new JSONObject(resource.toString()).put("sha1","a")).put(new JSONObject(resource.toString()).put("sha1","b")));
                return new JSONObject().put("resources",resources.put(resource));
            }
            return new JSONObject();
        }
        void serve(Socket client)throws Exception{
            try(client){
                InputStream input=client.getInputStream();ByteArrayOutputStream headers=new ByteArrayOutputStream();int value;
                while((value=input.read())!=-1){headers.write(value);byte[] bytes=headers.toByteArray();int n=bytes.length;if(n>=4&&bytes[n-4]==13&&bytes[n-3]==10&&bytes[n-2]==13&&bytes[n-1]==10)break;}
                String[] lines=headers.toString("US-ASCII").split("\r\n");String path=lines[0].split(" ")[1];int length=0;
                for(String line:lines)if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                for(int i=0;i<length;i++)if(input.read()==-1)throw new EOFException();
                boolean cast=path.contains("/credits")&&delayCast,cloud=path.contains("-resources")&&delayCloud;
                if(cast){castRequested.countDown();if(!castGate.await(8,TimeUnit.SECONDS))throw new IOException("演员响应等待超时");}
                if(cloud){cloudRequested.countDown();if(!cloudGate.await(8,TimeUnit.SECONDS))throw new IOException("资源响应等待超时");}
                byte[] body=response(path).toString().getBytes(StandardCharsets.UTF_8);
                OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);output.flush();
                if(cast)castSent.countDown();if(cloud)synchronized(this){delayedCloudSent++;}
            }
        }
        void release(){castGate.countDown();cloudGate.countDown();}
        @Override public void close()throws IOException{closed=true;release();socket.close();}
    }
}
