package xyz.etkn.tv;

import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** 播放中的右侧分集卡片：按页取元数据，只加载可见剧照。 */
final class PlayerEpisodePanel extends LinearLayout {
    private final TvActivity host;
    private final String currentId,seriesId;
    private final Consumer<JSONObject> select;
    private final TextView heading,notice;
    private final LinearLayout body,cards,scrollBody;
    private final ScrollView viewport;
    private final FrameLayout viewportFrame;
    private final Button retry;
    private final List<JSONObject> episodes=new ArrayList<>();
    private final Map<String,View> cells=new LinkedHashMap<>();
    private List<JSONObject> seasons=Collections.emptyList();
    private String seasonId="";
    private int start,next,total,ticket;
    private boolean initialized,loading;
    private Runnable retryPage;
    private View currentCell;
    private JSONObject currentEpisode;
    private ListView queueView;

    PlayerEpisodePanel(TvActivity host,String currentId,String seriesId,Consumer<JSONObject> select){
        super(host);this.host=host;this.currentId=currentId;this.seriesId=seriesId;this.select=select;
        setOrientation(VERTICAL);setPadding(host.dp(18),host.dp(20),host.dp(18),host.dp(20));setBackground(host.shape(TvActivity.PANEL,0));setFocusable(true);setFocusableInTouchMode(true);
        heading=host.text("分集列表",21,TvActivity.TEXT);addView(heading);
        body=host.column();addView(body,new LayoutParams(-1,0,1));
        viewport=new FocusScroll.Vertical(host);viewport.setClipChildren(true);viewport.setClipToPadding(true);
        viewportFrame=new FrameLayout(host){@Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);setClipBounds(new android.graphics.Rect(0,0,width,height));}};viewportFrame.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
        scrollBody=host.column();viewport.addView(scrollBody);cards=host.column();cards.setPadding(host.dp(6),host.dp(12),host.dp(6),host.dp(12));scrollBody.addView(cards);
        viewport.setOnScrollChangeListener((view,x,y,oldX,oldY)->{if(isShown()&&initialized&&!loading&&retryPage==null&&next<total&&y+view.getHeight()>=scrollBody.getHeight()-host.dp(280))loadPage(next,false,false);});
        notice=host.text("",13,TvActivity.MUTED);addView(notice);
        retry=host.button("重试",()->{if(retryPage!=null)retryPage.run();});retry.setBackground(host.selectionBackground());retry.setVisibility(GONE);addView(retry);
        host.imageSizeUpdater(this,()->{android.view.ViewGroup.LayoutParams bounds=getLayoutParams();if(bounds!=null&&bounds.width!=panelWidth()){bounds.width=panelWidth();setLayoutParams(bounds);}});
        viewport.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot)for(int i=0;i<cards.getChildCount();i++)((Runnable)cards.getChildAt(i).getTag(R.id.image_size_updater)).run();});
    }
    int panelWidth(){return Math.min(host.imageDp(400),host.dp(host.getResources().getConfiguration().screenWidthDp*3/5));}
    boolean contains(View view){while(view!=null){if(view==this)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}return false;}
    void openQueue(String[] titles, int current, IntConsumer choose) {
        if (queueView == null) {
            heading.setText("播放列表 · " + titles.length + " 个 MV");
            ListView list = queueView = new ListView(host);
            list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            list.setAdapter(new ArrayAdapter<String>(host, android.R.layout.simple_list_item_1, titles) {
                @Override public View getView(int index, View recycled, android.view.ViewGroup parent) {
                    TextView row = (TextView) super.getView(index, recycled, parent);
                    boolean playing = index == current;
                    row.setText(playing ? getItem(index) + " · 正在播放" : getItem(index));
                    row.setTextColor(playing ? TvActivity.ACCENT : TvActivity.TEXT);
                    row.setMaxLines(2);
                    row.setEllipsize(TextUtils.TruncateAt.END);
                    return row;
                }
            });
            list.setOnItemClickListener((parent, row, index, id) -> choose.accept(index));
            body.addView(list, new LayoutParams(-1, -1));
        }
        queueView.requestFocus();
        queueView.setSelection(current);
    }
    boolean showingQueue() { return queueView != null; }
    void open(){
        if(loading)return;
        if(initialized){focus(currentCell==null?cards.getChildAt(0):currentCell);return;}
        if(isShown())requestFocus();
        loading=true;notice.setText("正在读取分集…");retry.setVisibility(GONE);retryPage=null;int request=++ticket;
        host.async(()->{
            try{
                JSONObject data=new JSONObject().put("seasons",host.api.get("/api/tv/Shows/"+EtknApi.encode(seriesId)+"/Seasons?UserId="+EtknApi.encode(host.api.session.userId())));
                JSONObject current=host.api.get(host.api.userPath()+"/Items/"+EtknApi.encode(currentId));String season=current.optString("SeasonId");
                return data.put("page",fetch(season,currentId,0,40)).put("season",season);
            }catch(EtknApi.AuthException error){throw error;}catch(Exception error){return null;}
        },data->{
            if(request!=ticket)return;loading=false;
            if(data==null){failed(this::open);return;}
            initialized=true;seasons=EtknApi.rows(data.optJSONObject("seasons"));seasonId=data.optString("season");
            body.removeAllViews();scrollBody.removeAllViews();
            if(seasons.size()>1){LinearLayout choices=host.row();HorizontalScrollView rail=new FocusScroll.Row(host);rail.setHorizontalScrollBarEnabled(false);rail.addView(choices);scrollBody.addView(rail);
                for(JSONObject season:seasons)choices.addView(host.seasonCard(season,()->chooseSeason(season.optString("Id"))));
            }
            scrollBody.addView(cards);body.addView(viewportFrame,new LayoutParams(-1,0,1));applyPage(data.optJSONObject("page"),false,true,currentId);
        });
    }
    private JSONObject fetch(String season,String anchor,int offset,int limit)throws Exception{
        String path="/api/tv/Shows/"+EtknApi.encode(seriesId)+"/EpisodePage?UserId="+EtknApi.encode(host.api.session.userId())+"&Limit="+limit;
        if(!season.isEmpty())path+="&SeasonId="+EtknApi.encode(season);
        if(!anchor.isEmpty())path+="&AnchorId="+EtknApi.encode(anchor);else path+="&StartIndex="+offset;
        return host.api.get(path);
    }
    private void chooseSeason(String id){seasonId=id;loadPage(0,false,true);}
    private void failed(Runnable action){retryPage=action;notice.setText("分集加载失败，当前播放不受影响");retry.setVisibility(VISIBLE);if(isShown()&&cards.getChildCount()==0)retry.requestFocus();}
    private void loadPage(int offset,boolean previous,boolean reset){
        if(loading&&!reset)return;loading=true;retryPage=null;retry.setVisibility(GONE);notice.setText(previous?"正在读取前面的分集…":"正在读取分集…");
        int request=++ticket,limit=previous?Math.min(40,start):40;String season=seasonId,restore=focusedId();
        if(reset){episodes.clear();cells.clear();cards.removeAllViews();currentCell=null;currentEpisode=null;}
        host.async(()->{try{return fetch(season,"",offset,limit);}catch(EtknApi.AuthException error){throw error;}catch(Exception error){return null;}},page->{
            if(request!=ticket)return;loading=false;
            if(page==null){failed(()->loadPage(offset,previous,reset));return;}
            String retained=focusedId();applyPage(page,previous,reset,retained.isEmpty()?restore:retained);
        });
    }
    private String focusedId(){for(Map.Entry<String,View> entry:cells.entrySet())if(entry.getValue().hasFocus())return entry.getKey();return "";}
    private void applyPage(JSONObject page,boolean previous,boolean reset,String restore){
        List<JSONObject> rows=EtknApi.rows(page);
        if(reset){episodes.clear();start=page.optInt("StartIndex");next=page.optInt("NextStartIndex",start+rows.size());}
        else if(previous)start=page.optInt("StartIndex");
        else next=page.optInt("NextStartIndex",next+rows.size());
        total=page.optInt("TotalRecordCount",next);if(previous)episodes.addAll(0,rows);else episodes.addAll(rows);
        if(seasonId.isEmpty()&&!episodes.isEmpty())seasonId=episodes.get(0).optString("SeasonId");
        heading.setText("分集列表 · "+total+" 集");notice.setText(episodes.isEmpty()?"暂无分集":start>0?"上键加载前面的分集":"");
        if(reset||previous){cards.removeAllViews();cells.clear();currentCell=null;currentEpisode=null;appendCards(episodes);View target=cells.get(restore);focus(target==null?cards.getChildAt(0):target);}
        else appendCards(rows);
    }
    private void focus(View target){if(target!=null)target.post(()->{if(isShown()&&target.isAttachedToWindow()){target.requestFocus();viewport.scrollTo(0,cards.getTop()+target.getTop()-cards.getPaddingTop());}});}
    private void appendCards(List<JSONObject> rows){
        for(JSONObject episode:rows){
            boolean current=currentId.equals(episode.optString("Id"));JSONObject data=episode.optJSONObject("UserData");boolean watched=data!=null&&data.optBoolean("Played");
            int progress=progress(watched,DetailPresentation.position(episode),episode.optLong("RunTimeTicks"));
            LinearLayout card=new LinearLayout(host);card.setOrientation(HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setFocusable(true);card.setClickable(true);card.setBackground(host.episodeBackground(progress));
            card.setOnClickListener(view->select.accept(episode));describe(card,episode,watched,progress,current);
            cards.addView(card,new LayoutParams(-1,-2));cells.put(episode.optString("Id"),card);
            FrameLayout art=new FrameLayout(host);card.addView(art,new LayoutParams(0,0));
            ImageView image=new ImageView(host);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,host.dp(8));image.setForeground(Images.border(host.dp(8)));art.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView missing=host.text("暂无剧照",13,TvActivity.MUTED);missing.setGravity(Gravity.CENTER);art.addView(missing,new FrameLayout.LayoutParams(-1,-1));
            Images.visibleArtwork(host.api,size->host.api.artwork(episode,"Primary",size),image,ok->missing.setVisibility(ok?GONE:VISIBLE));
            TextView badge=host.text(current?"正在播放":watched?"✓ 已观看":episode.optBoolean("IsUnaired")?"未播出":"未入库",10,current||watched?TvActivity.ACCENT:TvActivity.TEXT);
            if(watched||current||DetailPresentation.placeholder(episode)){badge.setBackground(host.shape(TvActivity.PANEL,0));badge.setPadding(host.dp(4),host.dp(2),host.dp(4),host.dp(2));FrameLayout.LayoutParams mark=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);mark.setMargins(host.dp(3),host.dp(3),host.dp(3),0);art.addView(badge,mark);}
            LinearLayout info=host.column();info.setGravity(Gravity.CENTER_VERTICAL);card.addView(info,new LayoutParams(0,-1,1));
            TextView title=host.text(DetailPresentation.episodeNumber(episode)+" · "+EtknApi.title(episode),14,current?TvActivity.ACCENT:TvActivity.TEXT);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);title.setIncludeFontPadding(false);info.addView(title);
            TextView date=host.text("播出："+DetailPresentation.airDate(episode),11,TvActivity.MUTED);date.setMaxLines(1);date.setEllipsize(TextUtils.TruncateAt.END);date.setIncludeFontPadding(false);if(DetailPresentation.placeholder(episode))info.addView(date);
            TextView overview=host.text(episode.optString("Overview").isEmpty()?"暂无分集简介":episode.optString("Overview"),12,TvActivity.MUTED);overview.setMaxLines(3);overview.setEllipsize(TextUtils.TruncateAt.END);overview.setIncludeFontPadding(false);info.addView(overview);
            Runnable resize=()->{
                int gap=host.imageDp(6),height=host.imageDp(108);
                // 每档最多占视口的三分之一，给三条完整分集及间距留足空间。
                if(viewport.getHeight()>0)height=Math.min(height,Math.max(1,(viewport.getHeight()-cards.getPaddingTop()-cards.getPaddingBottom())/3-gap));
                float scale=Math.min(host.imageScale(),(float)height/host.dp(108));int padding=Math.round(host.dp(6)*scale);
                LayoutParams bounds=(LayoutParams)card.getLayoutParams();bounds.height=height;bounds.bottomMargin=gap;card.setLayoutParams(bounds);card.setPadding(padding,padding,padding,padding);
                LayoutParams picture=(LayoutParams)art.getLayoutParams();picture.width=Math.round(host.dp(128)*scale);picture.height=picture.width*9/16;art.setLayoutParams(picture);
                info.setPadding(Math.round(host.dp(8)*scale),0,Math.round(host.dp(2)*scale),0);title.setPadding(0,0,0,Math.round(host.dp(3)*scale));
                overview.setPadding(0,0,0,0);date.setPadding(0,0,0,0);title.setTextSize(14*scale);overview.setTextSize(12*scale);date.setTextSize(11*scale);badge.setTextSize(10*scale);missing.setTextSize(11*scale);
            };
            host.imageSizeUpdater(card,resize);resize.run();
            if(current){currentCell=card;currentEpisode=episode;}
        }
    }
    boolean navigate(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;int key=event.getKeyCode(),index=cards.indexOfChild(host.getCurrentFocus());
        if(index<0)return false;
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(index>0)cards.getChildAt(index-1).requestFocus(View.FOCUS_UP);else if(start>0){if(!loading)loadPage(Math.max(0,start-40),true,false);}else return seasons.size()<=1;return true;}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(index+1<cards.getChildCount())cards.getChildAt(index+1).requestFocus(View.FOCUS_DOWN);else if(next<total&&!loading)loadPage(next,false,false);return true;}
        return false;
    }
    private int progress(boolean watched,long position,long runtime){return watched?10000:runtime>0?(int)Math.min(9999,10000.0*Math.max(0,position)/runtime):0;}
    private void describe(View cell,JSONObject ep,boolean watched,int progress,boolean current){cell.setContentDescription(DetailPresentation.episodeNumber(ep)+"，"+EtknApi.title(ep)+(current?"，正在播放":"")+(watched?"，已观看":progress>0?"，续播进度 "+progress/100+"%":"")+(DetailPresentation.placeholder(ep)?"，播出："+DetailPresentation.airDate(ep):""));}
    void updateCurrent(ExoPlayer player){
        if(currentCell==null||currentEpisode==null||player==null)return;JSONObject data=currentEpisode.optJSONObject("UserData");boolean watched=data!=null&&data.optBoolean("Played");
        long runtime=player.getDuration()>0?player.getDuration()*10000:currentEpisode.optLong("RunTimeTicks");int progress=progress(watched,player.getCurrentPosition()*10000,runtime);
        ((LayerDrawable)currentCell.getBackground()).getDrawable(1).setLevel(progress);describe(currentCell,currentEpisode,watched,progress,true);
    }
}
