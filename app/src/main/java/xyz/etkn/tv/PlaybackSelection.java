package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Playback uses server indices; season preferences store an audio-list ordinal. */
final class PlaybackSelection {
    static final int AUTO=-2;
    static JSONObject nextEpisode(List<JSONObject> episodes,String currentId){
        JSONObject current=null,next=null;
        for(JSONObject episode:episodes)if(currentId.equals(episode.optString("Id"))){current=episode;break;}
        if(current==null||current.optInt("ParentIndexNumber",-1)<0||current.optInt("IndexNumber",-1)<0)return null;
        int season=current.optInt("ParentIndexNumber"),number=current.optInt("IndexNumber");
        for(JSONObject episode:episodes){
            int s=episode.optInt("ParentIndexNumber",-1),n=episode.optInt("IndexNumber",-1);
            if(!"Episode".equals(episode.optString("Type"))||episode.optString("Id").isEmpty()||episode.optBoolean("IsMissing")||"Virtual".equals(episode.optString("LocationType"))||s<season||n<0||s==season&&n<=number)continue;
            if(next==null||s<next.optInt("ParentIndexNumber")||s==next.optInt("ParentIndexNumber")&&n<next.optInt("IndexNumber"))next=episode;
        }
        return next;
    }
    static String seasonKey(JSONObject item){
        if(item==null||!"Episode".equals(item.optString("Type")))return "";
        String season=item.optString("SeasonId");if(!season.isEmpty())return season;
        String series=item.optString("SeriesId");return series.isEmpty()||!item.has("ParentIndexNumber")?"":new JSONArray().put(series).put(item.optInt("ParentIndexNumber")).toString();
    }
    static int audioOrdinal(JSONObject source,int index){List<JSONObject> tracks=streams(source,false);for(int i=0;i<tracks.size();i++)if(tracks.get(i).optInt("Index",-1)==index)return i;return -1;}
    static int audioAt(JSONObject source,int ordinal){List<JSONObject> tracks=streams(source,false);return ordinal>=0&&ordinal<tracks.size()?tracks.get(ordinal).optInt("Index",AUTO):AUTO;}
    static int sourceIndex(JSONArray sources,String id){if(id==null||id.isEmpty())return -1;for(int i=0;i<sources.length();i++)if(id.equals(sources.optJSONObject(i).optString("Id")))return i;return -1;}
    static String sourceLabel(JSONObject source,int index){return source==null?"默认":source.optString("Name","版本 "+(index+1));}
    static List<JSONObject> streams(JSONObject source,boolean subtitles){
        List<JSONObject> result=new ArrayList<>();JSONArray streams=source==null?null:source.optJSONArray("MediaStreams");
        if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject stream=streams.optJSONObject(i);if(stream!=null&&stream.optString("Type").equalsIgnoreCase(subtitles?"Subtitle":"Audio"))result.add(stream);}return result;
    }
    static JSONObject stream(JSONObject source,int index,boolean subtitles){for(JSONObject stream:streams(source,subtitles))if(stream.optInt("Index",-1)==index)return stream;return null;}
    static String streamLabel(JSONObject stream){String title=stream.optString("Title").trim();if(title.isEmpty())title=stream.optString("DisplayTitle").trim();title=title.replaceAll("\\s*[（(]默认(?:\\s+[^)）]*)?[)）]","").replace("默认","").trim();if(title.isEmpty())title=stream.optString("Language","未知语言")+" · "+stream.optString("Codec");return title;}
    static int defaultIndex(JSONObject source,boolean subtitles){
        if(source==null)return AUTO;
        String field=subtitles?"DefaultSubtitleStreamIndex":"DefaultAudioStreamIndex";
        if(source.has(field)&&!source.isNull(field))return source.optInt(field,subtitles?-1:AUTO);
        List<JSONObject> candidates=streams(source,subtitles);
        for(JSONObject stream:candidates)if(stream.optBoolean("IsDefault"))return stream.optInt("Index",AUTO);
        return subtitles?-1:candidates.isEmpty()?AUTO:candidates.get(0).optInt("Index",AUTO);
    }
    static String selectionLabel(JSONObject source,int index,boolean subtitles){
        boolean automatic=index==AUTO;if(automatic)index=defaultIndex(source,subtitles);
        if(subtitles&&index==-1)return "关闭";
        JSONObject stream=stream(source,index,subtitles);
        return stream==null?"自动选择":streamLabel(stream);
    }
    static int embeddedOrdinal(JSONObject source,int index,boolean subtitles){int ordinal=0;for(JSONObject stream:streams(source,subtitles)){if(stream.optBoolean("IsExternal"))continue;if(stream.optInt("Index",-1)==index)return ordinal;ordinal++;}return -1;}
    static String localTrackId(String id){return id==null?"":id.substring(id.lastIndexOf(':')+1);}
    static String externalId(int index){return "etkn-subtitle-"+index;}
}
