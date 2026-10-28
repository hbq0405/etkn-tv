package xyz.etkn.tv;

import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.view.Gravity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

public final class HomeActivity extends TvActivity {
    private String parent="",query="",mode="home";
    private int offset,revision;
    private boolean canDiscover;
    private HorizontalScrollView menu;
    private ScrollView homeScroll;
    private View previousFocus;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);
        homeScroll=new ScrollView(this);homeScroll.setFillViewport(true);homeScroll.setClipToPadding(false);
        content=column();homeScroll.addView(content);stage.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));
        nav=row();nav.setPadding(dp(28),dp(8),dp(18),dp(8));
        menu=new HorizontalScrollView(this);menu.setHorizontalScrollBarEnabled(false);menu.setBackgroundColor(android.graphics.Color.argb(245,11,17,26));menu.addView(nav);menu.setVisibility(View.GONE);
        stage.addView(menu,new FrameLayout.LayoutParams(-1,dp(76),Gravity.TOP));
        status=text("",13,MUTED);status.setPadding(dp(24),dp(4),dp(24),dp(4));
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);stage.addView(status,notice);
        setContentView(stage);refreshNavigation();loadHome();
    }
    @Override void nav(String title,Runnable click){super.nav(title,()->{hideMenu();click.run();});}
    private void showMenu(){previousFocus=getCurrentFocus();menu.setVisibility(View.VISIBLE);if(nav.getChildCount()>0)nav.getChildAt(0).requestFocus();}
    private void hideMenu(){menu.setVisibility(View.GONE);if(previousFocus==null||!previousFocus.isShown()||!previousFocus.requestFocus())content.requestFocus(View.FOCUS_DOWN);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&menu.getVisibility()!=View.VISIBLE){View focused=getCurrentFocus();if(focused==null||focused.focusSearch(View.FOCUS_UP)==null){showMenu();return true;}}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&menu.getVisibility()==View.VISIBLE){hideMenu();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){super.onResume();async(()->api.get("/api/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");boolean allowed=user!=null&&user.optBoolean("can_access_discovery");if(canDiscover!=allowed){canDiscover=allowed;refreshNavigation();}});}
    private void refreshNavigation(){
        nav.removeAllViews();FrameLayout account=new FrameLayout(this);account.setBackground(focusBackground());account.setFocusable(true);account.setClickable(true);account.setContentDescription("当前用户头像，按确定切换账号或登录新用户");
        ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(avatar,dp(19));
        FrameLayout.LayoutParams image=new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER);account.addView(avatar,image);
        LinearLayout.LayoutParams face=new LinearLayout.LayoutParams(dp(52),dp(54));face.setMargins(0,dp(4),dp(8),0);nav.addView(account,face);account.setOnClickListener(view->{hideMenu();showAccounts();});loadAvatar(avatar);
        nav("首页",this::loadHome);nav("媒体库",this::loadLibraries);nav("继续观看",()->{mode="resume";parent="";offset=0;loadItems();});nav("搜索",this::search);if(canDiscover)nav("影视探索",()->open(DiscoveryActivity.class));nav("设置",()->open(SettingsActivity.class));
    }
    private void loadHome(){
        mode="home";int request=++revision;content.removeAllViews();content.setPadding(0,0,0,dp(24));homeScroll.scrollTo(0,0);status.setText("");
        int heroHeight=dp(getResources().getConfiguration().screenHeightDp*0.60f);
        LinearLayout heroArea=column();heroArea.setGravity(Gravity.CENTER);heroArea.setFocusable(true);
        content.addView(heroArea,new LinearLayout.LayoutParams(-1,heroHeight));heroArea.addView(text("正在读取你的观影推荐…",17,MUTED));heroArea.requestFocus();
        LinearLayout resume=section("继续观看"),libraries=section("我的媒体库"),latest=column();
        LinearLayout.LayoutParams resumeLayout=(LinearLayout.LayoutParams)resume.getLayoutParams();resumeLayout.topMargin=-dp(20);resume.setLayoutParams(resumeLayout);resume.setPadding(dp(28),dp(8),dp(22),dp(2));
        latest.setPadding(0,dp(4),0,dp(8));content.addView(latest);
        async(()->{try{return api.get(api.userPath()+"/Items/PersonalRecommendations?Limit=12&Fields=Overview");}catch(EtknApi.AuthException e){throw e;}catch(Exception e){return new JSONObject().put("unavailable",true);}},result->{
            if(request!=revision)return;boolean focused=heroArea.hasFocus();heroArea.removeAllViews();List<JSONObject> items=EtknApi.rows(result);
            if(items.isEmpty())heroArea.addView(text(result.optBoolean("unavailable")?"个人推荐暂不可用，请确认 ETKN 已更新。":"多看几部、收藏喜欢的影片，首页会逐渐懂你的口味。",16,MUTED));
            else {RecommendationHero hero=new RecommendationHero(this,items);heroArea.setFocusable(false);heroArea.addView(hero,new LinearLayout.LayoutParams(-1,-1));if(focused)hero.requestFocus();}
        });
        loadShelf(resume,api.userPath()+"/Items/Resume?Limit=12&Fields=Overview,PrimaryImageAspectRatio",request,true,false,"暂无继续观看记录");
        loadShelf(libraries,api.userPath()+"/Views",request,true,true,"暂无可访问媒体库");
        loadLatestByLibrary(latest,request);
    }
    private void loadLatestByLibrary(LinearLayout target,int request){
        async(()->{
            List<JSONObject> sections=new java.util.ArrayList<>();
            JSONObject views=api.get(api.userPath()+"/Views");
            for(JSONObject library:EtknApi.rows(views)){
                try{
                    JSONObject latest=api.get(api.userPath()+"/Items/Latest?ParentId="+EtknApi.encode(library.optString("Id"))+"&Limit=12&IncludeItemTypes=Movie,Series&Fields=Overview");
                    List<JSONObject> items=EtknApi.rows(latest);
                    if(!items.isEmpty())sections.add(new JSONObject().put("Name",EtknApi.title(library)).put("Items",new org.json.JSONArray(items)));
                }catch(EtknApi.AuthException error){throw error;}catch(Exception ignored){}
            }
            return sections;
        },sections->{
            if(request!=revision)return;
            target.removeAllViews();
            if(sections.isEmpty()){target.addView(text("暂无已启用“显示在最新媒体库”的片库内容",14,MUTED));return;}
            for(JSONObject section:sections){
                LinearLayout area=section("最新"+section.optString("Name"),target);while(area.getChildCount()>1)area.removeViewAt(1);
                HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);GridLayout shelf=new GridLayout(this);shelf.setRowCount(1);shelf.setColumnCount(section.optJSONArray("Items").length());scroll.addView(shelf);area.addView(scroll);
                for(JSONObject item:EtknApi.rows(section))mediaCard(shelf,item,()->detail(item),false);
            }
        });
    }
    private void showAccounts(){
        List<SavedAccounts.Account> accounts=api.session.accounts();
        if(accounts.isEmpty())return;
        String[] labels=new String[accounts.size()];
        for(int index=0;index<accounts.size();index++){
            SavedAccounts.Account account=accounts.get(index);labels[index]=account.name+(account.userId.equals(api.session.userId())?"（当前）":"");
        }
        new AlertDialog.Builder(this).setTitle("切换用户").setItems(labels,(dialog,index)->{
            SavedAccounts.Account account=accounts.get(index);if(account.userId.equals(api.session.userId()))return;
            try{api.session.activate(account.userId);recreate();}catch(Exception error){status.setText(error.getMessage());}
        }).setNeutralButton("登录新用户",(dialog,index)->startActivity(new Intent(this,SetupActivity.class).putExtra(SetupActivity.EXTRA_ADD_ACCOUNT,true))).setNegativeButton("取消",null).show();
    }
    private LinearLayout section(String title){return section(title,content);}
    private LinearLayout section(String title,LinearLayout parent){
        LinearLayout area=column();area.setPadding(dp(28),dp(14),dp(22),dp(2));area.addView(text(title,20,android.graphics.Color.WHITE));area.addView(text("正在加载…",13,MUTED));parent.addView(area);return area;
    }
    private void loadShelf(LinearLayout area,String path,int request,boolean landscape,boolean libraries,String empty){
        async(()->{try{return api.get(path);}catch(EtknApi.AuthException e){throw e;}catch(Exception e){return new JSONObject().put("unavailable",true);}},result->{
            if(request!=revision)return;while(area.getChildCount()>1)area.removeViewAt(1);List<JSONObject> items=EtknApi.rows(result);
            if(items.isEmpty()){area.addView(text(result.optBoolean("unavailable")?"暂时无法加载，请稍后返回首页重试。":empty,14,MUTED));return;}
            HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);GridLayout shelf=new GridLayout(this);shelf.setRowCount(1);shelf.setColumnCount(items.size());scroll.addView(shelf);area.addView(scroll);
            for(JSONObject item:items)mediaCard(shelf,item,()->{if(libraries){parent=item.optString("Id");mode="items";offset=0;loadItems();}else detail(item);},landscape);
        });
    }
    private void search(){EditText field=input("搜索片库",false);new AlertDialog.Builder(this).setTitle("搜索片库").setView(field).setPositiveButton("搜索",(dialog,which)->{query=field.getText().toString();parent="";offset=0;mode="search";loadItems();}).setNegativeButton("取消",null).show();}
    private void loadLibraries(){mode="library";int request=++revision;status.setText("正在读取媒体库…");async(()->api.get(api.userPath()+"/Views"),result->{if(request!=revision)return;content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));List<JSONObject> items=EtknApi.rows(result);status.setText(items.isEmpty()?"暂无可访问媒体库":"选择媒体库");GridLayout grid=grid();grid.setColumnCount(Math.max(2,(getResources().getConfiguration().screenWidthDp-56)/272));for(JSONObject item:items)mediaCard(grid,item,()->{parent=item.optString("Id");mode="items";offset=0;loadItems();});});}
    private void loadItems(){
        int request=++revision;status.setText("正在加载…");
        String path=mode.equals("resume")?api.userPath()+"/Items/Resume?Limit=60":api.userPath()+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&Limit=60&StartIndex="+offset+"&SortBy=SortName&Fields=PrimaryImageAspectRatio,Overview"+(parent.isEmpty()?"":"&ParentId="+EtknApi.encode(parent))+(mode.equals("search")?"&SearchTerm="+EtknApi.encode(query):"");
        async(()->api.get(path),result->{if(request!=revision)return;content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));List<JSONObject> items=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",items.size());status.setText(items.isEmpty()?"暂无影片":"共 "+total+" 项  ·  第 "+(offset/60+1)+" 页");GridLayout grid=grid();for(JSONObject item:items)mediaCard(grid,item,()->detail(item));LinearLayout pages=row();if(offset>0)pages.addView(button("上一页",()->{offset=Math.max(0,offset-60);loadItems();}));if(!mode.equals("resume")&&offset+items.size()<total)pages.addView(button("下一页",()->{offset+=60;loadItems();}));content.addView(pages);});
    }
    @Override public void onBackPressed(){if(menu.getVisibility()==View.VISIBLE){hideMenu();return;}if(!mode.equals("home")){loadHome();return;}super.onBackPressed();}
}
