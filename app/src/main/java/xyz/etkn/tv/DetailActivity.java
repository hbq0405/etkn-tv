package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class DetailActivity extends TvActivity {
    private JSONObject item,playable;
    private final List<JSONObject> allEpisodes=new ArrayList<>();
    private JSONArray sources=new JSONArray();
    private int sourceIndex,audio=PlaybackSelection.AUTO,subtitle=PlaybackSelection.AUTO;
    private Button playButton,versionButton,audioButton,subtitleButton,restartButton;
    private LinearLayout episodeArea,preview;
    private TextView episodeTitle,episodeOverview;
    private ImageView backdrop,episodeImage;
    private ScrollView scroll;
    private GridLayout episodeGrid;
    private Button episodeEntry;

    private boolean inEpisodeMatrix(View view){while(view!=null){if(view==episodeGrid)return true;android.view.ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;}return false;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&episodeEntry!=null&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP)){
            View current=getCurrentFocus();int direction=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:View.FOCUS_UP;
            if(current!=null&&!inEpisodeMatrix(current)&&inEpisodeMatrix(current.focusSearch(direction))){episodeEntry.requestFocus();return true;}
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(backdrop,0);
        stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        stage.addView(backdropShade(),new FrameLayout.LayoutParams(-1,-1));
        scroll=new FocusScroll.Vertical(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=column();content.setPadding(dp(42),dp(26),dp(42),dp(28));scroll.addView(content);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        status=text("正在读取详情…",13,MUTED);content.addView(status);setContentView(stage);
        async(()->api.get(api.userPath()+"/Items/"+EtknApi.encode(getIntent().getStringExtra("id"))),this::render);
    }

    private void render(JSONObject result){
        item=result;content.removeAllViews();
        Images.load(api,api.artwork(item,"Backdrop",1920),backdrop);
        FrameLayout identity=new FrameLayout(this);int titleWidth=Math.min(560,getResources().getConfiguration().screenWidthDp-84);
        TextView title=text(EtknApi.title(item),32,Color.WHITE);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);title.setMaxLines(2);
        identity.addView(title,new FrameLayout.LayoutParams(-1,-1));
        ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_START);Images.rounded(logo,dp(4));logo.setVisibility(View.GONE);
        identity.addView(logo,new FrameLayout.LayoutParams(dp(Math.min(360,titleWidth)),dp(88),Gravity.CENTER_VERTICAL));
        content.addView(identity,new LinearLayout.LayoutParams(dp(titleWidth),dp(96)));
        Images.load(api,api.artwork(item,"Logo",720),logo,ok->{logo.setVisibility(ok?View.VISIBLE:View.GONE);title.setVisibility(ok?View.GONE:View.VISIBLE);});
        LinearLayout metadata=row();metadata.setGravity(Gravity.CENTER_VERTICAL);
        ImageView douban=new ImageView(this);douban.setImageDrawable(doubanMark());douban.setPadding(dp(3),dp(3),dp(3),dp(3));douban.setBackground(shape(Color.rgb(0,119,34),0));metadata.addView(douban,new LinearLayout.LayoutParams(dp(21),dp(21)));
        metadata.addView(meta(" "+DetailPresentation.rating(item,"DoubanRating")+"    ",Color.WHITE));
        ImageView tmdb=new ImageView(this);tmdb.setImageDrawable(tmdbMark());metadata.addView(tmdb,new LinearLayout.LayoutParams(dp(21),dp(21)));
        metadata.addView(meta(" "+DetailPresentation.rating(item,"TMDbRating")+"    ",Color.rgb(255,205,75)));
        String year=item.optString("ProductionYear");if(!year.isEmpty())metadata.addView(meta(year+"    ",MUTED));
        String duration=DetailPresentation.duration(item.optLong("RunTimeTicks"));if(!duration.isEmpty())metadata.addView(meta(duration+"    ",MUTED));
        String rating=item.optString("OfficialRating");if(!rating.isEmpty()){TextView certificate=meta(rating,Color.WHITE);certificate.setPadding(dp(7),dp(2),dp(7),dp(2));certificate.setBackground(shape(Color.TRANSPARENT,MUTED));metadata.addView(certificate);}
        content.addView(metadata);
        String categories=DetailPresentation.categories(item);if(!categories.isEmpty())content.addView(text(categories,14,MUTED));
        LinearLayout actions=row();
        playButton=button("播放",()->play(false));playButton.setId(View.generateViewId());playButton.setEnabled(false);actions.addView(playButton);
        restartButton=button("从头播放",()->play(true));restartButton.setVisibility(View.GONE);actions.addView(restartButton);content.addView(actions);
        LinearLayout selections=row();versionButton=button("版本",this::chooseVersion);audioButton=button("音轨",()->chooseStream(false));subtitleButton=button("字幕",()->chooseStream(true));
        selections.addView(versionButton);selections.addView(audioButton);selections.addView(subtitleButton);
        HorizontalScrollView selectionScroll=new FocusScroll.Row(this);selectionScroll.setHorizontalScrollBarEnabled(false);selectionScroll.addView(selections);content.addView(selectionScroll);
        TextView overview=text(item.optString("Overview","暂无简介"),15,Color.rgb(219,227,237));overview.setMaxLines(3);overview.setEllipsize(android.text.TextUtils.TruncateAt.END);overview.setFocusable(true);overview.setBackground(transparentFocusBackground());overview.setPadding(dp(10),dp(5),dp(10),dp(5));overview.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(EtknApi.title(item)).setMessage(item.optString("Overview")).setPositiveButton("关闭",null).show());
        content.addView(overview,new LinearLayout.LayoutParams(dp(Math.min(650,getResources().getConfiguration().screenWidthDp-84)),-2));
        status=text("",12,MUTED);content.addView(status);episodeArea=column();content.addView(episodeArea);updateChoices();
        String type=item.optString("Type");
        if(type.equals("Series")||type.equals("Season")||type.equals("Episode"))loadEpisodes();else{setPlayable(item);focusPlay();}
        renderCast();
        renderSimilar();
    }
    private TextView meta(String value,int color){TextView label=text(value,13,color);label.setGravity(Gravity.CENTER_VERTICAL);return label;}
    private android.graphics.drawable.Drawable episodeBackground(int progress){
        android.graphics.drawable.StateListDrawable outline=new android.graphics.drawable.StateListDrawable();
        outline.addState(new int[]{android.R.attr.state_focused},new FocusPulse(12,3,ACCENT));outline.addState(new int[]{android.R.attr.state_pressed},shape(Color.TRANSPARENT,ACCENT));outline.addState(new int[]{},shape(Color.TRANSPARENT,0));
        android.graphics.drawable.ClipDrawable fill=new android.graphics.drawable.ClipDrawable(shape(Color.rgb(29,91,62),0),Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL);fill.setLevel(progress);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shape(PANEL,0),fill,outline});
    }
    private void focusPlay(){playButton.post(()->{playButton.requestFocus();scroll.scrollTo(0,0);});}
    private void setPlayable(JSONObject value){
        boolean changed=playable==null||!playable.optString("Id").equals(value.optString("Id"));playable=value;
        sources=value.optJSONArray("MediaSources");if(sources==null)sources=new JSONArray();
        if(changed){sourceIndex=0;audio=subtitle=PlaybackSelection.AUTO;}
        playButton.setEnabled(true);playButton.setText(DetailPresentation.position(value)>0?"继续播放":"播放");restartButton.setVisibility(DetailPresentation.position(value)>0?View.VISIBLE:View.GONE);updateChoices();
    }
    private JSONObject selectedSource(){return sources.optJSONObject(sourceIndex);}
    @Override protected void onResume(){super.onResume();if(!PlaybackSelection.seasonKey(playable).isEmpty()){audio=PlaybackSelection.AUTO;updateChoices();}}
    private void updateChoices(){
        int locked=new TvPreferences(this,api).seasonAudio(PlaybackSelection.seasonKey(playable));
        if(locked>=0)audio=PlaybackSelection.audioAt(selectedSource(),locked);
        versionButton.setVisibility(sources.length()>1?View.VISIBLE:View.GONE);versionButton.setText("版本 · "+PlaybackSelection.sourceLabel(selectedSource(),sourceIndex));
        audioButton.setEnabled(playable!=null);subtitleButton.setEnabled(playable!=null);
        audioButton.setText("音轨 · "+PlaybackSelection.selectionLabel(selectedSource(),audio,false)+(locked>=0?" · 本季第 "+(locked+1)+" 音轨":""));subtitleButton.setText("字幕 · "+PlaybackSelection.selectionLabel(selectedSource(),subtitle,true));
    }
    private void chooseVersion(){
        String[] labels=new String[sources.length()];for(int i=0;i<labels.length;i++)labels[i]=PlaybackSelection.sourceLabel(sources.optJSONObject(i),i);
        new AlertDialog.Builder(this).setTitle("版本").setSingleChoiceItems(labels,sourceIndex,(dialog,index)->{sourceIndex=index;audio=subtitle=PlaybackSelection.AUTO;updateChoices();dialog.dismiss();}).setNegativeButton("取消",null).show();
    }
    private void chooseStream(boolean subtitles){
        List<JSONObject> streams=PlaybackSelection.streams(selectedSource(),subtitles);List<String> labels=new ArrayList<>();List<Integer> indices=new ArrayList<>();labels.add((subtitles?"":PlaybackSelection.seasonKey(playable).isEmpty()?"自动选择 · ":"自动选择（解除本季锁定） · ")+PlaybackSelection.selectionLabel(selectedSource(),PlaybackSelection.AUTO,subtitles));indices.add(PlaybackSelection.AUTO);
        if(subtitles){labels.add("关闭字幕");indices.add(-1);}
        for(int i=0;i<streams.size();i++){JSONObject stream=streams.get(i);labels.add((subtitles?"":"第 "+(i+1)+" 音轨 · ")+PlaybackSelection.streamLabel(stream));indices.add(stream.optInt("Index"));}
        int current=indices.indexOf(subtitles?subtitle:audio);
        new AlertDialog.Builder(this).setTitle(subtitles?"字幕":"音轨").setSingleChoiceItems(labels.toArray(new String[0]),current,(dialog,index)->{if(subtitles)subtitle=indices.get(index);else{audio=indices.get(index);new TvPreferences(this,api).saveSeasonAudio(PlaybackSelection.seasonKey(playable),PlaybackSelection.audioOrdinal(selectedSource(),audio));}updateChoices();dialog.dismiss();}).setNegativeButton("取消",null).show();
    }
    private void loadEpisodes(){
        String series=item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId",item.optString("ParentId"));status.setText("正在读取分集…");
        async(()->new JSONObject().put("seasons",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Seasons?UserId="+EtknApi.encode(api.session.userId()))).put("episodes",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&Fields=MediaSources,MediaStreams,Overview")),data->{
            allEpisodes.clear();allEpisodes.addAll(EtknApi.rows(data.optJSONObject("episodes")));
            if(item.optString("Type").equals("Season"))allEpisodes.removeIf(episode->!item.optString("Id").equals(episode.optString("SeasonId")));
            if(allEpisodes.isEmpty()){status.setText("暂无可播放分集");return;}
            JSONObject recommended=DetailPresentation.recommended(allEpisodes);if(item.optString("Type").equals("Episode"))for(JSONObject episode:allEpisodes)if(item.optString("Id").equals(episode.optString("Id"))){recommended=episode;break;}setPlayable(recommended);List<JSONObject> seasons=EtknApi.rows(data.optJSONObject("seasons"));
            LinearLayout seasonRow=row();HorizontalScrollView seasonScroll=new FocusScroll.Row(this);seasonScroll.setHorizontalScrollBarEnabled(false);seasonScroll.addView(seasonRow);
            if(seasons.size()>1&&!item.optString("Type").equals("Season")){episodeArea.addView(seasonScroll);for(JSONObject season:seasons)seasonRow.addView(button(EtknApi.title(season),()->showSeason(season.optString("Id"))));}
            preview=row();preview.setGravity(Gravity.CENTER_VERTICAL);preview.setPadding(0,dp(8),0,dp(8));
            episodeImage=new ImageView(this);episodeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(episodeImage,dp(8));preview.addView(episodeImage,new LinearLayout.LayoutParams(dp(224),dp(126)));
            LinearLayout copy=column();copy.setPadding(dp(18),0,0,0);episodeTitle=text("",19,Color.WHITE);episodeTitle.setMaxLines(2);copy.addView(episodeTitle);episodeOverview=text("",14,MUTED);episodeOverview.setMaxLines(4);episodeOverview.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(episodeOverview);preview.addView(copy,new LinearLayout.LayoutParams(0,dp(144),1));episodeArea.addView(preview);
            showSeason(recommended.optString("SeasonId"),recommended);status.setText(allEpisodes.size()+" 集");focusPlay();
        });
    }
    private void showSeason(String seasonId){
        showSeason(seasonId,null);
    }
    private void showSeason(String seasonId,JSONObject preferred){
        if(preview==null)return;
        while(episodeArea.indexOfChild(preview)+1<episodeArea.getChildCount())episodeArea.removeViewAt(episodeArea.getChildCount()-1);
        List<JSONObject> episodes=new ArrayList<>();for(JSONObject episode:allEpisodes)if(seasonId.isEmpty()||seasonId.equals(episode.optString("SeasonId")))episodes.add(episode);if(episodes.isEmpty())return;
        JSONObject selected=preferred==null?DetailPresentation.recommended(episodes):preferred;setPlayable(selected);updatePreview(selected);
        GridLayout grid=new GridLayout(this);episodeGrid=grid;episodeEntry=null;int available=getResources().getConfiguration().screenWidthDp-84;int columns=Math.max(3,available/112);grid.setColumnCount(columns);int cellWidth=(dp(available)-dp(10)*(columns-1))/columns;
        ScrollView matrix=new FocusScroll.Vertical(this);matrix.setFillViewport(false);matrix.addView(grid);episodeArea.addView(matrix,new LinearLayout.LayoutParams(-1,dp(Math.min(224,((episodes.size()+columns-1)/columns)*58+24))));
        for(JSONObject episode:episodes){JSONObject state=episode.optJSONObject("UserData");boolean watched=state!=null&&state.optBoolean("Played");Button cell=button(DetailPresentation.episodeNumber(episode)+(watched?" ✓":""),()->{setPlayable(episode);play(false);});cell.setContentDescription(DetailPresentation.episodeNumber(episode)+"，"+EtknApi.title(episode)+(watched?"，已观看":""));
            long runtime=episode.optLong("RunTimeTicks");int progress=watched?10000:runtime>0?(int)Math.min(9999,10000.0*DetailPresentation.position(episode)/runtime):0;
            cell.setBackground(episodeBackground(progress));if(!watched&&progress>0)cell.setContentDescription(cell.getContentDescription()+"，续播进度 "+progress/100+"%");
            if(episode==selected)episodeEntry=cell;
            GridLayout.LayoutParams bounds=new GridLayout.LayoutParams();bounds.width=cellWidth;bounds.height=dp(44);bounds.setMargins(0,dp(7),grid.getChildCount()%columns==columns-1?0:dp(10),dp(7));grid.addView(cell,bounds);
            cell.setOnFocusChangeListener((view,focused)->{if(focused){setPlayable(episode);updatePreview(episode);scroll.post(()->{Rect rect=new Rect();preview.getDrawingRect(rect);content.offsetDescendantRectToMyCoords(preview,rect);scroll.smoothScrollTo(0,rect.top-dp(24));});}});
        }
    }
    private void updatePreview(JSONObject episode){
        episodeTitle.setText(DetailPresentation.episodeNumber(episode)+"  ·  "+EtknApi.title(episode));episodeOverview.setText(episode.optString("Overview").isEmpty()?"暂无分集简介":episode.optString("Overview"));
        episodeImage.setImageDrawable(null);Images.load(api,api.artwork(episode,"Primary",640),episodeImage);
    }
    private void renderCast(){
        JSONArray people=item.optJSONArray("People");if(people==null||people.length()==0)return;
        content.addView(text("演员表",20,Color.WHITE));LinearLayout cast=row();HorizontalScrollView castScroll=new FocusScroll.Row(this);castScroll.setHorizontalScrollBarEnabled(false);castScroll.addView(cast);content.addView(castScroll);
        for(int i=0;i<people.length();i++){JSONObject person=people.optJSONObject(i);if(person==null||!person.optString("Type",person.optString("PersonType")).equalsIgnoreCase("Actor"))continue;
            LinearLayout card=column();card.setFocusable(true);card.setBackground(transparentFocusBackground());card.setPadding(dp(3),dp(3),dp(3),dp(3));LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(dp(112),dp(200));bounds.setMargins(0,dp(8),dp(14),dp(8));cast.addView(card,bounds);
            card.setContentDescription("演员 · "+person.optString("Name"));card.setClickable(true);card.setOnClickListener(view->startActivity(new Intent(this,PersonActivity.class).putExtra("id",person.optString("Id")).putExtra("name",person.optString("Name"))));
            ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setImageResource(android.R.drawable.ic_menu_myplaces);Images.rounded(portrait,dp(7));card.addView(portrait,new LinearLayout.LayoutParams(-1,dp(130)));Images.load(api,api.artwork(person,"Primary",240),portrait);
            TextView name=text(person.optString("Name"),13,Color.WHITE);name.setGravity(Gravity.CENTER);name.setMaxLines(1);card.addView(name);TextView role=text(person.optString("Role"),11,MUTED);role.setGravity(Gravity.CENTER);role.setMaxLines(2);role.setPadding(0,0,0,0);card.addView(role);
        }
    }
    private void renderSimilar(){
        LinearLayout area=column();content.addView(area);area.addView(text("更多类似",20,Color.WHITE));TextView loading=text("正在读取相似媒体…",13,MUTED);area.addView(loading);
        async(()->api.get("/api/tv/Items/"+EtknApi.encode(item.optString("Id"))+"/Similar?UserId="+EtknApi.encode(api.session.userId())+"&Limit=20"),result->{
            area.removeView(loading);List<JSONObject> items=EtknApi.rows(result);if(items.isEmpty()){area.addView(text("暂无相似推荐",13,MUTED));return;}
            HorizontalScrollView rail=new FocusScroll.Row(this);rail.setHorizontalScrollBarEnabled(false);GridLayout cards=new GridLayout(this);cards.setRowCount(1);cards.setColumnCount(items.size());rail.addView(cards);area.addView(rail);
            for(JSONObject media:items)posterCard(cards,media,()->detail(media),result.optString("EpisodeCountMode","unplayed"));
        });
    }
    private void play(boolean fromStart){
        if(playable==null)return;
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",playable.optString("Id")).putExtra("title",EtknApi.resumeTitle(playable)).putExtra("position",fromStart?0:DetailPresentation.position(playable)/10000).putExtra("seriesId",playable.optString("SeriesId",item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId")));
        if(selectedSource()!=null)intent.putExtra("sourceId",selectedSource().optString("Id"));intent.putExtra("seasonKey",PlaybackSelection.seasonKey(playable)).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);startActivity(intent);
    }
}
