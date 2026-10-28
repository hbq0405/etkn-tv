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
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

/** 在真实首页和 Android 焦点滚动路径上验收；只连接模拟器内的合成媒体服务。 */
public class HomeFocusTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private HomeActivity home;
    private File evidence;

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

    @SuppressWarnings("unchecked") private List<View> cards(){
        try{java.lang.reflect.Field field=HomeActivity.class.getDeclaredField("homeCards");field.setAccessible(true);return (List<View>)field.get(home);}
        catch(Exception error){throw new AssertionError(error);}
    }
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
        Fixture()throws Exception{
            Bitmap bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(45,80,110));
            Paint paint=new Paint();paint.setColor(Color.rgb(225,150,70));canvas.drawRect(0,0,640,18,paint);canvas.drawRect(0,342,640,360,paint);
            paint.setColor(Color.WHITE);paint.setTextSize(40);canvas.drawText("TV Focus Fixture",100,190,paint);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes);bitmap.recycle();image=bytes.toByteArray();
            Thread thread=new Thread(()->{while(!closed){try{serve(socket.accept());}catch(Exception ignored){if(closed)return;}}},"focus-fixture");thread.setDaemon(true);thread.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        void serve(Socket client)throws Exception{
            try(client){
                BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
                String request=reader.readLine();if(request==null)return;String path=request.split(" ")[1];while(true){String line=reader.readLine();if(line==null||line.isEmpty())break;}
                if(!path.startsWith("/api/tv/"))unexpectedPath=path;
                boolean picture=path.contains("/Images/");
                byte[] body=picture?image:response(path).toString().getBytes(StandardCharsets.UTF_8);
                OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+(picture?"image/jpeg":"application/json")+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);
            }
        }
        JSONObject response(String path)throws Exception{
            if(path.contains("check_status"))return new JSONObject().put("user",new JSONObject().put("can_access_discovery",false));
            if(path.contains("/Users/Me"))return new JSONObject();
            boolean libraries=path.endsWith("/Views");JSONArray items=new JSONArray();
            for(int i=0;i<(libraries?6:12);i++)items.put(new JSONObject().put("Id",(libraries?"library-":"item-")+i).put("Name",(libraries?"媒体库 ":"测试影片 ")+(i+1)).put("Type",libraries?"CollectionFolder":"Movie").put("RunTimeTicks",1000).put("UserData",new JSONObject().put("PlaybackPositionTicks",300)));
            return new JSONObject().put("Items",items);
        }
        @Override public void close()throws IOException{closed=true;socket.close();}
    }
}
