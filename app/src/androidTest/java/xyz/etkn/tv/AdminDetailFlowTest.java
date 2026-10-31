package xyz.etkn.tv;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Real TV controls and HTTP against a loopback-only synthetic media service. */
public class AdminDetailFlowTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private DetailActivity detail;
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<until){boolean[] result={false};main(()->result[0]=condition.getAsBoolean());if(result[0])return;Thread.sleep(80);}fail("Timed out waiting for admin detail state");}
    private Object field(String name){try{java.lang.reflect.Field value=DetailActivity.class.getDeclaredField(name);value.setAccessible(true);return value.get(detail);}catch(Exception error){throw new AssertionError(error);}}
    private AlertDialog dialog(){return (AlertDialog)field("managementDialog");}
    private Button button(String name){return (Button)field(name);}
    private List<Switch> switches(View root){List<Switch> result=new ArrayList<>();if(root instanceof Switch)result.add((Switch)root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)result.addAll(switches(((ViewGroup)root).getChildAt(i)));return result;}
    private void key(int code){instrumentation.sendKeyDownUpSync(code);instrumentation.waitForIdleSync();}
    private void open(Fixture server,boolean series)throws Exception{
        new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"admin-test","合成测试账号","loopback-only");
        detail=(DetailActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),DetailActivity.class).putExtra("id",series?"item-2":"item-1").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->field("playButton")!=null&&button("playButton").isEnabled()&&server.policyRead);
        instrumentation.waitForIdleSync();
        if(server.admin)await(()->button("refreshButton").isShown());
    }
    private void refresh(){main(()->button("refreshButton").performClick());instrumentation.waitForIdleSync();}
    private void confirm(){main(()->dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick());}
    private void screenshot(String name)throws Exception{
        instrumentation.waitForIdleSync();SystemClock.sleep(350);instrumentation.waitForIdleSync();
        File directory=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"admin-detail-0.1.39");assertTrue(directory.isDirectory()||directory.mkdirs());
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();try(FileOutputStream output=new FileOutputStream(new File(directory,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
    }
    @After public void cleanup(){if(detail!=null)main(()->detail.finish());new SessionStore(instrumentation.getTargetContext()).clear();}

    @Test public void ordinaryAccountHasNoManagementActions()throws Exception{
        try(Fixture server=new Fixture(false)){open(server,false);main(()->{assertFalse(button("refreshButton").isShown());assertFalse(button("deleteButton").isShown());assertTrue(button("playButton").isEnabled());});assertTrue(server.mutations.isEmpty());}
    }
    @Test public void delegatedRefreshKeepsOrdinaryLoginAndHidesDelete()throws Exception{
        try(Fixture server=new Fixture(false,true,false)){
            open(server,false);await(()->button("refreshButton").isShown());
            main(()->assertFalse(button("deleteButton").isShown()));
            assertEquals(SessionStore.PERSIST_FULL,new SessionStore(instrumentation.getTargetContext()).persist());
            refresh();confirm();await(()->server.mutations.size()==1&&!Boolean.TRUE.equals(field("managementBusy")));
            assertEquals("POST /api/tv/Items/item-1/Refresh?Action=replace_metadata",server.mutations.get(0));
        }
    }
    @Test public void delegatedDeleteHidesRefreshAndUsesExistingConfirmation()throws Exception{
        try(Fixture server=new Fixture(false,false,true)){
            open(server,false);await(()->button("deleteButton").isShown());
            main(()->{assertFalse(button("refreshButton").isShown());button("deleteButton").performClick();});
            main(()->assertSame(dialog().getButton(AlertDialog.BUTTON_NEGATIVE),dialog().getCurrentFocus()));
            confirm();await(()->detail.isFinishing());assertEquals(Collections.singletonList("DELETE /api/tv/Items/item-1"),server.mutations);
        }
    }
    @Test public void refreshSwitchesSupportRemoteAndDisableEmptySelection()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,false);refresh();List<Switch> options=switches(dialog().getWindow().getDecorView());
            main(()->{assertTrue(options.get(0).isChecked());assertFalse(options.get(1).isChecked());assertSame(options.get(0),dialog().getCurrentFocus());});screenshot("refresh-metadata.png");
            key(KeyEvent.KEYCODE_DPAD_CENTER);main(()->assertFalse(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()));
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);main(()->{assertSame(options.get(1),dialog().getCurrentFocus());assertTrue(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());});
            key(KeyEvent.KEYCODE_DPAD_DOWN);main(()->assertSame(dialog().getButton(AlertDialog.BUTTON_NEGATIVE),dialog().getCurrentFocus()));key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            await(()->server.mutations.size()==1&&!Boolean.TRUE.equals(field("managementBusy")));assertEquals("POST /api/tv/Items/item-1/Refresh?Action=replace_images",server.mutations.get(0));
            refresh();key(KeyEvent.KEYCODE_BACK);assertEquals(1,server.mutations.size());assertFalse(dialog().isShowing());
        }
    }
    @Test public void metadataAndCombinedRefreshTargetTheDetailItemAndCoalesceClicks()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,true);assertEquals("item-3",((JSONObject)field("playable")).getString("Id"));
            refresh();confirm();await(()->server.mutations.size()==1&&!Boolean.TRUE.equals(field("managementBusy")));
            assertEquals("POST /api/tv/Items/item-2/Refresh?Action=replace_metadata",server.mutations.get(0));
            refresh();main(()->switches(dialog().getWindow().getDecorView()).get(1).setChecked(true));server.gate=new CountDownLatch(1);confirm();
            await(()->server.mutations.size()==2);main(()->{assertFalse(button("refreshButton").isEnabled());assertFalse(button("deleteButton").isEnabled());button("refreshButton").performClick();});assertEquals(2,server.mutations.size());
            server.gate.countDown();await(()->!Boolean.TRUE.equals(field("managementBusy")));assertEquals("POST /api/tv/Items/item-2/Refresh?Action=replace_metadata_images",server.mutations.get(1));
        }
    }
    @Test public void deleteDefaultsToCancelAndExitsOnlyAfterConfirmedSuccess()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,false);main(()->button("deleteButton").performClick());main(()->assertSame(dialog().getButton(AlertDialog.BUTTON_NEGATIVE),dialog().getCurrentFocus()));screenshot("delete-confirmation.png");key(KeyEvent.KEYCODE_DPAD_CENTER);assertTrue(server.mutations.isEmpty());assertFalse(detail.isFinishing());
            main(()->button("deleteButton").performClick());confirm();await(()->detail.isFinishing());assertEquals(Collections.singletonList("DELETE /api/tv/Items/item-1"),server.mutations);
        }
    }
    @Test public void serverDenialRestoresActionsAndDoesNotExit()throws Exception{
        try(Fixture server=new Fixture(true)){
            open(server,false);server.error=true;refresh();confirm();await(()->server.mutations.size()==1&&!Boolean.TRUE.equals(field("managementBusy")));
            main(()->{assertTrue(button("refreshButton").isEnabled());assertTrue(button("deleteButton").isEnabled());assertFalse(detail.isFinishing());});
            server.error=false;refresh();confirm();await(()->server.mutations.size()==2&&!Boolean.TRUE.equals(field("managementBusy")));assertFalse(detail.isFinishing());
            server.error=true;main(()->button("deleteButton").performClick());confirm();await(()->server.mutations.size()==3&&!Boolean.TRUE.equals(field("managementBusy")));assertFalse(detail.isFinishing());assertTrue(button("deleteButton").isEnabled());
        }
    }

    @Test public void confirmedDeleteReloadsTheCallingMediaList()throws Exception{
        PersonActivity parent=null,reloaded=null;
        try(Fixture server=new Fixture(true)){
            new SessionStore(instrumentation.getTargetContext()).save(server.origin(),"admin-test","合成测试账号","loopback-only");
            parent=(PersonActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),PersonActivity.class).putExtra("id","person-1").putExtra("name","合成演员").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            PersonActivity original=parent;await(()->server.worksReads==1&&original.status.getText().toString().contains("1 / 1"));
            Instrumentation.ActivityMonitor opened=instrumentation.addMonitor(DetailActivity.class.getName(),null,false);
            main(()->original.detail(new JSONObject(Collections.singletonMap("Id","item-1"))));
            detail=(DetailActivity)opened.waitForActivityWithTimeout(12000);instrumentation.removeMonitor(opened);assertNotNull(detail);await(()->field("deleteButton")!=null&&button("deleteButton").isShown());
            Instrumentation.ActivityMonitor returning=instrumentation.addMonitor(PersonActivity.class.getName(),null,false);
            main(()->button("deleteButton").performClick());confirm();reloaded=(PersonActivity)returning.waitForActivityWithTimeout(12000);
            // The monitor can first report the returning old instance before recreate().
            if(reloaded==original)reloaded=(PersonActivity)returning.waitForActivityWithTimeout(12000);
            instrumentation.removeMonitor(returning);assertNotNull(reloaded);assertNotSame(original,reloaded);
            PersonActivity returned=reloaded;await(()->server.worksReads==2&&returned.status.getText().toString().contains("暂无可见关联媒体"));
            assertTrue(original.isDestroyed());
        }finally{if(reloaded!=null){PersonActivity page=reloaded;main(page::finish);}else if(parent!=null){PersonActivity page=parent;main(page::finish);}}
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket;
        final boolean admin;
        final boolean canRefresh,canDelete;
        volatile boolean closed,policyRead,error;
        volatile boolean deleted;
        volatile int worksReads;
        volatile CountDownLatch gate;
        final List<String> mutations=Collections.synchronizedList(new ArrayList<>());
        Fixture(boolean admin)throws Exception{this(admin,false,false);}
        Fixture(boolean admin,boolean canRefresh,boolean canDelete)throws Exception{this.admin=admin;this.canRefresh=canRefresh;this.canDelete=canDelete;socket=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));Thread thread=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception ignored){}});worker.setDaemon(true);worker.start();}catch(IOException ignored){}});thread.setDaemon(true);thread.start();}
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject item(String id,String type)throws Exception{return new JSONObject().put("Id",id).put("Name",type.equals("Series")?"合成剧集":"合成电影").put("Type",type).put("Overview","管理员媒体管理测试").put("MediaSources",new JSONArray()).put("People",new JSONArray());}
        void serve(Socket client)throws Exception{
            try(client){BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));String line=input.readLine();if(line==null)return;String[] request=line.split(" ");String method=request[0],path=request[1];while((line=input.readLine())!=null&&!line.isEmpty()){}
                boolean mutation=method.equals("DELETE")||path.contains("/Refresh?");JSONObject body=new JSONObject();String status="200 OK";
                if(mutation){mutations.add(method+" "+path);if(gate!=null&&!gate.await(10,TimeUnit.SECONDS))throw new IOException("gate timed out");if(error){status="403 Forbidden";body.put("detail","需要管理员权限");}else if(method.equals("DELETE")){deleted=true;status="204 No Content";}else body.put("submitted",true);}
                else if(path.equals("/api/tv/Users/Me")){body.put("Id","admin-test").put("Policy",new JSONObject().put("IsAdministrator",admin));if(canRefresh||canDelete)body.put("TvPermissions",new JSONObject().put("CanRefreshMetadata",canRefresh).put("CanDeleteMedia",canDelete));policyRead=true;}
                else if(path.contains("/Images/")){status="404 Not Found";}
                else if(path.contains("/Similar")||path.contains("/Seasons")){body.put("Items",new JSONArray());}
                else if(path.contains("/Persons/person-1/Items")){worksReads++;body.put("Items",deleted?new JSONArray():new JSONArray().put(item("item-1","Movie"))).put("TotalRecordCount",deleted?0:1);}
                else if(path.contains("/Episodes")){body.put("Items",new JSONArray().put(item("item-3","Episode").put("SeriesId","item-2").put("SeasonId","item-4").put("ParentIndexNumber",1).put("IndexNumber",1)));}
                else if(path.contains("/Items/item-")){body=item(path.endsWith("item-2")?"item-2":"item-1",path.endsWith("item-2")?"Series":"Movie");}
                byte[] bytes=status.startsWith("204")?new byte[0]:body.toString().getBytes(StandardCharsets.UTF_8);OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 "+status+"\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(bytes);
            }
        }
        public void close()throws IOException{closed=true;if(gate!=null)gate.countDown();socket.close();}
    }
}
