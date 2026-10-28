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
    static final ExecutorService IO = Executors.newFixedThreadPool(6);
    LinearLayout root, content, nav;
    TextView status;
    EtknApi api;
    int dp(float value) { return Math.round(getResources().getDisplayMetrics().density*value); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); api = new EtknApi(this); }
    private final android.os.Handler noticeTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable noticePoll=new Runnable(){@Override public void run(){IngestNotifications.check(TvActivity.this);noticeTimer.postDelayed(this,60000);}};
    @Override protected void onResume(){super.onResume();IngestNotifications.resumed(this);noticeTimer.post(noticePoll);}
    @Override protected void onPause(){noticeTimer.removeCallbacks(noticePoll);IngestNotifications.paused(this);super.onPause();}
    void screen(String title, String subtitle) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(36),dp(20),dp(36),dp(18)); root.setBackgroundColor(INK);
        TextView brand = text("ETKN  /  TV", 12, ACCENT); root.addView(brand);
        TextView heading = text(title, 29, Color.WHITE); heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
        if (!subtitle.isEmpty()) root.addView(text(subtitle, 13, MUTED));
        nav = row(); root.addView(nav); status = text("", 13, MUTED); root.addView(status);
        ScrollView scroll = new FocusScroll.Vertical(this); scroll.setFillViewport(true);
        content = column(); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(0,dp(5),0,dp(5)); return view; }
    static GradientDrawable shape(int color, int stroke) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(12); if(stroke!=0) drawable.setStroke(3,stroke); return drawable; }
    StateListDrawable focusBackground() { StateListDrawable state = new StateListDrawable(); state.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(39,69,73),ACCENT)); state.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(39,69,73),ACCENT)); state.addState(new int[]{},shape(PANEL,0)); return state; }
    Button button(String title, Runnable click) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false); view.setTextColor(Color.WHITE); view.setTextSize(14); view.setBackground(focusBackground()); view.setMinHeight(dp(44)); view.setPadding(dp(18),dp(8),dp(18),dp(8));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2,dp(44)); layout.setMargins(0,dp(6),dp(10),dp(8)); view.setLayoutParams(layout); view.setOnClickListener(v -> click.run()); return view;
    }
    void nav(String title, Runnable click) { nav.addView(button(title,click)); }
    EditText input(String hint, boolean password) {
        EditText view = new EditText(this); view.setSingleLine(true); view.setTextSize(17); view.setTextColor(Color.WHITE); view.setHintTextColor(MUTED); view.setHint(hint); view.setPadding(dp(12),dp(8),dp(12),dp(8));
        view.setInputType(password ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_CLASS_TEXT); view.setBackground(focusBackground());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(dp(480),dp(48)); layout.setMargins(0,dp(8),0,dp(8)); view.setLayoutParams(layout); return view;
    }
    <T> void async(Callable<T> work, Consumer<T> done) {
        IO.execute(() -> { try { T result=work.call(); runOnUiThread(() -> { if(!isFinishing()&&!isDestroyed()) done.accept(result); }); }
            catch(Exception error) { runOnUiThread(() -> { if(!isFinishing()&&!isDestroyed()) { status.setText(error.getMessage()==null?"连接失败，请重试":error.getMessage()); if(error instanceof EtknApi.AuthException) { api.session.clear(); startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); } } }); } });
    }
    void open(Class<?> activity) { startActivity(new Intent(this,activity)); }
    View mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click) {
        return mediaCard(grid,media,click,"CollectionFolder".equals(media.optString("Type")),false,null);
    }
    View mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click, boolean landscape) {
        return mediaCard(grid,media,click,landscape,false,null);
    }
    View mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click, boolean landscape, boolean resume, java.util.function.Consumer<View> focused) {
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(Color.TRANSPARENT);card.setOnClickListener(v->click.run());
        FrameLayout artwork=new FrameLayout(this);artwork.setForegroundGravity(android.view.Gravity.FILL);ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(9));artwork.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        int imageHeight=landscape?139:198;card.addView(artwork,new LinearLayout.LayoutParams(-1,dp(imageHeight)));
        TextView title=text(resume?EtknApi.resumeTitle(media):EtknApi.title(media),14,Color.WHITE);title.setMaxLines(resume?3:2);title.setPadding(0,dp(4),0,0);card.addView(title,new LinearLayout.LayoutParams(-1,dp(resume?66:46)));
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=dp(landscape?264:132);layout.height=dp(landscape?215:248);layout.setMargins(dp(6),dp(5),dp(6),dp(5));grid.addView(card,layout);
        String image=landscape&&!"CollectionFolder".equals(media.optString("Type"))?api.artwork(media,"Backdrop",640):api.image(media);
        Images.load(api,image,poster,success->{if(!success&&!image.equals(api.image(media)))Images.load(api,api.image(media),poster);});
        card.setOnFocusChangeListener((view,hasFocus)->{card.setScaleX(hasFocus?FOCUS_SCALE:1f);card.setScaleY(hasFocus?FOCUS_SCALE:1f);card.setTranslationZ(hasFocus?dp(8):0);artwork.setForeground(hasFocus?focusOutline():null);if(hasFocus&&focused!=null)focused.accept(card);});
        org.json.JSONObject data=media.optJSONObject("UserData");
        if(landscape&&data!=null&&data.optLong("PlaybackPositionTicks")>0&&media.optLong("RunTimeTicks")>0){
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.min(1000,1000.0*data.optLong("PlaybackPositionTicks")/media.optLong("RunTimeTicks")));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.argb(180,0,0,0)));FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,dp(4),android.view.Gravity.BOTTOM);bar.leftMargin=dp(10);bar.rightMargin=dp(10);bar.bottomMargin=dp(6);artwork.addView(progress,bar);
        }
        return card;
    }
    View posterCard(GridLayout grid,org.json.JSONObject media,Runnable click,String episodeCountMode){
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(Color.TRANSPARENT);card.setOnClickListener(v->click.run());
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(shape(Color.TRANSPARENT,0));artwork.setClipToOutline(true);ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(poster,dp(9));artwork.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        card.addView(artwork,new LinearLayout.LayoutParams(-1,dp(198)));
        android.graphics.drawable.GradientDrawable fade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,Color.argb(235,0,0,0)});
        LinearLayout ratings=row();ratings.setGravity(android.view.Gravity.CENTER);ratings.setPadding(0,dp(17),0,dp(3));ratings.setBackground(fade);
        FrameLayout.LayoutParams ratingBounds=new FrameLayout.LayoutParams(-1,dp(42),android.view.Gravity.BOTTOM);artwork.addView(ratings,ratingBounds);
        ImageView doubanIcon=new ImageView(this);doubanIcon.setImageDrawable(doubanMark());doubanIcon.setBackground(shape(Color.rgb(0,119,34),0));doubanIcon.setPadding(dp(3),dp(3),dp(3),dp(3));ratings.addView(doubanIcon,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView douban=text(rating(media,"DoubanRating"),11,Color.WHITE);douban.setGravity(android.view.Gravity.CENTER_VERTICAL);douban.setPadding(0,0,0,0);LinearLayout.LayoutParams scoreSpace=new LinearLayout.LayoutParams(-2,-1);scoreSpace.leftMargin=dp(3);scoreSpace.rightMargin=dp(8);ratings.addView(douban,scoreSpace);
        TextView star=text("★",12,Color.rgb(255,205,75));star.setGravity(android.view.Gravity.CENTER_VERTICAL);star.setPadding(0,0,0,0);ratings.addView(star);
        TextView tmdb=text(rating(media,"TMDbRating"),11,Color.WHITE);tmdb.setGravity(android.view.Gravity.CENTER_VERTICAL);tmdb.setPadding(0,0,0,0);LinearLayout.LayoutParams tmdbSpace=new LinearLayout.LayoutParams(-2,-1);tmdbSpace.leftMargin=dp(3);ratings.addView(tmdb,tmdbSpace);
        org.json.JSONObject userData=media.optJSONObject("UserData");
        if("Series".equalsIgnoreCase(media.optString("Type"))&&userData!=null){
            int count=episodeCountMode.equals("total")?media.optInt("RecursiveItemCount",userData.optInt("UnplayedItemCount")):userData.optInt("UnplayedItemCount");
            if(count>0){TextView badge=text((episodeCountMode.equals("total")?"总 ":"未看 ")+count+" 集",10,Color.WHITE);badge.setGravity(android.view.Gravity.CENTER);badge.setPadding(dp(6),0,dp(6),0);badge.setBackground(shape(Color.argb(205,11,17,26),0));FrameLayout.LayoutParams badgeBounds=new FrameLayout.LayoutParams(-2,dp(24),android.view.Gravity.TOP|android.view.Gravity.RIGHT);badgeBounds.setMargins(0,dp(6),dp(6),0);artwork.addView(badge,badgeBounds);}
        }
        TextView title=text(EtknApi.title(media),14,Color.WHITE);title.setGravity(android.view.Gravity.CENTER);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(2),dp(3),dp(2),0);card.addView(title,new LinearLayout.LayoutParams(-1,dp(25)));
        TextView year=text(media.optString("ProductionYear"),12,MUTED);year.setGravity(android.view.Gravity.CENTER);year.setPadding(0,0,0,0);card.addView(year,new LinearLayout.LayoutParams(-1,dp(20)));
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=dp(132);layout.height=dp(255);layout.setMargins(dp(6),dp(6),dp(6),dp(6));grid.addView(card,layout);
        Images.load(api,api.image(media),poster);
        card.setOnFocusChangeListener((view,hasFocus)->{card.setScaleX(hasFocus?FOCUS_SCALE:1f);card.setScaleY(hasFocus?FOCUS_SCALE:1f);card.setTranslationZ(hasFocus?dp(8):0);artwork.setForeground(hasFocus?focusOutline():null);});
        return card;
    }
    private String rating(org.json.JSONObject media,String field){double value=media.optDouble(field,Double.NaN);return Double.isNaN(value)?"–":String.format(java.util.Locale.ROOT,"%.1f",value);}
    private android.graphics.drawable.Drawable doubanMark(){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.WHITE);
        android.graphics.Path path=androidx.core.graphics.PathParser.createPathFromPathData("M.51 3.06h22.98V.755H.51V3.06Zm20.976 2.537v9.608h-2.137l-1.669 5.76H24v2.28H0v-2.28h6.32l-1.67-5.76H2.515V5.597h18.972Zm-5.066 9.608H7.58l1.67 5.76h5.501l1.67-5.76ZM18.367 7.9H5.634v5.025h12.733V7.9Z");canvas.drawPath(path,paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(),bitmap);
    }
    private android.graphics.drawable.Drawable focusOutline(){android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(dp(9));outline.setStroke(dp(2),ACCENT);return outline;}
    GridLayout grid() { return grid(content); }
    GridLayout grid(LinearLayout target) { GridLayout grid=new GridLayout(this); int width=getResources().getConfiguration().screenWidthDp; grid.setColumnCount(Math.max(3,(width-72)/144)); target.addView(grid); return grid; }
    void loadAvatar(ImageView view) {
        view.setImageResource(android.R.drawable.ic_menu_myplaces);
        async(()->api.get("/api/tv/Users/Me"),user->{String tag=user.optString("PrimaryImageTag");if(!tag.isEmpty())Images.load(api,api.server+api.userPath()+"/Images/Primary?maxWidth=160&tag="+EtknApi.encode(tag),view);});
    }
    void detail(org.json.JSONObject item) { startActivity(new Intent(this,DetailActivity.class).putExtra("id",item.optString("Id"))); }
}
