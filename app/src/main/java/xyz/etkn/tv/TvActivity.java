package xyz.etkn.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

abstract class TvActivity extends Activity {
    /** 媒体卡片聚焦放大比例；FocusScroll.MARGIN_DP 必须覆盖其对最宽卡片的单侧溢出。 */
    static final float FOCUS_SCALE = 1.08f;
    static final int INK = Color.rgb(11,17,26), PANEL = Color.rgb(25,36,50), ACCENT = Color.rgb(118,213,176), MUTED = Color.rgb(163,179,198);
    static final int OVERLAY = Color.argb(150,0,0,0);
    static final int TEXT=0xfff4f5f6, SOFT=0xffe1faf1, PROGRESS=0xffedc2ff;
    static final ExecutorService IO = Executors.newFixedThreadPool(6);
    LinearLayout root, content, nav;
    TextView status;
    ScrollView screenScroll;
    EtknApi api;
    int dp(float value) { return Math.round(getResources().getDisplayMetrics().density*value); }
    private TvPreferences preferences;
    private int appliedImageSize=-1;
    @Override public void onCreate(Bundle state) { api = new EtknApi(this); TvAppearance.load(this,api.server);setTheme(appearanceDark()?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(state); preferences=new TvPreferences(this,api); if(api.session.persist()!=SessionStore.PERSIST_FULL)getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); CrashLog.install(getApplicationContext()); CrashLog.reportLast(this); }
    private AppearanceBackground serverBackground;
    private boolean appearanceForeground,appearanceLoading;
    private final java.util.ArrayList<java.lang.ref.WeakReference<TextView>> themedTexts=new java.util.ArrayList<>();
    private final java.util.ArrayList<java.lang.ref.WeakReference<android.app.AlertDialog>> themedDialogs=new java.util.ArrayList<>();
    private final Runnable appearancePoll=new Runnable(){@Override public void run(){refreshAppearance();noticeTimer.postDelayed(this,3000);}};
    @Override public void setContentView(View view){
        if(this instanceof PlayerActivity||this instanceof DetailActivity){super.setContentView(view);return;}
        FrameLayout stage=new FrameLayout(this);serverBackground=new AppearanceBackground(this);stage.addView(serverBackground,new FrameLayout.LayoutParams(-1,-1));view.setBackgroundColor(Color.TRANSPARENT);stage.addView(view,new FrameLayout.LayoutParams(-1,-1));super.setContentView(stage);
        updateAppearanceViews();
    }
    private void refreshAppearance(){
        if(!appearanceForeground||appearanceLoading||api.token.isEmpty())return;
        appearanceLoading=true;
        IO.execute(()->{org.json.JSONObject value=null;try{value=api.get("/api/tv/System/Appearance");}catch(Exception ignored){}org.json.JSONObject result=value;runOnUiThread(()->{
            appearanceLoading=false;if(!appearanceForeground||isDestroyed()||!api.server.equals(api.session.server())||!api.token.equals(api.session.token())||result==null)return;
            if(!result.toString().equals(TvAppearance.current.toString())){TvAppearance.save(this,api.server,result);setTheme(appearanceDark()?R.style.AppThemeDark:R.style.AppTheme);updateAppearanceViews();appearanceChanged();}
        });});
    }
    private boolean forceDarkAppearance(){return this instanceof PlayerActivity||this instanceof DetailActivity;}
    boolean appearanceDark(){return forceDarkAppearance()||TvAppearance.dark();}
    int resolveColor(int color){return TvAppearance.resolve(color,forceDarkAppearance());}
    void appearanceChanged(){}
    void themeText(TextView view,int color){view.setTag(R.id.appearance_text,color);view.setTextColor(resolveColor(color));themedTexts.add(new java.lang.ref.WeakReference<>(view));}
    private void updateAppearanceViews(){
        themedTexts.removeIf(reference->{TextView view=reference.get();if(view==null)return true;Object color=view.getTag(R.id.appearance_text);if(color instanceof Integer)view.setTextColor(resolveColor((Integer)color));return false;});
        if(serverBackground!=null)serverBackground.update();updateAppearanceTree(getWindow().getDecorView());
        themedDialogs.removeIf(reference->{android.app.AlertDialog dialog=reference.get();if(dialog==null||!dialog.isShowing())return true;updateDialogAppearance(dialog);return false;});
    }
    android.app.AlertDialog.Builder dialogBuilder(){return new android.app.AlertDialog.Builder(this,appearanceDark()?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);}
    void themeDialog(android.app.AlertDialog dialog){
        themedDialogs.add(new java.lang.ref.WeakReference<>(dialog));updateDialogAppearance(dialog);
        ListView list=dialog.getListView();if(list!=null)list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){@Override public void onChildViewAdded(View parent,View child){themeDialogTree(child);}@Override public void onChildViewRemoved(View parent,View child){}});
    }
    private void updateDialogAppearance(android.app.AlertDialog dialog){
        if(dialog.getWindow()==null)return;android.graphics.drawable.GradientDrawable background=shape(TvAppearance.surface(appearanceDark()),0);background.setCornerRadius(dp(18));dialog.getWindow().setBackgroundDrawable(background);dialog.getWindow().setElevation(dp(8)*TvAppearance.shadow());
        themeDialogTree(dialog.getWindow().getDecorView());updateAppearanceTree(dialog.getWindow().getDecorView());
    }
    private void themeDialogTree(View view){
        if(view instanceof TextView){TextView text=(TextView)view;int color=view instanceof Button?ACCENT:TEXT;if(view.getTag(R.id.appearance_text)==null)themeText(text,color);else text.setTextColor(resolveColor((Integer)view.getTag(R.id.appearance_text)));}
        if(view instanceof Button){view.setStateListAnimator(null);view.setBackgroundTintList(null);view.setBackground(selectionBackground());}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)themeDialogTree(group.getChildAt(i));}
    }
    private void updateAppearanceTree(View view){
        if(view.getTag(R.id.appearance_shadow)!=null)shadow(view,(Integer)view.getTag(R.id.appearance_shadow));
        if(view instanceof CompoundButton){android.content.res.ColorStateList tint=new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{resolveColor(ACCENT),resolveColor(MUTED)});CompoundButton toggle=(CompoundButton)view;toggle.setButtonTintList(tint);if(toggle instanceof Switch){((Switch)toggle).setThumbTintList(tint);((Switch)toggle).setTrackTintList(tint);}}
        if(view instanceof EditText)((EditText)view).setHintTextColor(resolveColor(MUTED));
        if(view instanceof ProgressBar){ProgressBar bar=(ProgressBar)view;bar.setProgressTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));}
        view.invalidate();if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)updateAppearanceTree(group.getChildAt(i));}
    }
    void shadow(View view,int elevation){view.setTag(R.id.appearance_shadow,elevation);view.setOutlineProvider(new android.view.ViewOutlineProvider(){@Override public void getOutline(View target,android.graphics.Outline outline){outline.setRoundRect(0,0,target.getWidth(),target.getHeight(),dp(12));outline.setAlpha(0);}});view.setElevation(dp(elevation)*TvAppearance.shadow());view.setTranslationZ(0);OuterShadow.apply(view,dp(elevation),TvAppearance.shadow(),appearanceDark());}
    private final android.os.Handler noticeTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable noticePoll=new Runnable(){@Override public void run(){IngestNotifications.check(TvActivity.this);noticeTimer.postDelayed(this,60000);}};
    private final java.util.Map<Long,String> refreshTasks=new java.util.LinkedHashMap<>();
    private boolean refreshForeground,refreshChecking;
    private final Runnable refreshPoll=()->pollRefreshTasks();
    /** 前台 Activity 计数：归零表示应用整体退到后台，按登录方式配置重置门禁。 */
    private static int foregroundActivities;
    @Override protected void onStart(){super.onStart();foregroundActivities++;}
    @Override protected void onStop(){if(api.session.persist()!=SessionStore.PERSIST_FULL)getWindow().getDecorView().setVisibility(View.INVISIBLE);if(--foregroundActivities==0&&!isChangingConfigurations()){if(api.session.persist()==SessionStore.PERSIST_NONE)IngestNotifications.logout(this);LoginGate.reset();SessionStore.endTemporarySession();}super.onStop();}
    @Override protected void onResume(){super.onResume();if(!(this instanceof SetupActivity)&&(!LoginGate.isSatisfied()&&(api.session.persist()!=SessionStore.PERSIST_FULL||!api.token.isEmpty())||LoginGate.requiresPicker(this,api))){LoginGate.enter(this);return;}getWindow().getDecorView().setVisibility(View.VISIBLE);appearanceForeground=true;updateAppearanceViews();noticeTimer.removeCallbacks(appearancePoll);noticeTimer.post(appearancePoll);if(LoginGate.isSatisfied()){int size=preferences.imageSize();boolean changed=appliedImageSize>=0&&appliedImageSize!=size;appliedImageSize=size;if(changed){applyImageSizeNow();imageSizeChanged();}IngestNotifications.resumed(this);noticeTimer.post(noticePoll);refreshForeground=true;noticeTimer.post(refreshPoll);}}
    @Override protected void onPause(){appearanceForeground=false;noticeTimer.removeCallbacks(appearancePoll);refreshForeground=false;noticeTimer.removeCallbacks(refreshPoll);noticeTimer.removeCallbacks(noticePoll);IngestNotifications.paused(this);super.onPause();}
    int imageSize(){return preferences.imageSize();}
    float imageScale(){switch(imageSize()){case TvPreferences.IMAGE_EXTREMELY_SMALL:return 0.72f;case TvPreferences.IMAGE_SMALL:return 0.86f;case TvPreferences.IMAGE_LARGE:return 1.14f;case TvPreferences.IMAGE_EXTREMELY_LARGE:return 1.28f;default:return 1f;}}
    int imageDimension(int baseDp){return Math.round(baseDp*imageScale());}
    int imageDp(int baseDp){return dp(imageDimension(baseDp));}
    void scaleImageText(TextView view,float baseSp,int baseHeightDp){
        Runnable update=()->{
            view.setTextSize(baseSp*imageScale());
            ViewGroup.LayoutParams layout=view.getLayoutParams();
            int height=imageDp(baseHeightDp);
            if(layout!=null&&layout.height!=height){layout.height=height;view.setLayoutParams(layout);}
        };
        update.run();imageSizeUpdater(view,update);
    }

    void imageSizeUpdater(View view,Runnable updater){view.setTag(R.id.image_size_updater,updater);}
    void imageSizeChanged(){}
    int availableWidth(int insetDp){return Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(insetDp));}
    int cardColumns(int width,int cardWidthDp,int gapDp){return CardGeometry.columns(Math.round(width/getResources().getDisplayMetrics().density),cardWidthDp,gapDp,imageSize());}
    int gridCapacity(GridLayout grid,ScrollView viewport,float aspect,int captionHeightDp,int rowGapDp){
        Object tag=grid.getTag(R.id.adaptive_grid);if(!(tag instanceof AdaptiveGridConfig))return 1;
        AdaptiveGridConfig config=(AdaptiveGridConfig)tag;
        int width=grid.getWidth()>0?grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight():availableWidth(config.horizontalInsetDp);
        int height=viewport.getHeight()>0?viewport.getHeight():getResources().getDisplayMetrics().heightPixels;
        if(grid.isLaidOut()){int[] position=new int[2],top=new int[2];grid.getLocationInWindow(position);viewport.getLocationInWindow(top);height-=Math.max(0,position[1]-top[1]);}
        height-=grid.getPaddingTop()+viewport.getPaddingBottom();
        int columns=Math.max(1,grid.getColumnCount());
        return CardGeometry.capacity(width,height,columns,dp(config.gapDp),aspect,imageDp(captionHeightDp),dp(rowGapDp));
    }
    int shelfCapacity(int cardWidthDp,int gapDp,int insetDp){return CardGeometry.shelfCapacity(availableWidth(insetDp),imageDp(cardWidthDp)+2*imageDp(gapDp));}
    int prefetchDistance(GridLayout grid){return grid!=null&&grid.getChildCount()>0?grid.getChildAt(0).getHeight()+dp(20):dp(80);}
    void afterLayout(View view,Runnable action){
        if(view.isLaidOut()&&!view.isLayoutRequested()){action.run();return;}
        view.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){@Override public void onGlobalLayout(){view.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(!isFinishing()&&!isDestroyed())action.run();}});
    }
    private static final class AdaptiveGridConfig {int cardWidthDp,gapDp,horizontalInsetDp,lastColumns=-1,lastWidth=-1;AdaptiveGridConfig(int cardWidthDp,int gapDp,int horizontalInsetDp){this.cardWidthDp=cardWidthDp;this.gapDp=gapDp;this.horizontalInsetDp=horizontalInsetDp;}}
    boolean isAdaptiveGrid(GridLayout grid){return grid.getTag(R.id.adaptive_grid) instanceof AdaptiveGridConfig;}
    void watchGridColumns(GridLayout grid,int cardWidthDp,int gapDp,int horizontalInsetDp){
        grid.setTag(R.id.adaptive_grid,new AdaptiveGridConfig(cardWidthDp,gapDp,horizontalInsetDp));
        Runnable update=()->resizeAdaptiveGrid(grid);imageSizeUpdater(grid,update);
        grid.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{if(right-left!=oldRight-oldLeft)update.run();});
        update.run();
    }
    void setAdaptiveGridTarget(GridLayout grid,int cardWidthDp,int gapDp){
        Object tag=grid.getTag(R.id.adaptive_grid);if(!(tag instanceof AdaptiveGridConfig))return;
        AdaptiveGridConfig config=(AdaptiveGridConfig)tag;config.cardWidthDp=cardWidthDp;config.gapDp=gapDp;config.lastColumns=-1;resizeAdaptiveGrid(grid);
    }
    void resizeAdaptiveGrid(GridLayout grid){
        Object tag=grid.getTag(R.id.adaptive_grid);if(!(tag instanceof AdaptiveGridConfig))return;
        AdaptiveGridConfig config=(AdaptiveGridConfig)tag;
        int width=grid.getWidth()>0?grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight():dp(Math.max(1,getResources().getConfiguration().screenWidthDp-config.horizontalInsetDp));
        int columns=cardColumns(width,config.cardWidthDp,config.gapDp);
        if(columns==config.lastColumns&&width==config.lastWidth)return;
        for(int i=0;i<grid.getChildCount();i++){
            View child=grid.getChildAt(i);if(!(child.getLayoutParams() instanceof GridLayout.LayoutParams))continue;
            GridLayout.LayoutParams layout=(GridLayout.LayoutParams)child.getLayoutParams();layout.rowSpec=GridLayout.spec(GridLayout.UNDEFINED);layout.columnSpec=GridLayout.spec(GridLayout.UNDEFINED);layout.width=Math.max(1,width/columns-layout.leftMargin-layout.rightMargin);layout.height=ViewGroup.LayoutParams.WRAP_CONTENT;layout.setGravity(android.view.Gravity.START);child.setLayoutParams(layout);
        }
        grid.setColumnCount(columns);config.lastColumns=columns;config.lastWidth=width;
    }
    void addAdaptiveGridItem(GridLayout grid,View card,int gapDp,int verticalGapDp){
        AdaptiveGridConfig config=(AdaptiveGridConfig)grid.getTag(R.id.adaptive_grid);
        int width=grid.getWidth()>0?grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight():dp(Math.max(1,getResources().getConfiguration().screenWidthDp-config.horizontalInsetDp));
        // 少量条目仍占一个正常列宽，不能按现有条目数拉伸填满整行。
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=CardGeometry.cardWidth(width,Math.max(1,grid.getColumnCount()),dp(gapDp));layout.height=ViewGroup.LayoutParams.WRAP_CONTENT;
        layout.rowSpec=GridLayout.spec(GridLayout.UNDEFINED);layout.columnSpec=GridLayout.spec(GridLayout.UNDEFINED);layout.setGravity(android.view.Gravity.START);
        layout.setMargins(dp(gapDp),dp(verticalGapDp),dp(gapDp),dp(verticalGapDp));grid.addView(card,layout);
    }
    void fitArtworkHeight(View card,View artwork,float heightToWidth){
        Runnable update=()->{int width=card.getWidth();if(width<=0)return;int height=Math.round(width*heightToWidth);ViewGroup.LayoutParams layout=artwork.getLayoutParams();if(layout.height!=height){layout.height=height;artwork.setLayoutParams(layout);}};
        card.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{if(right-left!=oldRight-oldLeft)update.run();});card.post(update);
    }
    void applyImageSizeNow(){applyImageSize(getWindow().getDecorView());}
    private void applyImageSize(View view){Object update=view.getTag(R.id.image_size_updater);if(update instanceof Runnable)((Runnable)update).run();if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyImageSize(group.getChildAt(i));}}
    void refreshSubmitted(String itemId,org.json.JSONObject result){
        status.setText(result.optBoolean("submitted")?"元数据刷新任务已提交":"task_already_running".equals(result.optString("reason"))?"已有元数据刷新任务正在运行":"当前媒体无需提交刷新任务");
        long runId=result.optLong("RefreshRunId");
        if(result.optBoolean("submitted")&&runId>0){refreshTasks.put(runId,"/api/tv/Items/"+EtknApi.encode(itemId)+"/Refresh/"+runId);noticeTimer.removeCallbacks(refreshPoll);noticeTimer.postDelayed(refreshPoll,3000);}
    }
    private void pollRefreshTasks(){
        if(!api.token.equals(api.session.token())||!api.server.equals(api.session.server())){refreshTasks.clear();return;}
        if(!refreshForeground||refreshChecking||refreshTasks.isEmpty())return;
        refreshChecking=true;java.util.Map<Long,String> pending=new java.util.LinkedHashMap<>(refreshTasks);
        async(()->{java.util.Map<Long,org.json.JSONObject> results=new java.util.LinkedHashMap<>();for(java.util.Map.Entry<Long,String> task:pending.entrySet()){try{results.put(task.getKey(),api.get(task.getValue()));}catch(Exception ignored){}}return results;},results->{
            refreshChecking=false;
            if(!refreshForeground)return;
            if(!api.token.equals(api.session.token())||!api.server.equals(api.session.server())){refreshTasks.clear();return;}
            for(java.util.Map.Entry<Long,org.json.JSONObject> task:results.entrySet()){
                org.json.JSONObject result=task.getValue();
                if(result.optLong("RefreshRunId")!=task.getKey()||!result.optBoolean("finished")||refreshTasks.remove(task.getKey())==null)continue;
                String state=result.optString("status");
                if(state.equals("succeeded"))metadataRefreshed();
                else{String message=state.equals("cancelled")?"元数据/图片刷新已取消":state.equals("partial")?"元数据/图片刷新部分失败":"元数据/图片刷新失败";status.setText(message);Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
            }
            if(!refreshTasks.isEmpty())noticeTimer.postDelayed(refreshPoll,3000);
        });
    }
    void metadataRefreshed(){status.setText("元数据/图片已刷新");Toast.makeText(this,"元数据/图片已刷新",Toast.LENGTH_SHORT).show();}
    void screen(String title, String subtitle) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(36),dp(20),dp(36),dp(18)); root.setBackgroundColor(INK);
        TextView brand = text("ETKN  /  TV", 12, ACCENT); root.addView(brand);
        TextView heading = text(title, 29, TEXT); heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
        if (!subtitle.isEmpty()) root.addView(text(subtitle, 13, MUTED));
        nav = row(); root.addView(nav); status = text("", 13, MUTED); root.addView(status);
        ScrollView scroll = new FocusScroll.Vertical(this); scroll.setFillViewport(true);
        content = column(); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); screenScroll = scroll; setContentView(root);
    }
    LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL);view.setClipChildren(false);view.setClipToPadding(false); return view; }
    LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);view.setClipChildren(false);view.setClipToPadding(false); return view; }
    TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); themeText(view,color); view.setPadding(0,dp(5),0,dp(5)); return view; }
    GradientDrawable shape(int color, int stroke) { return new AppearanceShape(color,stroke,forceDarkAppearance()); }
    private android.graphics.drawable.Drawable focusFill(int color){return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shape(color,0),new FocusPulse(dp(12),dp(3),ACCENT,forceDarkAppearance())});}
    StateListDrawable focusBackground() { StateListDrawable state = new StateListDrawable(); state.addState(new int[]{android.R.attr.state_focused},focusFill(SOFT)); state.addState(new int[]{android.R.attr.state_pressed},shape(SOFT,ACCENT)); state.addState(new int[]{},shape(PANEL,0)); return state; }
    StateListDrawable transparentFocusBackground() { StateListDrawable state=new StateListDrawable();state.addState(new int[]{android.R.attr.state_focused},focusFill(Color.TRANSPARENT));state.addState(new int[]{android.R.attr.state_pressed},shape(Color.TRANSPARENT,ACCENT));state.addState(new int[]{},shape(Color.TRANSPARENT,0));return state; }
    StateListDrawable selectionBackground(){
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shape(PANEL,0),focusOutline()}));
        background.addState(new int[]{},shape(PANEL,0));return background;
    }
    android.graphics.drawable.Drawable episodeBackground(int progress){
        android.graphics.drawable.StateListDrawable outline=new android.graphics.drawable.StateListDrawable();
        outline.addState(new int[]{android.R.attr.state_focused},new FocusPulse(dp(12),dp(3),ACCENT,forceDarkAppearance()));outline.addState(new int[]{android.R.attr.state_pressed},shape(Color.TRANSPARENT,ACCENT));outline.addState(new int[]{},shape(Color.TRANSPARENT,0));
        android.graphics.drawable.ClipDrawable fill=new android.graphics.drawable.ClipDrawable(shape(PROGRESS,0),android.view.Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL);fill.setLevel(progress);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shape(PANEL,0),fill,outline});
    }
    View backdropShade(){View shade=new View(this);shade.setBackgroundColor(Color.argb(125,24,24,24));return shade;}
    Button button(String title, Runnable click) {
        Button view = new Button(this); view.setStateListAnimator(null);view.setBackgroundTintList(null);shadow(view,3);view.setText(title); view.setAllCaps(false); themeText(view,TEXT); view.setTextSize(14); view.setBackground(focusBackground()); view.setMinHeight(dp(44)); view.setPadding(dp(18),dp(8),dp(18),dp(8));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2,dp(44)); layout.setMargins(0,dp(6),dp(10),dp(8)); view.setLayoutParams(layout); view.setOnClickListener(v -> click.run()); return view;
    }
    void nav(String title, Runnable click) { nav.addView(button(title,click)); }
    EditText input(String hint, boolean password) {
        EditText view = new EditText(this); view.setBackgroundTintList(null);view.setSingleLine(true); view.setTextSize(17); themeText(view,TEXT); view.setHintTextColor(resolveColor(MUTED)); view.setHint(hint); view.setPadding(dp(12),dp(8),dp(12),dp(8));
        view.setInputType(password ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_CLASS_TEXT); view.setBackground(focusBackground());
        if(password){view.setSaveEnabled(false);if(android.os.Build.VERSION.SDK_INT>=26)view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);}
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(dp(480),dp(48)); layout.setMargins(0,dp(8),0,dp(8)); view.setLayoutParams(layout); return view;
    }
    <T> void async(Callable<T> work, Consumer<T> done) {
        IO.execute(() -> { try { T result=work.call(); runOnUiThread(() -> { if(!isFinishing()&&!isDestroyed()) done.accept(result); }); }
            catch(Exception error) { runOnUiThread(() -> { if(!isFinishing()&&!isDestroyed()) { status.setText(error.getMessage()==null?"连接失败，请重试":error.getMessage()); if(error instanceof EtknApi.AuthException) { api.session.clear(); startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); } } }); } });
    }
    void open(Class<?> activity) { startActivity(new Intent(this,activity)); }
    View seasonCard(org.json.JSONObject season,Runnable click){
        FrameLayout card=new FrameLayout(this);card.setBackground(shape(PANEL,0));shadow(card,4);card.setContentDescription("季海报 · "+EtknApi.title(season));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(9));card.addView(poster,new FrameLayout.LayoutParams(-1,imageDp(138)));
        Button choice=button(EtknApi.title(season),click);choice.setBackground(transparentFocusBackground());shadow(choice,0);choice.setGravity(android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);choice.setPadding(dp(4),0,dp(4),dp(6));choice.setTextSize(13);choice.setMinHeight(0);card.addView(choice,new FrameLayout.LayoutParams(-1,-1));
        choice.setOnFocusChangeListener((view,focused)->shadow(card,focused?8:4));
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(imageDp(92),imageDp(170));bounds.setMargins(dp(6),dp(12),dp(10),dp(12));card.setLayoutParams(bounds);
        imageSizeUpdater(card,()->{bounds.width=imageDp(92);bounds.height=imageDp(170);card.setLayoutParams(bounds);poster.getLayoutParams().height=imageDp(138);poster.requestLayout();choice.setTextSize(13*imageScale());});
        Images.visible(api,api.image(season),poster,success->{});return card;
    }
    View mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click) {
        return mediaCard(grid,media,click,"CollectionFolder".equals(media.optString("Type")),false,null);
    }
    View mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click, boolean landscape) {
        return mediaCard(grid,media,click,landscape,false,null);
    }
    View mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click, boolean landscape, boolean resume, java.util.function.Consumer<View> focused) {
        boolean compact=resume||"CollectionFolder".equals(media.optString("Type"));
        boolean adaptive=isAdaptiveGrid(grid);int gap=compact||focused!=null?12:6;
        int cardWidth=compact?192:landscape?264:132,cardHeight=compact?150:landscape?215:248;
        float artRatio=compact||landscape?9f/16f:1.5f;
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(Color.TRANSPARENT);card.setOnClickListener(v->click.run());card.setClipChildren(false);card.setClipToPadding(false);grid.setClipChildren(false);grid.setClipToPadding(false);
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(shape(PANEL,0));artwork.setClipToOutline(true);shadow(artwork,4);artwork.setForegroundGravity(android.view.Gravity.FILL);ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(9));artwork.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        int imageHeight=compact?108:landscape?139:198;card.addView(artwork,new LinearLayout.LayoutParams(-1,adaptive?dp(Math.round(cardWidth*artRatio)):imageDp(imageHeight)));
        TextView title=text(resume?EtknApi.resumeTitle(media):EtknApi.title(media),compact?12:14,TEXT);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(0,dp(4),0,0);card.addView(title,new LinearLayout.LayoutParams(-1,adaptive?dp(compact?42:46):imageDp(compact?42:46)));scaleImageText(title,compact?12:14,compact?42:46);
        if(adaptive){addAdaptiveGridItem(grid,card,gap,5);fitArtworkHeight(card,artwork,artRatio);}
        else{GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=imageDp(cardWidth);layout.height=imageDp(cardHeight);layout.setMargins(imageDp(gap),dp(5),imageDp(gap),dp(5));grid.addView(card,layout);imageSizeUpdater(card,()->{((LinearLayout.LayoutParams)artwork.getLayoutParams()).height=imageDp(imageHeight);artwork.requestLayout();((LinearLayout.LayoutParams)title.getLayoutParams()).height=imageDp(compact?42:46);title.requestLayout();GridLayout.LayoutParams bounds=(GridLayout.LayoutParams)card.getLayoutParams();bounds.width=imageDp(cardWidth);bounds.height=imageDp(cardHeight);bounds.setMargins(imageDp(gap),dp(5),imageDp(gap),dp(5));card.setLayoutParams(bounds);});}
        String image=landscape&&!"CollectionFolder".equals(media.optString("Type"))?api.artwork(media,"Backdrop",640):api.image(media);
        Images.visible(api,image,poster,success->{if(!success&&!image.equals(api.image(media)))Images.load(api,api.image(media),poster);});
        card.setOnFocusChangeListener((view,hasFocus)->{card.setScaleX(hasFocus?FOCUS_SCALE:1f);card.setScaleY(hasFocus?FOCUS_SCALE:1f);card.setTranslationZ(0);shadow(artwork,hasFocus?8:4);artwork.setForeground(hasFocus?focusOutline():null);if(hasFocus&&focused!=null)focused.accept(card);});
        org.json.JSONObject data=media.optJSONObject("UserData");
        if(landscape&&data!=null&&data.optLong("PlaybackPositionTicks")>0&&media.optLong("RunTimeTicks")>0){
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.min(1000,1000.0*data.optLong("PlaybackPositionTicks")/media.optLong("RunTimeTicks")));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.argb(180,0,0,0)));FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,dp(4),android.view.Gravity.BOTTOM);bar.leftMargin=dp(10);bar.rightMargin=dp(10);bar.bottomMargin=dp(6);artwork.addView(progress,bar);
        }
        return card;
    }
    View posterCard(GridLayout grid,org.json.JSONObject media,Runnable click,String episodeCountMode){
        return posterCard(grid,media,click,episodeCountMode,false);
    }
    View posterCard(GridLayout grid,org.json.JSONObject media,Runnable click,String episodeCountMode,boolean landscape){
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(Color.TRANSPARENT);card.setOnClickListener(v->click.run());card.setClipChildren(false);card.setClipToPadding(false);grid.setClipChildren(false);grid.setClipToPadding(false);
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(shape(PANEL,0));artwork.setClipToOutline(true);shadow(artwork,4);ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(9));artwork.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        boolean adaptive=isAdaptiveGrid(grid);int imageHeight=landscape?108:198,cardWidth=landscape?192:132,cardHeight=landscape?108:255,gap=landscape?12:6;float artRatio=landscape?9f/16f:1.5f;
        card.addView(artwork,new LinearLayout.LayoutParams(-1,adaptive?dp(Math.round(cardWidth*artRatio)):imageDp(imageHeight)));
        android.graphics.drawable.GradientDrawable fade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,Color.argb(235,0,0,0)});
        LinearLayout ratings=row();ratings.setGravity(android.view.Gravity.CENTER);ratings.setPadding(0,dp(17),0,dp(3));ratings.setBackground(fade);ratings.setVisibility("BoxSet".equals(media.optString("Type"))?View.GONE:View.VISIBLE);
        FrameLayout.LayoutParams ratingBounds=new FrameLayout.LayoutParams(-1,dp(42),android.view.Gravity.BOTTOM);artwork.addView(ratings,ratingBounds);
        int scaleBaseWidthDp=landscape?192:132;
        java.util.List<Runnable> artworkScaleUpdates=new java.util.ArrayList<>();
        TextView ratingYear=landscape?text(media.optString("ProductionYear"),11,Color.WHITE):null;
        if(ratingYear!=null){
            ratingYear.setPadding(0,0,dp(8),0);ratingYear.setGravity(android.view.Gravity.CENTER_VERTICAL);ratingYear.setVisibility(ratingYear.getText().length()==0?View.GONE:View.VISIBLE);ratings.addView(ratingYear,new LinearLayout.LayoutParams(-2,-1));
        }
        ImageView doubanIcon=new ImageView(this);doubanIcon.setImageDrawable(doubanMark());doubanIcon.setBackground(shape(Color.rgb(0,119,34),0));doubanIcon.setPadding(dp(3),dp(3),dp(3),dp(3));ratings.addView(doubanIcon,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView douban=text(rating(media,"DoubanRating"),11,Color.WHITE);douban.setGravity(android.view.Gravity.CENTER_VERTICAL);douban.setPadding(0,0,0,0);LinearLayout.LayoutParams scoreSpace=new LinearLayout.LayoutParams(-2,-1);scoreSpace.leftMargin=dp(3);scoreSpace.rightMargin=dp(8);ratings.addView(douban,scoreSpace);
        ImageView star=new ImageView(this);star.setImageDrawable(tmdbMark());ratings.addView(star,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView tmdb=text(rating(media,"TMDbRating"),11,Color.WHITE);tmdb.setGravity(android.view.Gravity.CENTER_VERTICAL);tmdb.setPadding(0,0,0,0);LinearLayout.LayoutParams tmdbSpace=new LinearLayout.LayoutParams(-2,-1);tmdbSpace.leftMargin=dp(3);ratings.addView(tmdb,tmdbSpace);
        Runnable resizeRatings=()->{
            float scale=artworkScale(artwork,scaleBaseWidthDp);
            ratingBounds.height=Math.round(42*scale);ratings.setLayoutParams(ratingBounds);ratings.setPadding(0,Math.round(17*scale),0,Math.round(3*scale));
            for(TextView value:new TextView[]{ratingYear,douban,tmdb})if(value!=null)value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11*scale);
            if(ratingYear!=null)ratingYear.setPadding(0,0,Math.round(8*scale),0);
            for(ImageView icon:new ImageView[]{doubanIcon,star}){LinearLayout.LayoutParams bounds=(LinearLayout.LayoutParams)icon.getLayoutParams();bounds.width=bounds.height=Math.round(18*scale);icon.setLayoutParams(bounds);}
            int padding=Math.round(3*scale);doubanIcon.setPadding(padding,padding,padding,padding);
            scoreSpace.leftMargin=Math.round(3*scale);scoreSpace.rightMargin=Math.round(8*scale);douban.setLayoutParams(scoreSpace);tmdbSpace.leftMargin=Math.round(3*scale);tmdb.setLayoutParams(tmdbSpace);
        };
        artworkScaleUpdates.add(resizeRatings);
        TextView badge=null;org.json.JSONObject userData=media.optJSONObject("UserData");
        if("Series".equalsIgnoreCase(media.optString("Type"))&&userData!=null){
            int count=episodeCountMode.equals("total")?media.optInt("RecursiveItemCount",userData.optInt("UnplayedItemCount")):userData.optInt("UnplayedItemCount");
            if(count>0){
                TextView episodeBadge=text((episodeCountMode.equals("total")?"总 ":"未看 ")+count+" 集",10,Color.WHITE);episodeBadge.setGravity(android.view.Gravity.CENTER);episodeBadge.setPadding(dp(6),0,dp(6),0);episodeBadge.setBackground(shape(Color.argb(216,0,119,34),0));FrameLayout.LayoutParams badgeBounds=new FrameLayout.LayoutParams(-2,dp(24),android.view.Gravity.TOP|android.view.Gravity.RIGHT);badgeBounds.setMargins(0,dp(6),dp(6),0);artwork.addView(episodeBadge,badgeBounds);badge=episodeBadge;
                artworkScaleUpdates.add(()->{float scale=artworkScale(artwork,scaleBaseWidthDp);episodeBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,10*scale);episodeBadge.setPadding(Math.round(6*scale),0,Math.round(6*scale),0);badgeBounds.height=Math.round(24*scale);badgeBounds.topMargin=Math.round(6*scale);badgeBounds.rightMargin=Math.round(6*scale);episodeBadge.setLayoutParams(badgeBounds);});
            }
        }
        if(landscape)bannerIdentity(artwork,media,badge);
        if(!landscape){
            TextView title=text(EtknApi.title(media),14,TEXT);title.setGravity(android.view.Gravity.CENTER);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(2),dp(3),dp(2),0);card.addView(title,new LinearLayout.LayoutParams(-1,imageDp(25)));
            artworkScaleUpdates.add(()->{float scale=artworkScale(artwork,scaleBaseWidthDp);title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14*scale);title.setPadding(Math.round(2*scale),Math.round(3*scale),Math.round(2*scale),0);LinearLayout.LayoutParams bounds=(LinearLayout.LayoutParams)title.getLayoutParams();bounds.height=Math.round(25*scale);title.setLayoutParams(bounds);});
            TextView year=text(media.optString("ProductionYear"),12,MUTED);year.setGravity(android.view.Gravity.CENTER);year.setPadding(0,0,0,0);card.addView(year,new LinearLayout.LayoutParams(-1,imageDp(20)));
            artworkScaleUpdates.add(()->{float scale=artworkScale(artwork,scaleBaseWidthDp);year.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*scale);LinearLayout.LayoutParams bounds=(LinearLayout.LayoutParams)year.getLayoutParams();bounds.height=Math.round(20*scale);year.setLayoutParams(bounds);});
        }
        Runnable resizeArtworkContent=()->artworkScaleUpdates.forEach(Runnable::run);
        resizeArtworkContent.run();imageSizeUpdater(artwork,resizeArtworkContent);artwork.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)resizeArtworkContent.run();});
        if(adaptive){addAdaptiveGridItem(grid,card,gap,6);fitArtworkHeight(card,artwork,artRatio);}
        else{GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=imageDp(cardWidth);layout.height=imageDp(cardHeight);layout.setMargins(imageDp(gap),imageDp(6),imageDp(gap),imageDp(6));grid.addView(card,layout);imageSizeUpdater(card,()->{((LinearLayout.LayoutParams)artwork.getLayoutParams()).height=imageDp(imageHeight);artwork.requestLayout();GridLayout.LayoutParams bounds=(GridLayout.LayoutParams)card.getLayoutParams();bounds.width=imageDp(cardWidth);bounds.height=imageDp(cardHeight);bounds.setMargins(imageDp(gap),imageDp(6),imageDp(gap),imageDp(6));card.setLayoutParams(bounds);});}
        if(landscape){
            Runnable loadBackdrop=()->Images.visibleArtwork(api,width->api.artwork(media,"Backdrop",imageSize()==TvPreferences.IMAGE_EXTREMELY_LARGE?720:360),poster,success->{if(!success)Images.thumbnail(api,api.image(media),poster,poster.getWidth(),poster.getHeight(),ok->{});});
            loadBackdrop.run();imageSizeUpdater(poster,loadBackdrop);
        }
        else Images.load(api,api.image(media),poster);
        card.setOnFocusChangeListener((view,hasFocus)->{card.setScaleX(hasFocus?FOCUS_SCALE:1f);card.setScaleY(hasFocus?FOCUS_SCALE:1f);card.setTranslationZ(0);shadow(artwork,hasFocus?8:4);artwork.setForeground(hasFocus?focusOutline():null);});
        return card;
    }
    private void bannerIdentity(FrameLayout artwork,org.json.JSONObject media,View badge){
        FrameLayout identity=new FrameLayout(this);identity.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(140,0,0,0),Color.TRANSPARENT}));
        FrameLayout.LayoutParams area=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP|android.view.Gravity.START);artwork.addView(identity,2,area);
        TextView title=text(EtknApi.title(media),20,Color.WHITE);title.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this,R.font.smiley_sans));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setIncludeFontPadding(false);title.setGravity(android.view.Gravity.TOP|android.view.Gravity.START);
        identity.addView(title,new FrameLayout.LayoutParams(-1,-1));
        Runnable resize=()->{int width=artwork.getWidth()>0?artwork.getWidth():imageDp(192);float scale=width/(float)dp(192);area.height=Math.round(width*0.34f);identity.setLayoutParams(area);int badgeInset=badge==null?0:badge.getMeasuredWidth()+((FrameLayout.LayoutParams)badge.getLayoutParams()).rightMargin+Math.round(dp(6)*scale);identity.setPadding(Math.round(width*0.045f),Math.round(width*0.04f),Math.max(Math.round(width*0.045f),badgeInset),Math.round(width*0.025f));title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,width*0.10f);title.setShadowLayer(Math.max(dp(1),width*0.012f),0,width*0.005f,Color.argb(235,0,0,0));};resize.run();imageSizeUpdater(identity,resize);artwork.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)resize.run();});if(badge!=null)badge.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)resize.run();});
    }
    private float artworkScale(FrameLayout artwork,int baseWidthDp){return (artwork.getWidth()>0?artwork.getWidth():imageDp(baseWidthDp))/(float)baseWidthDp;}
    private String rating(org.json.JSONObject media,String field){double value=media.optDouble(field,Double.NaN);return Double.isNaN(value)?"–":String.format(java.util.Locale.ROOT,"%.1f",value);}
    android.graphics.drawable.Drawable doubanMark(){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.WHITE);
        android.graphics.Path path=androidx.core.graphics.PathParser.createPathFromPathData("M.51 3.06h22.98V.755H.51V3.06Zm20.976 2.537v9.608h-2.137l-1.669 5.76H24v2.28H0v-2.28h6.32l-1.67-5.76H2.515V5.597h18.972Zm-5.066 9.608H7.58l1.67 5.76h5.501l1.67-5.76ZM18.367 7.9H5.634v5.025h12.733V7.9Z");canvas.drawPath(path,paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(),bitmap);
    }
    android.graphics.drawable.Drawable tmdbMark(){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(255,205,75));
        canvas.scale(24f/22f,24f/21.2f);canvas.translate(-1,-1);
        canvas.drawPath(androidx.core.graphics.PathParser.createPathFromPathData("M12 1 15.4 8.2 23 9.2 17.5 14.6 18.8 22.2 12 18.6 5.2 22.2 6.5 14.6 1 9.2 8.6 8.2Z"),paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(),bitmap);
    }
    android.graphics.drawable.Drawable focusOutline(){return new FocusPulse(dp(9),dp(3),ACCENT,forceDarkAppearance());}
    GridLayout grid() { return grid(content); }
    GridLayout grid(LinearLayout target) { return grid(target,132,6,72); }
    GridLayout grid(LinearLayout target,int cardWidthDp,int gapDp,int horizontalInsetDp) { GridLayout grid=new GridLayout(this);grid.setClipChildren(false);grid.setClipToPadding(false);watchGridColumns(grid,cardWidthDp,gapDp,horizontalInsetDp);target.addView(grid);return grid; }
    void loadAvatar(ImageView view) {
        view.setImageResource(android.R.drawable.ic_menu_myplaces);
        async(()->api.get("/api/tv/Users/Me"),user->{String tag=user.optString("PrimaryImageTag");if(!tag.isEmpty())Images.load(api,api.server+api.userPath()+"/Images/Primary?maxWidth=160&tag="+EtknApi.encode(tag),view);});
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==1001&&resultCode==RESULT_OK)mediaDeleted();}
    void mediaDeleted(){recreate();}
    CharSequence episodeLabel(org.json.JSONObject episode,String suffix,boolean dates){
        String number=DetailPresentation.episodeNumber(episode)+suffix;if(!dates)return number;
        String date=DetailPresentation.airDate(episode);android.text.SpannableString label=new android.text.SpannableString(number+"\n"+(date.isEmpty()?"\u00a0":date));
        label.setSpan(new android.text.style.AbsoluteSizeSpan(11,true),number.length()+1,label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        float scale=getResources().getDisplayMetrics().scaledDensity;
        label.setSpan((android.text.style.LineHeightSpan)(text,start,end,spanStart,v,metrics)->{metrics.ascent=-Math.round(17*scale);metrics.descent=Math.round(5*scale);metrics.top=metrics.ascent;metrics.bottom=metrics.descent;metrics.leading=0;},0,label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return label;
    }
    void detail(org.json.JSONObject item) { startActivityForResult(new Intent(this,DetailActivity.class).putExtra("id",item.optString("Id")),1001); }
    void resumePlayback(org.json.JSONObject item){startActivity(new Intent(this,PlayerActivity.class).putExtra("id",item.optString("Id")).putExtra("title",EtknApi.resumeTitle(item)).putExtra("position",DetailPresentation.position(item)/10000).putExtra("seriesId",item.optString("Type").equals("Episode")?item.optString("SeriesId"):"").putExtra("seasonKey",PlaybackSelection.seasonKey(item)).putExtra("resume",true));}
}
