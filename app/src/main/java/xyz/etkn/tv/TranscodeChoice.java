package xyz.etkn.tv;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import org.json.*;
import java.util.Locale;

final class TranscodeChoice {
    static final int[] BITRATES={0,2_000_000,4_000_000,8_000_000,12_000_000,20_000_000};
    static final String[] LABELS={"原画","最高 2 Mbps","最高 4 Mbps","最高 8 Mbps","最高 12 Mbps","最高 20 Mbps"};
    static long bitrate(JSONObject source){
        long value=source.optLong("Bitrate",source.optLong("BitRate"));
        if(value>0)return value;
        JSONArray streams=source.optJSONArray("MediaStreams");
        if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject stream=streams.optJSONObject(i);if(stream!=null&&!stream.optString("Type").equalsIgnoreCase("Subtitle"))value+=Math.max(0,stream.optLong("BitRate"));}
        if(value>0)return value;
        double seconds=source.optLong("RunTimeTicks")/10_000_000d;
        return seconds>0?(long)(source.optLong("Size")*8/seconds):0;
    }
    static boolean required(JSONObject source,int limit){return limit>0&&bitrate(source)>limit;}
    static JSONArray capabilities(){
        JSONArray result=new JSONArray();
        int[][] sizes={{1920,1080,60},{1920,1080,30},{1280,720,30},{854,480,30}};
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
