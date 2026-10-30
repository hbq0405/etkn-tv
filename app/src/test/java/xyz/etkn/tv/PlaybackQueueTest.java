package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueTest {
    private static JSONObject episode(String id,int season,int number,boolean played) throws Exception {
        return new JSONObject().put("Id",id).put("Type","Episode").put("ParentIndexNumber",season).put("IndexNumber",number).put("UserData",new JSONObject().put("Played",played));
    }

    @Test public void firstUnwatchedSkipsPlayedMissingAndVirtualInSeasonOrder() throws Exception {
        List<JSONObject> episodes=new ArrayList<>(List.of(
            episode("e2",1,2,false),
            episode("e1",1,1,true),
            new JSONObject().put("Id","e3").put("Type","Episode").put("ParentIndexNumber",1).put("IndexNumber",3).put("IsMissing",true),
            new JSONObject().put("Id","e4").put("Type","Episode").put("ParentIndexNumber",1).put("IndexNumber",4).put("LocationType","Virtual")
        ));
        assertEquals("e2",PlaybackSelection.firstUnwatched(episodes).optString("Id"));
    }

    @Test public void firstUnwatchedFallsBackToFirstPlayableWhenAllPlayed() throws Exception {
        List<JSONObject> episodes=new ArrayList<>(List.of(episode("e2",1,2,true),episode("e1",1,1,true)));
        assertEquals("e1",PlaybackSelection.firstUnwatched(episodes).optString("Id"));
    }

    @Test public void firstUnwatchedReturnsNullWithoutPlayableEpisodes() throws Exception {
        assertNull(PlaybackSelection.firstUnwatched(List.of()));
        assertNull(PlaybackSelection.firstUnwatched(List.of(new JSONObject().put("Id","v1").put("Type","Episode").put("ParentIndexNumber",1).put("IndexNumber",1).put("LocationType","Virtual"))));
    }

    @Test public void mergeTypesCombinesGenresAndTagsPreferringGenreKind() throws Exception {
        JSONObject facets=new JSONObject().put("Genres",new JSONArray().put("剧情").put("动作")).put("Tags",new JSONArray().put("4K").put("动作"));
        List<LibraryFilters.Entry> merged=LibraryFilters.mergeTypes(facets);
        assertEquals(3,merged.size());
        assertEquals("4K",merged.get(0).label);assertEquals("Tags",merged.get(0).kind);
        assertEquals("剧情",merged.get(1).label);assertEquals("Genres",merged.get(1).kind);
        assertEquals("动作",merged.get(2).label);assertEquals("Genres",merged.get(2).kind);
        assertTrue(LibraryFilters.mergeTypes(null).isEmpty());
        assertTrue(LibraryFilters.mergeTypes(new JSONObject()).isEmpty());
    }
}
