package xyz.etkn.tv;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import org.json.*;
import java.util.Locale;

final class TranscodeChoice {
    static final int[] BITRATES={0,80_000_000,40_000_000,20_000_000,12_000_000,8_000_000,4_000_000,2_000_000,1_000_000};
    static final String[] LABELS={"原画","4K · 80Mbps","4K · 40Mbps","1080p · 20Mbps","1080p · 12Mbps","1080p · 8Mbps","1080p · 4Mbps","720p · 2Mbps","720p · 1Mbps"};
    static int index(int limit){for(int i=0;i<BITRATES.length;i++)if(BITRATES[i]==limit)return i;return 0;}
    static int width(int limit){return limit<=0?0:limit>=40_000_000?3840:limit>=4_000_000?1920:1280;}
    static int height(int limit){return limit<=0?0:limit>=40_000_000?2160:limit>=4_000_000?1080:720;}
    static String label(int limit){return LABELS[index(limit)];}
    static long bitrate(JSONObject source){
        long value=source.optLong("Bitrate",source.optLong("BitRate"));
        if(value>0)return value;
        JSONArray streams=source.optJSONArray("MediaStreams");
        if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject stream=streams.optJSONObject(i);if(stream!=null&&!stream.optString("Type").equalsIgnoreCase("Subtitle"))value+=Math.max(0,stream.optLong("BitRate"));}
        if(value>0)return value;
        double seconds=source.optLong("RunTimeTicks")/10_000_000d;
        return seconds>0?(long)(source.optLong("Size")*8/seconds):0;
    }
    static boolean required(JSONObject source,int limit){
        if(limit<=0||source==null)return false;
        if(bitrate(source)>limit)return true;
        JSONArray streams=source.optJSONArray("MediaStreams");
        if(streams!=null)for(int i=0;i<streams.length();i++){
            JSONObject stream=streams.optJSONObject(i);if(stream==null||!stream.optString("Type").equalsIgnoreCase("Video"))continue;
            int w=stream.optInt("Width"),h=stream.optInt("Height");
            if(Math.abs(stream.optInt("Rotation"))%180!=0){int swap=w;w=h;h=swap;}
            return w>width(limit)||h>height(limit);
        }
        return false;
    }
    static JSONArray capabilities(){
        JSONArray result=new JSONArray();
        int[][] sizes={{3840,2160,30},{1920,1080,60},{1920,1080,30},{1280,720,30},{854,480,30}};
        for(int[] size:sizes){
            boolean supported=false;
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                String name=codec.getName().toLowerCase(Locale.ROOT);
                if(codec.isEncoder()||name.startsWith("omx.google.")||name.startsWith("c2.android.")||name.contains("ffmpeg")||name.contains(".sw."))continue;
                if(android.os.Build.VERSION.SDK_INT>=29&&!codec.isHardwareAccelerated())continue;
                try{MediaCodecInfo.VideoCapabilities video=codec.getCapabilitiesForType("video/avc").getVideoCapabilities();if(video!=null&&video.areSizeAndRateSupported(size[0],size[1],size[2])){supported=true;break;}}catch(IllegalArgumentException ignored){}
            }
            if(supported)try{result.put(new JSONObject().put("Width",size[0]).put("Height",size[1]).put("FrameRate",size[2]));}catch(JSONException ignored){}
        }
        return result;
    }
}
