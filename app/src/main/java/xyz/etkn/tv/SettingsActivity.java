package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import org.json.*;
import java.util.*;

public final class SettingsActivity extends TvActivity {
    private File downloaded;
    private boolean installing;
    private JSONObject playbackPreferences;
    private Button defaultAudio,defaultSubtitle,skipButton,loginModeButton,imageSizeButton;
    private boolean preferenceSaving,adminRememberSaving;
    private LinearLayout loginSection,adminRememberRow;
    private LinearLayout themeLockRow;private boolean themeLockSaving;
    private AlertDialog preferenceDialog;
    private final List<View> sharedControls=new ArrayList<>();
    private final Map<String,Button> appearanceRows=new LinkedHashMap<>();
    private final Map<String,LinearLayout> preferenceSwitches=new LinkedHashMap<>();
    private boolean renderingPreferences;
    private static final String[] AUDIO_KEYS={"chi","yue","original","eng","jpn","kor"};
    private static final String[] AUDIO_LABELS={"国语","粤语","原语言","英语","日语","韩语"};
    private static final String[] SUBTITLE_KEYS={"effect","chs","cht","chs_eng","cht_eng","chs_jpn","cht_jpn","chs_kor","cht_kor","original","eng","jpn","kor"};
    private static final String[] SUBTITLE_LABELS={"特效字幕","简体中文","繁体中文","简英双语","繁英双语","简日双语","繁日双语","简韩双语","繁韩双语","原语言","英文","日文","韩文"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);screen("设置","ETKN TV  "+BuildConfig.VERSION_NAME);nav("返回",this::finish);
        if(!api.token.isEmpty()){
            LinearLayout account=section("账号");
            LinearLayout identity=row();identity.setPadding(dp(2),dp(4),dp(2),dp(6));
            ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(avatar,dp(36));identity.addView(avatar,new LinearLayout.LayoutParams(dp(72),dp(72)));loadAvatar(avatar);
            LinearLayout who=column();who.setPadding(dp(18),dp(8),0,0);
            TextView name=text(api.session.name(),21,TEXT);name.setTypeface(null,Typeface.BOLD);who.addView(name);
            TextView server=text(api.server,13,MUTED);who.addView(server);
            identity.addView(who,new LinearLayout.LayoutParams(0,-2,1));account.addView(identity);
            LinearLayout playback=section("偏好设置 · 播放");
            defaultAudio=settingRow(TEXT);defaultAudio.setOnClickListener(view->editPreference(false));defaultAudio.setText(rowText("默认音轨","正在读取…"));
            defaultSubtitle=settingRow(TEXT);defaultSubtitle.setOnClickListener(view->editPreference(true));defaultSubtitle.setText(rowText("默认字幕","正在读取…"));
            defaultAudio.setEnabled(false);defaultSubtitle.setEnabled(false);playback.addView(defaultAudio);playback.addView(defaultSubtitle);sharedControls.add(defaultAudio);sharedControls.add(defaultSubtitle);
            skipButton=settingRow(TEXT);skipButton.setOnClickListener(view->chooseSkipMode());playback.addView(skipButton);sharedControls.add(skipButton);showSkipMode();
            playback.addView(note("音轨与字幕和个人中心同步：个人配置覆盖全局，未配置时跟随全局；按添加顺序确定优先级。"));
            playback.addView(note("共享偏好按账号保存，与 Web 双向同步；锁定偏好后全部暂停同步并禁止修改。片头片尾只对有标记的影片生效。"));
            TvPreferences preferences=new TvPreferences(this,api);
            LinearLayout display=section("偏好设置 · 界面外观");
            appearanceRow(display,"mode","显示模式",()->chooseValue("显示模式",new String[]{"明亮","暗黑"},TvAppearance.dark()?1:0,value->saveClient("mode",value==0?"light":"dark")));
            appearanceRow(display,"theme_color","主题色",()->chooseColor("theme_color","主题色",TvAppearance.primary()));
            appearanceRow(display,"shadow_color","阴影颜色",()->chooseColor("shadow_color","阴影颜色",TvAppearance.shadowColor(TvAppearance.dark())));
            appearanceRow(display,"glass_opacity","玻璃透明度",()->chooseNumber("glass_opacity","玻璃透明度",0,100,Math.round((float)TvAppearance.current.optDouble("glass_opacity",.76)*100),true));
            appearanceRow(display,"shadow_strength","阴影强度",()->chooseNumber("shadow_strength","阴影强度",0,100,Math.round(TvAppearance.shadow()*100),true));
            appearanceRow(display,"background_blur","背景模糊",()->chooseNumber("background_blur","背景模糊",0,30,TvAppearance.current.optInt("background_blur",10),false));
            imageSizeButton=settingRow(TEXT);imageSizeButton.setOnClickListener(view->chooseImageSize(preferences));display.addView(imageSizeButton);sharedControls.add(imageSizeButton);showImageSize(preferences);
            display.addView(note("背景图可在 Web 个人中心上传，同一账号会同步到电视；TV 不提供上传入口。"));
            themeLockRow=switchRow("锁定偏好","保存当前全部共享偏好及背景图副本；锁定时暂停同步并禁止修改，解锁后恢复当前账号的最新设置。",TvAppearance.locked(this,api.server),this::changeThemeLock);display.addView(themeLockRow);
            LinearLayout notices=section("通知");
            notices.addView(switchRow("我的订阅 / 追更入库通知","提醒本账号订阅内容，以及收藏或观看过的剧集更新。后台检查受电视系统调度影响。",IngestNotifications.enabled(this),on->{IngestNotifications.setEnabled(this,on);if(on&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},21);}));
            LinearLayout home=section("偏好设置 · 首页");
            sharedSwitch(home,"home_carousel","智能推荐轮播","首页背景按你的观影口味轮播推荐。",preferences.smartCarousel());
            sharedSwitch(home,"show_home_libraries","首页显示媒体库","控制首页“我的媒体库”分区，顶部媒体库入口始终可用。",preferences.showHomeLibraries());
            sharedSwitch(home,"hide_watched_latest","最新入库隐藏已观看","与 Web 播放偏好同步。",false);
            renderSharedPreferences();
        }
        LinearLayout login=section("登录");loginSection=login;
        loginModeButton=settingRow(TEXT);loginModeButton.setOnClickListener(view->chooseLoginMode());login.addView(loginModeButton);showLoginMode();
        login.addView(note("“最后登录用户”启动后直接进入上次用户的首页；“选择登录用户”每次启动或从后台返回时先显示用户选择框，可点选已登录用户或登录新用户。按本设备保存，对全部用户生效。"));
        LinearLayout app=section("应用");
        Button update=settingRow(TEXT);update.setText(rowText("检查更新","当前版本 "+BuildConfig.VERSION_NAME));update.setOnClickListener(view->checkUpdate());app.addView(update);
        Button licenses=settingRow(TEXT);licenses.setText(rowText("开源许可","GPL-3.0 及第三方组件"));licenses.setOnClickListener(view->showLicenses());app.addView(licenses);
        app.addView(note("更新通过 GitHub Releases 获取；安装需要系统确认。"));
        if(!api.token.isEmpty()){
            LinearLayout danger=section(null);
            Button logout=settingRow(Color.rgb(235,124,139));logout.setText("退出当前账号");logout.setOnClickListener(view->new AlertDialog.Builder(this).setTitle("退出当前账号？").setPositiveButton("退出",(dialog,index)->{IngestNotifications.logout(this);Images.clear();IO.execute(()->{try{api.post("/api/tv/Sessions/Logout",new org.json.JSONObject());}catch(Exception ignored){}});api.session.clear();startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}).setNegativeButton("取消",null).show());
            danger.addView(logout);
        }
    }
    /** 设置分区卡片：圆角面板 + 可选 ACCENT 竖条标题。 */
    private LinearLayout section(String title){
        LinearLayout panel=column();panel.setBackground(shape(PANEL,0));shadow(panel,8);panel.setPadding(dp(20),dp(14),dp(20),dp(10));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(10),0,0);content.addView(panel,layout);
        if(title!=null){
            LinearLayout head=row();head.setPadding(dp(2),dp(4),dp(2),dp(8));
            View bar=new View(this);bar.setBackground(shape(ACCENT,0));head.addView(bar,new LinearLayout.LayoutParams(dp(3),dp(14)));
            TextView label=text(title,13,ACCENT);label.setTypeface(null,Typeface.BOLD);label.setPadding(dp(9),0,0,0);head.addView(label,new LinearLayout.LayoutParams(-2,-2));
            panel.addView(head);
        }
        return panel;
    }
    /** 全宽设置行：左侧主标题 + 弱化摘要，右侧箭头，遥控器整行聚焦。 */
    private Button settingRow(int textColor){
        Button view=new Button(this);view.setStateListAnimator(null);shadow(view,3);view.setAllCaps(false);view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        themeText(view,textColor);view.setTextSize(16);view.setBackground(focusBackground());view.setMinHeight(dp(52));
        view.setSingleLine(true);view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(dp(18),dp(6),dp(16),dp(6));view.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,chevron(),null);view.setCompoundDrawablePadding(dp(10));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(52));layout.setMargins(0,0,0,dp(10));view.setLayoutParams(layout);
        return view;
    }
    /** 行文字：主标题白色 + 摘要弱化缩小。 */
    private CharSequence rowText(String title,String summary){
        SpannableStringBuilder builder=new SpannableStringBuilder();builder.append(title);int split=builder.length();builder.append("   ").append(summary);
        builder.setSpan(new android.text.style.CharacterStyle(){@Override public void updateDrawState(android.text.TextPaint paint){paint.setColor(TvAppearance.muted());}},split,builder.length(),0);builder.setSpan(new RelativeSizeSpan(0.8f),split,builder.length(),0);
        return builder;
    }
    /** 整行可聚焦的开关行：左标题副文本、右 Switch，确认键切换。 */
    private LinearLayout switchRow(String title,String summary,boolean checked,java.util.function.Consumer<Boolean> onChange){
        final Switch toggle=new Switch(this);toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button,on)->onChange.accept(on));
        LinearLayout line=row();line.setFocusable(true);line.setClickable(true);line.setBackground(focusBackground());line.setPadding(dp(18),dp(12),dp(20),dp(12));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,0,0,dp(10));line.setLayoutParams(layout);
        LinearLayout label=column();
        TextView head=text(title,16,TEXT);head.setPadding(0,0,0,0);label.addView(head);
        TextView detail=text(summary,12,MUTED);detail.setPadding(0,0,0,dp(10));label.addView(detail);
        line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams toggleLayout=new LinearLayout.LayoutParams(-2,-2);toggleLayout.gravity=Gravity.CENTER_VERTICAL;
        toggle.setFocusable(false);toggle.setClickable(false);line.addView(toggle,toggleLayout);
        line.setOnClickListener(view->toggle.setChecked(!toggle.isChecked()));
        return line;
    }
    private TextView note(String value){TextView view=text(value,12,MUTED);view.setPadding(dp(2),dp(2),dp(2),dp(8));return view;}
    private boolean editable(){return !TvAppearance.locked(this,api.server)&&!TvAppearance.saving&&!themeLockSaving&&TvAppearance.current.has("card_size");}
    private void appearanceRow(LinearLayout section,String key,String title,Runnable action){Button row=settingRow(TEXT);row.setTag(title);row.setOnClickListener(view->{if(editable())action.run();});section.addView(row);appearanceRows.put(key,row);sharedControls.add(row);}
    private void sharedSwitch(LinearLayout section,String key,String title,String summary,boolean checked){LinearLayout line=switchRow(title,summary,checked,on->{if(!renderingPreferences&&editable()){if(key.equals("hide_watched_latest"))saveHideWatched(on);else saveClient(key,on);}});section.addView(line);preferenceSwitches.put(key,line);sharedControls.add(line);}
    @Override void appearanceChanged(){if(defaultAudio!=null)renderSharedPreferences();}
    private void renderSharedPreferences(){
        renderingPreferences=true;
        try{
            JSONObject value=TvAppearance.current,playback=value.optJSONObject("playback_preferences");
            if(playback!=null)showPreferences(playback);
            for(Map.Entry<String,Button> entry:appearanceRows.entrySet()){
                String key=entry.getKey(),summary=value.optString(key);if(key.equals("mode"))summary=TvAppearance.dark()?"暗黑":"明亮";
                else if(key.equals("glass_opacity")||key.equals("shadow_strength"))summary=Math.round(value.optDouble(key)*100)+"%";
                else if(key.equals("background_blur"))summary=value.optInt(key,10)+"";
                else if(summary.isEmpty())summary="跟随默认";
                entry.getValue().setText(rowText((String)entry.getValue().getTag(),summary));
            }
            TvPreferences preferences=new TvPreferences(this,api);showSkipMode();showImageSize(preferences);
            for(Map.Entry<String,LinearLayout> entry:preferenceSwitches.entrySet())((Switch)entry.getValue().getChildAt(1)).setChecked(entry.getKey().equals("hide_watched_latest")?playback!=null&&playback.optBoolean("hide_watched_latest"):value.optBoolean(entry.getKey(),true));
            for(View control:sharedControls)control.setEnabled(editable()&&!preferenceSaving);
            if(themeLockRow!=null)themeLockRow.setEnabled(!TvAppearance.saving&&!themeLockSaving&&(value.has("card_size")||TvAppearance.locked(this,api.server)));
        }finally{renderingPreferences=false;}
    }
    private void saveClient(String key,Object value){
        if(!editable())return;TvAppearance.saving=true;TvAppearance.generation++;renderSharedPreferences();final String user=api.session.userId();
        async(()->{try{if(!TvAppearance.sameSession(api,user)||TvAppearance.locked(this,api.server))throw new Exception("偏好已锁定或账号已切换");return TvAppearance.update(api,key,value);}
            finally{runOnUiThread(()->{TvAppearance.saving=false;if(!isDestroyed())renderSharedPreferences();});}},result->{if(TvAppearance.sameSession(api,user)&&!TvAppearance.locked(this,api.server)){TvAppearance.save(this,api.server,result);reloadAppearance();status.setText("已同步当前账号偏好");}});
    }
    private void saveHideWatched(boolean value){savePlayback("hide_watched_latest",value);}
    private void chooseValue(String title,String[] labels,int selected,java.util.function.IntConsumer save){if(!editable())return;preferenceDialog=dialogBuilder().setTitle(title).setSingleChoiceItems(labels,selected,(dialog,index)->{dialog.dismiss();if(editable())save.accept(index);}).setNegativeButton("取消",null).create();preferenceDialog.show();themeDialog(preferenceDialog);}
    private void chooseNumber(String key,String title,int min,int max,int current,boolean percent){
        LinearLayout body=column();body.setPadding(dp(24),dp(12),dp(24),dp(12));TextView label=text("",18,TEXT);SeekBar slider=new SeekBar(this);slider.setMax(max-min);slider.setProgress(current-min);slider.setKeyProgressIncrement(1);body.addView(label);body.addView(slider);Runnable show=()->label.setText((slider.getProgress()+min)+(percent?"%":""));show.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){show.run();}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        preferenceDialog=dialogBuilder().setTitle(title).setView(body).setPositiveButton("保存",(dialog,index)->saveClient(key,percent?(Object)((slider.getProgress()+min)/100d):(Object)(slider.getProgress()+min))).setNegativeButton("取消",null).create();preferenceDialog.show();themeDialog(preferenceDialog);slider.requestFocus();
    }
    private void chooseColor(String key,String title,int current){
        LinearLayout body=column();body.setPadding(dp(24),dp(12),dp(24),dp(12));TextView preview=text("",20,TEXT);preview.setGravity(Gravity.CENTER);body.addView(preview);int[] channels={Color.red(current),Color.green(current),Color.blue(current)};SeekBar[] sliders=new SeekBar[3];
        Runnable show=()->{int color=Color.rgb(channels[0],channels[1],channels[2]);preview.setText(String.format(java.util.Locale.ROOT,"#%06x",color&0xffffff));preview.setBackgroundColor(color);int ink=(channels[0]*299+channels[1]*587+channels[2]*114)>140000?Color.BLACK:Color.WHITE;preview.setTextColor(ink);preview.setTag(R.id.appearance_text,ink);};
        LinearLayout palette=row();for(int color:new int[]{0xff087f70,0xff55c7b0,0xff3b82f6,0xff8b5cf6,0xffef476f,0xffffb347,0xffffffff,0xff000000}){Button swatch=button("●",()->{channels[0]=Color.red(color);channels[1]=Color.green(color);channels[2]=Color.blue(color);for(int i=0;i<3;i++)sliders[i].setProgress(channels[i]);show.run();});swatch.setTextColor(color);swatch.setTag(R.id.appearance_text,color);swatch.setContentDescription(String.format(java.util.Locale.ROOT,"#%06x",color&0xffffff));palette.addView(swatch,new LinearLayout.LayoutParams(0,dp(44),1));}body.addView(palette);
        for(int i=0;i<3;i++){final int channel=i;body.addView(text(new String[]{"红","绿","蓝"}[i],13,MUTED));SeekBar slider=new SeekBar(this);sliders[i]=slider;slider.setMax(255);slider.setProgress(channels[i]);slider.setKeyProgressIncrement(5);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){channels[channel]=value;show.run();}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});body.addView(slider);}show.run();
        preferenceDialog=dialogBuilder().setTitle(title).setView(body).setPositiveButton("保存",(dialog,index)->saveClient(key,String.format(java.util.Locale.ROOT,"#%02x%02x%02x",channels[0],channels[1],channels[2]))).setNeutralButton("默认颜色",(dialog,index)->saveClient(key,"")).setNegativeButton("取消",null).create();preferenceDialog.show();themeDialog(preferenceDialog);sliders[0].requestFocus();
    }
    private void changeThemeLock(boolean enabled){
        if(themeLockSaving||TvAppearance.saving||enabled&&!TvAppearance.current.has("card_size"))return;themeLockSaving=true;TvAppearance.saving=true;TvAppearance.generation++;themeLockRow.setEnabled(false);renderSharedPreferences();if(preferenceDialog!=null)preferenceDialog.dismiss();
        final String user=api.session.userId();final JSONObject snapshot;try{snapshot=new JSONObject(TvAppearance.current.toString());}catch(JSONException impossible){throw new IllegalStateException(impossible);}
        async(()->{try{if(enabled)TvAppearance.lock(this,api,user,snapshot);else TvAppearance.unlock(this,api,user);return enabled;}
            finally{runOnUiThread(()->{if(!isDestroyed()){((Switch)themeLockRow.getChildAt(1)).setChecked(TvAppearance.locked(this,api.server));themeLockRow.setEnabled(true);}themeLockSaving=false;TvAppearance.saving=false;if(!isDestroyed())renderSharedPreferences();});}},
            saved->{if(TvAppearance.sameSession(api,user)){reloadAppearance();if(!saved)syncAppearance();status.setText(saved?"已锁定当前账号全部共享偏好":"已恢复当前账号偏好同步");}});
    }
    private Drawable chevron(){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(dp(12),dp(18),android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(MUTED);
        Path path=new Path();path.moveTo(dp(4),dp(4));path.lineTo(dp(9),dp(9));path.lineTo(dp(4),dp(14));canvas.drawPath(path,paint);
        return new BitmapDrawable(getResources(),bitmap);
    }
    private void showSkipMode(){skipButton.setText(rowText("片头片尾",TvPreferences.SKIP_LABELS[new TvPreferences(this,api).skipMode()]));}
    private void showLicenses(){
        try{String[] names=getAssets().list("licenses");if(names==null)return;Arrays.sort(names);new AlertDialog.Builder(this).setTitle("开源许可 · GPL-3.0").setItems(names,(choice,index)->{
            try(java.io.InputStream stream=getAssets().open("licenses/"+names[index]);java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)data.write(buffer,0,count);new AlertDialog.Builder(this).setTitle(names[index]).setMessage(data.toString("UTF-8")).setPositiveButton("关闭",null).show();}catch(java.io.IOException error){status.setText("无法读取许可文件");}
        }).setNegativeButton("关闭",null).show();}catch(java.io.IOException error){status.setText("无法读取许可文件");}
    }
    private void chooseSkipMode(){chooseValue("片头片尾",TvPreferences.SKIP_LABELS,new TvPreferences(this,api).skipMode(),index->saveClient("skip_mode",index));}
    private void loadAdminRemember(){
        boolean restoreFocus=adminRememberRow!=null&&adminRememberRow.hasFocus();
        if(adminRememberRow!=null){loginSection.removeView(adminRememberRow);adminRememberRow=null;}
        if(api.token.isEmpty())return;
        async(()->api.get("/api/tv/Users/Me"),user->{
            JSONObject policy=user.optJSONObject("Policy");if(!api.session.userId().equals(user.optString("Id"))||policy==null||!policy.optBoolean("IsAdministrator"))return;
            boolean privateAccount=user.optBoolean("PrivateAccount");
            adminRememberRow=switchRow("保存管理员账号",privateAccount?"私密账号不保存登录状态。":"与 Web 按账号同步，不受锁定偏好限制；关闭只取消保存，当前登录继续有效。",api.session.rememberAdmin(api.server,api.session.userId())&&!privateAccount,this::changeAdminRemember);
            adminRememberRow.setEnabled(!privateAccount&&!adminRememberSaving);loginSection.addView(adminRememberRow);if(restoreFocus)adminRememberRow.requestFocus();
        });
    }
    private void changeAdminRemember(boolean enabled){
        if(adminRememberSaving)return;adminRememberSaving=true;EtknApi.accountPreferenceGeneration++;adminRememberRow.setEnabled(false);
        async(()->{try{api.rememberAdministrator(enabled);return enabled;}finally{runOnUiThread(()->{adminRememberSaving=false;if(!isFinishing()&&!isDestroyed())loadAdminRemember();});}},saved->status.setText(saved?"已保存当前管理员账号":"已关闭记住登录，下次进入需要密码"));
    }
    @Override void accountPreferencesChanged(){loadAdminRemember();}
    private void showLoginMode(){loginModeButton.setText(rowText("登录方式",LoginGate.LABELS[LoginGate.mode(this)]));}
    private void chooseLoginMode(){preferenceDialog=new AlertDialog.Builder(this).setTitle("登录方式").setSingleChoiceItems(LoginGate.LABELS,LoginGate.mode(this),(dialog,index)->{LoginGate.setMode(this,index);showLoginMode();dialog.dismiss();}).setNegativeButton("取消",null).create();preferenceDialog.show();}
    private void showImageSize(TvPreferences preferences){imageSizeButton.setText(rowText("图片大小",TvPreferences.IMAGE_SIZE_LABELS[preferences.imageSize()]));}
    private void chooseImageSize(TvPreferences preferences){chooseValue("图片大小",TvPreferences.IMAGE_SIZE_LABELS,preferences.imageSize(),index->saveClient("card_size",index));}
    private void showPreferences(JSONObject value){
        playbackPreferences=value;defaultAudio.setEnabled(true);defaultSubtitle.setEnabled(true);
        defaultAudio.setText(rowText("默认音轨",preferenceSummary(false)));defaultSubtitle.setText(rowText("默认字幕",preferenceSummary(true)));
    }
    private String preferenceSummary(boolean subtitles){
        String key=subtitles?"subtitle_priority":"audio_lang_priority";JSONObject personal=playbackPreferences.optJSONObject("preferences"),global=playbackPreferences.optJSONObject("global_preferences");JSONArray values=personal==null?null:personal.optJSONArray(key);boolean own=values!=null&&values.length()>0;
        if(!own)values=global==null?null:global.optJSONArray(key);List<String> names=new ArrayList<>();if(values!=null)for(int i=0;i<values.length();i++)names.add(preferenceName(values.optString(i),subtitles));
        return (names.isEmpty()?"未设置":android.text.TextUtils.join(" → ",names))+(own?"（个人）":"（全局）");
    }
    private String preferenceName(String value,boolean subtitles){String[] keys=subtitles?SUBTITLE_KEYS:AUDIO_KEYS,labels=subtitles?SUBTITLE_LABELS:AUDIO_LABELS;for(int i=0;i<keys.length;i++)if(keys[i].equals(value))return labels[i];return value;}
    private void editPreference(boolean subtitles){
        if(playbackPreferences==null||preferenceSaving||!editable())return;String key=subtitles?"subtitle_priority":"audio_lang_priority";JSONObject personal=playbackPreferences.optJSONObject("preferences");JSONArray current=personal==null?null:personal.optJSONArray(key);List<String> selected=new ArrayList<>();if(current!=null)for(int i=0;i<current.length();i++)selected.add(current.optString(i));
        showPreferenceEditor(subtitles,selected);
    }
    private void showPreferenceEditor(boolean subtitles,List<String> selected){
        LinearLayout body=column();body.setPadding(dp(20),dp(6),dp(20),dp(6));TextView order=text("",14,MUTED);body.addView(order);Button add=button("添加优先项",()->{});body.addView(add);
        LinearLayout entries=column();body.addView(entries);Runnable[] render={null};
        render[0]=()->{order.setText(selected.isEmpty()?"当前跟随全局配置":"按下面顺序依次匹配");entries.removeAllViews();for(int i=0;i<selected.size();i++){final int index=i;LinearLayout line=row();line.addView(text((i+1)+". "+preferenceName(selected.get(i),subtitles),15,TEXT));if(i>0)line.addView(button("上移",()->{Collections.swap(selected,index,index-1);render[0].run();}));line.addView(button("移除",()->{selected.remove(index);render[0].run();}));entries.addView(line);}};
        add.setOnClickListener(view->{String[] keys=subtitles?SUBTITLE_KEYS:AUDIO_KEYS;List<String> available=new ArrayList<>(),labels=new ArrayList<>();for(String value:keys)if(!selected.contains(value)){available.add(value);labels.add(preferenceName(value,subtitles));}if(available.isEmpty())return;new AlertDialog.Builder(this).setTitle("添加优先项").setItems(labels.toArray(new String[0]),(dialog,index)->{selected.add(available.get(index));render[0].run();}).setNegativeButton("取消",null).show();});render[0].run();
        ScrollView scroll=new ScrollView(this);scroll.addView(body);preferenceDialog=new AlertDialog.Builder(this).setTitle(subtitles?"默认字幕优先级":"默认音轨优先级").setView(scroll).setPositiveButton("保存",(dialog,index)->savePreference(subtitles,selected)).setNeutralButton("跟随全局",(dialog,index)->savePreference(subtitles,Collections.emptyList())).setNegativeButton("取消",null).create();preferenceDialog.show();
    }
    private void savePreference(boolean subtitles,List<String> selected){
        savePlayback(subtitles?"subtitle_priority":"audio_lang_priority",selected.isEmpty()?JSONObject.NULL:new JSONArray(selected));
    }
    private void savePlayback(String key,Object value){
        if(preferenceSaving||!editable())return;preferenceSaving=true;TvAppearance.saving=true;TvAppearance.generation++;renderSharedPreferences();final String user=api.session.userId();
        async(()->{try{if(!TvAppearance.sameSession(api,user)||TvAppearance.locked(this,api.server))throw new Exception("偏好已锁定或账号已切换");api.post("/api/tv/Users/Me/PlaybackPreferences",new JSONObject().put(key,value));return api.get("/api/tv/Users/Me/ClientPreferences");}finally{runOnUiThread(()->{preferenceSaving=false;TvAppearance.saving=false;if(!isDestroyed())renderSharedPreferences();});}},result->{if(TvAppearance.sameSession(api,user)&&!TvAppearance.locked(this,api.server)){TvAppearance.save(this,api.server,result);reloadAppearance();status.setText("已同步个人中心，下次播放使用新的默认轨道。");}});
    }
    private void checkUpdate(){status.setText("正在检查更新…");async(Updater::check,release->{if(release.code<=BuildConfig.VERSION_CODE){status.setText("当前已是最新发行版本。");return;}new AlertDialog.Builder(this).setTitle("ETKN TV "+release.version).setMessage(release.notes+"\n\n大小："+(release.size/1024/1024)+" MB").setPositiveButton("下载更新",(dialog,index)->{status.setText("开始下载…");async(()->Updater.download(this,release,percent->runOnUiThread(()->{if(!isDestroyed())status.setText("正在下载更新 "+percent+"%");})),apk->{downloaded=apk;status.setText("签名和文件校验通过，可以安装。");install();});}).setNegativeButton("稍后",null).show();});}
    private void install(){if(downloaded==null)return;if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){installing=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}installing=false;try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",downloaded);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception error){status.setText("此电视未提供安装器，请从下载目录手动安装。");}}
    @Override protected void onResume(){super.onResume();loadAdminRemember();if(defaultAudio!=null)renderSharedPreferences();if(installing){installing=false;if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())install();else status.setText("尚未允许安装更新，可授权后重试。");}}
}
