# NextLib JNI compatibility layer

Upstream: `anilbeesetti/nextlib`, tag `1.8.0-0.9.0`, source tree
`1b69e9490c172c42f73f7a6f1b9ffd1c8be4a060`, `media3ext/src/main/cpp`.
License: GPL-3.0; the original full license is retained in this directory.

ETKN modifications, 2026-10-29, cover `ffaudio.cpp` and `ffvideo.cpp`:

- Initialize FFmpeg's channel layout for TrueHD, including stereo.
- Preserve output format, sample rate and channel count before destroying the
  context during a TrueHD reset. The upstream code read a released context and
  recreated it without its channel layout.
- Convert FFmpeg 10-bit planar video frames to 8-bit `AV_PIX_FMT_YUV420P`
  before filling Media3's `VideoDecoderOutputBuffer`.
- Copy normalized Y/U/V planes directly into the Android YV12 window and fall
  back to the displayed width when a device reports a zero native-window stride.
  The 2026-10-30 correction also uses this fallback for the Y-plane copy width,
  preserving luminance on windows that report a zero stride.

`ffcommon.cpp`, `ffcommon.h` and `ffmain.cpp` are unchanged. The JNI library is
named `libetkn_media3ext.so`; `TvRenderersFactory` selects it
before constructing any decoder. Current APKs statically link the pinned FFmpeg
6.0 decoder sources. No media transport is added.

Rebuild the four ABI binaries with NDK r27c (27.2.12479018), Python 3.12+:

```text
python scripts/Build-PlayerNative.py --ndk /path/to/android-ndk-r27c --work /path/to/scratch
```

The script builds FFmpeg 6.0 decoders and links them into the JNI library in
`app/src/main/jniLibs`. On Windows, pass installed Git Bash and GNU make paths
with `--bash` and `--make`. Normal Gradle builds use the checked-in binaries.
`Build-NextlibCompat.py` retains the old dynamic-link build and must not replace
these binaries in current APKs, which do not package separate `libavcodec.so` or
`libswscale.so` files. Verify ELF dependencies after rebuilding.
The app can be rebuilt and installed with a developer's own Android signing key;
no ETKN private signing material or server credentials are required.

The Release includes a separate archive containing the original native-player
sources, their transitive native dependencies, original licenses and build
scripts. Collect it with `scripts/Collect-PlayerSources.py`.
