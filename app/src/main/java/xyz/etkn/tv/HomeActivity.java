package xyz.etkn.tv;

import android.os.Bundle;
import android.app.AlertDialog;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

public final class HomeActivity extends TvActivity {
    private String parent="",query="",mode="home";
    private int offset,revision;
    private boolean canDiscover;
    @Override public void onCreate(Bundle state){super.onCreate(state);screen("为你而选",api.session.name()+"  ·  ETKN 私人影院");refreshNavigation();loadHome();}
    @Override protected void onResume(){super.onResume();async(()->api.get("/api/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");canDiscover=user!=null&&user.optBoolean("can_access_discovery");refreshNavigation();});}
    private void refreshNavigation(){
        nav.removeAllViews();ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setContentDescription("当前用户头像");
        LinearLayout.LayoutParams face=new LinearLayout.LayoutParams(dp(38),dp(38));face.setMargins(0,dp(8),dp(12),0);nav.addView(avatar,face);loadAvatar(avatar);
        nav("首页",this::loadHome);nav("媒体库",this::loadLibraries);nav("继续观看",()->{mode="resume";parent="";offset=0;loadItems();});nav("搜索",this::search);if(canDiscover)nav("影视探索",()->open(DiscoveryActivity.class));nav("设置",()->open(SettingsActivity.class));
    }
    private void loadHome(){
        mode="home";int request=++revision;content.removeAllViews();status.setText("正在准备你的首页…");
        LinearLayout heroArea=column();content.addView(heroArea);heroArea.addView(text("正在读取你的观影推荐…",15,MUTED));LinearLayout libraries=column();content.addView(libraries);
        async(()->{try{return api.get(api.userPath()+"/Items/PersonalRecommendations?Limit=12&Fields=Overview");}catch(EtknApi.AuthException e){throw e;}catch(Exception e){return new JSONObject().put("unavailable",true);}},result->{
            if(request!=revision)return;heroArea.removeAllViews();List<JSONObject> items=EtknApi.rows(result);
            if(items.isEmpty())heroArea.addView(text(result.optBoolean("unavailable")?"个人推荐暂不可用，请确认 ETKN 已更新。":"多看几部、收藏喜欢的影片，首页会逐渐懂你的口味。",16,MUTED));
            else {RecommendationHero hero=new RecommendationHero(this,items);heroArea.addView(hero,new LinearLayout.LayoutParams(-1,dp(300)));}
        });
        async(()->api.get(api.userPath()+"/Views"),result->{if(request!=revision)return;status.setText("");libraries.addView(text("我的媒体库",19,android.graphics.Color.WHITE));GridLayout grid=grid(libraries);for(JSONObject item:EtknApi.rows(result))mediaCard(grid,item,()->{parent=item.optString("Id");mode="items";offset=0;loadItems();});});
    }
    private void search(){EditText field=input("搜索片库",false);new AlertDialog.Builder(this).setTitle("搜索片库").setView(field).setPositiveButton("搜索",(dialog,which)->{query=field.getText().toString();parent="";offset=0;mode="search";loadItems();}).setNegativeButton("取消",null).show();}
    private void loadLibraries(){mode="library";int request=++revision;status.setText("正在读取媒体库…");async(()->api.get(api.userPath()+"/Views"),result->{if(request!=revision)return;content.removeAllViews();List<JSONObject> items=EtknApi.rows(result);status.setText(items.isEmpty()?"暂无可访问媒体库":"选择媒体库");GridLayout grid=grid();for(JSONObject item:items)mediaCard(grid,item,()->{parent=item.optString("Id");mode="items";offset=0;loadItems();});});}
    private void loadItems(){
        int request=++revision;status.setText("正在加载…");
        String path=mode.equals("resume")?api.userPath()+"/Items/Resume?Limit=60":api.userPath()+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&Limit=60&StartIndex="+offset+"&SortBy=SortName&Fields=PrimaryImageAspectRatio,Overview"+(parent.isEmpty()?"":"&ParentId="+EtknApi.encode(parent))+(mode.equals("search")?"&SearchTerm="+EtknApi.encode(query):"");
        async(()->api.get(path),result->{if(request!=revision)return;content.removeAllViews();List<JSONObject> items=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",items.size());status.setText(items.isEmpty()?"暂无影片":"共 "+total+" 项  ·  第 "+(offset/60+1)+" 页");GridLayout grid=grid();for(JSONObject item:items)mediaCard(grid,item,()->detail(item));LinearLayout pages=row();if(offset>0)pages.addView(button("上一页",()->{offset=Math.max(0,offset-60);loadItems();}));if(!mode.equals("resume")&&offset+items.size()<total)pages.addView(button("下一页",()->{offset+=60;loadItems();}));content.addView(pages);});
    }
    @Override public void onBackPressed(){if(!mode.equals("home")){loadHome();return;}super.onBackPressed();}
}
