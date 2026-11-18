package xyz.etkn.tv;

import org.json.*;
import java.util.*;

/** Independent movie/series cursors shared by library search and discovery. */
final class DiscoverySearch {
    private final Map<String,Integer> pages=new HashMap<>(),totals=new HashMap<>();
    private final Map<String,String> errors=new HashMap<>();
    private final LinkedHashMap<String,JSONObject> rows=new LinkedHashMap<>();
    static String key(JSONObject item){return DiscoveryPresentation.mediaTypeOf(item,"movie")+":"+item.optString("id");}
    private boolean includes(String type,String kind){return type.equals("all")||type.equals(kind);}
    JSONObject request(String query,String type,boolean hide)throws JSONException{
        JSONObject body=new JSONObject().put("query",query.trim()).put("hide_in_library",hide);
        for(String kind:new String[]{"movie","series"}){
            int page=pages.getOrDefault(kind,0),total=totals.getOrDefault(kind,1);
            body.put(kind+"_page",includes(type,kind)&&page<total?page+1:0);
        }
        return body;
    }
    void accept(JSONObject payload){
        JSONObject sources=payload.optJSONObject("sources");if(sources==null)return;
        List<List<JSONObject>> batches=new ArrayList<>();
        for(String kind:new String[]{"movie","series"}){
            JSONObject source=sources.optJSONObject(kind);List<JSONObject> batch=new ArrayList<>();batches.add(batch);
            if(source==null)continue;
            if(source.has("error")){errors.put(kind,source.optString("error"));continue;}
            errors.remove(kind);pages.put(kind,source.optInt("page",pages.getOrDefault(kind,0)+1));totals.put(kind,Math.min(500,source.optInt("total_pages",0)));
            batch.addAll(EtknApi.rows(source));
        }
        for(int index=0;index<Math.max(batches.get(0).size(),batches.get(1).size());index++)for(List<JSONObject> batch:batches)if(index<batch.size()){JSONObject item=batch.get(index);rows.put(key(item),item);}
    }
    List<JSONObject> items(String type){
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject item:rows.values())if(type.equals("all")||DiscoveryPresentation.mediaTypeOf(item,"movie").equals(type.equals("series")?"tv":"movie"))result.add(item);
        return result;
    }
    boolean hasMore(String type){for(String kind:new String[]{"movie","series"})if(includes(type,kind)&&pages.getOrDefault(kind,0)<totals.getOrDefault(kind,1))return true;return false;}
    String error(String type){List<String> messages=new ArrayList<>();for(String kind:new String[]{"movie","series"})if(includes(type,kind)&&errors.containsKey(kind))messages.add(errors.get(kind));return String.join("；",messages);}
}
