package xyz.etkn.tv;

import android.app.*;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

final class IngestNotifications {
    private static final int JOB=2401;
    private static final String CHANNEL="my-subscriptions";
    private static final AtomicBoolean checking=new AtomicBoolean();
    private static WeakReference<Activity> foreground=new WeakReference<>(null);
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("ingest-notices",Context.MODE_PRIVATE);}
    private static String account(Context context){
        SessionStore session=new SessionStore(context);
        try{return Updater.hex(MessageDigest.getInstance("SHA-256").digest((session.server()+"\n"+session.userId()).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error){throw new IllegalStateException(error);}
    }
    static boolean enabled(Context context){return !new SessionStore(context).userId().isEmpty()&&prefs(context).getBoolean(account(context)+".enabled",false);}
    static void setEnabled(Context context,boolean on){
        prefs(context).edit().putBoolean(account(context)+".enabled",on).remove(account(context)+".cursor").apply();
        if(on){schedule(context);check(context);}else{((JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB);NotificationManagerCompat.from(context).cancelAll();}
    }
    static void schedule(Context context){
        if(!enabled(context))return;
        if(Build.VERSION.SDK_INT>=26)((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"我的订阅入库",NotificationManager.IMPORTANCE_DEFAULT));
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler.getPendingJob(JOB)==null)scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,IngestJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
    }
    static void resumed(Activity activity){foreground=new WeakReference<>(activity);schedule(activity);}
    static void paused(Activity activity){if(foreground.get()==activity)foreground.clear();}
    static void check(Context context){Context app=context.getApplicationContext();TvActivity.IO.execute(()->{try{AcquireTracker.poll(app);}catch(Exception ignored){}try{poll(app);}catch(Exception ignored){}});}
    static Activity foregroundActivity(){return foreground.get();}
    static void logout(Context context){
        ((JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB);
        NotificationManagerCompat.from(context).cancelAll();foreground.clear();
    }
    static void poll(Context context)throws Exception {
        if(!enabled(context)||!checking.compareAndSet(false,true))return;
        try{
            EtknApi api=new EtknApi(context);String identity=account(context),cursor=prefs(context).getString(identity+".cursor","");
            if(api.token.isEmpty())return;
            for(int page=0;page<5;page++){
                JSONObject result=api.get(api.userPath()+"/IngestUpdates?After="+EtknApi.encode(cursor));
                if(!enabled(context)||!identity.equals(account(context)))return;
                for(JSONObject item:EtknApi.rows(result)){
                    String version=item.optString("VersionId"),seen=identity+".seen."+version;
                    if(!result.optBoolean("Initial")&&!prefs(context).getBoolean(seen,false)){
                        show(context,item);prefs(context).edit().putBoolean(seen,true).apply();
                    }
                }
                cursor=result.getString("Cursor");prefs(context).edit().putString(identity+".cursor",cursor).apply();
                if(!result.optBoolean("HasMore"))break;
            }
        }finally{checking.set(false);}
    }
    private static void show(Context context,JSONObject item){
        String title=item.optString("SeriesName");String message=(title.isEmpty()?"":title+" · ")+item.optString("Name");
        Activity activity=foreground.get();if(activity!=null&&!activity.isFinishing())activity.runOnUiThread(()->Toast.makeText(activity,"订阅已入库："+message,Toast.LENGTH_LONG).show());
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(context,DetailActivity.class).putExtra("id",item.optString("Id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int id=item.optString("Id").hashCode();PendingIntent action=PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{NotificationManagerCompat.from(context).notify(id,new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.app_icon).setContentTitle("你的订阅已入库").setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(action).setAutoCancel(true).build());}catch(SecurityException ignored){}
    }
}
