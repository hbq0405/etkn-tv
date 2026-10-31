package xyz.etkn.tv;

import android.net.Uri;
import org.json.JSONObject;
import java.util.Locale;

final class PlaybackAddress {
    private PlaybackAddress(){}
    static String resolve(JSONObject source,String server)throws Exception{
        String direct=source.getString("DirectStreamUrl");
        String path=source.optString("Path","").trim();
        String container=source.optString("Container","").trim();
        String pathName="";
        try{String value=Uri.parse(path).getPath();if(value!=null)pathName=value.toLowerCase(Locale.ROOT);}catch(Exception ignored){}
        boolean iso="iso".equalsIgnoreCase(container)||pathName.endsWith(".iso");
        String address=iso&&!path.isEmpty()?path:direct;
        if(address.startsWith("/"))address=server+address;
        if(!Http.sameOrigin(address,server))throw new Exception("服务器返回了无效播放地址");
        return address;
    }
}