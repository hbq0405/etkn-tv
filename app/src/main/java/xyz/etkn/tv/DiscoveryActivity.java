package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class DiscoveryActivity extends TvActivity {
    private String kind="movie",query="";
    private int page=1,totalPages=1,revision,browseRevision,cloudRevision,eraIndex;
    private boolean allowed,loadingMore,hasMore;
    private GridLayout posterGrid;
    private TextView posterStatus,detailOverview;
    private ImageView detailBackdrop;
    private LinearLayout browseView;
    private View browseFocus;
    private HorizontalScrollView menu;
    private View previousFocus;
    private JSONObject selected,person,personPayload;
    private Integer season;
    private JSONArray seasonList;
    private final Deque<Runnable> back=new ArrayDeque<>();
    private final Map<String,TextView> ribbons=new HashMap<>();
    private final Map<String,FrameLayout> artworks=new HashMap<>();
    private final Map<String,JSONObject> ribbonItems=new HashMap<>();
    private final Map<String,JSONArray> castCache=new HashMap<>();
    private final Map<String,JSONObject> optionsCache=new HashMap<>();
    private final Map<String,JSONObject> seriesCache=new HashMap<>();
    private String sortKey="popularity.desc",countryValue="",countryLabel="",languageValue="",languageLabel="",ratingLabel="";
    private Integer ratingIndex,minVote,yearFrom,yearTo;
    private final LinkedHashMap<String,String> includeGenres=new LinkedHashMap<>(),excludeGenres=new LinkedHashMap<>();
    private boolean hideInLibrary;
    private static final String[][] SORTS={{"热度降序","popularity.desc"},{"热度升序","popularity.asc"},{"上映日期降序","date.desc"},{"上映日期升序","date.asc"},{"评分降序","vote_average.desc"},{"评分升序","vote_average.asc"}};
    private static final String[] ERA_LABELS={"全部","2020年代","2010年代","2000年代","90年代","80年代","70年代","更早"};
    private static final int[][] ERA_BOUNDS={{0,0},{2020,2029},{2010,2019},{2000,2009},{1990,1999},{1980,1989},{1970,1979},{0,1969}};
    private static final String[] VOTES={"全部","6 分以上","7 分以上","8 分以上","9 分以上"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // 顶部标题与导航收纳为上方向键唤出的菜单；海报墙直接顶到最上方。
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);
        screenScroll=new FocusScroll.Vertical(this);screenScroll.setFillViewport(true);screenScroll.setClipToPadding(false);
        content=column();screenScroll.addView(content);
        stage.addView(screenScroll,new FrameLayout.LayoutParams(-1,-1));
        nav=row();nav.setPadding(dp(28),dp(10),dp(18),dp(10));
        menu=new HorizontalScrollView(this);menu.setHorizontalScrollBarEnabled(false);menu.setBackgroundColor(Color.argb(245,11,17,26));menu.addView(nav);menu.setVisibility(View.GONE);
        stage.addView(menu,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
        status=text("",13,MUTED);status.setPadding(dp(24),dp(4),dp(24),dp(4));
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);
        stage.addView(status,notice);
        setContentView(stage);
        status.setText("正在检查访问权限…");
        screenScroll.setOnScrollChangeListener((View view,int x,int y,int oldX,int oldY)->{
            if(browseView!=null&&browseView.getParent()!=null&&posterGrid!=null&&hasMore&&!loadingMore&&screenScroll.getChildAt(0)!=null&&y+screenScroll.getHeight()>=screenScroll.getChildAt(0).getHeight()-dp(420))loadPage(page+1);
        });
    }
    @Override protected void onResume(){
        super.onResume();
        async(()->api.get("/api/tv/auth/check_status"),result->{
            JSONObject user=result.optJSONObject("user");allowed=user!=null&&user.optBoolean("can_access_discovery");
            if(!allowed){content.removeAllViews();browseView=null;status.setText("当前账号没有影视探索权限。请检查实例会员与用户模板设置。");return;}
            navigation();
            if(browseView==null)browse();
        });
    }
    private void navigation(){
        nav.removeAllViews();
        TextView brand=text("影视探索",14,ACCENT);brand.setPadding(dp(18),0,dp(18),0);
        LinearLayout.LayoutParams brandBounds=new LinearLayout.LayoutParams(-2,dp(44));brandBounds.setMargins(0,dp(6),dp(6),0);
        nav.addView(brand,brandBounds);
        nav("电影",()->switchKind("movie"));
        nav("剧集",()->switchKind("series"));
        nav("搜索",this::searchMedia);
        nav("筛选",this::openFilters);
        nav("重置",this::resetFilters);
    }
    @Override void nav(String title,Runnable click){super.nav(title,()->{hideMenu();click.run();});}
    private void showMenu(){
        previousFocus=getCurrentFocus();menu.setVisibility(View.VISIBLE);
        for(int i=0;i<nav.getChildCount();i++){View child=nav.getChildAt(i);if(child.isFocusable()){child.requestFocus();break;}}
    }
    private void hideMenu(){menu.setVisibility(View.GONE);if(previousFocus==null||!previousFocus.isShown()||!previousFocus.requestFocus())content.requestFocus(View.FOCUS_DOWN);}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
        if(event.getAction()==android.view.KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_UP&&menu.getVisibility()!=View.VISIBLE){
                View focused=getCurrentFocus();
                if(focused==null||focused.focusSearch(View.FOCUS_UP)==null){showMenu();return true;}
            }
            if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_DOWN&&menu.getVisibility()==View.VISIBLE){hideMenu();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    private void switchKind(String target){
        if(kind.equals(target))return;
        kind=target;query="";sortKey="popularity.desc";includeGenres.clear();excludeGenres.clear();
        countryValue="";countryLabel="";languageValue="";languageLabel="";ratingIndex=null;ratingLabel="";minVote=null;eraIndex=0;yearFrom=null;yearTo=null;
        browse();
    }

    // ---------- 海报墙（焦点背景 + 无限滚动） ----------
    private void browse(){browse(true);}
    private void browse(boolean reload){
        back.clear();selected=null;person=null;personPayload=null;season=null;seasonList=null;detailBackdrop=null;detailOverview=null;
        if(browseView==null)buildBrowseView();
        content.removeAllViews();content.addView(browseView,new LinearLayout.LayoutParams(-1,-2));
        status.setText("");
        if(reload){page=1;hasMore=true;loadingMore=false;loadOptions();loadPage(1);screenScroll.scrollTo(0,0);}
        else if(browseFocus!=null){View target=browseFocus;target.post(()->{if(target.isShown())target.requestFocus();});}
    }
    private void buildBrowseView(){
        browseView=column();
        posterGrid=new GridLayout(this);
        posterGrid.setColumnCount(Math.max(3,(getResources().getConfiguration().screenWidthDp-72)/144));
        posterGrid.setClipChildren(false);posterGrid.setClipToPadding(false);
        posterGrid.setPadding(0,dp(FocusScroll.MARGIN_DP),0,dp(FocusScroll.MARGIN_DP));
        LinearLayout.LayoutParams gridBounds=new LinearLayout.LayoutParams(-1,-2);gridBounds.topMargin=dp(10);
        browseView.addView(posterGrid,gridBounds);
        posterStatus=text("",13,MUTED);posterStatus.setGravity(Gravity.CENTER);
        browseView.addView(posterStatus,new LinearLayout.LayoutParams(-1,dp(36)));
    }
    private String genreNames(JSONObject item){
        JSONArray ids=item.optJSONArray("genre_ids");JSONObject options=optionsCache.get(kind);
        if(ids==null||options==null)return "";
        Map<String,String> labels=new HashMap<>();JSONArray genres=options.optJSONArray("genres");
        if(genres!=null)for(int i=0;i<genres.length();i++){JSONObject genre=genres.optJSONObject(i);if(genre!=null)labels.put(genre.optString("value"),genre.optString("label"));}
        List<String> names=new ArrayList<>();
        for(int i=0;i<ids.length()&&names.size()<2;i++){String label=labels.get(String.valueOf(ids.opt(i)));if(label!=null)names.add(label);}
        return String.join(" / ",names);
    }
    private View discoveryCard(GridLayout grid,JSONObject item,Runnable click){
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(Color.TRANSPARENT);card.setOnClickListener(v->click.run());
        FrameLayout artwork=new FrameLayout(this);
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(9));
        artwork.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        // 网页版样式：片名与年份压在海报底部渐变区，不再占用卡片下方空间。
        android.graphics.drawable.GradientDrawable fade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,Color.argb(70,0,0,0),Color.argb(225,0,0,0)});
        LinearLayout caption=column();caption.setBackground(fade);caption.setPadding(dp(6),dp(24),dp(6),dp(4));
        TextView title=text(EtknApi.title(item),13,Color.WHITE);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setShadowLayer(3,0,1,Color.BLACK);title.setPadding(0,0,0,0);
        caption.addView(title,new LinearLayout.LayoutParams(-1,dp(20)));
        TextView year=text(yearOf(item),11,Color.rgb(207,222,231));year.setMaxLines(1);year.setShadowLayer(3,0,1,Color.BLACK);year.setPadding(0,0,0,0);
        caption.addView(year,new LinearLayout.LayoutParams(-1,dp(16)));
        artwork.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        card.addView(artwork,new LinearLayout.LayoutParams(-1,dp(198)));
        double vote=item.optDouble("vote_average",0);
        if(vote>0){
            TextView rating=chipView("★ "+String.format(Locale.ROOT,"%.1f",vote),Color.argb(205,11,17,26),Color.rgb(247,184,36));
            FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(-2,dp(24),Gravity.TOP|Gravity.END);bounds.setMargins(0,dp(6),dp(6),0);
            artwork.addView(rating,bounds);
        }
        String ribbon=statusLabel(item);String id=item.optString("id");
        if(ribbon!=null){
            TextView tag=chipView(ribbon,statusColor(item),Color.WHITE);
            FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(-2,dp(24),Gravity.TOP|Gravity.START);bounds.setMargins(dp(6),dp(6),0,0);
            artwork.addView(tag,bounds);ribbons.put(id,tag);
        }
        if(!id.isEmpty()){artworks.put(id,artwork);ribbonItems.put(id,item);}
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=dp(132);layout.height=dp(198);layout.setMargins(dp(6),dp(10),dp(6),dp(10));
        grid.addView(card,layout);
        // 墙内卡片用 w185：无限滚动累积的解码位图约为 w342 的三分之一，避免长刷 OOM。
        String image=EtknApi.tmdbImage(item.optString("poster_path"),"w185");
        if(image.isEmpty())poster.setBackgroundColor(PANEL);else Images.visible(api,image,poster,success->{});
        card.setOnFocusChangeListener((view,hasFocus)->{
            card.setScaleX(hasFocus?FOCUS_SCALE:1f);card.setScaleY(hasFocus?FOCUS_SCALE:1f);card.setTranslationZ(hasFocus?dp(8):0);
            artwork.setForeground(hasFocus?focusOutline():null);
            if(hasFocus)browseFocus=card;
        });
        return card;
    }
    private TextView chipView(String label,int background,int foreground){
        TextView view=new TextView(this);view.setText(label);view.setTextSize(9);view.setTextColor(foreground);
        view.setGravity(Gravity.CENTER);view.setPadding(dp(8),0,dp(8),0);view.setMinHeight(dp(22));
        view.setBackground(shape(background,0));
        return view;
    }
    private void refreshRibbon(String id){
        TextView tag=ribbons.get(id);FrameLayout artwork=artworks.get(id);JSONObject item=ribbonItems.get(id);
        if(item==null||artwork==null)return;
        String label=statusLabel(item);if(label==null)return;
        if(tag==null){
            tag=chipView(label,statusColor(item),Color.WHITE);
            FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(-2,dp(24),Gravity.TOP|Gravity.START);bounds.setMargins(dp(6),dp(6),0,0);
            artwork.addView(tag,bounds);ribbons.put(id,tag);
        }else{tag.setText(label);tag.setBackground(shape(statusColor(item),0));}
    }
    private void loadOptions(){
        if(optionsCache.containsKey(kind))return;
        final String target=kind;
        async(()->api.get("/api/tv/discover/options?media_type="+target),result->{if(!optionsCache.containsKey(target))optionsCache.put(target,result);});
    }
    private void loadPage(int target){
        if(!allowed||loadingMore||(target>1&&(!hasMore||target<=page)))return;
        boolean append=target>1;
        if(append)loadingMore=true;
        int request=++browseRevision;
        if(!append){
            posterGrid.removeAllViews();ribbons.clear();artworks.clear();ribbonItems.clear();
            posterStatus.setText("正在探索…");
        }else posterStatus.setText("正在加载更多…");
        async(()->{try{return api.post("/api/tv/discover/query/"+kind,queryPayload(target));}catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("_loadError",error.getMessage()==null?"加载失败":error.getMessage());}},result->{
            if(request!=browseRevision)return;
            loadingMore=false;
            if(result.has("_loadError")){posterStatus.setText(result.optString("_loadError"));return;}
            List<JSONObject> rows=EtknApi.rows(result);
            totalPages=Math.min(result.optInt("total_pages",1),500);
            for(JSONObject item:rows)discoveryCard(posterGrid,item,()->enterDetail(item));
            page=target;hasMore=!rows.isEmpty()&&page<totalPages;
            String summary=filterSummary();
            if(!append&&rows.isEmpty())posterStatus.setText("暂无匹配结果，可调整筛选或搜索词");
            else posterStatus.setText((hasMore?"已加载 "+posterGrid.getChildCount()+" 项，下滚加载更多":"已经到底，共 "+posterGrid.getChildCount()+" 项")+(summary.isEmpty()?"":"  ·  "+summary));
            if(!append&&!rows.isEmpty()){View first=posterGrid.getChildAt(0);if(first!=null)first.requestFocus();}
        });
    }
    private JSONObject queryPayload(int targetPage)throws JSONException{
        String sort=sortKey;
        if(sort.equals("date.desc"))sort=kind.equals("movie")?"primary_release_date.desc":"first_air_date.desc";
        else if(sort.equals("date.asc"))sort=kind.equals("movie")?"primary_release_date.asc":"first_air_date.asc";
        JSONObject body=new JSONObject().put("page",targetPage).put("query",query).put("sort_by",sort).put("hide_in_library",hideInLibrary);
        if(!includeGenres.isEmpty())body.put("with_genres",String.join(",",includeGenres.keySet()));
        if(!excludeGenres.isEmpty())body.put("without_genres",String.join(",",excludeGenres.keySet()));
        if(!countryValue.isEmpty())body.put("with_origin_country",countryValue);
        if(!languageValue.isEmpty())body.put("with_original_language",languageValue);
        if(yearFrom!=null)body.put("release_date_gte",yearFrom+"-01-01");
        if(yearTo!=null)body.put("release_date_lte",yearTo+"-12-31");
        if(minVote!=null)body.put("vote_average_gte",(double)minVote);
        if(ratingIndex!=null)body.put("rating",(int)ratingIndex);
        return body;
    }

    // ---------- 详情页（全尺寸背景、演员表、季度） ----------
    private void enterDetail(JSONObject item){
        back.push(()->browse(false));
        showDetail(item,null);
    }
    private void enterSeason(JSONObject series,int number){
        final JSONObject source=series;
        back.push(()->showDetail(source,null));
        showDetail(series,number);
    }
    private void enterCloud(){
        final JSONObject item=selected;final Integer fromSeason=season;
        back.push(()->showDetail(item,fromSeason));
        showCloud();
    }
    private void showDetail(JSONObject item,Integer seasonNumber){
        selected=item;season=seasonNumber;++revision;
        content.removeAllViews();
        boolean series=mediaTypeOf(item,kind).equals("tv");
        JSONObject seasonEntry=seasonNumber==null?null:findSeason(seasonNumber);
        JSONObject statusSource=seasonEntry!=null?seasonEntry:item;
        FrameLayout hero=new FrameLayout(this);
        detailBackdrop=new ImageView(this);detailBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(detailBackdrop,new FrameLayout.LayoutParams(-1,-1));
        View shade=backdropShade();shade.setBackgroundColor(Color.argb(150,11,17,26));
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(10));
        FrameLayout.LayoutParams posterBounds=new FrameLayout.LayoutParams(dp(124),dp(186),Gravity.BOTTOM|Gravity.START);
        posterBounds.setMargins(dp(20),0,dp(8),dp(16));
        hero.addView(poster,posterBounds);
        LinearLayout copy=column();
        FrameLayout.LayoutParams copyBounds=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.END);
        copyBounds.setMargins(dp(148),0,dp(24),dp(12));
        LinearLayout titleRow=row();titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(EtknApi.title(item)+(seasonNumber==null?"":" · 第 "+seasonNumber+" 季"),23,Color.WHITE);
        title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String ribbon=statusLabel(statusSource);
        if(ribbon!=null){
            titleRow.addView(chipView(ribbon,statusColor(statusSource),Color.WHITE),new LinearLayout.LayoutParams(-2,dp(26)));
            LinearLayout.LayoutParams titleBounds=new LinearLayout.LayoutParams(0,-2,1);titleBounds.leftMargin=dp(10);titleRow.addView(title,titleBounds);
        }else titleRow.addView(title,new LinearLayout.LayoutParams(-1,-2));
        copy.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView meta=text(detailMeta(item,series,statusSource),12,Color.rgb(207,222,231));meta.setMaxLines(1);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(meta);
        detailOverview=text(item.optString("overview"),12,Color.rgb(226,232,238));
        detailOverview.setMaxLines(4);detailOverview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailOverview.setFocusable(true);detailOverview.setBackground(transparentFocusBackground());detailOverview.setPadding(dp(12),dp(4),dp(4),dp(4));
        final String overviewSource=item.optString("overview");
        detailOverview.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(EtknApi.title(item)).setMessage(overviewSource.isEmpty()?"暂无简介":overviewSource).setPositiveButton("关闭",null).show());
        copy.addView(detailOverview);
        hero.addView(copy,copyBounds);
        content.addView(hero,new LinearLayout.LayoutParams(-1,dp(296)));
        String backdrop=EtknApi.tmdbImage(item.optString("backdrop_path"),"w1280");
        if(backdrop.isEmpty())backdrop=EtknApi.tmdbImage(item.optString("poster_path"),"w780");
        if(!backdrop.isEmpty())Images.backdrop(api,backdrop,detailBackdrop,success->{});
        String posterUrl=EtknApi.tmdbImage(item.optString("poster_path"),"w342");
        if(posterUrl.isEmpty())poster.setBackgroundColor(PANEL);else Images.visible(api,posterUrl,poster,success->{});
        LinearLayout actions=row();
        if(!series||seasonNumber!=null)actions.addView(button(subscribeLabel(item,statusSource,seasonNumber),this::subscribe));
        actions.addView(button("搜索云资源",this::enterCloud));
        if(item.optBoolean("in_library")&&!item.optString("media_item_id").isEmpty())actions.addView(button("在片库中观看",()->openLibrary(item)));
        if(series&&seasonNumber==null)actions.addView(button("订阅全部缺失季",this::subscribeAllSeasons));
        content.addView(actions);
        View first=actions.getChildAt(0);if(first!=null)first.requestFocus();
        if(series){
            if(seasonNumber==null)renderSeasons(item);
            else if(seasonEntry!=null){
                content.addView(text("季信息",18,ACCENT));
                String air=seasonEntry.optString("air_date");
                content.addView(text(seasonEntry.optInt("episode_count")+" 集"+(air.isEmpty()?"":" · 首播 "+air),13,MUTED));
                String overview=seasonEntry.optString("overview");
                if(!overview.isEmpty())content.addView(text(overview,12,MUTED));
            }
        }
        loadCast(item,series?"series":"movie");
    }
    private String detailMeta(JSONObject item,boolean series,JSONObject statusSource){
        List<String> parts=new ArrayList<>();
        String year=yearOf(item);if(!year.isEmpty())parts.add(year);
        parts.add(series?"剧集":"电影");
        double vote=item.optDouble("vote_average",0);
        if(vote>0)parts.add("★ "+String.format(Locale.ROOT,"%.1f",vote));
        String genres=genreNames(item);if(!genres.isEmpty())parts.add(genres);
        return String.join("  ·  ",parts);
    }
    private JSONObject findSeason(int number){
        if(seasonList==null)return null;
        for(int i=0;i<seasonList.length();i++){JSONObject entry=seasonList.optJSONObject(i);if(entry!=null&&entry.optInt("season_number")==number)return entry;}
        return null;
    }
    private void renderSeasons(JSONObject item){
        content.addView(text("选择季度",18,ACCENT));
        String id=String.valueOf(item.opt("id"));
        JSONObject cached=seriesCache.get(id);
        if(cached!=null){enrichFromDetails(item,cached);renderSeasonList(cached.optJSONArray("seasons"));return;}
        TextView loading=text("正在读取季度…",13,MUTED);content.addView(loading);
        int request=revision;
        async(()->api.get("/api/tv/discover/series/"+EtknApi.encode(item.optString("id"))),details->{
            if(request!=revision)return;
            content.removeView(loading);
            seriesCache.put(id,details);
            enrichFromDetails(item,details);
            renderSeasonList(details.optJSONArray("seasons"));
        });
    }
    private void enrichFromDetails(JSONObject item,JSONObject details){
        fillIfEmpty(item,"backdrop_path",details.optString("backdrop_path"));
        fillIfEmpty(item,"overview",details.optString("overview"));
        if(selected==item){
            String backdrop=EtknApi.tmdbImage(item.optString("backdrop_path"),"w1280");
            if(!backdrop.isEmpty()&&detailBackdrop!=null)Images.backdrop(api,backdrop,detailBackdrop,success->{});
        }
    }
    private void renderSeasonList(JSONArray seasons){
        seasonList=seasons;
        if(seasons==null)return;
        for(int i=0;i<seasons.length();i++){
            JSONObject entry=seasons.optJSONObject(i);if(entry==null)continue;
            int number=entry.optInt("season_number");if(number<=0)continue;
            content.addView(seasonRow(entry));
        }
    }
    private View seasonRow(JSONObject entry){
        int number=entry.optInt("season_number");
        LinearLayout row=column();row.setFocusable(true);row.setClickable(true);row.setBackground(focusBackground());row.setPadding(dp(16),dp(8),dp(16),dp(10));
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text("第 "+number+" 季 · "+entry.optString("name",""),15,Color.WHITE);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        String ribbon=statusLabel(entry);
        if(ribbon!=null)line.addView(chipView(ribbon,statusColor(entry),Color.WHITE));
        row.addView(line);
        String air=entry.optString("air_date");
        row.addView(text(entry.optInt("episode_count")+" 集"+(air.isEmpty()?"":" · 首播 "+air),12,MUTED));
        row.setOnClickListener(v->enterSeason(selected,number));
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2);bounds.topMargin=dp(6);
        content.addView(row,bounds);
        return row;
    }
    private void loadCast(JSONObject item,String mediaType){
        String id=String.valueOf(item.opt("id"));
        JSONArray cached=castCache.get(id);
        if(cached!=null){renderCast(cached);return;}
        TextView loading=text("正在读取演员…",13,MUTED);content.addView(loading);
        int request=revision;
        async(()->api.get("/api/tv/discover/media/"+mediaType+"/"+EtknApi.encode(id)+"/credits"),result->{
            if(request!=revision)return;
            content.removeView(loading);
            JSONArray cast=result.optJSONArray("cast");
            castCache.put(id,cast);
            if(selected==item){
                fillIfEmpty(item,"backdrop_path",result.optString("backdrop_path"));
                fillIfEmpty(item,"overview",result.optString("overview"));
                String backdrop=EtknApi.tmdbImage(item.optString("backdrop_path"),"w1280");
                if(!backdrop.isEmpty()&&detailBackdrop!=null)Images.backdrop(api,backdrop,detailBackdrop,success->{});
                if(detailOverview!=null)detailOverview.setText(item.optString("overview"));
            }
            renderCast(cast);
        });
    }
    private void renderCast(JSONArray cast){
        if(cast==null||cast.length()==0)return;
        content.addView(text("演员（点击查看参演作品）",18,ACCENT));
        HorizontalScrollView scroll=new FocusScroll.Row(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(2),dp(10),dp(2),dp(10));
        GridLayout grid=new GridLayout(this);grid.setRowCount(1);grid.setColumnCount(cast.length());grid.setClipChildren(false);grid.setClipToPadding(false);
        scroll.addView(grid);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<cast.length();i++){
            JSONObject actor=cast.optJSONObject(i);if(actor==null)continue;
            actorCard(grid,actor);
        }
    }
    private void actorCard(GridLayout grid,JSONObject actor){
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(Color.TRANSPARENT);
        card.setOnClickListener(v->enterPerson(actor));
        FrameLayout artwork=new FrameLayout(this);
        ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(portrait,dp(9));
        artwork.addView(portrait,new FrameLayout.LayoutParams(-1,-1));
        card.addView(artwork,new LinearLayout.LayoutParams(dp(102),dp(150)));
        TextView name=text(actor.optString("name"),11,Color.WHITE);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(3),0,0);
        card.addView(name,new LinearLayout.LayoutParams(dp(102),dp(17)));
        TextView character=text(actor.optString("character"),9,MUTED);character.setMaxLines(1);character.setEllipsize(android.text.TextUtils.TruncateAt.END);character.setGravity(Gravity.CENTER);
        card.addView(character,new LinearLayout.LayoutParams(dp(102),dp(15)));
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=dp(102);layout.height=dp(196);layout.setMargins(dp(6),dp(4),dp(6),dp(4));
        grid.addView(card,layout);
        String image=EtknApi.tmdbImage(actor.optString("profile_path"),"w185");
        if(image.isEmpty())portrait.setBackgroundColor(PANEL);else Images.visible(api,image,portrait,success->{});
        card.setOnFocusChangeListener((view,hasFocus)->{card.setScaleX(hasFocus?FOCUS_SCALE:1f);card.setScaleY(hasFocus?FOCUS_SCALE:1f);card.setTranslationZ(hasFocus?dp(8):0);artwork.setForeground(hasFocus?focusOutline():null);});
    }
    private void enterPerson(JSONObject actor){
        if(actor.optString("id").isEmpty())return;
        final JSONObject from=selected;final Integer fromSeason=season;
        back.push(()->showDetail(from,fromSeason));
        person=actor;personPayload=null;++revision;
        content.removeAllViews();status.setText("正在读取演员作品…");
        int request=revision;
        async(()->api.get("/api/tv/discover/person/"+EtknApi.encode(actor.optString("id"))+"/credits"),result->{if(request!=revision)return;personPayload=result;renderPerson(result);});
    }
    private void enterWork(JSONObject work){
        final JSONObject payload=personPayload;
        back.push(()->{if(payload!=null)renderPerson(payload);});
        showDetail(work,null);
    }
    private void renderPerson(JSONObject payload){
        content.removeAllViews();status.setText("");detailBackdrop=null;detailOverview=null;
        JSONObject personInfo=payload.optJSONObject("person");
        JSONArray works=payload.optJSONArray("works");
        LinearLayout header=row();
        ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(portrait,dp(10));
        LinearLayout.LayoutParams portraitBounds=new LinearLayout.LayoutParams(dp(132),dp(198));portraitBounds.rightMargin=dp(22);portraitBounds.topMargin=dp(6);
        header.addView(portrait,portraitBounds);
        LinearLayout info=column();
        TextView name=text(personInfo==null?"":personInfo.optString("name"),26,Color.WHITE);name.setTypeface(null,Typeface.BOLD);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);
        final String biography=personInfo==null?"":personInfo.optString("biography");
        TextView bio=text(biography.isEmpty()?"暂无人物简介":biography,13,MUTED);bio.setMaxLines(7);bio.setEllipsize(android.text.TextUtils.TruncateAt.END);bio.setFocusable(true);bio.setBackground(transparentFocusBackground());bio.setPadding(dp(14),dp(6),dp(6),dp(6));
        bio.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(name.getText()).setMessage(biography.isEmpty()?"暂无人物简介":biography).setPositiveButton("关闭",null).show());
        info.addView(bio);
        header.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(header,new LinearLayout.LayoutParams(-1,-2));
        content.addView(text("参演作品（TMDb）",18,ACCENT));
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(Math.max(3,(getResources().getConfiguration().screenWidthDp-72)/144));
        grid.setClipChildren(false);grid.setClipToPadding(false);grid.setPadding(0,dp(FocusScroll.MARGIN_DP),0,dp(FocusScroll.MARGIN_DP));
        content.addView(grid);
        int shown=0;
        if(works!=null)for(int i=0;i<works.length();i++){JSONObject work=works.optJSONObject(i);if(work==null)continue;discoveryCard(grid,work,()->enterWork(work));shown++;}
        status.setText(shown==0?"TMDb 未返回参演作品":"共 "+shown+" 部参演作品，已入库的带缎带标记");
        String image=EtknApi.tmdbImage(personInfo==null?"":personInfo.optString("profile_path"),"w342");
        if(!image.isEmpty())Images.visible(api,image,portrait,success->{});
        if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();else content.requestFocus(View.FOCUS_DOWN);
    }

    // ---------- 订阅 ----------
    private String subscribeLabel(JSONObject item,JSONObject statusSource,Integer seasonNumber){
        if(statusSource.optBoolean("in_library"))return seasonNumber==null?"已入库 · 再次订阅":"本季已入库 · 再次订阅";
        String status=statusSource.optString("subscription_status","NONE");
        if("SUBSCRIBED".equals(status))return (seasonNumber==null?"已订阅":"本季已订阅")+" · 再次订阅";
        if("WANTED".equals(status))return (seasonNumber==null?"待订阅":"本季待订阅")+" · 再次订阅";
        return seasonNumber==null?"订阅影片":"订阅本季";
    }
    private void subscribe(){
        JSONObject item=selected;Integer selectedSeason=season;
        if(item==null)return;
        boolean series=mediaTypeOf(item,kind).equals("tv");
        new AlertDialog.Builder(this).setTitle("确认订阅").setMessage("订阅“"+EtknApi.title(item)+(selectedSeason==null?"":" · 第 "+selectedSeason+" 季")+"”？").setPositiveButton("订阅",(dialog,which)->{
            status.setText("正在提交订阅…");
            async(()->{try{
                JSONObject body=new JSONObject().put("tmdb_id",item.optString("id")).put("name",EtknApi.title(item)).put("entity_type",series?"season":"movie").put("poster_path",item.optString("poster_path")).put("release_date",item.optString("release_date")).put("first_air_date",item.optString("first_air_date"));
                if(selectedSeason!=null)body.put("season_number",selectedSeason);
                return api.post("/api/tv/discover/subscribe",body);
            }catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("_error",error.getMessage()==null?"订阅请求失败":error.getMessage());}},result->{
                status.setText("");
                if(result.has("_error")){new AlertDialog.Builder(this).setTitle("订阅失败").setMessage(result.optString("_error")).setPositiveButton("关闭",null).show();return;}
                String demand=result.optString("demand_id");
                markSubscribed(item,selectedSeason);
                new AlertDialog.Builder(this).setTitle("订阅已提交").setMessage("已写入统一需求"+(demand.isEmpty()?"":"（需求 #"+demand+"）")+"，服务端将按订阅配置自动获取资源，入库后会发送通知。").setPositiveButton("知道了",null).show();
            });
        }).setNegativeButton("取消",null).show();
    }
    private void markSubscribed(JSONObject item,Integer seasonNumber){
        if(seasonNumber==null)setString(item,"subscription_status","WANTED");
        else{
            JSONObject entry=findSeason(seasonNumber);
            if(entry!=null)setString(entry,"subscription_status","WANTED");
        }
        refreshRibbon(item.optString("id"));
        showDetail(item,seasonNumber);
    }
    private void subscribeAllSeasons(){
        if(seasonList==null){new AlertDialog.Builder(this).setTitle("季度尚未加载完成").setMessage("请稍候再试。").setPositiveButton("关闭",null).show();return;}
        final List<JSONObject> missing=new ArrayList<>();
        for(int i=0;i<seasonList.length();i++){
            JSONObject entry=seasonList.optJSONObject(i);if(entry==null)continue;
            int number=entry.optInt("season_number");if(number<=0)continue;
            if(entry.optBoolean("in_library"))continue;
            String status=entry.optString("subscription_status","NONE");
            if("SUBSCRIBED".equals(status)||"WANTED".equals(status))continue;
            missing.add(entry);
        }
        if(missing.isEmpty()){new AlertDialog.Builder(this).setTitle("无需订阅").setMessage("所有季度均已入库或订阅。").setPositiveButton("关闭",null).show();return;}
        final JSONObject item=selected;
        new AlertDialog.Builder(this).setTitle("订阅全部缺失季").setMessage("将按季度写入 "+missing.size()+" 条统一需求，服务端将按订阅配置自动获取资源。").setPositiveButton("订阅",(dialog,which)->{
            status.setText("正在提交订阅…");
            async(()->{
                int done=0;String failure="";
                for(JSONObject entry:missing){
                    try{
                        JSONObject body=new JSONObject().put("tmdb_id",item.optString("id")).put("name",EtknApi.title(item)).put("entity_type","season").put("season_number",entry.optInt("season_number")).put("poster_path",entry.optString("poster_path",item.optString("poster_path"))).put("first_air_date",entry.optString("air_date",item.optString("first_air_date")));
                        api.post("/api/tv/discover/subscribe",body);done++;
                    }catch(EtknApi.AuthException error){throw error;}
                    catch(Exception error){failure=error.getMessage()==null?"订阅请求失败":error.getMessage();break;}
                }
                return new JSONObject().put("done",done).put("failure",failure);
            },result->{
                status.setText("");
                if(result.optString("failure").isEmpty()){
                    for(JSONObject entry:missing)setString(entry,"subscription_status","WANTED");
                    showDetail(item,null);
                    new AlertDialog.Builder(this).setTitle("订阅已提交").setMessage("已按季度写入 "+result.optInt("done",0)+" 条统一需求。").setPositiveButton("知道了",null).show();
                }else{
                    new AlertDialog.Builder(this).setTitle("订阅失败").setMessage(result.optString("failure")+"（已完成 "+result.optInt("done",0)+" 条）").setPositiveButton("关闭",null).show();
                }
            });
        }).setNegativeButton("取消",null).show();
    }
    private void openLibrary(JSONObject item){
        String id=item.optString("media_item_id");
        if(!id.isEmpty()){startActivity(new Intent(this,DetailActivity.class).putExtra("id",id));return;}
        libraryMatches();
    }
    private void libraryMatches(){
        final JSONObject item=selected;if(item==null)return;
        int request=++revision;
        status.setText("正在查询片库…");
        async(()->api.get(api.userPath()+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&Limit=60&Fields=ProviderIds&SearchTerm="+EtknApi.encode(EtknApi.title(item))),result->{
            if(request!=revision)return;
            content.removeAllViews();
            GridLayout grid=new GridLayout(this);
            grid.setColumnCount(Math.max(3,(getResources().getConfiguration().screenWidthDp-72)/144));
            content.addView(grid);
            int found=0;
            for(JSONObject media:EtknApi.rows(result)){
                JSONObject ids=media.optJSONObject("ProviderIds");
                if(ids!=null&&item.optString("id").equals(ids.optString("Tmdb"))){posterCard(grid,media,()->detail(media),"total");found++;}
            }
            status.setText(found>0?"已入库，选择影片观看。":"尚未查到入库影片，可稍后刷新。");
            content.addView(button("刷新入库状态",this::libraryMatches));
        });
    }

    // ---------- 云资源搜索 ----------
    private void showCloud(){
        ++cloudRevision;
        final JSONObject item=selected;final Integer wantedSeason=season;
        if(item==null)return;
        content.removeAllViews();detailBackdrop=null;detailOverview=null;
        content.addView(text(EtknApi.title(item)+(wantedSeason==null?"":" · 第 "+wantedSeason+" 季"),24,Color.WHITE));
        status.setText("正在搜索共享池、re0 和频道…");
        String tmdb=EtknApi.encode(item.optString("id")),title=EtknApi.encode(EtknApi.title(item)),type=mediaTypeOf(item,kind);
        String[] providers={"shared_pool","re0","channel"};
        String[] paths={
            "/api/tv/discover/shared-resources?tmdb_id="+tmdb+"&item_type="+(wantedSeason==null?type:"season")+"&q="+title+"&limit=100",
            "/api/tv/discover/re0-resources?tmdb_id="+tmdb+"&media_type="+type+"&require_complete=false"+(wantedSeason==null?"":"&season="+wantedSeason),
            "/api/tv/discover/channel-resources?tmdb_id="+tmdb+"&media_type="+type+"&q="+title+(wantedSeason==null?"":"&season_number="+wantedSeason)};
        int[] pending={3};
        for(int i=0;i<providers.length;i++){
            final String provider=providers[i],path=paths[i];
            LinearLayout area=column();content.addView(area);
            LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(text(provider.equals("shared_pool")?"共享池":provider.equals("re0")?"re0":"频道",17,ACCENT));
            TextView sourceStatus=text("搜索中…",12,MUTED);
            LinearLayout.LayoutParams statusBounds=new LinearLayout.LayoutParams(-2,-2);statusBounds.leftMargin=dp(12);
            head.addView(sourceStatus,statusBounds);
            area.addView(head);
            final LinearLayout target=area;final TextView counter=sourceStatus;
            int request=cloudRevision;
            IO.execute(()->{
                try{
                    List<CloudResources.Resource> rows=CloudResources.parse(provider,api.get(path),wantedSeason);
                    runOnUiThread(()->{
                        if(isDestroyed()||request!=cloudRevision)return;
                        counter.setText(rows.isEmpty()?"暂无匹配资源":rows.size()+" 个版本");
                        for(CloudResources.Resource row:rows)resourceCard(target,row,item,wantedSeason,type);
                        if(--pending[0]==0)status.setText("搜索完成，选择资源版本并确认获取。");
                    });
                }catch(Exception error){
                    runOnUiThread(()->{
                        if(isDestroyed()||request!=cloudRevision)return;
                        counter.setText(error instanceof EtknApi.AuthException?"登录已失效":(error.getMessage()==null?"搜索失败":error.getMessage()));
                        if(--pending[0]==0)status.setText("搜索完成，部分来源暂不可用。");
                    });
                }
            });
        }
    }
    private void resourceCard(LinearLayout area,CloudResources.Resource row,JSONObject item,Integer wantedSeason,String type){
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackground(focusBackground());
        card.setPadding(dp(16),dp(10),dp(16),dp(12));
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(EtknApi.title(row.source),15,Color.WHITE);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        String version=CloudResources.versionLabel(row);
        if(!version.isEmpty())line.addView(chipView(version,Color.rgb(55,71,90),Color.WHITE));
        String transfer=CloudResources.transferLabel(row);
        if(!transfer.isEmpty())line.addView(chipView(transfer,transfer.equals("可转存")?Color.rgb(38,95,155):Color.rgb(30,111,63),Color.WHITE));
        card.addView(line);
        LinearLayout chips=row();chips.setPadding(0,dp(4),0,0);
        for(CloudResources.Chip chip:CloudResources.chips(row)){
            TextView view=chipView(chip.text,toneColor(chip.tone),Color.WHITE);
            LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-2,dp(24));bounds.rightMargin=dp(6);
            chips.addView(view,bounds);
        }
        card.addView(chips,new LinearLayout.LayoutParams(-1,-2));
        card.setOnClickListener(v->confirmAcquire(row,card,item,wantedSeason,type));
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2);bounds.topMargin=dp(8);
        area.addView(card,bounds);
    }
    private static int toneColor(String tone){
        switch(tone==null?"":tone){
            case "success":return Color.rgb(30,111,63);
            case "warning":return Color.rgb(143,101,33);
            case "info":return Color.rgb(38,95,155);
            default:return Color.rgb(55,71,90);
        }
    }
    private void confirmAcquire(CloudResources.Resource row,final View card,JSONObject item,Integer wantedSeason,String type){
        String label=CloudResources.label(row,row.source.optInt("_version_index",1)-1);
        new AlertDialog.Builder(this).setTitle("确认获取此版本").setMessage(label+"\n\n将按 ETKN 配置转存或获取并入库；可能消耗服务端资源积分。").setPositiveButton("确认获取",(dialog,which)->{
            status.setText("正在提交获取任务…");
            async(()->{try{
                if(row.provider.equals("shared_pool"))return api.post("/api/tv/discover/shared-resources/acquire",new JSONObject().put("source",CloudResources.sharedSource(row.source)));
                if(row.provider.equals("channel"))return api.post("/api/tv/discover/channel-resources/acquire",new JSONObject().put("resource",row.source));
                JSONObject body=new JSONObject().put("slug",row.source.optString("slug",row.source.optString("id"))).put("title",EtknApi.title(item)).put("tmdb_id",item.optString("id")).put("media_type",type);
                if(wantedSeason!=null)body.put("season_number",wantedSeason);
                return api.post("/api/tv/discover/re0-resources/acquire",body);
            }catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("_error",error.getMessage());}},result->{
                status.setText("");
                if(result.has("_error")){new AlertDialog.Builder(this).setTitle("获取提交失败").setMessage(result.optString("_error")).setPositiveButton("关闭",null).show();return;}
                long runId=result.optLong("workflow_run_id",0);
                AcquireTracker.track(this,runId,EtknApi.title(item));
                markAcquired(card);
                new AlertDialog.Builder(this).setTitle("获取任务已提交").setMessage((runId>0?"任务 #"+runId+" 已创建，":"获取任务已创建，")+"入库完成后即可在片库观看；转存失败时会收到通知，成功后由入库通知提醒。").setPositiveButton("刷新入库状态",(dialog2,which2)->libraryMatches()).setNegativeButton("关闭",null).show();
            });
        }).setNegativeButton("取消",null).show();
    }
    private void markAcquired(View card){
        if(!(card instanceof LinearLayout))return;
        LinearLayout layout=(LinearLayout)card;
        if(layout.getChildCount()<2||!(layout.getChildAt(1) instanceof LinearLayout))return;
        LinearLayout chips=(LinearLayout)layout.getChildAt(1);
        TextView mark=chipView("已提交",Color.rgb(30,111,63),Color.WHITE);
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-2,dp(24));bounds.leftMargin=dp(6);
        chips.addView(mark,bounds);
    }

    // ---------- 筛选 ----------
    private void openFilters(){
        new AlertDialog.Builder(this).setTitle("筛选影视").setItems(filterDimensions(),(dialog,which)->{
            switch(which){
                case 0:chooseSort();break;case 1:chooseGenres();break;case 2:chooseCountry();break;case 3:chooseLanguage();break;
                case 4:chooseEra();break;case 5:chooseVote();break;case 6:chooseRating();break;case 7:toggleHide();break;
                default:resetFilters();
            }
        }).setNegativeButton("关闭",null).show();
    }
    private String[] filterDimensions(){
        return new String[]{
            "排序："+sortLabel(),
            "风格："+genreFilterLabel(),
            "地区："+(countryLabel.isEmpty()?"全部":countryLabel),
            "语言："+(languageLabel.isEmpty()?"全部":languageLabel),
            "年代："+ERA_LABELS[eraIndex],
            "最低评分："+voteLabel(),
            "分级："+(ratingLabel.isEmpty()?"全部":ratingLabel),
            "隐藏已入库："+(hideInLibrary?"开":"关"),
            "重置全部筛选"
        };
    }
    private String sortLabel(){for(String[] entry:SORTS)if(entry[1].equals(sortKey))return entry[0];return SORTS[0][0];}
    private String genreFilterLabel(){
        if(!includeGenres.isEmpty())return "含 "+String.join("、",includeGenres.values());
        if(!excludeGenres.isEmpty())return "排 "+String.join("、",excludeGenres.values());
        return "全部";
    }
    private String voteLabel(){return minVote==null?"全部":minVote+" 分以上";}
    private String filterSummary(){
        List<String> parts=new ArrayList<>();
        if(!query.isEmpty())parts.add("搜索“"+query+"”");
        if(!sortKey.equals("popularity.desc"))parts.add(sortLabel());
        if(!includeGenres.isEmpty())parts.add("含"+includeGenres.size()+"个风格");
        if(!excludeGenres.isEmpty())parts.add("排"+excludeGenres.size()+"个风格");
        if(!countryLabel.isEmpty())parts.add(countryLabel);
        if(!languageLabel.isEmpty())parts.add(languageLabel);
        if(eraIndex>0)parts.add(ERA_LABELS[eraIndex]);
        if(minVote!=null)parts.add(minVote+"分+");
        if(!ratingLabel.isEmpty())parts.add("分级"+ratingLabel);
        if(hideInLibrary)parts.add("隐藏已入库");
        return String.join(" · ",parts);
    }
    private void chooseSort(){
        String[] labels=new String[SORTS.length];int selected=0;
        for(int i=0;i<SORTS.length;i++){labels[i]=SORTS[i][0];if(SORTS[i][1].equals(sortKey))selected=i;}
        new AlertDialog.Builder(this).setTitle("排序").setSingleChoiceItems(labels,selected,(dialog,which)->{dialog.dismiss();sortKey=SORTS[which][1];browse();}).setNegativeButton("取消",null).show();
    }
    private void chooseGenres(){
        JSONObject options=optionsCache.get(kind);
        JSONArray genres=options==null?null:options.optJSONArray("genres");
        if(genres==null||genres.length()==0){new AlertDialog.Builder(this).setTitle("风格").setMessage("风格选项尚未加载完成，请稍后再试。").setPositiveButton("关闭",null).show();return;}
        new AlertDialog.Builder(this).setTitle("风格筛选").setItems(new String[]{"包含所选风格","排除所选风格","清除风格筛选"},(dialog,which)->{
            if(which==2){includeGenres.clear();excludeGenres.clear();browse();return;}
            final boolean include=which==0;
            final Map<String,String> selected=include?includeGenres:excludeGenres;
            String[] labels=new String[genres.length()];final boolean[] checked=new boolean[genres.length()];
            for(int i=0;i<genres.length();i++){
                JSONObject genre=genres.optJSONObject(i);labels[i]=genre==null?"":genre.optString("label");
                checked[i]=genre!=null&&selected.containsKey(genre.optString("value"));
            }
            new AlertDialog.Builder(this).setTitle(include?"包含风格（可多选）":"排除风格（可多选）").setMultiChoiceItems(labels,checked,(dialog2,index,value)->checked[index]=value).setPositiveButton("应用",(dialog2,which2)->{
                selected.clear();
                for(int i=0;i<genres.length();i++)if(checked[i]){JSONObject genre=genres.optJSONObject(i);if(genre!=null)selected.put(genre.optString("value"),genre.optString("label"));}
                (include?excludeGenres:includeGenres).clear();
                browse();
            }).setNegativeButton("取消",null).show();
        }).setNegativeButton("取消",null).show();
    }
    private void chooseCountry(){
        JSONObject options=optionsCache.get(kind);
        JSONArray countries=options==null?null:options.optJSONArray("countries");
        if(countries==null||countries.length()==0){new AlertDialog.Builder(this).setTitle("地区").setMessage("地区选项尚未加载完成，请稍后再试。").setPositiveButton("关闭",null).show();return;}
        final List<String> labels=new ArrayList<>(),values=new ArrayList<>();
        labels.add("全部地区");values.add("");
        for(int i=0;i<countries.length();i++){JSONObject item=countries.optJSONObject(i);if(item==null)continue;labels.add(item.optString("label"));values.add(item.optString("value"));}
        new AlertDialog.Builder(this).setTitle("地区").setSingleChoiceItems(labels.toArray(new String[0]),values.indexOf(countryValue),(dialog,which)->{dialog.dismiss();countryValue=values.get(which);countryLabel=which==0?"":labels.get(which);browse();}).setNegativeButton("取消",null).show();
    }
    private void chooseLanguage(){
        JSONObject options=optionsCache.get(kind);
        JSONArray languages=options==null?null:options.optJSONArray("languages");
        if(languages==null||languages.length()==0){new AlertDialog.Builder(this).setTitle("语言").setMessage("语言选项尚未加载完成，请稍后再试。").setPositiveButton("关闭",null).show();return;}
        final List<String> labels=new ArrayList<>(),values=new ArrayList<>();
        labels.add("全部语言");values.add("");
        for(int i=0;i<languages.length();i++){JSONObject item=languages.optJSONObject(i);if(item==null)continue;labels.add(item.optString("label"));values.add(item.optString("value"));}
        new AlertDialog.Builder(this).setTitle("语言").setSingleChoiceItems(labels.toArray(new String[0]),values.indexOf(languageValue),(dialog,which)->{dialog.dismiss();languageValue=values.get(which);languageLabel=which==0?"":labels.get(which);browse();}).setNegativeButton("取消",null).show();
    }
    private void chooseEra(){
        new AlertDialog.Builder(this).setTitle("年代").setSingleChoiceItems(ERA_LABELS,eraIndex,(dialog,which)->{
            dialog.dismiss();eraIndex=which;
            yearFrom=ERA_BOUNDS[which][0]>0?ERA_BOUNDS[which][0]:null;
            yearTo=ERA_BOUNDS[which][1]>0?ERA_BOUNDS[which][1]:null;
            browse();
        }).setNegativeButton("取消",null).show();
    }
    private void chooseVote(){
        int selected=minVote==null?0:minVote-5;
        new AlertDialog.Builder(this).setTitle("最低评分").setSingleChoiceItems(VOTES,selected,(dialog,which)->{dialog.dismiss();minVote=which==0?null:which+5;browse();}).setNegativeButton("取消",null).show();
    }
    private void chooseRating(){
        JSONObject options=optionsCache.get(kind);
        JSONArray ratings=options==null?null:options.optJSONArray("ratings");
        if(ratings==null||ratings.length()==0){new AlertDialog.Builder(this).setTitle("分级").setMessage("分级选项尚未加载完成，请稍后再试。").setPositiveButton("关闭",null).show();return;}
        final List<String> labels=new ArrayList<>(),values=new ArrayList<>();
        labels.add("全部");values.add("");
        for(int i=0;i<ratings.length();i++){JSONObject item=ratings.optJSONObject(i);if(item==null)continue;labels.add(item.optString("label"));values.add(String.valueOf(item.opt("value")));}
        String current=ratingIndex==null?"":String.valueOf(ratingIndex);
        new AlertDialog.Builder(this).setTitle("分级").setSingleChoiceItems(labels.toArray(new String[0]),values.indexOf(current),(dialog,which)->{
            dialog.dismiss();
            Integer parsed=null;
            String raw=values.get(which).trim();
            if(raw.matches("\\d+"))parsed=Integer.valueOf(raw);
            ratingIndex=parsed;ratingLabel=which==0?"":labels.get(which);
            browse();
        }).setNegativeButton("取消",null).show();
    }
    private void toggleHide(){
        hideInLibrary=!hideInLibrary;browse();
    }
    private void resetFilters(){
        query="";sortKey="popularity.desc";includeGenres.clear();excludeGenres.clear();
        countryValue="";countryLabel="";languageValue="";languageLabel="";ratingIndex=null;ratingLabel="";minVote=null;
        eraIndex=0;yearFrom=null;yearTo=null;hideInLibrary=false;
        browse();
    }
    private void searchMedia(){
        EditText search=input("影片名称",false);search.setText(query);
        new AlertDialog.Builder(this).setTitle("搜索影视").setView(search).setPositiveButton("搜索",(dialog,which)->{query=search.getText().toString().trim();browse();}).setNegativeButton("取消",null).show();
    }

    @Override public void onBackPressed(){
        if(menu.getVisibility()==View.VISIBLE){hideMenu();return;}
        if(!back.isEmpty()){back.pop().run();return;}
        super.onBackPressed();
    }

    // ---------- 纯展示助手（实现见 DiscoveryPresentation，可离线测试） ----------
    static String yearOf(JSONObject item){return DiscoveryPresentation.yearOf(item);}
    static String statusLabel(JSONObject item){return DiscoveryPresentation.statusLabel(item);}
    static int statusColor(JSONObject item){return DiscoveryPresentation.statusColor(item);}
    static String mediaTypeOf(JSONObject item,String fallback){return DiscoveryPresentation.mediaTypeOf(item,fallback);}
    private static void setString(JSONObject item,String key,String value){try{item.put(key,value);}catch(JSONException ignored){}}
    private static void fillIfEmpty(JSONObject item,String key,String value){
        if(value==null||value.isEmpty()||!item.optString(key).isEmpty())return;
        try{item.put(key,value);}catch(JSONException ignored){}
    }
}
