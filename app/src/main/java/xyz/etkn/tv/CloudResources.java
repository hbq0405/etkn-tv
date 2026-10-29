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
    /** 资源标签：text 为展示文案，tone 决定底色（default/success/warning/info）。 */
    static final class Chip {
        final String text,tone;
        Chip(String text,String tone){this.text=text;this.tone=tone;}
    }
    static List<Resource> parse(String provider,JSONObject payload,Integer season)throws JSONException {
        List<Resource> result=new ArrayList<>();
        for(JSONObject parent:EtknApi.rows(payload)) {
            JSONArray versions=provider.equals("shared_pool")?parent.optJSONArray("versions"):null;
            if(versions==null||versions.length()==0){add(result,provider,new JSONObject(parent.toString()),season);continue;}
            int count=0;
            for(int i=0;i<versions.length();i++)if(versions.optJSONObject(i)!=null)count++;
            int index=0;
            for(int i=0;i<versions.length();i++){
                JSONObject version=versions.optJSONObject(i);if(version==null)continue;
                JSONObject exact=new JSONObject(version.toString());
                // A parent is a display shell. Never inherit its SHA1, source ID or files.
                for(String key:new String[]{"title","name","tmdb_id","season_number","pan_type","release_year","year","poster_path"})
                    if(!exact.has(key)||exact.isNull(key)||exact.optString(key).isEmpty())if(parent.has(key))exact.put(key,parent.get(key));
                exact.remove("versions");
                exact.put("_version_index",++index);exact.put("_version_count",count);
                add(result,provider,exact,season);
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
    /** 共享池版本序号：父卡聚合多个版本时显示“版本 2/5”，单一版本留空。 */
    static String versionLabel(Resource row){
        int count=row.source.optInt("_version_count",0),index=row.source.optInt("_version_index",0);
        return count>1?"版本 "+index+"/"+count:"";
    }
    /** 共享池获取方式：有效分享通道走转存，否则秒传；re0/频道留空。 */
    static String transferLabel(Resource row){
        if(!row.provider.equals("shared_pool"))return "";
        JSONObject channel=row.source.optJSONObject("share_channel");if(channel==null)channel=row.source.optJSONObject("logical_season_share_channel");
        String status=channel==null?"":channel.optString("status","").toLowerCase();
        if(row.source.optBoolean("share_transfer_available")||row.source.optBoolean("has_valid_share_channel"))return "可转存";
        String mode=row.source.optString("preferred_transfer_mode",row.source.optString("transfer_mode","")).toLowerCase();
        return mode.equals("share")||status.equals("valid")?"可转存":"可秒传";
    }
    /** 资源标签集合，最多 10 个；纯函数便于离线测试。 */
    static List<Chip> chips(Resource row){
        JSONObject value=row.source;List<Chip> chips=new ArrayList<>();Set<String> seen=new HashSet<>();
        push(chips,seen,panTypeLabel(value.optString("pan_type")),"default");
        push(chips,seen,sizeText(value),"default");
        push(chips,seen,firstValue(value,"video_resolution","resolution","resolution_display"),"success");
        String effect=firstValue(value,"effect","effect_key","effect_display","hdr_type","dynamic_range","source");
        if(!effect.isEmpty()&&!effect.equalsIgnoreCase("SDR")&&!effect.equalsIgnoreCase("channel")&&!effect.equals("共享秒传")&&!effect.equals("可秒传"))push(chips,seen,effect,"warning");
        push(chips,seen,codecLabel(value),"default");
        push(chips,seen,frameRateLabel(value),"default");
        push(chips,seen,episodeProgress(value),"info");
        JSONArray labels=value.optJSONArray("tag_labels");
        if(labels!=null)for(int i=0;i<labels.length();i++){
            String label=labels.optString(i);
            if(!label.isEmpty())push(chips,seen,label,label.contains("一致")||label.contains("完结")||label.contains("完整")?"success":"default");
        }
        if(value.optBoolean("is_clean_version"))push(chips,seen,"纯净版","warning");
        if(value.optBoolean("is_animation"))push(chips,seen,"动漫","success");
        if(value.optBoolean("is_short_drama"))push(chips,seen,"短剧","info");
        if(row.provider.equals("re0")){
            if(value.optBoolean("already_owned"))push(chips,seen,"已解锁","success");
            else{
                double points=value.optDouble("unlock_points",0);
                push(chips,seen,points<=0?"免费":String.format(java.util.Locale.ROOT,"%.0f 积分",points),"warning");
            }
            String completion=value.optString("_completion_label");
            if(!completion.isEmpty())push(chips,seen,completion,completion.contains("完整")?"success":"default");
            String quality=value.optString("quality");
            if(!quality.isEmpty()&&!"[]".equals(quality))push(chips,seen,quality,"default");
        }
        if(row.provider.equals("channel")){
            push(chips,seen,value.optString("message_date"),"info");
            if(!value.optString("source_channel").isEmpty())push(chips,seen,"来源："+value.optString("source_channel"),"info");
        }
        return chips;
    }
    private static void push(List<Chip> chips,Set<String> seen,String text,String tone){
        if(text==null||text.isEmpty()||!seen.add(text)||chips.size()>=10)return;
        chips.add(new Chip(text,tone));
    }
    static String panTypeLabel(String type){
        if(type==null||type.isEmpty())return "115网盘";
        String value=type.toLowerCase();
        if(value.equals("115"))return "115网盘";
        if(value.equals("rapid115")||value.equals("rapid")||value.equals("shared_pool"))return "115秒传";
        if(value.equals("magnet"))return "磁力";
        if(value.equals("ed2k"))return "ED2K";
        if(value.equals("bt"))return "BT";
        if(type.equals("离线"))return "离线";
        return type.toUpperCase(java.util.Locale.ROOT);
    }
    /** 体积：优先展示服务端文本，否则按字节换算。 */
    static String sizeText(JSONObject source){
        for(String key:new String[]{"share_size","size_text"}){
            String provided=source.optString(key);
            if(!provided.isEmpty())return provided;
        }
        double bytes=0;
        for(String key:new String[]{"total_size","size","file_size","size_bytes"}){
            double value=source.optDouble(key,0);
            if(value>0){bytes=value;break;}
        }
        if(bytes<=0)return "";
        String[] units={"B","KB","MB","GB","TB"};int index=0;
        while(bytes>=1024&&index<units.length-1){bytes/=1024;index++;}
        return String.format(java.util.Locale.ROOT,index==0?"%.0f %s":index==1?"%.1f %s":"%.2f %s",bytes,units[index]);
    }
    /** 集数进度：progress_text “24/24” 或 current/total、file_count。 */
    static String episodeProgress(JSONObject value){
        String progress=value.optString("progress_text").trim();
        if(progress.matches("\\d+\\s*/\\s*\\d+"))return progress.replace(" ","")+" 集";
        double current=value.optDouble("progress_current",value.optDouble("episode_available",-1)),total=value.optDouble("progress_total",value.optDouble("episode_total",0));
        if(current>=0&&total>0)return String.format(java.util.Locale.ROOT,"%.0f/%.0f 集",current,total);
        int count=value.optInt("file_count",0);
        return count>0?count+" 集":"";
    }
    static String codecLabel(JSONObject value){
        String text=firstValue(value,"video_codec","codec");
        return text.isEmpty()||text.equalsIgnoreCase("未知")?"":text;
    }
    /** 帧率：“24000/1001” 折算为 “23.976 fps”。 */
    static String frameRateLabel(JSONObject value){
        String raw=firstValue(value,"frame_rate","fps").replace("fps","").replace("FPS","").trim();
        if(raw.isEmpty())return "";
        try{
            Matcher fraction=Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)$").matcher(raw);
            double number=fraction.matches()?Double.parseDouble(fraction.group(1))/Double.parseDouble(fraction.group(2)):Double.parseDouble(raw);
            if(number>0){
                String formatted=String.format(java.util.Locale.ROOT,"%.3f",number).replaceAll("0+$","").replaceAll("\\.$","");
                return formatted+" fps";
            }
        }catch(NumberFormatException ignored){}
        return raw+" fps";
    }
    /** 依次在资源本体与 version_summary/summary_json 等嵌套摘要里找第一个非空字段。 */
    static String firstValue(JSONObject source,String...keys){
        for(JSONObject container:containers(source)){
            for(String key:keys){
                if(!container.has(key)||container.isNull(key))continue;
                String text=container.optString(key).trim();
                if(!text.isEmpty()&&!text.equalsIgnoreCase("unknown")&&!text.equals("未知")&&!text.equals("[]"))return text;
            }
        }
        return "";
    }
    private static List<JSONObject> containers(JSONObject source){
        List<JSONObject> list=new ArrayList<>();list.add(source);
        for(String key:new String[]{"version_summary","summary_json","media_signature_json","rapid_meta_json"}){
            Object value=source.opt(key);if(value==null)continue;
            if(value instanceof JSONObject)list.add((JSONObject)value);
            else if(value instanceof String){
                try{list.add(new JSONObject((String)value));}catch(JSONException ignored){}
            }
        }
        return list;
    }
}
