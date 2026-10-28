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
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
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
    void mediaCard(GridLayout grid, org.json.JSONObject media, Runnable click) {
        LinearLayout card=column(); card.setPadding(dp(8),dp(8),dp(8),dp(8)); card.setFocusable(true); card.setClickable(true); card.setBackground(focusBackground()); card.setOnClickListener(v -> click.run());
        ImageView poster = new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP); poster.setBackgroundColor(PANEL); card.addView(poster,new LinearLayout.LayoutParams(-1,dp(152)));
        TextView title=text(EtknApi.title(media),14,Color.WHITE); title.setMaxLines(2); card.addView(title,new LinearLayout.LayoutParams(-1,dp(46)));
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams(); layout.width=dp(132); layout.height=dp(220); layout.setMargins(dp(4),dp(5),dp(4),dp(5)); grid.addView(card,layout);
        Images.load(api,api.image(media),poster);
    }
    GridLayout grid() { return grid(content); }
    GridLayout grid(LinearLayout target) { GridLayout grid=new GridLayout(this); int width=getResources().getConfiguration().screenWidthDp; grid.setColumnCount(Math.max(3,(width-72)/140)); target.addView(grid); return grid; }
    void loadAvatar(ImageView view) {
        view.setImageResource(android.R.drawable.ic_menu_myplaces);
        async(()->api.get("/emby/Users/Me"),user->{String tag=user.optString("PrimaryImageTag");if(!tag.isEmpty())Images.load(api,api.server+api.userPath()+"/Images/Primary?maxWidth=160&tag="+EtknApi.encode(tag),view);});
    }
    void detail(org.json.JSONObject item) { startActivity(new Intent(this,DetailActivity.class).putExtra("id",item.optString("Id"))); }
}
