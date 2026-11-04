package xyz.etkn.tv;

import org.json.JSONObject;
import java.util.Locale;

/** TS PID plus codec distinguishes TrueHD from its AC3 compatibility core. */
final class DiscTrackIdentity {
    static String id(int pid,String mime){return "etkn-ts-"+pid+"-"+mime;}
    static boolean matches(JSONObject stream,String formatId){
        int pid=stream.optInt("TransportStreamId",-1);if(pid<=0)return false;
        String codec=stream.optString("Codec").toLowerCase(Locale.ROOT),mime;
        if(codec.startsWith("dts"))mime="audio/vnd.dts.hd";
        else switch(codec){
            case "h264":mime="video/avc";break;
            case "hevc":case "h265":mime="video/hevc";break;
            case "mpeg2video":mime="video/mpeg2";break;
            case "vc1":mime="video/wvc1";break;
            case "truehd":case "mlp":mime="audio/true-hd";break;
            case "ac3":mime="audio/ac3";break;
            case "eac3":mime="audio/eac3";break;
            case "aac":mime="audio/mp4a-latm";break;
            case "mp2":case "mp3":mime="audio/mpeg";break;
            case "pcm_bluray":mime="audio/raw";break;
            case "pgs":case "pgssub":case "hdmv_pgs_subtitle":mime="application/pgs";break;
            default:return false;
        }
        return id(pid,mime).equals(PlaybackSelection.localTrackId(formatId));
    }
}
