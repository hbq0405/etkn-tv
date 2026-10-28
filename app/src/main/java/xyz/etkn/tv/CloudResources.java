package xyz.etkn.tv;

import org.json.*;
import java.util.*;
import java.util.regex.*;

final class CloudResources {
    static final class Resource {
        final String provider;
        final JSONObject source;
        Resource(String provider,JSONObject source){this.provider=provider;this.source=source;}
    }
    static List<Resource> parse(String provider,JSONObject payload,Integer season)throws JSONException {
        List<Resource> result=new ArrayList<>();
        for(JSONObject parent:EtknApi.rows(payload)) {
            JSONArray versions=provider.equals("shared_pool")?parent.optJSONArray("versions"):null;
            if(versions==null||versions.length()==0){add(result,provider,new JSONObject(parent.toString()),season);continue;}
            for(int i=0;i<versions.length();i++){
                JSONObject version=versions.optJSONObject(i);if(version==null)continue;
                JSONObject exact=new JSONObject(version.toString());
                // A parent is a display shell. Never inherit its SHA1, source ID or files.
                for(String key:new String[]{"title","name","tmdb_id","season_number","pan_type","release_year","year","poster_path"})
                    if(!exact.has(key)||exact.isNull(key)||exact.optString(key).isEmpty())if(parent.has(key))exact.put(key,parent.get(key));
                exact.remove("versions");add(result,provider,exact,season);
            }
        }
        return result;
    }
    private static void add(List<Resource> rows,String provider,JSONObject item,Integer requested){
        Integer season=season(item);if(requested!=null&&((season==null&&provider.equals("shared_pool"))||(season!=null&&!requested.equals(season))))return;
        rows.add(new Resource(provider,item));
    }
    static Integer season(JSONObject item){
        if(item.has("season_number")&&!item.isNull("season_number"))return item.optInt("season_number");
        Matcher matcher=Pattern.compile("(?i)(?:第\\s*|\\bS|Season\\s*)(\\d{1,3})(?:\\s*季|\\b)").matcher(EtknApi.title(item));
        return matcher.find()?Integer.valueOf(matcher.group(1)):null;
    }
    static JSONObject sharedSource(JSONObject original)throws JSONException {
        JSONObject exact=new JSONObject(original.toString()),group=exact.optJSONObject("logical_group");
        for(String key:new String[]{"logical_group_id","content_version_key","best_asset_map"})if((!exact.has(key)||exact.isNull(key))&&group!=null&&group.has(key))exact.put(key,group.get(key));
        Object map=exact.opt("best_asset_map");if(map instanceof String)exact.put("best_asset_map",new JSONObject((String)map));
        return exact;
    }
    static String label(Resource row,int index){
        JSONObject value=row.source;StringBuilder text=new StringBuilder((index+1)+". "+EtknApi.title(value));
        for(String key:new String[]{"resolution","quality","video_codec","release_group","share_size","size_text","progress_text"})if(!value.optString(key).isEmpty())text.append("  ·  ").append(value.optString(key));
        return text.toString();
    }
}
