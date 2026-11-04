# Blu-ray ISO playback component

This directory contains the small JNI bridge used by ETKN TV for direct Blu-ray and UHD Blu-ray ISO playback.

The build script fetches and verifies these upstream source releases:

- libbluray 1.3.4, LGPL-2.1-or-later, SHA-256 `478ffd68a0f5dde8ef6ca989b7f035b5a0a22c599142e5cd3ff7b03bbebe5f2b`
- libudfread 1.1.2, LGPL-2.1-or-later, SHA-256 `d5946f19b27288ce0911eb11fe583c136767421220ece3bafc96245efd208915`
- FFmpeg 6.0, LGPL/GPL components selected by the existing ETKN decoder build, SHA-256 `57be87c22d9b49c112b6d24bc67d42508660e6b718b3db89c44e47e289137082`

The application supplies block reads from the original HTTP resource. libbluray selects the server's probed playlist when supplied, otherwise its main playlist, and exposes MPEG-TS packets to FFmpeg. Track identity uses the transport PID and codec, including separate TrueHD and AC3 compatibility streams sharing a PID. The server remains a direct-link/redirect transport; no media proxy or transcoding path is added.

`seek_point.c.inc` is appended in a generated translation unit to the pinned libbluray `bluray.c`. It uses the loaded CLPI navigation index without moving the live reader or invoking Java. Media3's SeekMap queries must not call `bd_seek_time`; actual seeks remain on the loader thread. The pinned upstream source is not edited.

Disc Java applications and encrypted discs are rejected. The player supports the selected title, seeking, audio/video tracks and PGS subtitle tracks exposed by the playlist.

Rebuild all ABIs with `scripts/Build-BlurayNative.py`. The downloaded archives and their licenses remain outside the APK source tree; the APK ships the corresponding license texts under `app/src/main/assets/licenses/`.
