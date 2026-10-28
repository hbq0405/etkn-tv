package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ResourceBoundaryTest {
    @Test public void expandedVersionsNeverInheritParentResourceIdentity()throws Exception {
        JSONObject shell=new JSONObject().put("title","Example").put("season_number",1).put("sha1","wrong").put("source_id",999).put("files",new JSONArray().put("wrong"));
        shell.put("versions",new JSONArray().put(new JSONObject().put("sha1","first").put("source_id",1)).put(new JSONObject().put("sha1","second").put("source_id",2)));
        List<CloudResources.Resource> rows=CloudResources.parse("shared_pool",new JSONObject().put("resources",new JSONArray().put(shell)),1);
        assertEquals(2,rows.size());assertEquals("first",rows.get(0).source.getString("sha1"));assertEquals(2,rows.get(1).source.getInt("source_id"));
        assertFalse(rows.get(0).source.has("files"));assertEquals("Example",rows.get(0).source.getString("title"));
    }
    @Test public void seasonSelectionIncludesSpecialsAndExcludesUnknownSharedSeasons()throws Exception {
        JSONArray items=new JSONArray().put(new JSONObject().put("season_number",0)).put(new JSONObject().put("season_number",1)).put(new JSONObject().put("title","Unknown"));
        List<CloudResources.Resource> rows=CloudResources.parse("shared_pool",new JSONObject().put("resources",items),0);
        assertEquals(1,rows.size());assertEquals(Integer.valueOf(0),CloudResources.season(rows.get(0).source));
    }
    @Test public void groupedResourcePreservesExactAssetMap()throws Exception {
        JSONObject source=new JSONObject().put("sha1","selected").put("logical_group",new JSONObject().put("logical_group_id","g").put("content_version_key","v").put("best_asset_map","{\"1\":{\"sha1\":\"episode\"}}"));
        JSONObject body=CloudResources.sharedSource(source);
        assertEquals("selected",body.getString("sha1"));assertEquals("g",body.getString("logical_group_id"));assertEquals("episode",body.getJSONObject("best_asset_map").getJSONObject("1").getString("sha1"));
    }
    @Test public void updateManifestOnlyAcceptsConfiguredReleaseRepository()throws Exception {
        JSONObject manifest=new JSONObject().put("packageName",BuildConfig.APPLICATION_ID).put("versionCode",2).put("versionName","0.2").put("size",200000).put("sha256","a".repeat(64)).put("apkUrl","https://github.com/hbq0405/etkn-tv/releases/download/v0.2/etkn.apk");
        assertEquals(2,new Updater.Release(manifest).code);
        for(String url:new String[]{"https://github.com/other/repo/releases/download/v0.2/etkn.apk","http://github.com/hbq0405/etkn-tv/releases/download/v0.2/etkn.apk","https://github.com/hbq0405/etkn-tv/releases/download/v0.2/etkn.apk?redirect=bad"}){
            manifest.put("apkUrl",url);try{new Updater.Release(manifest);fail("untrusted update accepted");}catch(Exception expected){}
        }
    }
}
