package xyz.etkn.tv;

import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MusicVideoAudioTest {
    @Test public void mapsVideoTimeToSongAndUsesEmbeddedAudioOutsideOverlap() throws org.json.JSONException {
        JSONArray segments = new JSONArray("[[2,6,0],[6,10,5.5]]");
        assertEquals(750, MusicVideoAudio.songPosition(null, 750));
        assertEquals(2_000, MusicVideoAudio.songPosition(segments, 0));
        assertEquals(-1, MusicVideoAudio.songPosition(segments, 4_000));
        assertEquals(6_250, MusicVideoAudio.songPosition(segments, 5_750));
        assertEquals(-1, MusicVideoAudio.songPosition(segments, 9_500));
    }
}
