package xyz.etkn.tv;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackAddressTest {
    @Test public void isoStrmUsesThePathInsideTheStrmFile() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","iso")
            .put("Path","/api/p115/play/pick-code/OurBits.iso")
            .put("DirectStreamUrl","/Videos/item-1/original.strm?X-Emby-Token=secret");
        assertEquals("http://tv.local/api/p115/play/pick-code/OurBits.iso",PlaybackAddress.resolve(source,"http://tv.local"));
    }
    @Test public void ordinaryRemoteSourceKeepsAuthenticatedPlaybackRoute() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","mkv")
            .put("Path","/api/p115/play/pick-code/movie.mkv")
            .put("DirectStreamUrl","/Videos/item-2/original.strm?X-Emby-Token=secret");
        assertEquals("http://tv.local/Videos/item-2/original.strm?X-Emby-Token=secret",PlaybackAddress.resolve(source,"http://tv.local"));
    }
    @Test public void isoPathCanUseTheDirectAddressStoredInStrm() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","iso")
            .put("Path","https://cdn.example/OurBits.iso")
            .put("DirectStreamUrl","/Videos/item-3/original.strm");
        assertEquals("https://cdn.example/OurBits.iso",PlaybackAddress.resolve(source,"http://tv.local"));
    }
    @Test public void isoPathWithChineseSpacesAndPunctuationIsEncodedForHttp() throws Exception {
        String raw="http://192.168.31.175:5257/api/p115/play/akzy3ipi21ilphf9/爱上库珀一家 (2015) · BluRay原盘 · 1080p · H264 · AC3 2.0 · 2Audios - TTG.iso";
        JSONObject source=new JSONObject().put("Container","iso").put("Path",raw).put("DirectStreamUrl","/Videos/item-5/original.strm");
        assertEquals(okhttp3.HttpUrl.get(raw).toString(),PlaybackAddress.resolve(source,"http://tv.local"));
    }
    @Test public void pathWithoutLeadingSlashResolvesAgainstServer() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","iso")
            .put("Path","api/p115/play/pick-code/OurBits.iso")
            .put("DirectStreamUrl","/Videos/item-4/original.strm");
        assertEquals("http://tv.local/api/p115/play/pick-code/OurBits.iso",PlaybackAddress.resolve(source,"http://tv.local"));
    }

    @Test public void sameOriginIsoGetsSessionCredentials() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","iso")
            .put("Path","/api/p115/play/pick-code/OurBits.iso?MediaSourceId=version-7")
            .put("DirectStreamUrl","/Videos/item-6/original.strm");
        assertEquals("http://tv.local/api/p115/play/pick-code/OurBits.iso?MediaSourceId=version-7&X-Emby-Token=secret&UserId=user-1",
            PlaybackAddress.resolve(source,"http://tv.local","secret","user-1"));
    }

    @Test public void externalIsoDoesNotReceiveSessionCredentials() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","iso")
            .put("Path","https://cdn.example/OurBits.iso")
            .put("DirectStreamUrl","/Videos/item-7/original.strm");
        assertEquals("https://cdn.example/OurBits.iso",PlaybackAddress.resolve(source,"http://tv.local","secret","user-1"));
    }}
