package xyz.etkn.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class LanDiscovery {
    private final ExecutorService pool=Executors.newFixedThreadPool(24);
    private volatile boolean stopped;
    void cancel() { stopped=true; pool.shutdownNow(); }
    static List<String> hosts(byte[] address,int prefix) {
        if(address.length!=4 || prefix<1 || prefix>30)return Collections.emptyList();
        int bits=(address[0]&255)<<24|(address[1]&255)<<16|(address[2]&255)<<8|address[3]&255;
        int mask=-1 << (32-Math.max(24,prefix)), base=bits&mask, end=base|~mask;
        List<String> hosts=new ArrayList<>();
        for(int ip=base+1;ip<end;ip++)hosts.add(((ip>>>24)&255)+"."+((ip>>>16)&255)+"."+((ip>>>8)&255)+"."+(ip&255));
        return hosts;
    }
    void scan(Context context, Consumer<JSONObject> found, Runnable complete) {
        TvActivity.IO.execute(() -> {
            try {
                Set<String> candidates=new LinkedHashSet<>();
                ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                for(Network network:manager.getAllNetworks()) {
                    LinkProperties links=manager.getLinkProperties(network); if(links==null)continue;
                    for(LinkAddress link:links.getLinkAddresses())if(link.getAddress() instanceof Inet4Address && link.getAddress().isSiteLocalAddress()) candidates.addAll(hosts(link.getAddress().getAddress(),link.getPrefixLength()));
                }
                CountDownLatch latch=new CountDownLatch(candidates.size());
                for(String host:candidates) {
                    if(stopped)break;
                    pool.execute(() -> { try { if(!stopped) { JSONObject server=probe("http://"+host+":5257"); if(server!=null&&!stopped)found.accept(server); } } finally {latch.countDown();} });
                }
                if(!stopped)latch.await(25,TimeUnit.SECONDS);
            }catch(Exception ignored){}finally{boolean completed=!stopped;stopped=true;pool.shutdownNow();if(completed)complete.run();}
        });
    }
    private JSONObject probe(String address) {
        try {
            JSONObject health=read(address+"/api/health"); if(!"ok".equals(health.optString("status")))return null;
            JSONObject auth=read(address+"/api/auth/check_status"); if(!auth.has("status"))return null;
            JSONObject info=read(address+"/emby/System/Info/Public");
            return new JSONObject().put("url",address).put("name",info.optString("ServerName","ETKN")).put("id",info.optString("Id",address));
        }catch(Exception ignored){return null;}
    }
    private JSONObject read(String address)throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();conn.setConnectTimeout(650);conn.setReadTimeout(1000);conn.setInstanceFollowRedirects(false);
        try {if(conn.getResponseCode()!=200)throw new Exception();return new JSONObject(EtknApi.readText(conn.getInputStream()));}finally{conn.disconnect();}
    }
}
