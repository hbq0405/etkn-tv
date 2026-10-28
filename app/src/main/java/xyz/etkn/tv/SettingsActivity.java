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

public final class SettingsActivity extends TvActivity {
    private File downloaded;
    private boolean installing;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);screen("设置","ETKN TV  "+BuildConfig.VERSION_NAME);nav("返回",this::finish);
        if(!api.token.isEmpty()){
            ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(avatar,dp(36));content.addView(avatar,new LinearLayout.LayoutParams(dp(72),dp(72)));loadAvatar(avatar);
            content.addView(text(api.session.name(),24,android.graphics.Color.WHITE));content.addView(text(api.server,13,MUTED));
            Switch notice=new Switch(this);notice.setText("我的订阅 / 追更入库通知");notice.setTextSize(17);notice.setTextColor(android.graphics.Color.WHITE);notice.setPadding(dp(12),dp(14),dp(12),dp(14));notice.setFocusable(true);notice.setBackground(focusBackground());notice.setChecked(IngestNotifications.enabled(this));content.addView(notice);
            content.addView(text("只提醒本账号订阅内容的入库与更新。后台检查受电视系统调度影响。",13,MUTED));
            notice.setOnCheckedChangeListener((button,on)->{IngestNotifications.setEnabled(this,on);if(on&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},21);});
        }
        content.addView(button("检查 App 更新",this::checkUpdate));
        content.addView(text("更新通过 GitHub Releases 获取；安装需要系统确认。",13,MUTED));
        if(!api.token.isEmpty())content.addView(button("退出当前账号",()->new AlertDialog.Builder(this).setTitle("退出当前账号？").setPositiveButton("退出",(dialog,index)->{IngestNotifications.logout(this);Images.clear();IO.execute(()->{try{api.post("/emby/Sessions/Logout",new org.json.JSONObject());}catch(Exception ignored){}});api.session.clear();startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}).setNegativeButton("取消",null).show()));
    }
    private void checkUpdate(){status.setText("正在检查更新…");async(Updater::check,release->{if(release.code<=BuildConfig.VERSION_CODE){status.setText("当前已是最新发行版本。");return;}new AlertDialog.Builder(this).setTitle("ETKN TV "+release.version).setMessage(release.notes+"\n\n大小："+(release.size/1024/1024)+" MB").setPositiveButton("下载更新",(dialog,index)->{status.setText("开始下载…");async(()->Updater.download(this,release,percent->runOnUiThread(()->{if(!isDestroyed())status.setText("正在下载更新 "+percent+"%");})),apk->{downloaded=apk;status.setText("签名和文件校验通过，可以安装。");install();});}).setNegativeButton("稍后",null).show();});}
    private void install(){if(downloaded==null)return;if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){installing=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}installing=false;try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",downloaded);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception error){status.setText("此电视未提供安装器，请从下载目录手动安装。");}}
    @Override protected void onResume(){super.onResume();if(installing){installing=false;if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())install();else status.setText("尚未允许安装更新，可授权后重试。");}}
}
