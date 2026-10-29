package xyz.etkn.tv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Foreground SSE, with bounded reconnect and an explicit close on pause. */
final class HomeUpdates implements AutoCloseable {
    private volatile boolean closed;
    private volatile HttpURLConnection connection;
    private final Thread worker;
    HomeUpdates(EtknApi api,java.util.function.Consumer<String> update,Runnable expired){
        worker=new Thread(()->{
            while(!closed){
                try{
                    HttpURLConnection current=(HttpURLConnection)new URL(api.server+api.userPath()+"/Home/Events").openConnection();
                    connection=current;current.setInstanceFollowRedirects(false);current.setConnectTimeout(7000);current.setReadTimeout(25000);
                    current.setRequestProperty("Accept","text/event-stream");current.setRequestProperty("X-Emby-Token",api.token);
                    if(current.getResponseCode()==401){expired.run();return;}
                    if(current.getResponseCode()!=200||!String.valueOf(current.getContentType()).startsWith("text/event-stream"))throw new java.io.IOException("Notification stream unavailable");
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(current.getInputStream(),StandardCharsets.UTF_8))){
                        String event="",data="",line;
                        while(!closed&&(line=reader.readLine())!=null){
                            if(line.startsWith("event:"))event=line.substring(6).trim();
                            else if(line.startsWith("data:"))data=line.substring(5).trim();
                            else if(line.isEmpty()){
                                if("auth-expired".equals(event)){expired.run();return;}
                                if("home".equals(event)&&!data.isEmpty())update.accept(new JSONObject(data).getString("revision"));
                                event="";data="";
                            }
                        }
                    }
                }catch(Exception ignored){}finally{HttpURLConnection current=connection;connection=null;if(current!=null)current.disconnect();}
                if(!closed)try{Thread.sleep(3000);}catch(InterruptedException ignored){return;}
            }
        },"home-updates");worker.setDaemon(true);worker.start();
    }
    @Override public void close(){closed=true;HttpURLConnection current=connection;if(current!=null)current.disconnect();worker.interrupt();}
}
