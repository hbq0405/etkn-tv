package xyz.etkn.tv;

import java.net.URI;
import org.json.JSONObject;
import java.util.Locale;

final class PlaybackAddress {
    private PlaybackAddress(){}
    static String resolve(JSONObject source,String server)throws Exception{
        String direct=source.getString("DirectStreamUrl");
        String path=source.optString("Path","").trim();
        String container=source.optString("Container","").trim();
        String pathName="";
        try{String value=URI.create(path).getPath();if(value!=null)pathName=value.toLowerCase(Locale.ROOT);}catch(Exception ignored){}
        boolean iso="iso".equalsIgnoreCase(container)||pathName.endsWith(".iso");
        String address=iso&&!path.isEmpty()?path:direct;
        if(address.startsWith("/"))address=server+address;
        else if(URI.create(address).getScheme()==null)address=server+"/"+address;
        URI resolved=URI.create(address);String scheme=resolved.getScheme();
        if(resolved.getHost()==null||(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme)))throw new Exception("服务器返回了无效播放地址");
        if(!iso&&!sameOrigin(address,server))throw new Exception("服务器返回了无效播放地址");
        return address;
    }
    private static boolean sameOrigin(String a,String b){
        URI x=URI.create(a),y=URI.create(b);
        int xp=x.getPort()>=0?x.getPort():"https".equalsIgnoreCase(x.getScheme())?443:80;
        int yp=y.getPort()>=0?y.getPort():"https".equalsIgnoreCase(y.getScheme())?443:80;
        return x.getScheme().equalsIgnoreCase(y.getScheme())&&x.getHost()!=null&&x.getHost().equalsIgnoreCase(y.getHost())&&xp==yp;
    }
}