package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import org.json.*;
import java.util.*;

public final class SettingsActivity extends TvActivity {
    private File downloaded;
    private boolean installing;
    private JSONObject playbackPreferences;
    private Button defaultAudio,defaultSubtitle,skipButton;
    private boolean preferenceSaving;
    private AlertDialog preferenceDialog;
    private static final String[] AUDIO_KEYS={"chi","yue","original","eng","jpn","kor"};
    private static final String[] AUDIO_LABELS={"国语","粤语","原语言","英语","日语","韩语"};
    private static final String[] SUBTITLE_KEYS={"effect","chs","cht","chs_eng","cht_eng","chs_jpn","cht_jpn","chs_kor","cht_kor","original","eng","jpn","kor"};
    private static final String[] SUBTITLE_LABELS={"特效字幕","简体中文","繁体中文","简英双语","繁英双语","简日双语","繁日双语","简韩双语","繁韩双语","原语言","英文","日文","韩文"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);screen("设置","ETKN TV  "+BuildConfig.VERSION_NAME);nav("返回",this::finish);
        if(!api.token.isEmpty()){
            ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(avatar,dp(36));content.addView(avatar,new LinearLayout.LayoutParams(dp(72),dp(72)));loadAvatar(avatar);
            content.addView(text(api.session.name(),24,android.graphics.Color.WHITE));content.addView(text(api.server,13,MUTED));
            defaultAudio=button("默认音轨 · 正在读取…",()->editPreference(false));defaultSubtitle=button("默认字幕 · 正在读取…",()->editPreference(true));
            defaultAudio.setEnabled(false);defaultSubtitle.setEnabled(false);for(Button preference:new Button[]{defaultAudio,defaultSubtitle}){LinearLayout.LayoutParams layout=(LinearLayout.LayoutParams)preference.getLayoutParams();layout.width=-1;layout.height=-2;content.addView(preference,layout);}
            content.addView(text("与个人中心同步；个人配置覆盖全局，未配置时跟随全局。按添加顺序确定优先级。",13,MUTED));
            skipButton=button("",this::chooseSkipMode);content.addView(skipButton);showSkipMode();
            content.addView(text("仅对有片头片尾标记的影片生效，保存到此电视当前账号。",13,MUTED));
            Switch notice=new Switch(this);notice.setText("我的订阅 / 追更入库通知");notice.setTextSize(17);notice.setTextColor(android.graphics.Color.WHITE);notice.setPadding(dp(12),dp(14),dp(12),dp(14));notice.setFocusable(true);notice.setBackground(focusBackground());notice.setChecked(IngestNotifications.enabled(this));content.addView(notice);
            content.addView(text("只提醒本账号订阅内容的入库与更新。后台检查受电视系统调度影响。",13,MUTED));
            notice.setOnCheckedChangeListener((button,on)->{IngestNotifications.setEnabled(this,on);if(on&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},21);});
        }
        content.addView(button("检查 App 更新",this::checkUpdate));
        content.addView(text("更新通过 GitHub Releases 获取；安装需要系统确认。",13,MUTED));
        content.addView(button("开源许可",this::showLicenses));
        if(!api.token.isEmpty())content.addView(button("退出当前账号",()->new AlertDialog.Builder(this).setTitle("退出当前账号？").setPositiveButton("退出",(dialog,index)->{IngestNotifications.logout(this);Images.clear();IO.execute(()->{try{api.post("/api/tv/Sessions/Logout",new org.json.JSONObject());}catch(Exception ignored){}});api.session.clear();startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}).setNegativeButton("取消",null).show()));
    }
    private void showSkipMode(){skipButton.setText("片头片尾 · "+TvPreferences.SKIP_LABELS[new TvPreferences(this,api).skipMode()]);}
    private void showLicenses(){
        try{String[] names=getAssets().list("licenses");if(names==null)return;Arrays.sort(names);new AlertDialog.Builder(this).setTitle("开源许可 · GPL-3.0").setItems(names,(choice,index)->{
            try(java.io.InputStream stream=getAssets().open("licenses/"+names[index]);java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)data.write(buffer,0,count);new AlertDialog.Builder(this).setTitle(names[index]).setMessage(data.toString("UTF-8")).setPositiveButton("关闭",null).show();}catch(java.io.IOException error){status.setText("无法读取许可文件");}
        }).setNegativeButton("关闭",null).show();}catch(java.io.IOException error){status.setText("无法读取许可文件");}
    }
    private void chooseSkipMode(){TvPreferences preferences=new TvPreferences(this,api);preferenceDialog=new AlertDialog.Builder(this).setTitle("片头片尾").setSingleChoiceItems(TvPreferences.SKIP_LABELS,preferences.skipMode(),(dialog,index)->{preferences.setSkipMode(index);showSkipMode();dialog.dismiss();}).setNegativeButton("取消",null).create();preferenceDialog.show();}
    private void showPreferences(JSONObject value){
        playbackPreferences=value;defaultAudio.setEnabled(true);defaultSubtitle.setEnabled(true);
        defaultAudio.setText("默认音轨 · "+preferenceSummary(false));defaultSubtitle.setText("默认字幕 · "+preferenceSummary(true));
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
    @Override protected void onResume(){super.onResume();if(defaultAudio!=null)async(()->api.get("/api/tv/Users/Me/PlaybackPreferences"),this::showPreferences);if(installing){installing=false;if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())install();else status.setText("尚未允许安装更新，可授权后重试。");}}
}
