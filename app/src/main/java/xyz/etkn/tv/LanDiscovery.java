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
import java.net.URI;
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
                    pool.execute(() -> { try { if(!stopped) { for(int port:new int[]{8097,8096,5257}) { if(stopped)break; try { JSONObject server=resolve("http://"+host+":"+port,650); if(!stopped)found.accept(server); } catch(Exception ignored){} } } } finally {latch.countDown();} });
                }
                if(!stopped)latch.await(25,TimeUnit.SECONDS);
            }catch(Exception ignored){}finally{boolean completed=!stopped;stopped=true;pool.shutdownNow();if(completed)complete.run();}
        });
    }
    static JSONObject resolve(String address,int timeout)throws Exception {
        JSONObject original=identify(address,timeout);
        JSONObject connection=read(address+"/api/system/tv-discovery",timeout);
        if(!"ETKN".equals(connection.optString("ProductName")))throw new Exception("请先更新 ETKN 服务端");
        URI entered=new URI(address);
        String candidate=connection.optString("MediaServerAddress");
        URI advertised=candidate.isEmpty()?null:new URI(EtknApi.normalizeServer(candidate));
        if(advertised==null || !entered.getHost().equalsIgnoreCase(advertised.getHost())) {
            if(entered.getPort()!=connection.optInt("ApiPort",5257))return original;
            candidate=new URI(entered.getScheme(),null,entered.getHost(),connection.getInt("MediaServerPort"),entered.getPath(),null,null).toString();
        }
        candidate=EtknApi.normalizeServer(candidate);
        if(candidate.equals(address))return original;
        JSONObject media;
        try { media=identify(candidate,timeout); }
        catch(Exception error){throw new Exception("无法连接媒体服务器，请填写 ETKN 媒体服务器地址",error);}
        if(original.optString("id").isEmpty() || !original.getString("id").equals(media.optString("id")))throw new Exception("媒体服务器身份不一致，请核对地址");
        return media;
    }
    private static JSONObject identify(String address,int timeout)throws Exception {
        JSONObject health=read(address+"/api/health",timeout);
        if(!"ok".equals(health.optString("status")))throw new Exception("该地址不是可用的 ETKN");
        JSONObject info=read(address+"/api/tv/System/Info/Public",timeout);
        if(!"ETKN".equals(info.optString("ProductName"))||info.optInt("TvApiVersion")!=1)throw new Exception("请先更新 ETKN 服务端以支持独立 TV 接口");
        return new JSONObject().put("url",address).put("name",info.optString("ServerName","ETKN")).put("id",info.optString("Id"));
    }
    private static JSONObject read(String address,int timeout)throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();conn.setConnectTimeout(timeout);conn.setReadTimeout(Math.max(timeout,1000));conn.setInstanceFollowRedirects(false);
        try {if(conn.getResponseCode()!=200)throw new Exception("请检查媒体地址及 ETKN 服务端版本");return new JSONObject(EtknApi.readText(conn.getInputStream()));}finally{conn.disconnect();}
    }
}
