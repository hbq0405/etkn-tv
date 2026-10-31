package xyz.etkn.tv;

import okhttp3.HttpUrl;
import org.json.JSONObject;
import java.util.Locale;

final class PlaybackAddress {
    private PlaybackAddress(){}
    static String resolve(JSONObject source,String server)throws Exception{
        return resolve(source,server,"","");
    }
    static String resolve(JSONObject source,String server,String token,String userId)throws Exception{
        String direct=source.getString("DirectStreamUrl");
        String path=source.optString("Path","").trim();
        String container=source.optString("Container","").trim();
        boolean iso="iso".equalsIgnoreCase(container)||path.toLowerCase(Locale.ROOT).contains(".iso");
        String address=iso&&!path.isEmpty()?path:direct;
        if(address.startsWith("/"))address=server+address;
        else if(!address.matches("(?i)^https?://.*"))address=server+"/"+address;
        HttpUrl resolved;
        try{resolved=HttpUrl.get(address);}catch(IllegalArgumentException error){throw new Exception("服务器返回了无效播放地址",error);}
        if(!"http".equalsIgnoreCase(resolved.scheme())&&!"https".equalsIgnoreCase(resolved.scheme()))throw new Exception("服务器返回了无效播放地址");
        HttpUrl serverUrl=HttpUrl.get(server);
        if(!iso&&!sameOrigin(resolved,serverUrl))throw new Exception("服务器返回了无效播放地址");
        if(iso&&sameOrigin(resolved,serverUrl)){
            HttpUrl.Builder builder=resolved.newBuilder();
            if(!token.trim().isEmpty()&&resolved.queryParameter("X-Emby-Token")==null)builder.addQueryParameter("X-Emby-Token",token.trim());
            if(!userId.trim().isEmpty()&&resolved.queryParameter("UserId")==null)builder.addQueryParameter("UserId",userId.trim());
            resolved=builder.build();
        }
        return resolved.toString();
    }
    private static boolean sameOrigin(HttpUrl a,HttpUrl b){
        return a.scheme().equalsIgnoreCase(b.scheme())&&a.host().equalsIgnoreCase(b.host())&&a.port()==b.port();
    }}
