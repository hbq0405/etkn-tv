package xyz.etkn.tv;

import android.graphics.Color;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** 播放中的选集沿用详情页数字矩阵、已看勾选与绿色进度填充。 */
final class PlayerEpisodePanel extends LinearLayout {
    private final TvActivity host;
    private final String currentId;
    private final Consumer<JSONObject> select;
    private final Runnable retry;
    private final TextView heading,description;
    private final LinearLayout body;
    private List<JSONObject> episodes=Collections.emptyList();
    private Button currentCell;
    private JSONObject currentEpisode;

    PlayerEpisodePanel(TvActivity host,String currentId,Consumer<JSONObject> select,Runnable retry){
        super(host);this.host=host;this.currentId=currentId;this.select=select;this.retry=retry;
        setOrientation(VERTICAL);setPadding(host.dp(24),host.dp(20),host.dp(24),host.dp(20));setBackgroundColor(Color.argb(245,11,17,26));setFocusable(true);setFocusableInTouchMode(true);
        heading=host.text("选集",23,Color.WHITE);addView(heading);
        description=host.text("",14,TvActivity.MUTED);description.setMaxLines(2);addView(description,new LayoutParams(-1,host.dp(56)));
        body=host.column();addView(body,new LayoutParams(-1,0,1));
    }
    boolean contains(View view){while(view!=null){if(view==this)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}return false;}
    void loading(){body.removeAllViews();body.addView(host.text("正在读取分集…",15,TvActivity.MUTED));requestFocus();}
    void failed(){body.removeAllViews();body.addView(host.text("分集加载失败，当前播放不受影响",15,TvActivity.MUTED));Button again=host.button("重试",retry);body.addView(again);again.requestFocus();}
    void showEpisodes(List<JSONObject> rows){
        episodes=rows;body.removeAllViews();currentCell=null;currentEpisode=null;
        if(rows.isEmpty()){body.addView(host.text("暂无可播放分集",15,TvActivity.MUTED));requestFocus();return;}
        LinkedHashSet<Integer> seasons=new LinkedHashSet<>();JSONObject selected=rows.get(0);
        for(JSONObject episode:rows){seasons.add(episode.optInt("ParentIndexNumber"));if(currentId.equals(episode.optString("Id")))selected=episode;}
        LinearLayout matrix=host.column();
        if(seasons.size()>1){LinearLayout seasonRow=host.row();HorizontalScrollView scroll=new FocusScroll.Row(host);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(seasonRow);body.addView(scroll);
            for(int season:seasons)seasonRow.addView(host.button(season==0?"特别篇":"第 "+season+" 季",()->showSeason(matrix,season,null)));
        }
        body.addView(matrix,new LayoutParams(-1,0,1));showSeason(matrix,selected.optInt("ParentIndexNumber"),selected);
    }
    private void showSeason(LinearLayout matrix,int season,JSONObject preferred){
        matrix.removeAllViews();currentCell=null;currentEpisode=null;
        List<JSONObject> rows=new ArrayList<>();for(JSONObject ep:episodes)if(ep.optInt("ParentIndexNumber")==season)rows.add(ep);
        if(rows.isEmpty())return;JSONObject entry=preferred==null?DetailPresentation.recommended(rows):preferred;
        heading.setText("选集 · "+(season==0?"特别篇":"第 "+season+" 季"));
        GridLayout grid=new GridLayout(host);int available=Math.min(480,host.getResources().getConfiguration().screenWidthDp*3/5)-48,columns=Math.max(3,available/112);grid.setColumnCount(columns);
        ScrollView scroll=new FocusScroll.Vertical(host);scroll.addView(grid);matrix.addView(scroll,new LayoutParams(-1,-1));Button focus=null;
        for(JSONObject ep:rows){boolean current=currentId.equals(ep.optString("Id"));Button cell=host.button("",()->select.accept(ep));cell.setSingleLine(true);cell.setPadding(host.dp(8),0,host.dp(8),0);cell.setTextColor(current?TvActivity.ACCENT:Color.WHITE);
            JSONObject data=ep.optJSONObject("UserData");boolean watched=data!=null&&data.optBoolean("Played");int progress=progress(watched,DetailPresentation.position(ep),ep.optLong("RunTimeTicks"));
            cell.setText(DetailPresentation.episodeNumber(ep)+(watched?" ✓":"")+(current?" ▶":""));cell.setBackground(host.episodeBackground(progress));describe(cell,ep,watched,progress,current);
            cell.setOnFocusChangeListener((view,focused)->{if(focused)description.setText((current?"正在播放 · ":"")+DetailPresentation.episodeNumber(ep)+" · "+EtknApi.title(ep));});
            GridLayout.LayoutParams bounds=new GridLayout.LayoutParams();bounds.width=(host.dp(available)-host.dp(10)*(columns-1))/columns;bounds.height=host.dp(44);bounds.setMargins(0,host.dp(7),grid.getChildCount()%columns==columns-1?0:host.dp(10),host.dp(7));grid.addView(cell,bounds);
            if(current){currentCell=cell;currentEpisode=ep;}if(ep==entry)focus=cell;
        }
        if(focus!=null)focus.requestFocus();
    }
    private int progress(boolean watched,long position,long runtime){return watched?10000:runtime>0?(int)Math.min(9999,10000.0*Math.max(0,position)/runtime):0;}
    private void describe(Button cell,JSONObject ep,boolean watched,int progress,boolean current){cell.setContentDescription(DetailPresentation.episodeNumber(ep)+"，"+EtknApi.title(ep)+(current?"，正在播放":"")+(watched?"，已观看":progress>0?"，续播进度 "+progress/100+"%":""));}
    void updateCurrent(ExoPlayer player){
        if(currentCell==null||currentEpisode==null||player==null)return;JSONObject data=currentEpisode.optJSONObject("UserData");boolean watched=data!=null&&data.optBoolean("Played");
        long runtime=player.getDuration()>0?player.getDuration()*10000:currentEpisode.optLong("RunTimeTicks");int progress=progress(watched,player.getCurrentPosition()*10000,runtime);
        ((LayerDrawable)currentCell.getBackground()).getDrawable(1).setLevel(progress);describe(currentCell,currentEpisode,watched,progress,true);
    }
}
