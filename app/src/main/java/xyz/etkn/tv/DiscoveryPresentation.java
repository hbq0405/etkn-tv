package xyz.etkn.tv;

import android.graphics.Color;
import org.json.JSONObject;

/** 影视探索纯展示助手：独立于 Activity，便于离线单元测试。 */
final class DiscoveryPresentation {
    static String yearOf(JSONObject item){
        String date=item.optString("release_date",item.optString("first_air_date",""));
        if(date.length()>=4)return date.substring(0,4);
        int year=item.optInt("year",item.optInt("release_year",0));
        return year>0?String.valueOf(year):"";
    }
    static String statusLabel(JSONObject item){
        if(item.optBoolean("in_library"))return "已入库";
        switch(item.optString("subscription_status","NONE")){
            case "SUBSCRIBED":return "已订阅";
            case "WANTED":return "待订阅";
            case "PAUSED":return "已暂停";
            case "REQUESTED":return "待审核";
            case "PENDING_RELEASE":return "未发行";
            case "IGNORED":return "已忽略";
            default:return null;
        }
    }
    static int statusColor(JSONObject item){
        if(item.optBoolean("in_library"))return Color.rgb(103,194,58);
        switch(item.optString("subscription_status","NONE")){
            case "WANTED":return Color.rgb(114,46,209);
            case "REQUESTED":return Color.rgb(230,162,60);
            case "PENDING_RELEASE":return Color.rgb(144,147,153);
            case "IGNORED":return Color.rgb(48,49,51);
            default:return Color.rgb(64,158,255);
        }
    }
    static String mediaTypeOf(JSONObject item,String fallback){
        String type=item.optString("media_type",fallback);
        return "tv".equals(type)||"series".equals(type)?"tv":"movie";
    }
}
