package xyz.etkn.tv;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONObject;

/** Account preferences shared with Web; a local lock freezes the entire snapshot. */
final class TvAppearance {
    static JSONObject current=new JSONObject();
    static int generation;
    static volatile boolean saving;
    static String cacheKey(String server,String user){return "user/"+new org.json.JSONArray().put(server).put(user);}
    static void load(Context context,String server){
        android.content.SharedPreferences prefs=context.getSharedPreferences("appearance",0);
        String locked=prefs.getString(lockKey(server,new SessionStore(context).userId()),null);
        try{current=new JSONObject(locked==null?prefs.getString(cacheKey(server,new SessionStore(context).userId()),"{}"):locked);}
        catch(Exception ignored){current=new JSONObject();}
    }
    static void save(Context context,String server,JSONObject value){if(locked(context,server))return;current=value;context.getSharedPreferences("appearance",0).edit().putString(cacheKey(server,new SessionStore(context).userId()),value.toString()).apply();}
    static JSONObject account(Context context,String server,String user){
        android.content.SharedPreferences prefs=context.getSharedPreferences("appearance",0);
        try{return new JSONObject(prefs.getString(lockKey(server,user),prefs.getString(cacheKey(server,user),"{}")));}catch(Exception ignored){return new JSONObject();}
    }
    static JSONObject update(EtknApi api,String key,Object value)throws Exception{
        JSONObject latest=api.get("/api/tv/Users/Me/ClientPreferences"),body=new JSONObject();
        for(String field:new String[]{"revision","mode","theme_color","background_blur","glass_opacity","shadow_strength","shadow_color","card_size","show_home_libraries","home_carousel","skip_mode"})if(latest.has(field))body.put(field,latest.get(field));
        if(!sameSession(api,api.userId))throw new Exception("账号已切换，请重新设置");
        body.put(key,value);api.put("/api/tv/Users/Me/ClientPreferences",body);
        return api.get("/api/tv/Users/Me/ClientPreferences");
    }
    private static String lockKey(String server,String user){return "locked/"+new org.json.JSONArray().put(server).put(user);}
    static boolean locked(Context context,String server){return context.getSharedPreferences("appearance",0).contains(lockKey(server,new SessionStore(context).userId()));}
    private static java.io.File backgroundFile(Context context,String server,String user){String key=lockKey(server,user);return new java.io.File(context.getFilesDir(),"appearance-"+java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".img");}
    static java.io.File lockedBackground(Context context,EtknApi api){java.io.File file=backgroundFile(context,api.server,api.session.userId());return current.optString("_locked_background").equals(file.getName())&&file.isFile()?file:null;}
    static boolean sameSession(EtknApi api,String user){return !user.isEmpty()&&api.server.equals(api.session.server())&&user.equals(api.session.userId())&&api.token.equals(api.session.token());}
    /** Runs on IO; publish the snapshot only after its wallpaper is safely local. */
    static void lock(Context context,EtknApi api,String user,JSONObject snapshot)throws Exception{
        if(!sameSession(api,user))throw new Exception("账号已切换，请重新设置");
        java.io.File target=backgroundFile(context,api.server,user),temporary=null;String path=snapshot.optString("background_url");
        try{
            if(!path.isEmpty()){
                String address=api.server+path;if(!Http.sameOrigin(address,api.server))throw new Exception("背景图地址无效");
                temporary=java.io.File.createTempFile("appearance-",".tmp",context.getFilesDir());
                java.net.HttpURLConnection connection=Http.image(address,api);
                try(java.io.InputStream input=connection.getInputStream();java.io.FileOutputStream output=new java.io.FileOutputStream(temporary)){
                    byte[] buffer=new byte[8192];int count,total=0;while((count=input.read(buffer))!=-1){total+=count;if(total>32*1024*1024)throw new Exception("背景图过大");output.write(buffer,0,count);}
                }finally{connection.disconnect();}
                android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(temporary.getPath(),bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new Exception("背景图无法读取");
            }
            if(!sameSession(api,user))throw new Exception("账号已切换，请重新设置");
            if(temporary!=null){if(!temporary.renameTo(target))throw new Exception("无法保存锁定背景");snapshot.put("_locked_background",target.getName());}
            android.content.SharedPreferences prefs=context.getSharedPreferences("appearance",0);String key=lockKey(api.server,user),previous=prefs.getString(key,null);
            if(!prefs.edit().putString(key,snapshot.toString()).commit()){prefs.edit().putString(key,previous).apply();if(temporary!=null)target.delete();throw new Exception("无法保存锁定偏好，请重试");}
        }finally{if(temporary!=null&&temporary.exists())temporary.delete();}
    }
    static void unlock(Context context,EtknApi api,String user)throws Exception{if(!sameSession(api,user))throw new Exception("账号已切换，请重新设置");android.content.SharedPreferences prefs=context.getSharedPreferences("appearance",0);String key=lockKey(api.server,user),previous=prefs.getString(key,null);if(!prefs.edit().remove(key).commit()){prefs.edit().putString(key,previous).apply();throw new Exception("无法取消偏好锁定，请重试");}backgroundFile(context,api.server,user).delete();}
    static boolean dark(){return "dark".equals(current.optString("mode","dark"));}
    static int primary(){try{return Color.parseColor(current.optString("theme_color").isEmpty()?(dark()?"#55c7b0":"#087f70"):current.getString("theme_color"));}catch(Exception ignored){return 0xff087f70;}}
    static int mix(int from,int to,float amount){return Color.rgb(Math.round(Color.red(from)*(1-amount)+Color.red(to)*amount),Math.round(Color.green(from)*(1-amount)+Color.green(to)*amount),Math.round(Color.blue(from)*(1-amount)+Color.blue(to)*amount));}
    static float shadow(){return (float)Math.max(0,Math.min(1,current.optDouble("shadow_strength",1)));}
    static int shadowColor(boolean dark){try{String value=current.optString("shadow_color");return value.isEmpty()?(dark?Color.BLACK:0xff1f332e):Color.parseColor(value);}catch(IllegalArgumentException invalid){return dark?Color.BLACK:0xff1f332e;}}
    static int translucent(int color){return (color&0x00ffffff)|(Math.round(255*(float)Math.max(0,Math.min(1,current.optDouble("glass_opacity",.76))))<<24);}
    static int background(){return background(dark());}
    static int background(boolean dark){return dark?0xff28332f:0xffb5bfb9;}
    static int surface(){return surface(dark());}
    static int surface(boolean dark){return dark?0xff1e2825:0xfffafdfb;}
    static int text(){return text(dark());}
    static int text(boolean dark){return dark?0xffedf4f1:0xff1f332e;}
    static int muted(){return muted(dark());}
    static int muted(boolean dark){return dark?0xffa6b9b1:0xff61746d;}
    static int accent(){return accent(dark());}
    static int accent(boolean dark){int color=primary();double brightness=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/255000.0;return dark&&brightness<.55?mix(color,Color.WHITE,.5f):!dark&&brightness>.65?mix(color,Color.BLACK,.4f):color;}
    static int resolve(int color){return resolve(color,false);}
    static int resolve(int color,boolean forceDark){
        boolean dark=forceDark||dark();
        if(color==TvActivity.INK)return background(dark);if(color==TvActivity.PANEL)return translucent(surface(dark));
        if(color==TvActivity.TEXT)return text(dark);if(color==TvActivity.MUTED)return muted(dark);
        if(color==TvActivity.ACCENT)return accent(dark);if(color==TvActivity.SOFT)return translucent(mix(surface(dark),primary(),.16f));
        if(color==TvActivity.PROGRESS)return (primary()&0x00ffffff)|(Math.round(Color.alpha(translucent(primary()))*.36f)<<24);
        return color;
    }
}
