package xyz.etkn.tv;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.app.AlertDialog;
import android.graphics.Typeface;
import org.json.JSONObject;

/** TV music uses a full-screen control panel with a persistent queue on the right. */
public final class MusicPlayerActivity extends TvActivity implements MusicPlayback.Listener {
    private MusicPlayback playback;
    private TextView name,time,queueTitle;
    private Button pause,album,artist,mode;
    private ImageView cover;
    private SeekBar seek;
    private ListView queue;
    private BaseAdapter adapter;
    private Button more;
    private JSONObject shown;
    private int queueSize=-1,playingIndex=-2;
    private boolean updating;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);playback=MusicPlayback.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root=column();root.setPadding(dp(32),dp(12),dp(32),dp(18));
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("ETKN  /  正在播放",14,ACCENT),new LinearLayout.LayoutParams(0,-2,1));top.addView(button("返回音乐库",this::finish));top.addView(button("停止播放",()->{MusicPlayback.stop();finish();}));root.addView(top);
        LinearLayout body=row();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=column();left.setGravity(Gravity.CENTER_HORIZONTAL);left.setPadding(dp(16),dp(2),dp(24),dp(8));body.addView(left,new LinearLayout.LayoutParams(0,-1,1.1f));
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(shape(PANEL,0));artwork.setClipToOutline(true);artwork.setForeground(Images.border(dp(12)));shadow(artwork,8);
        TextView placeholder=text("♪",72,ACCENT);placeholder.setGravity(Gravity.CENTER);artwork.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(cover,dp(12));artwork.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        int size=Math.max(80,Math.min(240,getResources().getConfiguration().screenHeightDp-410));left.addView(artwork,new LinearLayout.LayoutParams(dp(size),dp(size)));
        name=text("选择一首歌",23,TEXT);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);left.addView(name,new LinearLayout.LayoutParams(-1,dp(40)));
        album=button("专辑",()->{JSONObject item=playback.queue.current();if(item!=null&&!item.optString("AlbumKey").isEmpty())MusicActivity.open(this,"","albums",item.optString("AlbumKey"),item.optString("Album"));});
        artist=button("歌手",()->{JSONObject item=playback.queue.current();if(item!=null)MusicActions.artist(this,item,"");});
        for(Button view:new Button[]{album,artist}){view.setTextSize(13);view.setSingleLine(true);view.setEllipsize(android.text.TextUtils.TruncateAt.END);view.setMinHeight(0);LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,dp(32));bounds.setMargins(dp(18),dp(2),dp(18),dp(2));left.addView(view,bounds);}
        seek=new SeekBar(this);seek.setMax(1000);seek.setKeyProgressIncrement(20);seek.setContentDescription("播放进度，左右键调节");left.addView(seek,new LinearLayout.LayoutParams(-1,dp(34)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&!updating)playback.seek(playback.duration()*value/1000);}@Override public void onStartTrackingTouch(SeekBar bar){}@Override public void onStopTrackingTouch(SeekBar bar){}});
        time=text("0:00 / 0:00",12,MUTED);time.setGravity(Gravity.CENTER);left.addView(time,new LinearLayout.LayoutParams(-1,dp(25)));
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);controls.addView(button("上一首",()->playback.next(-1,false)));pause=button("播放",()->playback.toggle());controls.addView(pause);controls.addView(button("下一首",()->playback.next(1,false)));left.addView(controls);
        LinearLayout options=row();options.setGravity(Gravity.CENTER);mode=button("顺序播放",this::chooseMode);options.addView(mode);options.addView(button("音量",this::volume));options.addView(button("歌单",()->{JSONObject item=playback.queue.current();if(item!=null)MusicActions.playlist(this,item,()->{});}));left.addView(options);
        LinearLayout right=column();right.setPadding(dp(12),dp(10),dp(12),dp(8));right.setBackground(shape(PANEL,0));shadow(right,6);LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(0,-1,1);bounds.setMargins(dp(14),dp(8),dp(8),dp(8));body.addView(right,bounds);
        queueTitle=text("播放列表",19,TEXT);right.addView(queueTitle);
        queue=new ListView(this);queue.setDivider(null);queue.setDividerHeight(dp(10));queue.setPadding(dp(12),dp(12),dp(12),dp(12));queue.setClipToPadding(false);queue.setSelector(focusOutline());queue.setItemsCanFocus(false);queue.setCacheColorHint(android.graphics.Color.TRANSPARENT);right.addView(queue,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new BaseAdapter(){@Override public int getCount(){return playback.queue.items.size();}@Override public Object getItem(int position){return playback.queue.items.get(position);}@Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View recycled,ViewGroup parent){
                JSONObject item=playback.queue.items.get(position);LinearLayout row=row();row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(shape(position==playback.queue.index?SOFT:PANEL,0));
                ImageView image=new ImageView(MusicPlayerActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,dp(8));row.addView(image,new LinearLayout.LayoutParams(dp(44),dp(44)));Images.load(api,MusicViews.artwork(api,item,128),image);
                LinearLayout labels=column();labels.setPadding(dp(12),0,0,0);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView title=text((position==playback.queue.index?"♫  ":(position+1)+".  ")+item.optString("Name"),14,position==playback.queue.index?ACCENT:TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);TextView detail=text(MusicViews.artists(item),12,MUTED);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(detail);return row;
            }};queue.setAdapter(adapter);queue.setOnItemClickListener((parent,view,index,id)->playback.playAt(index));queue.setOnItemLongClickListener((parent,view,index,id)->{queueActions(index);return true;});
        more=button("加载后续歌曲",()->playback.loadMore());right.addView(more);
        status=text("",12,MUTED);status.setMaxLines(2);root.addView(status);setContentView(root);changed();pause.requestFocus();
    }
    @Override public void changed(){
        if(isDestroyed()||name==null)return;updating=true;
        JSONObject item=playback.queue.current();if(item!=shown){shown=item;if(item!=null){name.setText(item.optString("Name"));album.setText("专辑 · "+item.optString("Album","未知专辑"));artist.setText("歌手 · "+MusicViews.artists(item));cover.setImageDrawable(null);Images.load(api,MusicViews.artwork(api,item,600),cover);}else{name.setText("播放列表为空");cover.setImageDrawable(null);}}
        pause.setText(playback.playing()?"暂停":playback.loading()?"加载中":"播放");mode.setText(MusicQueue.MODES[playback.queue.mode]);status.setText(playback.message);time.setText(MusicViews.time(playback.position())+" / "+MusicViews.time(playback.duration()));seek.setEnabled(playback.duration()>0);seek.setProgress(playback.duration()>0?(int)(playback.position()*1000/playback.duration()):0);
        queueTitle.setText("播放列表  "+playback.queue.items.size()+(playback.hasMore()?" / "+playback.queue.total:"")+" 首");more.setVisibility(playback.hasMore()?View.VISIBLE:View.GONE);more.setEnabled(!playback.moreLoading());more.setText(playback.moreLoading()?"正在加载…":"加载后续歌曲");
        if(queueSize!=playback.queue.items.size()||playingIndex!=playback.queue.index){queueSize=playback.queue.items.size();playingIndex=playback.queue.index;adapter.notifyDataSetChanged();if(!queue.hasFocus()&&playingIndex>=0)queue.setSelection(playingIndex);}
        updating=false;
    }
    private void chooseMode(){AlertDialog dialog=dialogBuilder().setTitle("播放模式").setSingleChoiceItems(MusicQueue.MODES,playback.queue.mode,(choice,index)->{choice.dismiss();playback.mode(index);}).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);}
    private void volume(){LinearLayout body=column();body.setPadding(dp(24),dp(12),dp(24),dp(12));TextView value=text("音量 "+Math.round(playback.volume()*100)+"%",16,TEXT);body.addView(value);SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(playback.volume()*100));slider.setKeyProgressIncrement(5);body.addView(slider);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user){playback.volume(progress/100f);value.setText("音量 "+progress+"%");}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});AlertDialog dialog=dialogBuilder().setTitle("播放音量").setView(body).setPositiveButton("关闭",null).setNeutralButton("静音",(choice,index)->playback.volume(0)).create();dialog.show();themeDialog(dialog);slider.requestFocus();}
    private void queueActions(int index){if(index<0||index>=playback.queue.items.size())return;JSONObject item=playback.queue.items.get(index);AlertDialog dialog=dialogBuilder().setTitle(item.optString("Name")).setItems(new String[]{"立即播放","保存到歌单","移出播放列表"},(choice,action)->{if(action==0)playback.playAt(index);else if(action==1)MusicActions.playlist(this,item,()->{});else playback.remove(index);}).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();boolean media=key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_NEXT||key==KeyEvent.KEYCODE_MEDIA_PREVIOUS||key==KeyEvent.KEYCODE_MEDIA_STOP||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND;if(media){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(key==KeyEvent.KEYCODE_MEDIA_NEXT)playback.next(1,false);else if(key==KeyEvent.KEYCODE_MEDIA_PREVIOUS)playback.next(-1,false);else if(key==KeyEvent.KEYCODE_MEDIA_STOP){MusicPlayback.stop();finish();}else if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND)playback.seek(playback.position()+(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?10000:-10000));else if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY&&!playback.playing()||key==KeyEvent.KEYCODE_MEDIA_PAUSE&&playback.playing())playback.toggle();}return true;}if(key==KeyEvent.KEYCODE_MENU&&queue.hasFocus()){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)queueActions(queue.getSelectedItemPosition());return true;}return super.dispatchKeyEvent(event);}
    @Override protected void onResume(){super.onResume();if(playback!=null){playback.addListener(this);changed();}}
    @Override protected void onPause(){if(playback!=null)playback.removeListener(this);super.onPause();}
    @Override void appearanceChanged(){if(adapter!=null)adapter.notifyDataSetChanged();}
}
