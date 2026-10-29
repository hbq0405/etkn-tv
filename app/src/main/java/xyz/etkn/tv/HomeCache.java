package xyz.etkn.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Only home shelves, isolated by server and account; always revalidated online. */
final class HomeCache {
    private static final long MAX_AGE_MS=24*60*60*1000L;
    private final SharedPreferences prefs;
    HomeCache(Context context,EtknApi api){
        prefs=context.getSharedPreferences("home-"+Images.digest(api.server+"\n"+api.session.userId()),Context.MODE_PRIVATE);
    }
    JSONObject get(String path){
        try{
            JSONObject entry=new JSONObject(prefs.getString(path,"{}"));
            long age=System.currentTimeMillis()-entry.optLong("time");
            return age>=0&&age<MAX_AGE_MS?entry.optJSONObject("data"):null;
        }catch(Exception ignored){return null;}
    }
    void put(String path,JSONObject data){
        if(data.has("unavailable"))return;
        try{prefs.edit().putString(path,new JSONObject().put("time",System.currentTimeMillis()).put("data",data).toString()).apply();}catch(Exception ignored){}
    }
    String revision(){return prefs.getString("revision","");}
    void revision(String value){prefs.edit().putString("revision",value).apply();}
}
