package xyz.etkn.tv;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class DiscoveryActivity extends TvActivity {
    private String kind="movie",query="";
    private int page=1,revision;
    private JSONObject selected;
    private Integer season;
    private boolean allowed;
    @Override public void onCreate(Bundle state){super.onCreate(state);screen("影视探索","寻找下一部想看的影片");nav("返回",this::finish);status.setText("正在检查访问权限…");}
    @Override protected void onResume(){super.onResume();async(()->api.get("/api/tv/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");allowed=user!=null&&user.optBoolean("can_access_discovery");if(!allowed){content.removeAllViews();status.setText("当前账号没有影视探索权限。请检查实例会员与用户模板设置。");return;}navigation();if(selected==null)load();});}
    private void navigation(){nav.removeAllViews();nav("返回",this::finish);nav("电影",()->{kind="movie";page=1;selected=null;load();});nav("剧集",()->{kind="series";page=1;selected=null;load();});nav("搜索",()->{EditText search=input("影片名称",false);search.setText(query);new AlertDialog.Builder(this).setTitle("搜索影视").setView(search).setPositiveButton("搜索",(dialog,which)->{query=search.getText().toString();page=1;selected=null;load();}).setNegativeButton("取消",null).show();});}
    private void load(){
        if(!allowed)return;selected=null;int request=++revision;status.setText("正在探索…");
        async(()->api.post("/api/tv/discover/query/"+kind,new JSONObject().put("page",page).put("query",query)),result->{
            if(request!=revision)return;content.removeAllViews();List<JSONObject> rows=EtknApi.rows(result);status.setText((kind.equals("movie")?"电影":"剧集")+"  ·  第 "+page+" 页");GridLayout grid=grid();for(JSONObject item:rows)mediaCard(grid,item,()->select(item,null));LinearLayout pages=row();if(page>1)pages.addView(button("上一页",()->{page--;load();}));if(page<result.optInt("total_pages",1))pages.addView(button("下一页",()->{page++;load();}));content.addView(pages);
        });
    }
    private void select(JSONObject item,Integer seasonNumber){
        ++revision;selected=item;season=seasonNumber;content.removeAllViews();status.setText("");content.addView(text(EtknApi.title(item)+(season==null?"":"  ·  第 "+season+" 季"),27,android.graphics.Color.WHITE));
        TextView synopsis=text(item.optString("overview"),16,MUTED);synopsis.setMaxLines(5);content.addView(synopsis);
        if(item.optBoolean("in_library"))content.addView(button("在片库中查看",this::libraryMatches));
        if(kind.equals("series")&&season==null){
            content.addView(text("选择要订阅或搜索的季度",16,ACCENT));int request=revision;
            async(()->api.get("/api/tv/discover/series/"+EtknApi.encode(item.optString("id"))),details->{if(request!=revision)return;JSONArray seasons=details.optJSONArray("seasons");if(seasons!=null)for(int i=0;i<seasons.length();i++){JSONObject s=seasons.optJSONObject(i);if(s==null)continue;int number=s.optInt("season_number");content.addView(button("第 "+number+" 季  "+s.optString("name")+"  ·  "+s.optInt("episode_count")+" 集",()->select(item,number)));}});
        }else{content.addView(button("订阅"+(season==null?"影片":"本季"),this::subscribe));content.addView(button("搜索云资源",this::searchResources));}
        content.addView(button("返回探索",this::load));
    }
    private void subscribe(){
        JSONObject item=selected;Integer selectedSeason=season;
        new AlertDialog.Builder(this).setTitle("确认订阅").setMessage("订阅“"+EtknApi.title(item)+(selectedSeason==null?"":" · 第 "+selectedSeason+" 季")+"”？").setPositiveButton("订阅",(dialog,which)->{
            status.setText("正在提交订阅…");async(()->{JSONObject body=new JSONObject().put("tmdb_id",item.optString("id")).put("name",EtknApi.title(item)).put("entity_type",selectedSeason==null?"movie":"season").put("poster_path",item.optString("poster_path")).put("release_date",item.optString("release_date")).put("first_air_date",item.optString("first_air_date"));if(selectedSeason!=null)body.put("season_number",selectedSeason);return api.post("/api/tv/discover/subscribe",body);},result->status.setText("订阅已提交，服务端将按配置获取资源。"));
        }).setNegativeButton("取消",null).show();
    }
    private void searchResources(){
        int request=++revision;JSONObject item=selected;Integer wantedSeason=season;content.removeAllViews();status.setText("正在搜索共享池、re0 和频道…");content.addView(text(EtknApi.title(item)+(season==null?"":" · 第 "+season+" 季"),24,android.graphics.Color.WHITE));
        content.addView(button("返回影片",()->select(item,wantedSeason)));String tmdb=EtknApi.encode(item.optString("id")),title=EtknApi.encode(EtknApi.title(item)),type=kind.equals("movie")?"movie":"tv";
        String[] providers={"shared_pool","re0","channel"};
        String[] paths={"/api/tv/discover/shared-resources?tmdb_id="+tmdb+"&item_type="+(wantedSeason==null?type:"season")+"&q="+title+"&limit=100",
                "/api/tv/discover/re0-resources?tmdb_id="+tmdb+"&media_type="+type+"&require_complete=false"+(wantedSeason==null?"":"&season="+wantedSeason),
                "/api/tv/discover/channel-resources?tmdb_id="+tmdb+"&media_type="+type+"&q="+title+(wantedSeason==null?"":"&season_number="+wantedSeason)};
        int[] pending={3};
        for(int i=0;i<providers.length;i++){
            String provider=providers[i],path=paths[i];LinearLayout area=column();content.addView(area);area.addView(text(provider.equals("shared_pool")?"共享池":provider.equals("re0")?"re0":"频道",18,ACCENT));TextView sourceStatus=text("搜索中…",13,MUTED);area.addView(sourceStatus);
            IO.execute(()->{try{List<CloudResources.Resource> rows=CloudResources.parse(provider,api.get(path),wantedSeason);runOnUiThread(()->{if(isDestroyed()||request!=revision)return;sourceStatus.setText(rows.isEmpty()?"暂无匹配资源":rows.size()+" 个资源版本");int index=0;for(CloudResources.Resource row:rows){String label=CloudResources.label(row,index++);area.addView(button(label,()->acquire(row,label,item,wantedSeason,type)));}if(--pending[0]==0)status.setText("搜索完成，请选择资源版本并确认获取。");});}catch(Exception error){runOnUiThread(()->{if(isDestroyed()||request!=revision)return;sourceStatus.setText(error instanceof EtknApi.AuthException?"登录已失效":error.getMessage());if(--pending[0]==0)status.setText("搜索完成，部分来源暂不可用。");});}});
        }
    }
    private void acquire(CloudResources.Resource row,String label,JSONObject item,Integer wantedSeason,String type){
        new AlertDialog.Builder(this).setTitle("确认获取此版本").setMessage(label+"\n\n将按 ETKN 配置转存或获取并入库；可能消耗服务端资源积分。").setPositiveButton("确认获取",(dialog,which)->{
            status.setText("正在提交获取任务…");async(()->{
                if(row.provider.equals("shared_pool"))return api.post("/api/tv/discover/shared-resources/acquire",new JSONObject().put("source",CloudResources.sharedSource(row.source)));
                if(row.provider.equals("channel"))return api.post("/api/tv/discover/channel-resources/acquire",new JSONObject().put("resource",row.source));
                JSONObject body=new JSONObject().put("slug",row.source.optString("slug",row.source.optString("id"))).put("title",EtknApi.title(item)).put("tmdb_id",item.optString("id")).put("media_type",type);if(wantedSeason!=null)body.put("season_number",wantedSeason);return api.post("/api/tv/discover/re0-resources/acquire",body);
            },result->{status.setText("获取任务已提交，入库后即可观看。");content.addView(button("刷新入库状态 / 观看",this::libraryMatches),0);});
        }).setNegativeButton("取消",null).show();
    }
    private void libraryMatches(){JSONObject item=selected;int request=++revision;status.setText("正在查询片库…");async(()->api.get(api.userPath()+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&Limit=60&Fields=ProviderIds&SearchTerm="+EtknApi.encode(EtknApi.title(item))),result->{if(request!=revision)return;content.removeAllViews();content.addView(button("返回影片",()->select(item,season)));GridLayout grid=grid();int found=0;for(JSONObject media:EtknApi.rows(result)){JSONObject ids=media.optJSONObject("ProviderIds");if(ids!=null&&item.optString("id").equals(ids.optString("Tmdb"))){mediaCard(grid,media,()->detail(media));found++;}}status.setText(found>0?"已入库，选择影片观看。":"尚未查到入库影片，可稍后刷新。");content.addView(button("刷新入库状态",this::libraryMatches));});}
    @Override public void onBackPressed(){if(selected!=null){selected=null;load();}else super.onBackPressed();}
}
