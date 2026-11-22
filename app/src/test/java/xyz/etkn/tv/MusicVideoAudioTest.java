package xyz.etkn.tv;

import androidx.media3.common.Player;
import androidx.media3.common.PlaybackParameters;
import okhttp3.HttpUrl;
import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class MusicVideoAudioTest {
    @Test public void mapsVideoTimeToSongAndUsesEmbeddedAudioOutsideOverlap() throws org.json.JSONException {
        JSONArray segments = new JSONArray("[[2,6,0],[6,10,5.5]]");
        assertEquals(750, MusicVideoAudio.songPosition(null, 750));
        assertEquals(2_000, MusicVideoAudio.songPosition(segments, 0));
        assertEquals(-1, MusicVideoAudio.songPosition(segments, 4_000));
        assertEquals(6_250, MusicVideoAudio.songPosition(segments, 5_750));
        assertEquals(-1, MusicVideoAudio.songPosition(segments, 9_500));
    }

    @Test public void preloadsTheUpcomingSongOffsetWhileTheMvIntroIsPlaying() throws org.json.JSONException {
        JSONArray segments = new JSONArray("[[2,10,12]]");
        assertEquals(-1, MusicVideoAudio.songPosition(segments, 11_999));
        assertEquals(2_000, MusicVideoAudio.songPosition(segments, 11_999, true));
        assertEquals(2_000, MusicVideoAudio.songPosition(segments, 12_000));
        assertEquals(2_500, MusicVideoAudio.songPosition(segments, 12_500));
        assertEquals(-1, MusicVideoAudio.songPosition(segments, 20_000, true));
    }

    @Test public void waitsForBufferingThenStartsTheAlignedOriginal() {
        Player song = mock(Player.class);
        PlaybackParameters normal = PlaybackParameters.DEFAULT;
        when(song.getPlaybackState()).thenReturn(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_READY);
        when(song.getCurrentPosition()).thenReturn(2_000L, 2_420L);
        when(song.getPlaybackParameters()).thenReturn(normal);
        when(song.isPlaying()).thenReturn(true);
        when(song.isCurrentMediaItemSeekable()).thenReturn(true);

        assertFalse(MusicVideoAudio.syncSong(song, 2_350, normal));
        verify(song, never()).seekTo(anyLong());
        assertFalse(MusicVideoAudio.syncSong(song, 2_400, normal));
        verify(song).setPlaybackParameters(new PlaybackParameters(1.5f, 1f));
        assertTrue(MusicVideoAudio.syncSong(song, 2_500, normal));
        verify(song, never()).seekTo(anyLong());
    }

    @Test public void convertedAudioStartsAtTheAlignedOffset() {
        HttpUrl address = HttpUrl.get("http://localhost/api/tv/Audio/item-3/stream?Format=flac&MediaSourceId=version-2");
        assertEquals(address, MusicVideoAudio.streamAddress(address, 0));
        HttpUrl aligned = MusicVideoAudio.streamAddress(address, 2_500);
        assertEquals("25000000", aligned.queryParameter("StartTimeTicks"));
        assertEquals("flac", aligned.queryParameter("Format"));
        assertEquals("version-2", aligned.queryParameter("MediaSourceId"));
    }
}
