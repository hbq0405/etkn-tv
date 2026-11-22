package xyz.etkn.tv;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.SystemClock;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import okhttp3.HttpUrl;
import org.json.JSONArray;
import org.json.JSONObject;

/** Plays an associated song on the video's clock, with the embedded audio as fallback. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class MusicVideoAudio {
    private static final long SYNC_TOLERANCE_MS = 300;
    private final Context context;
    private final EtknApi api;
    private final Handler timer;
    private final ExoPlayer video;
    private final Runnable changed;
    private final String videoId, title, videoSourceId, playSession;
    private final long videoLengthTicks;
    private final int audioStreamIndex;
    private final CancellationSignal lookup = new CancellationSignal();
    private final Runnable tick = this::update;
    private CancellationSignal request;
    private String trackId = "";
    private HttpUrl songAddress;
    private long streamOffset;
    private ExoPlayer song;
    private JSONArray segments;
    private boolean selected, audible;

    MusicVideoAudio(Context context, EtknApi api, Handler timer, ExoPlayer video, Runnable changed,
                    String videoId, String title, JSONObject source, String playSession, int audioStreamIndex) {
        this.context = context;
        this.api = api;
        this.timer = timer;
        this.video = video;
        this.changed = changed;
        this.videoId = videoId;
        this.title = title;
        this.videoSourceId = source.optString("Id");
        this.videoLengthTicks = source.optLong("RunTimeTicks");
        this.playSession = playSession;
        this.audioStreamIndex = audioStreamIndex;
    }

    void findTrack(boolean preferMusicAudio) {
        TvActivity.IO.execute(() -> {
            try {
                JSONObject data = api.get(musicPath() + "/RelatedTrack/" + EtknApi.encode(videoId)
                        + "?Title=" + EtknApi.encode(title), lookup);
                if (data.isNull("ItemId")) return;
                timer.post(() -> {
                    if (lookup.isCanceled() || !TvAppearance.sameSession(api, api.userId)) return;
                    trackId = data.optString("ItemId");
                    if (preferMusicAudio) select();
                    changed.run();
                });
            } catch (Exception ignored) { /* The embedded audio remains available. */ }
        });
    }

    boolean available() { return !trackId.isEmpty(); }
    boolean selected() { return selected; }
    boolean audible() { return audible; }

    void select() {
        if (!available() || selected) return;
        selected = true;
        CancellationSignal current = request = new CancellationSignal();
        String track = trackId;
        TvActivity.IO.execute(() -> {
            try {
                JSONObject info = api.post("/api/tv/Items/" + EtknApi.encode(track) + "/PlaybackInfo?PlaySessionId="
                        + EtknApi.encode(playSession), new JSONObject(), current);
                JSONObject source = info.getJSONArray("MediaSources").getJSONObject(0);
                String resolved = PlaybackAddress.resolve(source, api.server, api.token, api.userId);
                HttpUrl address = HttpUrl.get(resolved).newBuilder().setQueryParameter("X-Emby-Token", api.token)
                        .setQueryParameter("UserId", api.userId).setQueryParameter("PlaySessionId", playSession)
                        .setQueryParameter("MediaSourceId", source.optString("Id")).build();
                long songLength = source.optLong("RunTimeTicks");
                boolean sharedTimeline = songLength > 0 && videoLengthTicks > 0
                        && Math.abs(songLength - videoLengthTicks) <= 30_000_000L;
                boolean needsAlignment = !sharedTimeline || Math.abs(songLength - videoLengthTicks) > 3_500_000L;
                timer.post(() -> {
                    if (!isCurrent(current)) return;
                    if (sharedTimeline) prepare(current, address, null);
                    if (needsAlignment && isCurrent(current)) {
                        String path = musicPath() + "/VideoSync/" + EtknApi.encode(track) + "/" + EtknApi.encode(videoId)
                                + "?Title=" + EtknApi.encode(title) + "&AudioSourceId=" + EtknApi.encode(source.optString("Id"))
                                + "&VideoSourceId=" + EtknApi.encode(videoSourceId) + "&AudioStreamIndex=" + audioStreamIndex;
                        align(current, address, path, SystemClock.elapsedRealtime() + 90_000);
                    }
                });
            } catch (Exception error) {
                timer.post(() -> { if (isCurrent(current)) fallback(); });
            }
        });
    }

    private String musicPath() { return "/api/tv/Users/" + EtknApi.encode(api.userId) + "/Music"; }

    private void align(CancellationSignal signal, HttpUrl address, String path, long deadline) {
        if (!isCurrent(signal)) return;
        TvActivity.IO.execute(() -> {
            try {
                JSONObject data = api.get(path, signal);
                timer.post(() -> {
                    if (!isCurrent(signal)) return;
                    if ("cache".equals(data.optString("Pending")) && SystemClock.elapsedRealtime() < deadline) {
                        timer.postDelayed(() -> align(signal, address, path, deadline), 3000);
                        return;
                    }
                    JSONArray matches = data.optJSONArray("Segments");
                    if (matches != null && matches.length() > 0) {
                        prepare(signal, address, matches);
                    } else if (song == null) fallback();
                });
            } catch (EtknApi.HttpStatusException error) {
                timer.post(() -> {
                    if (!isCurrent(signal)) return;
                    if (error.status == 503 && SystemClock.elapsedRealtime() < deadline) {
                        timer.postDelayed(() -> align(signal, address, path, deadline), 1800);
                    } else if (song == null) fallback();
                });
            } catch (Exception error) {
                timer.post(() -> { if (isCurrent(signal) && song == null) fallback(); });
            }
        });
    }

    private boolean isCurrent(CancellationSignal signal) {
        return request == signal && !signal.isCanceled() && TvAppearance.sameSession(api, api.userId);
    }

    private void prepare(CancellationSignal signal, HttpUrl address, JSONArray matches) {
        if (!isCurrent(signal)) return;
        try {
            segments = matches;
            songAddress = address;
            if (song == null) {
                song = TvPlayerFactory.create(context, null, false, false, false);
                song.setVolume(0f);
                song.addListener(new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException error) {
                        if (isCurrent(signal)) fallback();
                    }
                });
            }
            resyncAfterSeek();
        } catch (RuntimeException | LinkageError error) { fallback(); }
    }

    static long songPosition(JSONArray segments, long videoPosition) {
        return songPosition(segments, videoPosition, false);
    }

    static long songPosition(JSONArray segments, long videoPosition, boolean preload) {
        if (segments == null) return videoPosition;
        for (int index = 0; index < segments.length(); index++) {
            JSONArray segment = segments.optJSONArray(index);
            if (segment == null || segment.length() < 3) continue;
            long start = Math.round(segment.optDouble(0) * 1000);
            long end = Math.round(segment.optDouble(1) * 1000);
            long at = Math.round(segment.optDouble(2) * 1000);
            if (preload && videoPosition < at) return start;
            if (videoPosition >= at && videoPosition - at < end - start) return start + videoPosition - at;
        }
        return -1;
    }

    void resync() {
        if (song == null) return;
        setAudible(false);
        update();
    }

    void resyncAfterSeek() {
        if (song == null) return;
        setAudible(false);
        song.pause();
        long position = songPosition(segments, video.getCurrentPosition(), true);
        if (position >= 0 && (song.getMediaItemCount() == 0
                || Math.abs(song.getCurrentPosition() + streamOffset - position) > SYNC_TOLERANCE_MS)) {
            streamOffset = "flac".equals(songAddress.queryParameter("Format")) ? position : 0;
            song.setMediaItem(MediaItem.fromUri(streamAddress(songAddress, streamOffset).toString()), position - streamOffset);
            song.prepare();
        }
        update();
    }

    static HttpUrl streamAddress(HttpUrl address, long offset) {
        return offset == 0 ? address : address.newBuilder()
                .setQueryParameter("StartTimeTicks", Long.toString(offset * 10_000)).build();
    }

    private void update() {
        timer.removeCallbacks(tick);
        if (song == null) return;
        if (!TvAppearance.sameSession(api, api.userId)) { close(); return; }
        long target = songPosition(segments, video.getCurrentPosition());
        long duration = song.getDuration();
        if (!video.isPlaying() || target < streamOffset || (duration > 0 && target - streamOffset >= duration)) {
            song.pause();
            setAudible(false);
        } else {
            setAudible(syncSong(song, target - streamOffset, video.getPlaybackParameters()));
        }
        timer.postDelayed(tick, 160);
    }

    static boolean syncSong(Player song, long target, PlaybackParameters videoSpeed) {
        if (song.getPlaybackState() != Player.STATE_READY) return false;
        long lag = target - song.getCurrentPosition();
        if (lag < -SYNC_TOLERANCE_MS) {
            song.pause();
            return false;
        }
        // Let the current buffer finish loading. Small lag can be recovered while the song is muted.
        if (lag > 2500 && song.isCurrentMediaItemSeekable()) {
            song.seekTo(target);
            return false;
        }
        PlaybackParameters speed = lag > SYNC_TOLERANCE_MS
                ? videoSpeed.withSpeed(videoSpeed.speed * 1.5f) : videoSpeed;
        if (!speed.equals(song.getPlaybackParameters())) song.setPlaybackParameters(speed);
        song.play();
        return lag <= SYNC_TOLERANCE_MS && song.isPlaying();
    }

    private void setAudible(boolean enabled) {
        if (enabled == audible) return;
        audible = enabled;
        video.setVolume(enabled ? 0f : 1f);
        if (song != null) song.setVolume(enabled ? 1f : 0f);
        changed.run();
    }

    private void fallback() { deselect(); changed.run(); }

    void deselect() {
        selected = false;
        if (request != null) { request.cancel(); request = null; }
        timer.removeCallbacks(tick);
        setAudible(false);
        if (song != null) { song.release(); song = null; }
    }

    void close() { lookup.cancel(); deselect(); }
}
