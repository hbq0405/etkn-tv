package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class DetailPresentation {
    static String contentRating(JSONObject item){String custom=item.isNull("CustomRating")?"":item.optString("CustomRating").trim();return custom.isEmpty()?(item.isNull("OfficialRating")?"":item.optString("OfficialRating").trim()):custom;}
    static String rating(JSONObject item,String field){double value=item.optDouble(field,Double.NaN);return Double.isNaN(value)?"–":String.format(Locale.ROOT,"%.1f",value);}
    static String tomatoRating(JSONObject item){
        double value=item.optDouble("CriticRating",Double.NaN);
        return Double.isNaN(value)||value<0||value>100?"–":String.format(Locale.ROOT,"%.0f%%",value);
    }
    static boolean rottenTomato(JSONObject item){
        double value=item.optDouble("CriticRating",Double.NaN);
        return value>=0&&value<60;
    }
    static String categories(JSONObject item){
        Set<String> values=new LinkedHashSet<>();for(String key:new String[]{"Genres","Tags"}){JSONArray array=item.optJSONArray(key);if(array!=null)for(int i=0;i<array.length();i++){Object raw=array.opt(i);String value=raw instanceof JSONObject?((JSONObject)raw).optString("Name"):raw instanceof String?(String)raw:"";value=value.trim();if(!value.isEmpty())values.add(value);}}
        return String.join(" · ",values);
    }
    static String duration(long ticks){long minutes=ticks/600000000;return minutes<=0?"":minutes>=60?minutes/60+" 小时 "+minutes%60+" 分钟":minutes+" 分钟";}
    static long position(JSONObject item){JSONObject data=item.optJSONObject("UserData");return data==null?0:Math.max(0,data.optLong("PlaybackPositionTicks"));}
    static String episodeNumber(JSONObject item){int number=item.optInt("IndexNumber",-1);return number>=0?"第 "+number+" 集":"特别篇";}
    static boolean placeholder(JSONObject item){return item.optBoolean("IsMissing")||item.optBoolean("IsUnaired")||item.optBoolean("IsVirtualItem")||"Virtual".equals(item.optString("LocationType"));}
    static String airDate(JSONObject item){
        return airDate(item,Calendar.getInstance());
    }
    static String airDate(JSONObject item,Calendar today){
        if(!placeholder(item))return "";String date=item.optString("PremiereDate");
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}(T.*)?"))return "播出时间待定";
        date=date.substring(0,10);java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setTimeZone(today.getTimeZone());
        if(date.equals(format.format(today.getTime())))return "今天播出";
        Calendar tomorrow=(Calendar)today.clone();tomorrow.add(Calendar.DAY_OF_MONTH,1);
        return date.equals(format.format(tomorrow.getTime()))?"明天播出":date;
    }
    static boolean episodeRowHasDates(List<JSONObject> episodes,int index,int columns){
        int start=index/columns*columns;for(int i=start;i<Math.min(start+columns,episodes.size());i++)if(placeholder(episodes.get(i)))return true;return false;
    }
    static JSONObject recommended(List<JSONObject> episodes){
        JSONObject resume=null;String latest="";
        for(JSONObject episode:episodes)if(position(episode)>0){String date=episode.optJSONObject("UserData").optString("LastPlayedDate");if(resume==null||date.compareTo(latest)>0){resume=episode;latest=date;}}
        if(resume!=null)return resume;
        return episodes.get(0);
    }
}
