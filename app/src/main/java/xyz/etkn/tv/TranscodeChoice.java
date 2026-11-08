package xyz.etkn.tv;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import org.json.*;
import java.util.Locale;

final class TranscodeChoice {
    static final int[] BITRATES={0,80_000_000,60_000_000,40_000_000,20_000_000,12_000_000,8_000_000,4_000_000,2_000_000,1_000_000};
    static final String[] LABELS={"自动 · 优先原画","4K · 80Mbps","4K · 60Mbps","4K · 40Mbps","1080p · 20Mbps","1080p · 12Mbps","1080p · 8Mbps","1080p · 4Mbps","720p · 2Mbps","720p · 1Mbps"};
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
    static JSONObject video(JSONObject source){
        JSONArray streams=source==null?null:source.optJSONArray("MediaStreams");
        if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject stream=streams.optJSONObject(i);if(stream!=null&&stream.optString("Type").equalsIgnoreCase("Video"))return stream;}
        return new JSONObject();
    }
    static boolean hardwareRequired(JSONObject source,JSONArray caps){
        JSONObject video=video(source);int w=video.optInt("Width"),h=video.optInt("Height");
        if(w<=0||h<=0||caps.length()==0)return false;
        double fps=video.optDouble("RealFrameRate",video.optDouble("AverageFrameRate",0));
        long rate=video.optLong("BitRate");
        for(int i=0;i<caps.length();i++){
            JSONObject cap=caps.optJSONObject(i);if(cap==null)continue;
            if(cap.optInt("Width")>=w&&cap.optInt("Height")>=h&&cap.optDouble("FrameRate")+.01>=fps
                    &&(cap.optLong("Bitrate")==0||rate<=cap.optLong("Bitrate")))return false;
        }
        return true;
    }
    static JSONArray sourceCapabilities(JSONObject source){
        JSONObject video=video(source);String codec=video.optString("Codec").toLowerCase(Locale.ROOT);
        if(codec.equals("hevc")||codec.equals("h265"))return capabilities("video/hevc",video.optInt("BitDepth")>8,source);
        if(codec.equals("h264")||codec.equals("avc"))return capabilities("video/avc",false,source);
        return new JSONArray();
    }
    static JSONArray capabilities(){return capabilities(null);}
    static JSONArray capabilities(JSONObject source){return capabilities("video/avc",false,source);}
    static JSONArray hevcCapabilities(){return hevcCapabilities(null);}
    static JSONArray hevcCapabilities(JSONObject source){return capabilities("video/hevc",false,source);}
    static JSONArray hdrCapabilities(android.content.Context context){
        android.view.WindowManager manager=(android.view.WindowManager)context.getSystemService(android.content.Context.WINDOW_SERVICE);
        if(manager!=null)for(int type:manager.getDefaultDisplay().getHdrCapabilities().getSupportedHdrTypes())
            if(type==android.view.Display.HdrCapabilities.HDR_TYPE_HDR10)return capabilities("video/hevc",true,null);
        return new JSONArray();
    }
    static JSONArray hdrCapabilities(android.content.Context context,JSONObject source){
        return hdrCapabilities(context).length()==0?new JSONArray():capabilities("video/hevc",true,source);
    }
    private static JSONArray capabilities(String mime,boolean main10,JSONObject source){
        JSONArray result=new JSONArray();
        JSONObject original=video(source);
        int[][] sizes={{original.optInt("Width"),original.optInt("Height")},{3840,2160},{1920,1080},{1280,720},{854,480}};
        for(int[] size:sizes){
            if(size[0]<2||size[1]<2)continue;
            double rate=0;long bitrate=0;
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                String name=codec.getName().toLowerCase(Locale.ROOT);
                if(codec.isEncoder()||name.startsWith("omx.google.")||name.startsWith("c2.android.")||name.contains("ffmpeg")||name.contains(".sw.")||name.endsWith(".secure")||name.endsWith(".tunneled"))continue;
                if(android.os.Build.VERSION.SDK_INT>=29&&!codec.isHardwareAccelerated())continue;
                try{
                    MediaCodecInfo.CodecCapabilities caps=codec.getCapabilitiesForType(mime);
                    boolean profileSupported=!main10;
                    if(main10)for(MediaCodecInfo.CodecProfileLevel profile:caps.profileLevels)
                        if(profile.profile==MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10||profile.profile==MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10)profileSupported=true;
                    MediaCodecInfo.VideoCapabilities video=caps.getVideoCapabilities();
                    if(profileSupported&&video!=null&&video.isSizeSupported(size[0],size[1])){
                        double maximum=Math.min(120,video.getSupportedFrameRatesFor(size[0],size[1]).getUpper());
                        long bits=Math.min(1_000_000_000,video.getBitrateRange().getUpper());
                        if(maximum>rate||maximum==rate&&bits>bitrate){rate=maximum;bitrate=bits;}
                    }
                }catch(IllegalArgumentException ignored){}
            }
            if(rate>=1)try{result.put(new JSONObject().put("Width",size[0]).put("Height",size[1]).put("FrameRate",rate).put("Bitrate",bitrate));}catch(JSONException ignored){}
        }
        return result;
    }
}
