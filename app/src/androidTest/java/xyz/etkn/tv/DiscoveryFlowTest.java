package xyz.etkn.tv;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Button;
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
        if(activity!=null)await(activity::isDestroyed);
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
    @Test public void subscriptionsChooseOneOrAllWithoutNarrowingCloudSearch()throws Exception{
        JSONObject series=server.item("tv");
        onMain(()->invoke("enterDetail",new Class<?>[]{JSONObject.class},series));
        await(()->field("seasonList")!=null);
        onMain(()->{
            assertFalse(viewText(activity.content).contains("选择季度"));
            invoke("chooseSubscriptionSeasons",new Class<?>[0]);
        });
        AlertDialog choices=(AlertDialog)field("subscriptionSeasonsDialog");
        onMain(()->{assertTrue(viewText(choices.getWindow().getDecorView()).contains("第一季"));assertTrue(viewText(choices.getWindow().getDecorView()).contains("2026-01-01"));});
        capture("subscription-seasons");
        onMain(()->findAction(choices.getWindow().getDecorView(),"订阅第 1 季").performClick());
        clickDialogButton("订阅");
        await(()->server.subscriptions.size()==1&&activity.status.getText().length()==0);
        assertEquals(1,server.subscriptions.get(0).getInt("season_number"));
        onMain(()->assertNull("选季订阅不改变详情搜索范围",field("season")));
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        onMain(()->invoke("chooseSubscriptionSeasons",new Class<?>[0]));
        onMain(()->((AlertDialog)field("subscriptionSeasonsDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        clickDialogButton("订阅");
        await(()->server.subscriptions.size()==2&&activity.status.getText().length()==0);
        assertEquals("已选季不重复批量订阅",2,server.subscriptions.get(1).getInt("season_number"));
        assertEquals("探索合成影片 第 1 季",server.subscriptions.get(0).getString("name"));
        assertEquals("2026-01-01",server.subscriptions.get(0).getString("release_date"));
        assertEquals(50,server.subscriptions.get(0).getInt("priority"));
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        onMain(()->invoke("enterCloud",new Class<?>[0]));
        await(()->activity.status.getText().toString().startsWith("搜索完成"));
        assertEquals(3,server.cloudPaths.size());
        for(String path:server.cloudPaths){assertFalse(path.contains("&season="));assertFalse(path.contains("&season_number="));assertFalse(path.contains("item_type=season"));}
        assertEquals("系列数据与订阅选季复用缓存",1,server.seasonRequests);
    }
    @Test public void movieResourcesRenderAllProvidersAndReopenWithoutDuplicateViews()throws Exception{
        for(int pass=0;pass<3;pass++){
            onMain(()->invoke("enterDetail",new Class<?>[]{JSONObject.class},server.item("movie")));
            onMain(()->invoke("enterCloud",new Class<?>[0]));
            await(()->activity.status.getText().toString().startsWith("搜索完成"));
            if(pass==0)capture("cloud-results");
            onMain(()->{
                assertEquals(4,activity.content.getChildCount());
                LinearLayout results=(LinearLayout)field("cloudResults");assertEquals(3,results.getChildCount());
                assertEquals(3,((ViewGroup)results.getChildAt(0)).getChildCount());
                assertEquals(2,((ViewGroup)results.getChildAt(1)).getChildCount());
                assertEquals(2,((ViewGroup)results.getChildAt(2)).getChildCount());
                assertEquals(activity.dp(32),activity.screenScroll.getPaddingLeft());
                assertEquals(activity.dp(32),activity.screenScroll.getPaddingRight());
                assertEquals(activity.dp(24),activity.screenScroll.getPaddingTop());
                assertEquals(activity.dp(40),activity.screenScroll.getPaddingBottom());
                assertTrue("滚动中的资源也必须保留四周边距",activity.screenScroll.getClipToPadding());
                activity.onBackPressed();
                assertEquals("10",((JSONObject)field("selected")).optString("id"));
                assertEquals(0,activity.screenScroll.getPaddingBottom());
                activity.onBackPressed();
                assertSame(field("browseView"),activity.content.getChildAt(0));
                assertEquals(0,activity.content.getPaddingLeft());
                assertEquals(0,activity.content.getPaddingTop());
            });
        }
        assertEquals(9,server.cloudRequests);
    }
    @Test public void cloudSourceFiltersAndTitleRetryKeepIdentity()throws Exception{
        onMain(()->{invoke("enterDetail",new Class<?>[]{JSONObject.class},server.item("tv"));invoke("enterCloud",new Class<?>[0]);});
        await(()->activity.status.getText().toString().startsWith("搜索完成"));
        onMain(()->{
            ((Map<String,Button>)field("cloudSourceButtons")).get("re0").performClick();
            Map<String,LinearLayout> areas=(Map<String,LinearLayout>)field("cloudAreas");
            assertEquals(View.VISIBLE,areas.get("re0").getVisibility());assertEquals(View.GONE,areas.get("shared_pool").getVisibility());assertEquals(View.GONE,areas.get("channel").getVisibility());
            assertEquals("来源筛选使用已加载结果",3,server.cloudRequests);
            android.widget.EditText search=(android.widget.EditText)field("cloudSearch");search.setText("另一个搜索标题");((ViewGroup)search.getParent()).getChildAt(1).performClick();
        });
        await(()->server.cloudRequests==6&&activity.status.getText().toString().startsWith("搜索完成"));
        assertEquals("all",field("cloudSource"));
        for(String path:server.cloudPaths.subList(3,6)){
            assertTrue(path.contains("tmdb_id=10"));assertFalse(path.contains("&season="));assertFalse(path.contains("&season_number="));
            if(!path.contains("re0-resources"))assertTrue(URLDecoder.decode(path,"UTF-8").contains("q=另一个搜索标题"));
        }
    }
    @Test public void detailPosterActionsAndCastFitOneScreen()throws Exception{
        String overview=String.join("",Collections.nCopies(60,"这是用于检查电视一屏布局的完整长简介。"));
        JSONArray cast=new JSONArray();
        for(int i=0;i<8;i++)cast.put(new JSONObject().put("id","actor"+i).put("name","合成演员"+i).put("character","角色"+i));
        JSONObject seasons=server.seasons();
        for(int i=3;i<=20;i++)seasons.getJSONArray("seasons").put(new JSONObject().put("season_number",i).put("name","第"+i+"季").put("episode_count",12));
        onMain(()->{
            ((Map<String,JSONArray>)field("castCache")).put("10",cast);
            ((Map<String,JSONObject>)field("seriesCache")).put("10",seasons);
        });
        for(String type:new String[]{"movie","tv"}){
            JSONObject item=server.item(type).put("overview",overview).put("tagline",String.join("",Collections.nCopies(8,"长标语与完整演职员同时展示，不挤压操作按钮。"))).put("in_library",true).put("media_item_id","local-10");
            onMain(()->invoke("enterDetail",new Class<?>[]{JSONObject.class},item));
            await(()->activity.content.getChildCount()==3&&!activity.content.isLayoutRequested());
            capture("detail-"+type);
            onMain(()->{
                ViewGroup hero=(ViewGroup)activity.content.getChildAt(0);
                assertEquals("仅保留海报和文字，不加载详情背景图",2,hero.getChildCount());
                ImageView poster=(ImageView)hero.getChildAt(0);
                assertTrue("海报高于旧版186dp",poster.getHeight()>activity.dp(186));
                assertEquals(Math.round(poster.getHeight()*2f/3f),poster.getWidth());
                ViewGroup copy=(ViewGroup)hero.getChildAt(1);
                ViewGroup actions=(ViewGroup)copy.getChildAt(3);
                TextView intro=(TextView)field("detailOverview");
                assertTrue("操作按钮在简介上方",actions.getBottom()<=intro.getTop());
                assertTrue("简介仍有可读高度",intro.getHeight()>=activity.dp(40));
                assertEquals(overview,intro.getText().toString());
                View last=activity.content.getChildAt(2);
                assertTrue("演员区与底部留白均在首屏: "+last.getBottom()+" + "+activity.content.getPaddingBottom()+" <= "+activity.screenScroll.getHeight(),last.getBottom()+activity.content.getPaddingBottom()<=activity.screenScroll.getHeight());
                assertTrue("所有操作按钮完整显示",actions.getChildAt(actions.getChildCount()-1).getRight()<=copy.getWidth());
                assertFalse(viewText(activity.content).contains("选择季度"));
                assertEquals(0,activity.screenScroll.getScrollY());
            });
        }
        onMain(()->invoke("chooseSubscriptionSeasons",new Class<?>[0]));
        AlertDialog dialog=(AlertDialog)field("subscriptionSeasonsDialog");
        onMain(()->findAction(dialog.getWindow().getDecorView(),"订阅第 1 季").requestFocus());
        for(int i=1;i<20;i++)instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        await(()->findAction(dialog.getWindow().getDecorView(),"订阅第 20 季").hasFocus());
        capture("subscription-long-seasons");
        onMain(()->{
            View last=findAction(dialog.getWindow().getDecorView(),"订阅第 20 季");android.graphics.Rect visible=new android.graphics.Rect();
            assertTrue("遥控器可以滚动到最后一季",last.getGlobalVisibleRect(visible));assertEquals(last.getHeight(),visible.height());
            assertTrue(dialog.getWindow().getDecorView().getHeight()<activity.screenScroll.getHeight());
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown());
            dialog.dismiss();
        });
    }
    @Test public void sharedPoolSameSha1RendersOneVersionAndShareCount()throws Exception{
        server.duplicateSha=true;
        onMain(()->{invoke("enterDetail",new Class<?>[]{JSONObject.class},server.item("movie"));invoke("enterCloud",new Class<?>[0]);});
        await(()->activity.status.getText().toString().startsWith("搜索完成"));
        onMain(()->{ViewGroup shared=(ViewGroup)((Map<?,?>)field("cloudAreas")).get("shared_pool");assertEquals(3,shared.getChildCount());String text=viewText(shared);assertTrue(text.contains("共享源 2"));assertTrue(text.contains("版本 1/2"));assertTrue(text.contains("可转存"));});
    }
    private String viewText(View view){String result=view instanceof TextView?((TextView)view).getText().toString():"";if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result+=" "+viewText(((ViewGroup)view).getChildAt(i));return result;}
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
    @Test public void posterWallCentersAndReflowsAtDifferentWidths()throws Exception{
        GridLayout grid=(GridLayout)field("posterGrid");
        View browse=(View)field("browseView");
        TvPreferences preferences=new TvPreferences(activity,activity.api);int size=preferences.imageSize();
        float density=activity.getResources().getDisplayMetrics().density;
        int originalWidth=browse.getWidth();
        onMain(()->{
            for(int i=0;i<20;i++)invoke("discoveryCard",new Class<?>[]{GridLayout.class,JSONObject.class,Runnable.class},grid,server.item("movie"),(Runnable)()->{});
            grid.getChildAt(0).requestFocus();
        });
        View focused=grid.getChildAt(0);
        for(int width:new int[]{originalWidth,originalWidth/2,originalWidth*3/4,originalWidth}){
            onMain(()->{ViewGroup.LayoutParams bounds=browse.getLayoutParams();bounds.width=width;browse.setLayoutParams(bounds);});
            int availableDp=Math.max(1,Math.round((width-browse.getPaddingLeft()-browse.getPaddingRight())/density));
            int mediumColumns=Math.max(1,(availableDp+12)/144);
            int columns=Math.max(1,mediumColumns+TvPreferences.IMAGE_MEDIUM-size);
            await(()->browse.getWidth()==width&&grid.getColumnCount()==columns&&grid.getChildAt(columns).getTop()>focused.getTop()&&focused.getHeight()==Math.round(focused.getWidth()*1.5f));
            onMain(()->{
                int left=grid.getLeft()+focused.getLeft();
                int right=width-grid.getLeft()-grid.getChildAt(columns-1).getRight();
                assertTrue("海报墙两侧留白应相等",Math.abs(left-right)<=1);
                assertEquals(Math.round(focused.getWidth()*1.5f),focused.getHeight());
                assertSame("重排后保持同一卡片焦点",focused,activity.getCurrentFocus());
                assertEquals(21,grid.getChildCount());
            });
        }
        int fullWidth=focused.getWidth(),fullLeft=focused.getLeft();
        onMain(()->{while(grid.getChildCount()>1)grid.removeViewAt(grid.getChildCount()-1);});
        await(()->!grid.isLayoutRequested());
        onMain(()->{assertEquals("仅剩一项仍保持正常列宽",fullWidth,focused.getWidth());assertEquals(fullLeft,focused.getLeft());});
    }
    @Test public void imageSizePreferenceResizesPosterGridAndDefaultsToMedium()throws Exception{
        TvPreferences preferences=new TvPreferences(activity,activity.api);
        GridLayout grid=(GridLayout)field("posterGrid");
        View card=grid.getChildAt(0);
        assertEquals(TvPreferences.IMAGE_MEDIUM,preferences.imageSize());
        float density=activity.getResources().getDisplayMetrics().density;
        for(int size:new int[]{TvPreferences.IMAGE_EXTREMELY_SMALL,TvPreferences.IMAGE_SMALL,TvPreferences.IMAGE_MEDIUM,TvPreferences.IMAGE_LARGE,TvPreferences.IMAGE_EXTREMELY_LARGE}){
            onMain(()->{preferences.setImageSize(size);activity.applyImageSizeNow();card.requestFocus();});
            await(()->{
                int availableDp=Math.max(1,Math.round((grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight())/density));
                int mediumColumns=Math.max(1,(availableDp+12)/144);
                return grid.getColumnCount()==Math.max(1,mediumColumns+TvPreferences.IMAGE_MEDIUM-size)&&card.getWidth()>0;
            });
            onMain(()->{
                assertEquals(Math.round(card.getWidth()*1.5f),card.getHeight());
                assertSame(card,activity.getCurrentFocus());
            });
        }
        onMain(()->{preferences.setImageSize(TvPreferences.IMAGE_MEDIUM);activity.applyImageSizeNow();});
    }
    @Test public void discoveryFiltersWaitForConfirmationAndSubmitKeywordAndCompanyTogether()throws Exception{
        int initial=server.queryRequests;
        onMain(()->{
            invoke("openFilters",new Class<?>[0]);
            ((Map<String,String>)field("keywordFilters")).put("科幻","科幻");
            ((Map<String,String>)field("studioFilters")).put("华纳兄弟","华纳兄弟");
            invoke("toggleHide",new Class<?>[0]);
            assertEquals("筛选尚未确认时不应重新请求",initial,server.queryRequests);
            ((Button)field("filterConfirmButton")).performClick();
        });
        await(()->server.queryRequests==initial+1);
        JSONObject payload=new JSONObject(server.lastQueryBody);
        assertEquals("科幻",payload.getJSONArray("keywords").getString(0));
        assertEquals("华纳兄弟",payload.getJSONArray("studios").getString(0));
        assertTrue(payload.getBoolean("hide_in_library"));
    }
    @Test public void discoveryMenuHasRoomForFocusedButton()throws Exception{
        onMain(()->invoke("showMenu",new Class<?>[0]));
        await(()->((View)field("menu")).getTranslationY()==0);
        onMain(()->{
            View menu=(View)field("menu"),focused=activity.getCurrentFocus();assertNotNull(focused);
            int[] container=new int[2],button=new int[2];menu.getLocationOnScreen(container);focused.getLocationOnScreen(button);
            assertTrue("聚焦按钮上方保留空间",button[1]>=container[1]);
            assertTrue("放大后的聚焦按钮完整位于菜单内",button[1]+focused.getHeight()*focused.getScaleY()<=container[1]+menu.getHeight());
        });
    }
    @Test public void multiChoiceRightKeyJumpsToApplyButton()throws Exception{
        JSONArray options=new JSONArray();
        for(int i=0;i<12;i++)options.put(new JSONObject().put("value","v"+i).put("label","选项"+i));
        Map<String,String> selected=new LinkedHashMap<>();
        onMain(()->invoke("chooseMultiOptions",new Class<?>[]{String.class,JSONArray.class,Map.class,Runnable.class,String.class},"关键词（可多选）",options,selected,(Runnable)()->{},"keyword"));
        await(()->field("multiChoiceDialog")!=null);
        AlertDialog dialog=(AlertDialog)field("multiChoiceDialog");
        onMain(()->dialog.getListView().requestFocus());
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus());
        onMain(dialog::dismiss);
    }
    private void onMain(Runnable action){
        Throwable[] failure={null};
        instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError("探索页面操作失败",failure[0]);
    }
    private void capture(String name)throws Exception{
        onMain(()->activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));
        SystemClock.sleep(250);instrumentation.waitForIdleSync();
        android.graphics.Bitmap image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);
        try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),name+".png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
    }
    private Object field(String name){try{java.lang.reflect.Field f=DiscoveryActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception error){throw new AssertionError(error);}}
    private void invoke(String name,Class<?>[] types,Object...args){try{java.lang.reflect.Method m=DiscoveryActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(java.lang.reflect.InvocationTargetException error){throw new AssertionError(error.getCause());}catch(Exception error){throw new AssertionError(error);}}
    private void await(BooleanSupplier condition)throws Exception{
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until){boolean[] ready={false};onMain(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}
        capture("timeout");
        String[] state={""};onMain(()->state[0]="children="+activity.content.getChildCount()+", layout="+activity.content.isLayoutRequested()+", shown="+activity.content.isShown()+", status="+activity.status.getText()+", height="+activity.content.getHeight()+", viewport="+activity.screenScroll.getHeight());
        fail("等待探索页面超时: "+state[0]);
    }
    private View findAction(View view,String description){
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findAction(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}
        return null;
    }
    private void clickDialogButton(String label)throws Exception{
        long until=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<until){
            android.view.accessibility.AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))
                if(label.contentEquals(node.getText()==null?"":node.getText())&&"android.widget.Button".contentEquals(node.getClassName())&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return;
            SystemClock.sleep(80);
        }
        fail("未找到确认弹窗按钮: "+label);
    }


    private static final class Fixture implements AutoCloseable{
        final ServerSocket socket=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
        final CountDownLatch castRequested=new CountDownLatch(1),castGate=new CountDownLatch(1),castSent=new CountDownLatch(1),cloudRequested=new CountDownLatch(1),cloudGate=new CountDownLatch(1);
        volatile boolean closed,delayCast,delayCloud,duplicateSha;
        volatile int seasonRequests,cloudRequests,delayedCloudSent,queryRequests;
        volatile String lastQueryBody="{}";
        final List<JSONObject> subscriptions=Collections.synchronizedList(new ArrayList<>());
        final List<String> cloudPaths=Collections.synchronizedList(new ArrayList<>());
        Fixture()throws Exception{
            Thread accept=new Thread(()->{while(!closed)try{
                Socket client=socket.accept();Thread worker=new Thread(()->{try{serve(client);}catch(Exception error){if(!closed)android.util.Log.e("DiscoveryFixture","request",error);}});worker.setDaemon(true);worker.start();
            }catch(IOException error){if(closed)return;}},"discovery-fixture");accept.setDaemon(true);accept.start();
        }
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        JSONObject item(String type){try{return new JSONObject().put("id","10").put("title","探索合成影片").put("media_type",type).put("overview","本地合成简介").put("poster_path","").put("backdrop_path","");}catch(Exception error){throw new AssertionError(error);}}
        JSONObject seasons(){try{return new JSONObject().put("seasons",new JSONArray().put(new JSONObject().put("season_number",1).put("name","第一季").put("episode_count",8).put("air_date","2026-01-01").put("overview","本季独立简介")).put(new JSONObject().put("season_number",2).put("name","第二季").put("episode_count",6)));}catch(Exception error){throw new AssertionError(error);}}
        JSONObject response(String path)throws Exception{
            if(path.contains("check_status"))return new JSONObject().put("user",new JSONObject().put("can_access_discovery",true));
            if(path.contains("/query/"))return new JSONObject().put("results",new JSONArray().put(item("movie"))).put("total_pages",1);
            if(path.contains("/discover/series/")){seasonRequests++;return seasons();}
            if(path.contains("/credits"))return new JSONObject().put("cast",new JSONArray().put(new JSONObject().put("id","90").put("name","合成演员")));
            if(path.contains("-resources")){
                synchronized(this){cloudRequests++;cloudPaths.add(path);}
                JSONArray resources=new JSONArray();
                JSONObject resource=new JSONObject().put("title","合成资源").put("resolution","1080p").put("video_codec","H265").put("size",1024000000).put("tag_labels",new JSONArray().put("一致版"));
                if(path.contains("shared-resources")){JSONArray versions=new JSONArray().put(new JSONObject(resource.toString()).put("sha1","a")).put(new JSONObject(resource.toString()).put("sha1","b"));if(duplicateSha)versions.put(new JSONObject(resource.toString()).put("sha1","A").put("share_channel_status","valid"));resource.put("versions",versions);}
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
                ByteArrayOutputStream requestBody=new ByteArrayOutputStream();for(int i=0;i<length;i++){int next=input.read();if(next==-1)throw new EOFException();requestBody.write(next);}
                if(path.contains("/query/")){queryRequests++;lastQueryBody=requestBody.toString("UTF-8");}
                if(path.contains("/discover/subscribe"))subscriptions.add(new JSONObject(requestBody.toString("UTF-8")));
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
