package xyz.etkn.tv;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

/** One persistent focus target: rotating artwork must never move remote focus. */
final class RecommendationHero extends FrameLayout {
    private final TvActivity activity;
    private final List<JSONObject> items;
    private final ImageView backdrop, logo;
    private final TextView title, metadata, overview, counter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int index;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (isShown() && !hasFocus() && items.size() > 1) show(index + 1);
            handler.postDelayed(this, 8000);
        }
    };

    RecommendationHero(TvActivity activity, List<JSONObject> items) {
        super(activity); this.activity = activity; this.items = items;
        setFocusable(true); setClickable(true); setContentDescription("为你推荐，左右切换，确定查看详情");
        setBackground(activity.focusBackground()); setPadding(activity.dp(3),activity.dp(3),activity.dp(3),activity.dp(3));
        backdrop = new ImageView(activity); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(backdrop, new LayoutParams(-1,-1));
        View shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(9,17,25),Color.argb(190,9,17,25),Color.argb(20,9,17,25)}));
        addView(shade,new LayoutParams(-1,-1));
        LinearLayout copy = activity.column(); copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(activity.dp(28),activity.dp(15),activity.dp(24),activity.dp(15));
        addView(copy,new LayoutParams(activity.dp(510),-1,Gravity.START));
        copy.addView(activity.text("为你推荐  /  你的观影口味",12,TvActivity.ACCENT));
        FrameLayout identity = new FrameLayout(activity);
        copy.addView(identity,new LinearLayout.LayoutParams(-1,activity.dp(86)));
        title = activity.text("",32,Color.WHITE); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2);
        identity.addView(title,new LayoutParams(-1,-1));
        logo = new ImageView(activity); logo.setScaleType(ImageView.ScaleType.FIT_START); logo.setVisibility(GONE);
        identity.addView(logo,new LayoutParams(activity.dp(330),activity.dp(80),Gravity.CENTER_VERTICAL));
        metadata=activity.text("",13,TvActivity.ACCENT); copy.addView(metadata);
        overview=activity.text("",14,Color.rgb(216,226,236));overview.setMaxLines(3);copy.addView(overview);
        counter=activity.text("",12,Color.WHITE);copy.addView(counter);
        setOnClickListener(v -> activity.detail(items.get(index)));
        setOnKeyListener((v,key,event) -> {
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT && key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){show(index+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));restart();}
            return true;
        });
        setOnFocusChangeListener((v,focus)->{setScaleX(focus?1.008f:1);setScaleY(focus?1.008f:1);});
        show(0);
    }
    private void show(int target) {
        index=(target%items.size()+items.size())%items.size(); JSONObject item=items.get(index);
        title.setText(EtknApi.title(item));title.setVisibility(VISIBLE);logo.setVisibility(GONE);logo.setImageDrawable(null);
        metadata.setText(item.optString("ProductionYear","")+"  ·  "+(item.optString("Type").equals("Series")?"剧集":"电影")+
                (item.optDouble("CommunityRating",0)>0?"  ·  ★ "+item.optString("CommunityRating"):""));
        overview.setText(item.optString("Overview"));counter.setText((index+1)+" / "+items.size()+"     ◀  ▶ 切换    确定 查看详情");
        backdrop.setImageDrawable(null);
        Images.load(activity.api,activity.api.artwork(item,"Backdrop",1280),backdrop);
        String logoUrl=activity.api.artwork(item,"Logo",600);
        logo.setTag(logoUrl);
        JSONObject tags=item.optJSONObject("ImageTags");
        if(tags!=null && !tags.optString("Logo").isEmpty())
            Images.load(activity.api,logoUrl,logo,success->{
                logo.setVisibility(success?VISIBLE:GONE);title.setVisibility(success?GONE:VISIBLE);
            });
    }
    private void restart(){handler.removeCallbacks(rotate);handler.postDelayed(rotate,8000);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();restart();}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
