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
    private Button defaultAudio,defaultSubtitle,skipButton,loginModeButton;
    private boolean preferenceSaving,adminRememberSaving;
    private LinearLayout loginSection,adminRememberRow;
    private AlertDialog preferenceDialog;
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
            TextView name=text(api.session.name(),21,Color.WHITE);name.setTypeface(null,Typeface.BOLD);who.addView(name);
            TextView server=text(api.server,13,MUTED);who.addView(server);
            identity.addView(who,new LinearLayout.LayoutParams(0,-2,1));account.addView(identity);
            LinearLayout playback=section("播放偏好");
            defaultAudio=settingRow(Color.WHITE);defaultAudio.setOnClickListener(view->editPreference(false));defaultAudio.setText(rowText("默认音轨","正在读取…"));
            defaultSubtitle=settingRow(Color.WHITE);defaultSubtitle.setOnClickListener(view->editPreference(true));defaultSubtitle.setText(rowText("默认字幕","正在读取…"));
            defaultAudio.setEnabled(false);defaultSubtitle.setEnabled(false);playback.addView(defaultAudio);playback.addView(defaultSubtitle);
            skipButton=settingRow(Color.WHITE);skipButton.setOnClickListener(view->chooseSkipMode());playback.addView(skipButton);showSkipMode();
            playback.addView(note("音轨与字幕和个人中心同步：个人配置覆盖全局，未配置时跟随全局；按添加顺序确定优先级。"));
            playback.addView(note("片头片尾仅对有片头片尾标记的影片生效，保存到此电视当前账号。"));
            LinearLayout notices=section("通知");
            notices.addView(switchRow("我的订阅 / 追更入库通知","只提醒本账号订阅内容的入库与更新。后台检查受电视系统调度影响。",IngestNotifications.enabled(this),on->{IngestNotifications.setEnabled(this,on);if(on&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},21);}));
            TvPreferences preferences=new TvPreferences(this,api);
            LinearLayout home=section("首页");
            home.addView(switchRow("智能推荐轮播","首页背景按你的观影口味自动轮播推荐，不再跟随焦点联动。保存到此电视当前账号。",preferences.smartCarousel(),on->preferences.setSmartCarousel(on)));
        }
        LinearLayout login=section("登录");loginSection=login;
        loginModeButton=settingRow(Color.WHITE);loginModeButton.setOnClickListener(view->chooseLoginMode());login.addView(loginModeButton);showLoginMode();
        login.addView(note("“最后登录用户”启动后直接进入上次用户的首页；“选择登录用户”每次启动或从后台返回时先显示用户选择框，可点选已登录用户或登录新用户。按本设备保存，对全部用户生效。"));
        LinearLayout app=section("应用");
        Button update=settingRow(Color.WHITE);update.setText(rowText("检查更新","当前版本 "+BuildConfig.VERSION_NAME));update.setOnClickListener(view->checkUpdate());app.addView(update);
        Button licenses=settingRow(Color.WHITE);licenses.setText(rowText("开源许可","GPL-3.0 及第三方组件"));licenses.setOnClickListener(view->showLicenses());app.addView(licenses);
        app.addView(note("更新通过 GitHub Releases 获取；安装需要系统确认。"));
        if(!api.token.isEmpty()){
            LinearLayout danger=section(null);
            Button logout=settingRow(Color.rgb(235,124,139));logout.setText("退出当前账号");logout.setOnClickListener(view->new AlertDialog.Builder(this).setTitle("退出当前账号？").setPositiveButton("退出",(dialog,index)->{IngestNotifications.logout(this);Images.clear();IO.execute(()->{try{api.post("/api/tv/Sessions/Logout",new org.json.JSONObject());}catch(Exception ignored){}});api.session.clear();startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}).setNegativeButton("取消",null).show());
            danger.addView(logout);
        }
    }
    /** 设置分区卡片：圆角面板 + 可选 ACCENT 竖条标题。 */
    private LinearLayout section(String title){
        LinearLayout panel=column();panel.setBackground(shape(PANEL,0));panel.setPadding(dp(20),dp(14),dp(20),dp(10));
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
        Button view=new Button(this);view.setAllCaps(false);view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        view.setTextColor(textColor);view.setTextSize(16);view.setBackground(focusBackground());view.setMinHeight(dp(52));
        view.setSingleLine(true);view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(dp(18),dp(6),dp(16),dp(6));view.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,chevron(),null);view.setCompoundDrawablePadding(dp(10));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(52));layout.setMargins(0,0,0,dp(10));view.setLayoutParams(layout);
        return view;
    }
    /** 行文字：主标题白色 + 摘要弱化缩小。 */
    private CharSequence rowText(String title,String summary){
        SpannableStringBuilder builder=new SpannableStringBuilder();builder.append(title);int split=builder.length();builder.append("   ").append(summary);
        builder.setSpan(new ForegroundColorSpan(MUTED),split,builder.length(),0);builder.setSpan(new RelativeSizeSpan(0.8f),split,builder.length(),0);
        return builder;
    }
    /** 整行可聚焦的开关行：左标题副文本、右 Switch，确认键切换。 */
    private LinearLayout switchRow(String title,String summary,boolean checked,java.util.function.Consumer<Boolean> onChange){
        final Switch toggle=new Switch(this);toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button,on)->onChange.accept(on));
        LinearLayout line=row();line.setFocusable(true);line.setClickable(true);line.setBackground(focusBackground());line.setPadding(dp(18),dp(12),dp(20),dp(12));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,0,0,dp(10));line.setLayoutParams(layout);
        LinearLayout label=column();
        TextView head=text(title,16,Color.WHITE);head.setPadding(0,0,0,0);label.addView(head);
        TextView detail=text(summary,12,MUTED);detail.setPadding(0,0,0,dp(10));label.addView(detail);
        line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams toggleLayout=new LinearLayout.LayoutParams(-2,-2);toggleLayout.gravity=Gravity.CENTER_VERTICAL;
        toggle.setFocusable(false);toggle.setClickable(false);line.addView(toggle,toggleLayout);
        line.setOnClickListener(view->toggle.setChecked(!toggle.isChecked()));
        return line;
    }
    private TextView note(String value){TextView view=text(value,12,MUTED);view.setPadding(dp(2),dp(2),dp(2),dp(8));return view;}
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
    private void chooseSkipMode(){TvPreferences preferences=new TvPreferences(this,api);preferenceDialog=new AlertDialog.Builder(this).setTitle("片头片尾").setSingleChoiceItems(TvPreferences.SKIP_LABELS,preferences.skipMode(),(dialog,index)->{preferences.setSkipMode(index);showSkipMode();dialog.dismiss();}).setNegativeButton("取消",null).create();preferenceDialog.show();}
    private void loadAdminRemember(){
        boolean restoreFocus=adminRememberRow!=null&&adminRememberRow.hasFocus();
        if(adminRememberRow!=null){loginSection.removeView(adminRememberRow);adminRememberRow=null;}
        if(api.token.isEmpty())return;
        async(()->api.get("/api/tv/Users/Me"),user->{
            JSONObject policy=user.optJSONObject("Policy");if(!api.session.userId().equals(user.optString("Id"))||policy==null||!policy.optBoolean("IsAdministrator"))return;
            boolean privateAccount=user.optBoolean("PrivateAccount");
            adminRememberRow=switchRow("记住管理员登录",privateAccount?"私密账号不保存登录状态。":"开启后在此电视记住本管理员登录，重新进入免输密码；关闭后下次进入需要密码。",api.session.rememberAdmin(api.server,api.session.userId())&&!privateAccount,this::changeAdminRemember);
            adminRememberRow.setEnabled(!privateAccount&&!adminRememberSaving);loginSection.addView(adminRememberRow);if(restoreFocus)adminRememberRow.requestFocus();
        });
    }
    private void changeAdminRemember(boolean enabled){
        if(adminRememberSaving)return;adminRememberSaving=true;adminRememberRow.setEnabled(false);
        async(()->{try{api.rememberAdministrator(enabled);return enabled;}finally{runOnUiThread(()->{adminRememberSaving=false;if(!isFinishing()&&!isDestroyed())loadAdminRemember();});}},saved->status.setText(saved?"已记住此管理员登录":"已关闭记住登录，下次进入需要密码"));
    }
    private void showLoginMode(){loginModeButton.setText(rowText("登录方式",LoginGate.LABELS[LoginGate.mode(this)]));}
    private void chooseLoginMode(){preferenceDialog=new AlertDialog.Builder(this).setTitle("登录方式").setSingleChoiceItems(LoginGate.LABELS,LoginGate.mode(this),(dialog,index)->{LoginGate.setMode(this,index);showLoginMode();dialog.dismiss();}).setNegativeButton("取消",null).create();preferenceDialog.show();}
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
        if(playbackPreferences==null||preferenceSaving)return;String key=subtitles?"subtitle_priority":"audio_lang_priority";JSONObject personal=playbackPreferences.optJSONObject("preferences");JSONArray current=personal==null?null:personal.optJSONArray(key);List<String> selected=new ArrayList<>();if(current!=null)for(int i=0;i<current.length();i++)selected.add(current.optString(i));
        showPreferenceEditor(subtitles,selected);
    }
    private void showPreferenceEditor(boolean subtitles,List<String> selected){
        LinearLayout body=column();body.setPadding(dp(20),dp(6),dp(20),dp(6));TextView order=text("",14,MUTED);body.addView(order);Button add=button("添加优先项",()->{});body.addView(add);
        LinearLayout entries=column();body.addView(entries);Runnable[] render={null};
        render[0]=()->{order.setText(selected.isEmpty()?"当前跟随全局配置":"按下面顺序依次匹配");entries.removeAllViews();for(int i=0;i<selected.size();i++){final int index=i;LinearLayout line=row();line.addView(text((i+1)+". "+preferenceName(selected.get(i),subtitles),15,android.graphics.Color.WHITE));if(i>0)line.addView(button("上移",()->{Collections.swap(selected,index,index-1);render[0].run();}));line.addView(button("移除",()->{selected.remove(index);render[0].run();}));entries.addView(line);}};
        add.setOnClickListener(view->{String[] keys=subtitles?SUBTITLE_KEYS:AUDIO_KEYS;List<String> available=new ArrayList<>(),labels=new ArrayList<>();for(String value:keys)if(!selected.contains(value)){available.add(value);labels.add(preferenceName(value,subtitles));}if(available.isEmpty())return;new AlertDialog.Builder(this).setTitle("添加优先项").setItems(labels.toArray(new String[0]),(dialog,index)->{selected.add(available.get(index));render[0].run();}).setNegativeButton("取消",null).show();});render[0].run();
        ScrollView scroll=new ScrollView(this);scroll.addView(body);preferenceDialog=new AlertDialog.Builder(this).setTitle(subtitles?"默认字幕优先级":"默认音轨优先级").setView(scroll).setPositiveButton("保存",(dialog,index)->savePreference(subtitles,selected)).setNeutralButton("跟随全局",(dialog,index)->savePreference(subtitles,Collections.emptyList())).setNegativeButton("取消",null).create();preferenceDialog.show();
    }
    private void savePreference(boolean subtitles,List<String> selected){
        if(preferenceSaving)return;preferenceSaving=true;status.setText("正在同步个人播放偏好…");
        async(()->{try{JSONObject body=new JSONObject().put(subtitles?"subtitle_priority":"audio_lang_priority",selected.isEmpty()?JSONObject.NULL:new JSONArray(selected));return api.post("/api/tv/Users/Me/PlaybackPreferences",body);}finally{runOnUiThread(()->preferenceSaving=false);}},value->{showPreferences(value);status.setText("已同步个人中心，下次播放使用新的默认轨道。");});
    }
    private void checkUpdate(){status.setText("正在检查更新…");async(Updater::check,release->{if(release.code<=BuildConfig.VERSION_CODE){status.setText("当前已是最新发行版本。");return;}new AlertDialog.Builder(this).setTitle("ETKN TV "+release.version).setMessage(release.notes+"\n\n大小："+(release.size/1024/1024)+" MB").setPositiveButton("下载更新",(dialog,index)->{status.setText("开始下载…");async(()->Updater.download(this,release,percent->runOnUiThread(()->{if(!isDestroyed())status.setText("正在下载更新 "+percent+"%");})),apk->{downloaded=apk;status.setText("签名和文件校验通过，可以安装。");install();});}).setNegativeButton("稍后",null).show();});}
    private void install(){if(downloaded==null)return;if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){installing=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}installing=false;try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",downloaded);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception error){status.setText("此电视未提供安装器，请从下载目录手动安装。");}}
    @Override protected void onResume(){super.onResume();loadAdminRemember();if(defaultAudio!=null)async(()->api.get("/api/tv/Users/Me/PlaybackPreferences"),this::showPreferences);if(installing){installing=false;if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())install();else status.setText("尚未允许安装更新，可授权后重试。");}}
}
