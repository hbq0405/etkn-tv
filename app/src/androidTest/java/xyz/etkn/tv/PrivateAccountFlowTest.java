package xyz.etkn.tv;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 只连接模拟器内的合成服务；覆盖真实 SharedPreferences、密码弹窗、后台生命周期和旧令牌。 */
public class PrivateAccountFlowTest {
    private final Instrumentation device=InstrumentationRegistry.getInstrumentation();
    private final List<Activity> activities=new ArrayList<>();
    private Context context;
    private Fixture server;
    private SessionStore session;
    private Map<String,?> original;
    private volatile Activity resumed;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity activity,Bundle state){activities.add(activity);}
        public void onActivityResumed(Activity activity){resumed=activity;}
        public void onActivityStarted(Activity activity){} public void onActivityPaused(Activity activity){}
        public void onActivityStopped(Activity activity){} public void onActivitySaveInstanceState(Activity activity,Bundle state){}
        public void onActivityDestroyed(Activity activity){}
    };
    private void onMain(Runnable work){device.runOnMainSync(work);}
    private void await(BooleanSupplier condition)throws Exception{
        long end=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<end){boolean[] ready={false};onMain(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(100);}
        fail("Login state did not become ready");
    }
    @Before public void setup()throws Exception{
        context=device.getTargetContext();server=new Fixture();
        ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(lifecycle);
        original=context.getSharedPreferences("session",0).getAll();context.getSharedPreferences("session",0).edit().clear().commit();
        SessionStore.endTemporarySession();LoginGate.reset();LoginGate.setMode(context,LoginGate.LAST_USER);
        session=new SessionStore(context);
    }
    @After public void cleanup()throws Exception{
        onMain(()->{((Application)context.getApplicationContext()).unregisterActivityLifecycleCallbacks(lifecycle);for(Activity activity:activities)if(!activity.isDestroyed())activity.finish();});
        device.waitForIdleSync();SessionStore.endTemporarySession();LoginGate.reset();
        android.content.SharedPreferences.Editor edit=context.getSharedPreferences("session",0).edit().clear();
        for(Map.Entry<String,?> entry:original.entrySet()){
            Object value=entry.getValue();if(value instanceof String)edit.putString(entry.getKey(),(String)value);
            else if(value instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)value);
            else if(value instanceof Integer)edit.putInt(entry.getKey(),(Integer)value);
            else if(value instanceof Long)edit.putLong(entry.getKey(),(Long)value);
        }
        edit.commit();server.close();
    }
    private <T extends Activity>T start(Class<T> type){
        T activity=type.cast(device.startActivitySync(new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)));
        return activity;
    }
    private void background()throws Exception{
        device.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);await(()->!LoginGate.isSatisfied());
        assertEquals("",session.token());
    }
    private SetupActivity returnToApp()throws Exception{
        Instrumentation.ActivityMonitor monitor=device.addMonitor(SetupActivity.class.getName(),null,false);
        try{
            onMain(()->context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            SetupActivity setup=(SetupActivity)monitor.waitForActivityWithTimeout(10000);assertNotNull("后台恢复必须自动进入登录页",setup);return setup;
        }finally{device.removeMonitor(monitor);}
    }
    private boolean hasPasswordInput(){for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(passwordInput(root)!=null)return true;return false;}
    private void clickText(String text)throws Exception{
        await(()->clickInWindows(text));
    }
    private void enterPassword(String password)throws Exception{
        await(()->{for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){EditText input=passwordInput(root);if(input!=null){input.setText(password);return true;}}return false;});
        clickText("登录");
    }
    private EditText passwordInput(View root){
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){EditText found=passwordInput(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private boolean clickInWindows(String text){
        List<View> roots=android.view.inspector.WindowInspector.getGlobalWindowViews();
        for(int i=roots.size()-1;i>=0;i--)if(clickView(roots.get(i),text))return true;
        return false;
    }
    private boolean clickView(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(text)){
            View target=view;
            while(target!=null){
                if(target.isClickable()&&target.isEnabled())return target.performClick();
                if(target.getParent() instanceof ListView){ListView list=(ListView)target.getParent();int index=list.getPositionForView(target);return list.performItemClick(target,index,list.getItemIdAtPosition(index));}
                target=target.getParent() instanceof View?(View)target.getParent():null;
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(clickView(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    private void showAccounts(HomeActivity home){
        onMain(()->{try{java.lang.reflect.Method method=HomeActivity.class.getDeclaredMethod("showAccounts");method.setAccessible(true);method.invoke(home);}catch(Exception error){throw new AssertionError(error);}});
    }
    private List<String> labels(View view){
        List<String> result=new ArrayList<>();if(view instanceof TextView)result.add(((TextView)view).getText().toString());
        if(view instanceof ViewGroup)for(int index=0;index<((ViewGroup)view).getChildCount();index++)result.addAll(labels(((ViewGroup)view).getChildAt(index)));
        return result;
    }
    @Test public void privateHotStartHidesIdentityAndRequiresFullLogin()throws Exception{
        session.saveSession(server.origin(),"private","private","token-private",SessionStore.PERSIST_NONE);LoginGate.satisfy();
        HomeActivity home=start(HomeActivity.class);
        assertTrue((home.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
        assertFalse(context.getSharedPreferences("session",0).getAll().toString().contains("token-private"));
        background();assertEquals("",session.userId());assertTrue(session.accounts().isEmpty());
        assertEquals(View.INVISIBLE,home.getWindow().getDecorView().getVisibility());
        SetupActivity setup=returnToApp();
        await(()->labels(setup.getWindow().getDecorView()).contains("连接并登录"));
        assertFalse(labels(setup.getWindow().getDecorView()).contains("private"));
        assertFalse(LoginGate.isSatisfied());
    }
    @Test public void adminHotStartWithLastUserModeStillRequiresPassword()throws Exception{
        session.saveSession(server.origin(),"admin","admin","token-admin",SessionStore.PERSIST_ACCOUNT);LoginGate.satisfy();
        start(HomeActivity.class);background();
        assertEquals("admin",session.accounts().get(0).userId);assertTrue(session.accounts().get(0).needsPassword);
        SetupActivity setup=returnToApp();
        await(()->labels(setup.getWindow().getDecorView()).contains("admin"));
        clickText("admin");await(this::hasPasswordInput);
        assertEquals("",session.token());assertFalse(LoginGate.isSatisfied());
        clickText("取消");assertEquals("",session.token());
    }
    @Test public void switchingToAdminRequiresPasswordAndWrongPasswordPreservesCurrentUser()throws Exception{
        session.saveSession(server.origin(),"admin","admin","token-admin",SessionStore.PERSIST_ACCOUNT);
        session.save(server.origin(),"normal","normal","token-normal");
        HomeActivity home=start(HomeActivity.class);showAccounts(home);clickText("admin");
        enterPassword("");assertEquals(0,server.logins);assertEquals("normal",session.userId());
        enterPassword("wrong");await(()->hasPasswordInput()&&server.logins>0);
        assertEquals("normal",session.userId());assertEquals("token-normal",session.token());
        // 错误后弹窗仍可重试，成功才切换，且再次选择当前管理员也必须输入密码。
        enterPassword("fixture-password");await(()->session.userId().equals("admin")&&resumed instanceof HomeActivity&&resumed!=home);
        assertFalse(context.getSharedPreferences("session",0).getAll().toString().contains("token-admin"));
        assertEquals(SessionStore.PERSIST_ACCOUNT,session.persist());
        assertTrue(session.accounts().get(0).needsPassword);assertEquals("",session.accountToken("admin"));
        showAccounts((HomeActivity)resumed);clickText("admin");await(this::hasPasswordInput);clickText("取消");
    }
    @Test public void upgradeCleansOldPrivateAndNonCurrentAdminTokens()throws Exception{
        session.save(server.origin(),"private","private","token-private");
        session.save(server.origin(),"admin","admin","token-admin");
        session.save(server.origin(),"normal","normal","token-normal");
        new EtknApi(context).refreshSavedAccounts();
        assertEquals("normal",session.userId());assertEquals("token-normal",session.token());
        assertEquals(2,session.accounts().size());assertTrue(session.accounts().get(0).needsPassword);
        assertEquals("admin",session.accounts().get(0).userId);
        SessionStore.endTemporarySession();assertEquals("token-normal",new SessionStore(context).token());
    }
    @Test public void delayedLoginAfterBackgroundCannotRestoreTemporarySession()throws Exception{
        session.saveSession(server.origin(),"admin","admin","token-admin",SessionStore.PERSIST_ACCOUNT);LoginGate.satisfy();
        HomeActivity home=start(HomeActivity.class);showAccounts(home);clickText("admin");
        server.holdLogin=true;enterPassword("fixture-password");
        await(()->server.logins>0);background();server.loginGate.countDown();
        Thread.sleep(300);assertEquals("",session.token());assertFalse(LoginGate.isSatisfied());
    }
    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        private volatile boolean closed;
        volatile int logins;
        volatile boolean holdLogin;
        final java.util.concurrent.CountDownLatch loginGate=new java.util.concurrent.CountDownLatch(1);
        Fixture()throws Exception{
            Thread accept=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception ignored){}});worker.setDaemon(true);worker.start();}catch(Exception ignored){return;}});
            accept.setDaemon(true);accept.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        private JSONObject user(String name)throws Exception{return new JSONObject().put("Id",name).put("Name",name).put("PrivateAccount",name.equals("private")).put("Policy",new JSONObject().put("IsAdministrator",name.equals("admin")));}
        private void serve(Socket client)throws Exception{
            try(client){
                BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
                String request=input.readLine();if(request==null)return;String path=request.split(" ")[1],token="";int length=0;
                for(String header;(header=input.readLine())!=null&&!header.isEmpty();){if(header.toLowerCase(Locale.ROOT).startsWith("x-emby-token:"))token=header.substring(header.indexOf(':')+1).trim();if(header.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(header.indexOf(':')+1).trim());}
                char[] data=new char[length];int count=0;while(count<length){int read=input.read(data,count,length-count);if(read<0)break;count+=read;}
                JSONObject result=new JSONObject().put("Items",new org.json.JSONArray());int status=200;
                if(path.equals("/api/health"))result=new JSONObject().put("status","ok");
                else if(path.equals("/api/tv/System/Info/Public"))result=new JSONObject().put("ProductName","ETKN").put("TvApiVersion",1).put("Id","fixture");
                else if(path.equals("/api/system/tv-discovery"))result=new JSONObject().put("ProductName","ETKN").put("MediaServerAddress",origin());
                else if(path.equals("/api/tv/Users/Me"))result=user(token.replace("token-",""));
                else if(path.equals("/api/tv/Users/AuthenticateByName")){
                    JSONObject body=new JSONObject(new String(data));logins++;
                    if(holdLogin)loginGate.await(10,java.util.concurrent.TimeUnit.SECONDS);
                    if(!body.optString("Pw").equals("fixture-password")){status=401;result=new JSONObject();}
                    else{String name=body.getString("Username");result=new JSONObject().put("User",user(name)).put("AccessToken","token-"+name).put("PrivateAccount",name.equals("private"));}
                }
                byte[] body=result.toString().getBytes(StandardCharsets.UTF_8);OutputStream output=client.getOutputStream();
                output.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);
            }
        }
        public void close()throws IOException{closed=true;loginGate.countDown();socket.close();}
    }
}
