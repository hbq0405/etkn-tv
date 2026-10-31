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
    @Test public void pathWithoutLeadingSlashResolvesAgainstServer() throws Exception {
        JSONObject source=new JSONObject()
            .put("Container","iso")
            .put("Path","api/p115/play/pick-code/OurBits.iso")
            .put("DirectStreamUrl","/Videos/item-4/original.strm");
        assertEquals("http://tv.local/api/p115/play/pick-code/OurBits.iso",PlaybackAddress.resolve(source,"http://tv.local"));
    }}