package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class DetailPresentation {
    static String rating(JSONObject item,String field){double value=item.optDouble(field,Double.NaN);return Double.isNaN(value)?"–":String.format(Locale.ROOT,"%.1f",value);}
    static String categories(JSONObject item){
        Set<String> values=new LinkedHashSet<>();for(String key:new String[]{"Genres","Tags"}){JSONArray array=item.optJSONArray(key);if(array!=null)for(int i=0;i<array.length();i++){Object raw=array.opt(i);String value=raw instanceof JSONObject?((JSONObject)raw).optString("Name"):raw instanceof String?(String)raw:"";value=value.trim();if(!value.isEmpty())values.add(value);}}
        return String.join(" · ",values);
    }
    static String duration(long ticks){long minutes=ticks/600000000;return minutes<=0?"":minutes>=60?minutes/60+" 小时 "+minutes%60+" 分钟":minutes+" 分钟";}
    static long position(JSONObject item){JSONObject data=item.optJSONObject("UserData");return data==null?0:Math.max(0,data.optLong("PlaybackPositionTicks"));}
    static String episodeNumber(JSONObject item){int number=item.optInt("IndexNumber",-1);return number>=0?"第 "+number+" 集":"特别篇";}
    static JSONObject recommended(List<JSONObject> episodes){
        JSONObject resume=null;String latest="";
        for(JSONObject episode:episodes)if(position(episode)>0){String date=episode.optJSONObject("UserData").optString("LastPlayedDate");if(resume==null||date.compareTo(latest)>0){resume=episode;latest=date;}}
        if(resume!=null)return resume;
        for(JSONObject episode:episodes){JSONObject data=episode.optJSONObject("UserData");if(data==null||!data.optBoolean("Played"))return episode;}
        return episodes.get(0);
    }
}
