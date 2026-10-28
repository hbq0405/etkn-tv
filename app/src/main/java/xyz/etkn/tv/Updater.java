package xyz.etkn.tv;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

final class Updater {
    static final class Release {
        final long code,size;final String version,notes,url,sha256;
        Release(JSONObject json)throws Exception {
            code=json.getLong("versionCode");size=json.getLong("size");version=json.getString("versionName");notes=json.optString("releaseNotes");url=json.getString("apkUrl");sha256=json.getString("sha256").toLowerCase(Locale.ROOT);
            URI uri=new URI(url);
            if(!json.optString("packageName").equals(BuildConfig.APPLICATION_ID)||code<1||size<100000||size>200*1024*1024||!sha256.matches("[0-9a-f]{64}")||!"https".equals(uri.getScheme())||!"github.com".equals(uri.getHost())||!uri.getPath().startsWith("/hbq0405/etkn-tv/releases/download/")||!uri.getPath().endsWith(".apk")||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new Exception("更新信息无效");
        }
    }
    static HttpURLConnection connect(String address)throws Exception {
        URL url=new URL(address);
        for(int i=0;i<6;i++){
            if(!url.getProtocol().equals("https"))throw new Exception("更新地址必须使用 HTTPS");
            HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(15000);conn.setReadTimeout(30000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("User-Agent",EtknApi.USER_AGENT);
            int code=conn.getResponseCode();
            if(code>=300&&code<400){String location=conn.getHeaderField("Location");conn.disconnect();if(location==null)throw new Exception("下载地址无效");url=new URL(url,location);continue;}
            if(code==404){conn.disconnect();throw new Exception("发行仓库尚未提供正式版本");}
            if(code!=200){conn.disconnect();throw new Exception("更新服务暂不可用（HTTP "+code+"）");}return conn;
        }throw new Exception("更新服务跳转过多");
    }
    static Release check()throws Exception {
        HttpURLConnection conn=connect(BuildConfig.UPDATE_MANIFEST_URL);
        try{return new Release(new JSONObject(EtknApi.readText(conn.getInputStream())));}finally{conn.disconnect();}
    }
    static synchronized File download(Context context,Release release,java.util.function.Consumer<Integer> progress)throws Exception {
        File directory=context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);if(directory==null)throw new Exception("下载目录不可用");
        File partial=new File(directory,"etkn-tv-update.part"),apk=new File(directory,"etkn-tv-update.apk");
        HttpURLConnection conn=connect(release.url);MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try{
            try(InputStream input=conn.getInputStream();OutputStream output=new FileOutputStream(partial)){
                byte[] buffer=new byte[32768];long size=0;int length,last=-1;
                while((length=input.read(buffer))!=-1){size+=length;if(size>release.size)throw new Exception("更新包大小不符");hash.update(buffer,0,length);output.write(buffer,0,length);int percent=(int)(size*100/release.size);if(percent!=last){last=percent;progress.accept(percent);}}
                if(size!=release.size||!hex(hash.digest()).equals(release.sha256))throw new Exception("更新包校验失败，请重新下载");
            }
            validatePackage(context,partial,release);
            if(apk.exists()&&!apk.delete())throw new Exception("无法替换旧更新包");
            if(!partial.renameTo(apk))throw new Exception("无法保存更新包");return apk;
        }finally{conn.disconnect();if(partial.exists())partial.delete();}
    }
    @SuppressWarnings("deprecation") static void validatePackage(Context context,File apk,Release release)throws Exception {
        PackageManager manager=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo installed=manager.getPackageInfo(context.getPackageName(),flags),candidate=manager.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if(candidate==null||!candidate.packageName.equals(context.getPackageName())||version(candidate)!=release.code||version(candidate)<=version(installed))throw new Exception("更新包版本或应用标识不符");
        android.content.pm.Signature[] current=Build.VERSION.SDK_INT>=28?(installed.signingInfo==null?null:installed.signingInfo.getApkContentsSigners()):installed.signatures;
        android.content.pm.Signature[] next=Build.VERSION.SDK_INT>=28?(candidate.signingInfo==null?null:candidate.signingInfo.getApkContentsSigners()):candidate.signatures;
        if(current==null||next==null||current.length!=next.length)throw new Exception("更新包签名不符");
        Set<String> a=new HashSet<>(),b=new HashSet<>();for(android.content.pm.Signature sig:current)a.add(hex(MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())));for(android.content.pm.Signature sig:next)b.add(hex(MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())));if(!a.equals(b))throw new Exception("更新包签名不符");
    }
    @SuppressWarnings("deprecation") static long version(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte value:bytes)text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}
}
