package xyz.etkn.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;

/** Device preferences are isolated by server and account. Track priorities live on the server. */
final class TvPreferences {
    static final int SKIP_BUTTON=0,SKIP_AUTO=1,SKIP_OFF=2;
    static final String[] SKIP_LABELS={"显示跳过按钮","自动跳过","关闭"};
    static final int IMAGE_EXTREMELY_SMALL=0,IMAGE_SMALL=1,IMAGE_MEDIUM=2,IMAGE_LARGE=3,IMAGE_EXTREMELY_LARGE=4;
    static final String[] IMAGE_SIZE_LABELS={"极小","小","中","大","极大"};
    private final SharedPreferences prefs;
    private final String scope;
    TvPreferences(Context context,EtknApi api){
        prefs=context.getSharedPreferences("tv_preferences",Context.MODE_PRIVATE);
        scope=new JSONArray().put(api.server).put(api.session.userId()).toString()+"/";
    }
    org.json.JSONObject danmaku(){
        try{return new org.json.JSONObject(prefs.getString(scope+"danmaku","{\"enabled\":false,\"rows\":8,\"font_size\":21,\"opacity\":85,\"speed\":1}"));}
        catch(org.json.JSONException invalid){return new org.json.JSONObject();}
    }
    void saveDanmaku(org.json.JSONObject settings){prefs.edit().putString(scope+"danmaku",settings.toString()).apply();}
    int bitrateLimit(){return prefs.getInt(scope+"bitrate-limit",0);}
    void setBitrateLimit(int value){prefs.edit().putInt(scope+"bitrate-limit",value).apply();}
    int skipMode(){int value=prefs.getInt(scope+"skip",SKIP_BUTTON);return value>=0&&value<SKIP_LABELS.length?value:SKIP_BUTTON;}
    void setSkipMode(int value){prefs.edit().putInt(scope+"skip",value).apply();}
    int seasonAudio(String season){return season==null||season.isEmpty()?-1:prefs.getInt(scope+"season-audio/"+season,-1);}
    void saveSeasonAudio(String season,int ordinal){if(season==null||season.isEmpty())return;SharedPreferences.Editor edit=prefs.edit();String key=scope+"season-audio/"+season;if(ordinal<0)edit.remove(key);else edit.putInt(key,ordinal);edit.apply();}
    String[] librarySort(String id){return new String[]{prefs.getString(scope+id+"/sort","SortName"),prefs.getString(scope+id+"/order","Ascending")};}
    void saveLibrarySort(String id,String field,String direction){prefs.edit().putString(scope+id+"/sort",field).putString(scope+id+"/order",direction).apply();}
    String libraryView(String id){return prefs.getString(scope+id+"/view","portrait");}
    void saveLibraryView(String id,String view){prefs.edit().putString(scope+id+"/view",view).apply();}
    String libraryPlayMode(String id){return prefs.getString(scope+id+"/playmode","sequence");}
    void saveLibraryPlayMode(String id,String mode){prefs.edit().putString(scope+id+"/playmode",mode).apply();}
    boolean libraryFolders(String id){return prefs.getBoolean(scope+id+"/folders",false);}
    void saveLibraryFolders(String id,boolean folders){prefs.edit().putBoolean(scope+id+"/folders",folders).apply();}
    boolean smartCarousel(){return prefs.getBoolean(scope+"smart-carousel",false);}
    void setSmartCarousel(boolean on){prefs.edit().putBoolean(scope+"smart-carousel",on).apply();}
    int imageSize(){
        String migratedKey=scope+"image-size-five-level";
        if(!prefs.getBoolean(migratedKey,false)){
            int legacy=prefs.getInt(scope+"image-size",1);
            int migrated=legacy==0?IMAGE_SMALL:legacy==2?IMAGE_LARGE:IMAGE_MEDIUM;
            prefs.edit().putInt(scope+"image-size",migrated).putBoolean(migratedKey,true).apply();
            return migrated;
        }
        int value=prefs.getInt(scope+"image-size",IMAGE_MEDIUM);return value>=0&&value<IMAGE_SIZE_LABELS.length?value:IMAGE_MEDIUM;
    }
    void setImageSize(int value){if(value>=0&&value<IMAGE_SIZE_LABELS.length)prefs.edit().putInt(scope+"image-size",value).putBoolean(scope+"image-size-five-level",true).apply();}
}
