package xyz.etkn.tv;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

final class Http {
    static boolean sameOrigin(String a,String b) {
        try {URI x=URI.create(a),y=URI.create(b);
            return x.getScheme().equalsIgnoreCase(y.getScheme()) && x.getHost()!=null && x.getHost().equalsIgnoreCase(y.getHost()) && port(x)==port(y);
        }catch(Exception error){return false;}
    }
    private static int port(URI uri){return uri.getPort()>=0?uri.getPort():"https".equalsIgnoreCase(uri.getScheme())?443:80;}
    static HttpURLConnection image(String address,EtknApi api)throws Exception { return image(address,api,api.token); }
    static HttpURLConnection image(String address,EtknApi api,String token)throws Exception {
        URL current=new URL(address);
        for(int hop=0;hop<6;hop++) {
            HttpURLConnection conn=(HttpURLConnection)current.openConnection();
            conn.setConnectTimeout(7000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
            if(sameOrigin(current.toString(),api.server))conn.setRequestProperty("X-Emby-Token",token);
            int code=conn.getResponseCode();
            if(code>=300&&code<400){String next=conn.getHeaderField("Location");conn.disconnect();if(next==null)throw new Exception("图片重定向无效");URL target=new URL(current,next);if(!target.getProtocol().equals("https")&&!sameOrigin(target.toString(),api.server))throw new Exception("图片地址无效");current=target;continue;}
            if(code!=200){conn.disconnect();throw new Exception("图片不可用");}return conn;
        }
        throw new Exception("图片重定向过多");
    }
}
