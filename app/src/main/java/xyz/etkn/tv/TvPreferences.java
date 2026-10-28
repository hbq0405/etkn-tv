package xyz.etkn.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;

/** Device preferences are isolated by server and account. Track priorities live on the server. */
final class TvPreferences {
    static final int SKIP_BUTTON=0,SKIP_AUTO=1,SKIP_OFF=2;
    static final String[] SKIP_LABELS={"显示跳过按钮","自动跳过","关闭"};
    private final SharedPreferences prefs;
    private final String scope;
    TvPreferences(Context context,EtknApi api){
        prefs=context.getSharedPreferences("tv_preferences",Context.MODE_PRIVATE);
        scope=new JSONArray().put(api.server).put(api.session.userId()).toString()+"/";
    }
    int skipMode(){int value=prefs.getInt(scope+"skip",SKIP_BUTTON);return value>=0&&value<SKIP_LABELS.length?value:SKIP_BUTTON;}
    void setSkipMode(int value){prefs.edit().putInt(scope+"skip",value).apply();}
    String[] librarySort(String id){return new String[]{prefs.getString(scope+id+"/sort","SortName"),prefs.getString(scope+id+"/order","Ascending")};}
    void saveLibrarySort(String id,String field,String direction){prefs.edit().putString(scope+id+"/sort",field).putString(scope+id+"/order",direction).apply();}
}
