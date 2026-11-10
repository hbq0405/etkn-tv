package xyz.etkn.tv;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import static org.junit.Assert.*;

/** Synthetic server only: acquisition completion is separate from playable ingestion. */
public class AcquireNotificationTest {
    @Test public void waitsForIngestionNotifiesActualOutcomeOnceAndIsolatesAccounts()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        NotificationManager notifications=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        context.getSharedPreferences("acquire-tracker",0).edit().clear().commit();
        context.getSharedPreferences("ingest-notices",0).edit().clear().commit();
        try(Server server=new Server()){
            SessionStore session=new SessionStore(context);session.save(server.origin(),"acquire-one","一","synthetic");
            assertFalse(IngestNotifications.enabled(context));
            String[] outcomes={"ingested","replaced","version_added","version_added","version_added"},labels={"已入库","洗版成功","新增版本","已更新","已更新"};
            for(int i=0;i<outcomes.length;i++){
                notifications.cancelAll();AcquireTracker.track(context,100+i,"合成电影");server.ready=false;
                AcquireTracker.poll(context);assertTrue(AcquireTracker.hasPending(context));assertEquals(0,notifications.getActiveNotifications().length);
                server.item=new JSONObject().put("Id","item-"+i).put("VersionId",i).put("EventId",i+":sha").put("Name","合成电影").put("Outcome",outcomes[i]);server.ready=true;server.complete=false;
                if(i==3)server.item.put("Type","Episode");
                if(i==4)server.item.put("SeriesName","合成剧集");
                AcquireTracker.poll(context);assertTrue(AcquireTracker.hasPending(context));server.complete=true;AcquireTracker.poll(context);assertFalse(AcquireTracker.hasPending(context));
                StatusBarNotification[] shown=notifications.getActiveNotifications();assertEquals(1,shown.length);assertEquals(labels[i],shown[0].getNotification().extras.getString(Notification.EXTRA_TITLE));
                long time=shown[0].getPostTime();IngestNotifications.notifyIngest(context,server.item);AcquireTracker.poll(context);
                assertEquals(time,notifications.getActiveNotifications()[0].getPostTime());
            }
            notifications.cancelAll();server.ready=false;AcquireTracker.track(context,200,"隔离电影");
            session.save(server.origin(),"acquire-two","二","synthetic");assertFalse(AcquireTracker.hasPending(context));int requests=server.requests;AcquireTracker.poll(context);assertEquals(requests,server.requests);
            session.save(server.origin(),"acquire-one","一","synthetic");assertTrue(AcquireTracker.hasPending(context));server.gate=new CountDownLatch(1);server.entered=new CountDownLatch(1);server.ready=true;
            Thread poll=new Thread(()->AcquireTracker.poll(context));poll.start();assertTrue(server.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));session.save(server.origin(),"acquire-two","二","synthetic");server.gate.countDown();poll.join(5000);
            assertFalse(poll.isAlive());assertEquals(0,notifications.getActiveNotifications().length);session.save(server.origin(),"acquire-one","一","synthetic");assertTrue(AcquireTracker.hasPending(context));
        }finally{
            notifications.cancelAll();context.getSharedPreferences("acquire-tracker",0).edit().clear().commit();context.getSharedPreferences("ingest-notices",0).edit().clear().commit();new SessionStore(context).clear();
            android.content.SharedPreferences sessions=context.getSharedPreferences("session",0);sessions.edit().putString("accounts",SavedAccounts.remove(SavedAccounts.remove(sessions.getString("accounts","[]"),"acquire-one"),"acquire-two")).commit();
            ((android.app.job.JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(2401);
        }
    }
    private static final class Server implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        volatile boolean closed,ready,complete=true;volatile int requests;volatile JSONObject item;volatile CountDownLatch gate,entered;
        Server()throws Exception{Thread thread=new Thread(()->{while(!closed)try(Socket client=socket.accept()){
            BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));String request=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            assertTrue(request.contains("/api/tv/discover/acquire-status/"));requests++;JSONObject result=new JSONObject().put("finished",ready&&complete).put("failed",false).put("status","succeeded");
            if(ready)result.put("ingest_items",new JSONArray().put(item));if(entered!=null)entered.countDown();if(gate!=null)gate.await();
            byte[] body=result.toString().getBytes(StandardCharsets.UTF_8);OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);
        }catch(Exception error){if(!closed)throw new AssertionError(error);}},"acquire-notice-fixture");thread.setDaemon(true);thread.start();}
        String origin(){return "http://127.0.0.1:"+socket.getLocalPort();}
        @Override public void close()throws IOException{closed=true;if(gate!=null)gate.countDown();socket.close();}
    }
}
