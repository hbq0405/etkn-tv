package xyz.etkn.tv;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Preview is local. Only explicit confirmation writes shared calibration to the server. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class SubtitleOffsetSession {
    final SubtitleOffsetClock clock;
    private final PlayerActivity host;
    private final EtknApi api;
    private final JSONObject source;
    private final String path;
    private final Supplier<ExoPlayer> player;
    private final Supplier<JSONObject> selected;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final Runnable poll=new Runnable(){@Override public void run(){refresh();if(!closed)handler.postDelayed(this,10000);}};
    private AlertDialog dialog;
    private volatile boolean closed;
    private boolean loading,saving,confirmed;
    private int draft;
    SubtitleOffsetSession(PlayerActivity host,EtknApi api,String itemId,JSONObject source,Supplier<ExoPlayer> player,Supplier<JSONObject> selected){
        this.host=host;this.api=api;this.source=source;this.player=player;this.selected=selected;
        clock=new SubtitleOffsetClock(source.optLong("RunTimeTicks")/10000);
        String translation=source.optJSONObject("SubtitleTranslation")==null?"":source.optJSONObject("SubtitleTranslation").optString("Key");
        path="/api/tv/Items/"+EtknApi.encode(itemId)+"/"+EtknApi.encode(source.optString("Id"))+"/SubtitleOffsets"+(translation.isEmpty()?"":"?TranslationKey="+EtknApi.encode(translation));
        selectedChanged();handler.post(poll);
    }
    static String label(int value){return value==0?"未偏移":String.format(Locale.ROOT,"%s %.1f 秒",value>0?"延后":"提前",Math.abs(value)/1000.0);}
    void selectedChanged(){if(dialog==null){JSONObject target=selected.get();clock.milliseconds=target==null?0:target.optInt("SubtitleOffsetMs");}}
    private void preview(int value){
        if(clock.milliseconds==value)return;clock.milliseconds=value;
        ExoPlayer current=player.get();
        // Media3 elides same-position seeks. One millisecond forces subtitle samples to be read again.
        if(current!=null){long at=current.getCurrentPosition();current.seekTo(at>0?at-1:1);}
    }
    private List<JSONObject> targets(){List<JSONObject> values=PlaybackSelection.streams(source,true);JSONObject plan=source.optJSONObject("SubtitleTranslation");if(plan!=null)values.add(plan);return values;}
    static void inheritCalibration(JSONObject source,JSONObject original){
        if(source==original)return;
        for(JSONObject stream:PlaybackSelection.streams(source,true)){
            JSONObject match=PlaybackSelection.stream(original,stream.optInt("Index",-1),true);
            if(match!=null&&match.has("SubtitleOffsetKey"))try{stream.put("SubtitleOffsetKey",match.getString("SubtitleOffsetKey"));stream.put("SubtitleOffsetMs",match.optInt("SubtitleOffsetMs"));}catch(Exception ignored){}
        }
    }
    private void refresh(){
        if(closed||loading||dialog!=null)return;
        boolean available=false;for(JSONObject target:targets())if(target.has("SubtitleOffsetKey"))available=true;
        if(!available)return;loading=true;
        network.execute(()->{try{JSONObject result=api.get(path).optJSONObject("Offsets");handler.post(()->{
            loading=false;if(closed||dialog!=null||result==null)return;
            JSONObject active=selected.get();int previous=clock.milliseconds;
            for(JSONObject target:targets()){String key=target.optString("SubtitleOffsetKey");if(result.has(key))try{target.put("SubtitleOffsetMs",result.optInt(key));}catch(Exception ignored){}}
            if(active!=null&&previous!=active.optInt("SubtitleOffsetMs"))preview(active.optInt("SubtitleOffsetMs"));
        });}catch(Exception ignored){handler.post(()->loading=false);}});
    }
    void show(){
        JSONObject target=selected.get();if(closed||dialog!=null||target==null||!target.has("SubtitleOffsetKey")){TvNotice.show(host,"请先选择可独立播放的字幕",android.widget.Toast.LENGTH_LONG);return;}
        if(target.optBoolean("IsBurnedIn")){TvNotice.show(host,"字幕已合成到视频，无法单独偏移",android.widget.Toast.LENGTH_LONG);return;}
        draft=clock.milliseconds;confirmed=false;
        LinearLayout panel=host.column();panel.setPadding(host.dp(18),host.dp(14),host.dp(18),host.dp(14));
        panel.addView(host.text("字幕偏移",21,TvActivity.TEXT));TextView value=host.text(label(draft),24,TvActivity.TEXT);panel.addView(value);
        panel.addView(host.text("字幕太早请延后，太晚请提前\n调节立即预览，确认后所有设备共用",12,TvActivity.MUTED));
        List<Button> buttons=new ArrayList<>();
        for(int step:new int[]{500,100}){LinearLayout row=host.row();for(int direction:new int[]{-1,1}){
            Button button=host.button((direction<0?"提前 ":"延后 ")+(step/1000.0)+" 秒",()->{draft=Math.max(-600000,Math.min(600000,draft+direction*step));preview(draft);value.setText(label(draft));});
            buttons.add(button);row.addView(button,new LinearLayout.LayoutParams(0,host.dp(44),1));
        }panel.addView(row);}
        Button zero=host.button("归零",()->{draft=0;preview(0);value.setText(label(0));});buttons.add(zero);panel.addView(zero);
        TextView notice=host.text("",12,TvActivity.MUTED);panel.addView(notice);
        LinearLayout actions=host.row();Button cancel=host.button("取消",()->dialog.dismiss());buttons.add(cancel);actions.addView(cancel);
        Button confirm=host.button("确认",()->{
            if(saving)return;saving=true;dialog.setCancelable(false);for(Button button:buttons)button.setEnabled(false);notice.setText("正在保存…");int requested=draft;
            network.execute(()->{try{api.post(path,new JSONObject().put("SubtitleKey",target.optString("SubtitleOffsetKey")).put("OffsetMs",requested));handler.post(()->{
                if(closed)return;saving=false;try{target.put("SubtitleOffsetMs",requested);}catch(Exception ignored){}confirmed=true;dialog.dismiss();
            });}catch(Exception failure){handler.post(()->{if(closed)return;saving=false;dialog.setCancelable(true);for(Button button:buttons)button.setEnabled(true);notice.setText("保存失败，请重试或取消");});}});
        });buttons.add(confirm);actions.addView(confirm);panel.addView(actions);
        dialog=host.dialogBuilder().setView(panel).create();dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored->{dialog=null;if(!confirmed&&!closed)preview(target.optInt("SubtitleOffsetMs"));});
        dialog.setOnShowListener(ignored->{Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(host.shape(TvActivity.PANEL,0));window.setDimAmount(0);window.setGravity(Gravity.TOP|Gravity.RIGHT);android.view.WindowManager.LayoutParams params=window.getAttributes();params.x=host.dp(20);params.y=host.dp(24);window.setAttributes(params);window.setLayout(host.dp(320),-2);}buttons.get(0).requestFocus();});
        dialog.show();
    }
    void close(){closed=true;handler.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();network.shutdownNow();}
}
