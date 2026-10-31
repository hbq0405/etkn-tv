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
    private View findText(View view,String text){
        if(view instanceof TextView&&text.equals(((TextView)view).getText().toString()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    @Test public void savedAccountCheckDoesNotShowIntermediateScreen()throws Exception{
        session.save(server.origin(),"admin","admin","token-admin");LoginGate.reset();
        server.holdPolicy=true;
        SetupActivity setup=start(SetupActivity.class);await(()->server.policyRequests>0);
        onMain(()->{
            List<String> visible=labels(setup.getWindow().getDecorView());
            assertFalse("登录框前不应闪现检查页",visible.contains("重新检查"));
            assertFalse(visible.contains("正在检查已保存账号的登录规则…"));
            assertFalse("策略检查完成前不能展示旧账号",visible.contains("admin"));
        });
        server.policyGate.countDown();
        await(()->labels(setup.getWindow().getDecorView()).contains("admin"));
        assertEquals("",session.token());assertTrue(session.accounts().get(0).needsPassword);
        clickText("admin");await(this::hasPasswordInput);clickText("取消");
    }
    @Test public void everyAccountReachesNewUserWithOneDownPress()throws Exception{
        for(int i=0;i<8;i++)session.saveSession(server.origin(),"account-"+i,"account-"+i,"unused",SessionStore.PERSIST_ACCOUNT);
        LoginGate.reset();
        SetupActivity setup=start(SetupActivity.class);
        await(()->findText(setup.getWindow().getDecorView(),"account-7")!=null);
        for(int i=0;i<8;i++){
            String name="account-"+i;
            onMain(()->((View)findText(setup.getWindow().getDecorView(),name).getParent()).requestFocus());
            device.waitForIdleSync();device.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            await(()->setup.getCurrentFocus() instanceof Button&&"登录新用户".equals(((Button)setup.getCurrentFocus()).getText().toString()));
        }
        device.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        await(()->resumed instanceof SetupActivity&&resumed!=setup&&labels(resumed.getWindow().getDecorView()).contains("连接并登录"));
    }
    @Test public void accountPickerShowsWholeCardsWithOneOrManyUsers()throws Exception{
        File evidence=new File(context.getExternalFilesDir(null),"account-picker-width");assertTrue(evidence.isDirectory()||evidence.mkdirs());
        for(int count:new int[]{1,3,8}){
            context.getSharedPreferences("session",0).edit().clear().commit();session=new SessionStore(context);
            for(int i=0;i<count;i++)session.save(server.origin(),"normal-"+i,"用户 "+(i+1),"token-normal-"+i);
            LoginGate.reset();LoginGate.setMode(context,LoginGate.PICK_USER);
            SetupActivity setup=start(SetupActivity.class);
            await(()->{View name=findText(setup.getWindow().getDecorView(),"用户 1");return name!=null&&name.getWidth()>0&&setup.getCurrentFocus()==name.getParent();});
            device.waitForIdleSync();
            android.graphics.Bitmap screenshot=device.getUiAutomation().takeScreenshot();
            try(FileOutputStream output=new FileOutputStream(new File(evidence,"users-"+count+".png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{screenshot.recycle();}
            for(int i=0;i<count;i++){
                String name="用户 "+(i+1);await(()->findText(setup.getWindow().getDecorView(),name).getParent()==setup.getCurrentFocus());
                device.waitForIdleSync();Thread.sleep(150);
                onMain(()->{
                    View card=setup.getCurrentFocus();android.graphics.Rect visible=new android.graphics.Rect();assertTrue(card.getGlobalVisibleRect(visible));
                    int width=Math.round(card.getWidth()*card.getScaleX()),height=Math.round(card.getHeight()*card.getScaleY());
                    android.util.Log.i("AccountPickerWidth","count="+count+" card="+card.getWidth()+"x"+card.getHeight()+" scale="+card.getScaleX()+" visible="+visible);
                    assertEquals("账号卡片不能只显示半个："+name,width,visible.width(),2);
                    assertEquals("账号头像、名称和焦点框须完整显示："+name,height,visible.height(),2);
                    View newUser=findText(setup.getWindow().getDecorView(),"登录新用户");int[] action=new int[2];newUser.getLocationOnScreen(action);
                    assertTrue("新用户按钮应在账号列表下方",action[1]>=visible.bottom);
                });
                if(i+1<count)device.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            device.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);await(()->setup.getCurrentFocus() instanceof Button&&"登录新用户".equals(((Button)setup.getCurrentFocus()).getText().toString()));
            onMain(setup::finish);device.waitForIdleSync();
        }
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
    private Object field(Object owner,String name){try{java.lang.reflect.Field value=owner.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    @Test public void administratorCanOptIntoRememberedLoginAndDisableItAgain()throws Exception{
        session.saveSession(server.origin(),"admin","admin","token-admin",SessionStore.PERSIST_ACCOUNT);LoginGate.satisfy();
        SettingsActivity settings=start(SettingsActivity.class);await(()->field(settings,"adminRememberRow")!=null);
        assertFalse(session.rememberAdmin(server.origin(),"admin"));clickText("记住管理员登录");
        await(()->session.persist()==SessionStore.PERSIST_FULL&&session.rememberAdmin(server.origin(),"admin")&&field(settings,"adminRememberRow")!=null&&((View)field(settings,"adminRememberRow")).isEnabled());
        assertFalse(context.getSharedPreferences("session",0).getAll().toString().contains("token-admin"));assertFalse(session.accounts().get(0).needsPassword);
        SessionStore.endTemporarySession();assertEquals("token-admin",new SessionStore(context).token());assertEquals(SessionStore.PERSIST_FULL,new EtknApi(context).savedAccountPolicy("admin"));
        device.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);await(()->!LoginGate.isSatisfied());assertEquals("token-admin",session.token());
        returnToApp();await(()->resumed instanceof HomeActivity&&LoginGate.isSatisfied());assertFalse(hasPasswordInput());
        SettingsActivity again=start(SettingsActivity.class);await(()->field(again,"adminRememberRow")!=null);clickText("记住管理员登录");
        await(()->session.persist()==SessionStore.PERSIST_ACCOUNT&&!session.rememberAdmin(server.origin(),"admin"));
        assertEquals("token-admin",session.token());assertEquals("",session.accountToken("admin"));assertTrue(session.accounts().get(0).needsPassword);
        background();returnToApp();clickText("admin");await(this::hasPasswordInput);clickText("取消");
    }
    @Test public void rememberLoginSwitchIsHiddenForOrdinaryUsersAndCannotOverridePrivacy()throws Exception{
        session.save(server.origin(),"normal","normal","token-normal");SettingsActivity normal=start(SettingsActivity.class);await(()->server.policyRequests>0);device.waitForIdleSync();Thread.sleep(300);
        assertNull(field(normal,"adminRememberRow"));try{new EtknApi(context).rememberAdministrator(true);fail("普通用户不能设置管理员记住登录");}catch(Exception expected){assertTrue(expected.getMessage().contains("只有管理员"));}
        assertFalse(session.rememberAdmin(server.origin(),"normal"));
        onMain(normal::finish);await(normal::isDestroyed);server.adminPrivate=true;
        session.saveSession(server.origin(),"admin","admin","token-admin",SessionStore.PERSIST_NONE);session.setRememberAdmin(server.origin(),"admin",true);LoginGate.satisfy();
        SettingsActivity privateAdmin=start(SettingsActivity.class);await(()->field(privateAdmin,"adminRememberRow")!=null);assertFalse(((View)field(privateAdmin,"adminRememberRow")).isEnabled());
        try{new EtknApi(context).rememberAdministrator(true);fail("私密规则必须优先");}catch(Exception expected){assertTrue(expected.getMessage().contains("私密账号"));}
        assertEquals(SessionStore.PERSIST_NONE,session.persist());assertEquals("",session.accountToken("admin"));assertFalse(context.getSharedPreferences("session",0).getAll().toString().contains("token-admin"));
        onMain(privateAdmin::finish);await(privateAdmin::isDestroyed);session.save(server.origin(),"admin","admin","token-admin");
        assertEquals(SessionStore.PERSIST_NONE,new EtknApi(context).savedAccountPolicy("admin"));assertEquals("",session.token());assertTrue(session.accounts().stream().noneMatch(account->"admin".equals(account.userId)));assertTrue(session.accounts().stream().anyMatch(account->"normal".equals(account.userId)));
    }
    @Test public void delayedRememberSettingCannotRestoreAdminAfterBackground()throws Exception{
        session.saveSession(server.origin(),"admin","admin","token-admin",SessionStore.PERSIST_ACCOUNT);LoginGate.satisfy();
        SettingsActivity settings=start(SettingsActivity.class);await(()->field(settings,"adminRememberRow")!=null);int before=server.policyRequests;server.holdPolicy=true;clickText("记住管理员登录");
        await(()->server.policyRequests>before);background();server.policyGate.countDown();await(()->!Boolean.TRUE.equals(field(settings,"adminRememberSaving")));
        assertEquals("",session.token());assertEquals(SessionStore.PERSIST_ACCOUNT,session.persist());assertFalse(session.rememberAdmin(server.origin(),"admin"));
    }
    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket=new ServerSocket(0,32,InetAddress.getByName("127.0.0.1"));
        private volatile boolean closed;
        volatile int logins;
        volatile boolean holdLogin,adminPrivate;
        volatile int policyRequests;
        volatile boolean holdPolicy;
        final java.util.concurrent.CountDownLatch loginGate=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch policyGate=new java.util.concurrent.CountDownLatch(1);
        Fixture()throws Exception{
            Thread accept=new Thread(()->{while(!closed)try{Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception ignored){}});worker.setDaemon(true);worker.start();}catch(Exception ignored){return;}});
            accept.setDaemon(true);accept.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        private JSONObject user(String name)throws Exception{return new JSONObject().put("Id",name).put("Name",name).put("PrivateAccount",name.equals("private")||name.equals("admin")&&adminPrivate).put("Policy",new JSONObject().put("IsAdministrator",name.equals("admin")));}
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
                else if(path.equals("/api/tv/Users/Me")){policyRequests++;if(holdPolicy)policyGate.await(10,java.util.concurrent.TimeUnit.SECONDS);result=user(token.replace("token-",""));}
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
        public void close()throws IOException{closed=true;loginGate.countDown();policyGate.countDown();socket.close();}
    }
}
