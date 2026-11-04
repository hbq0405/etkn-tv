package xyz.etkn.tv;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/** 获取任务成功后继续等待实际可播放版本，按版本变化提醒并与追更通知去重。 */
final class AcquireTracker {
    private static final String CHANNEL="acquire-failures";
    private static final long MAX_AGE_MS=7L*24*60*60*1000;
    private static final java.util.concurrent.atomic.AtomicBoolean checking=new java.util.concurrent.atomic.AtomicBoolean();
    static void track(Context context,long runId,String title){
        if(runId<=0||title==null)return;
        title=title.replace("\n"," ");
        prefs(context).edit().putString(account(context)+"."+runId,title+"\n"+System.currentTimeMillis()).apply();
        IngestNotifications.schedule(context);
    }
    static boolean hasPending(Context context){String prefix=account(context)+".";for(String key:prefs(context).getAll().keySet())if(key.startsWith(prefix))return true;return false;}
    static void poll(Context context){
        if(!checking.compareAndSet(false,true))return;
        try{
        SharedPreferences preferences=prefs(context);
        String identity=account(context);
        Map<String,?> entries=preferences.getAll();
        if(entries.isEmpty())return;
        EtknApi api=new EtknApi(context);
        if(api.token.isEmpty())return;
        for(Map.Entry<String,?> entry:new TreeMap<>(entries).entrySet()){
            String key=entry.getKey();
            if(!key.startsWith(identity+"."))continue;
            long runId;
            try{runId=Long.parseLong(key.substring(identity.length()+1));}
            catch(NumberFormatException error){preferences.edit().remove(key).apply();continue;}
            String value=String.valueOf(entry.getValue());
            String title=value.contains("\n")?value.substring(0,value.indexOf('\n')):value;
            long started=value.contains("\n")?parseTime(value.substring(value.indexOf('\n')+1)):0;
            if(started>0&&System.currentTimeMillis()-started>MAX_AGE_MS){preferences.edit().remove(key).apply();continue;}
            try{
                JSONObject result=api.get("/api/tv/discover/acquire-status/"+runId);
                if(!identity.equals(account(context)))return;
                org.json.JSONArray ingested=result.optJSONArray("ingest_items");
                if(ingested!=null)for(int i=0;i<ingested.length();i++)IngestNotifications.notifyIngest(context,ingested.optJSONObject(i));
                if(!result.optBoolean("finished"))continue;
                preferences.edit().remove(key).apply();
                if(result.optBoolean("failed"))notify(context,runId,title,result.optString("error"));
            }catch(EtknApi.AuthException error){return;}
            catch(Exception ignored){return;}
        }
        }finally{checking.set(false);}
    }
    private static long parseTime(String text){
        try{return Long.parseLong(text.trim());}catch(NumberFormatException error){return 0;}
    }
    private static void notify(Context context,long runId,String title,String error){
        String message=(title==null||title.isEmpty()?"云资源":title)+(error==null||error.isEmpty()?"获取失败":"："+error);
        Activity activity=IngestNotifications.foregroundActivity();
        TvNotice.show(activity,"云资源获取失败："+message,Toast.LENGTH_LONG);
        if(Build.VERSION.SDK_INT>=26)((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"云资源获取",NotificationManager.IMPORTANCE_DEFAULT));
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try{NotificationManagerCompat.from(context).notify((int)(runId%Integer.MAX_VALUE),new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.app_icon).setContentTitle("云资源获取失败").setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).build());}catch(SecurityException ignored){}
    }
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("acquire-tracker",Context.MODE_PRIVATE);}
    private static String account(Context context){
        SessionStore session=new SessionStore(context);
        try{return Updater.hex(MessageDigest.getInstance("SHA-256").digest((session.server()+"\n"+session.userId()).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error){throw new IllegalStateException(error);}
    }
}
