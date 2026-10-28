package xyz.etkn.tv;

import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

public final class DetailActivity extends TvActivity {
    private JSONObject item;
    @Override public void onCreate(Bundle state){super.onCreate(state);screen("影片详情","");nav("返回",this::finish);load();}
    private void load(){async(()->api.get(api.userPath()+"/Items/"+EtknApi.encode(getIntent().getStringExtra("id"))),result->{item=result;content.removeAllViews();content.addView(text(EtknApi.title(item),28,android.graphics.Color.WHITE));content.addView(text(item.optString("ProductionYear")+"  ·  "+item.optString("OfficialRating")+"  ·  "+item.optString("Type"),13,MUTED));TextView description=text(item.optString("Overview"),16,MUTED);description.setMaxLines(5);content.addView(description);
            if(item.optString("Type").equals("Series"))loadSeasons();else if(item.optString("Type").equals("Season"))loadEpisodes(item.optString("SeriesId",item.optString("ParentId")),item.optString("Id"));else {long ticks=item.optJSONObject("UserData")==null?0:item.optJSONObject("UserData").optLong("PlaybackPositionTicks");content.addView(button(ticks>0?"继续播放":"播放",()->play(item,ticks)));if(ticks>0)content.addView(button("从头播放",()->play(item,0)));}
        });}
    private void loadSeasons(){async(()->api.get("/api/tv/Shows/"+EtknApi.encode(item.optString("Id"))+"/Seasons?UserId="+EtknApi.encode(api.session.userId())),result->{status.setText("选择季度");LinearLayout seasons=row();HorizontalScrollView seasonScroll=new HorizontalScrollView(this);seasonScroll.addView(seasons);content.addView(seasonScroll);LinearLayout episodeArea=column();content.addView(episodeArea);for(JSONObject season:EtknApi.rows(result))seasons.addView(button(EtknApi.title(season),()->{episodeArea.removeAllViews();loadEpisodeRows(item.optString("Id"),season.optString("Id"),episodeArea);}));if(!EtknApi.rows(result).isEmpty())loadEpisodeRows(item.optString("Id"),EtknApi.rows(result).get(0).optString("Id"),episodeArea);});}
    private void loadEpisodes(String series,String season){LinearLayout area=column();content.addView(area);loadEpisodeRows(series,season,area);}
    private int episodeRequest;
    private void loadEpisodeRows(String series,String season,LinearLayout target){int request=++episodeRequest;status.setText("正在读取分集…");async(()->api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&SeasonId="+EtknApi.encode(season)),result->{if(request!=episodeRequest)return;target.removeAllViews();List<JSONObject> episodes=EtknApi.rows(result);status.setText(episodes.size()+" 集");for(JSONObject episode:episodes){long position=episode.optJSONObject("UserData")==null?0:episode.optJSONObject("UserData").optLong("PlaybackPositionTicks");Button button=button("第 "+episode.optInt("IndexNumber")+" 集  "+EtknApi.title(episode)+(position>0?"  ·  继续观看":""),()->play(episode,position));target.addView(button);}});}
    private void play(JSONObject playable,long ticks){startActivity(new Intent(this,PlayerActivity.class).putExtra("id",playable.optString("Id")).putExtra("title",EtknApi.title(playable)).putExtra("position",ticks/10000));}
}
