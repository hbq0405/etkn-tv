# NextLib JNI compatibility layer

Upstream: `anilbeesetti/nextlib`, tag `1.8.0-0.9.0`, source tree
`1b69e9490c172c42f73f7a6f1b9ffd1c8be4a060`, `media3ext/src/main/cpp`.
License: GPL-3.0; the original full license is retained in this directory.

ETKN modifications, 2026-10-29, are limited to `ffaudio.cpp`:

- Initialize FFmpeg's channel layout for TrueHD, including stereo.
- Preserve output format, sample rate and channel count before destroying the
  context during a TrueHD reset. The upstream code read a released context and
  recreated it without its channel layout.

`ffcommon.cpp`, `ffcommon.h`, `ffmain.cpp` and `ffvideo.cpp` are unchanged.
The JNI library is named `libetkn_media3ext.so`; `TvRenderersFactory` selects it
before constructing any decoder. The FFmpeg shared libraries and Java classes
come unchanged from the pinned Maven dependency. No media transport is added.

Rebuild the four ABI binaries with NDK r27c (27.2.12479018), Python 3.12+:

```text
python scripts/Build-NextlibCompat.py --ndk /path/to/android-ndk-r27c --work /path/to/scratch
```

The script downloads the original AAR and FFmpeg 6.0 public headers, links
against the AAR's unmodified FFmpeg libraries, and writes into
`app/src/main/jniLibs`. Normal Gradle builds use those checked-in binaries.
The app can be rebuilt and installed with a developer's own Android signing key;
no ETKN private signing material or server credentials are required.

The Release includes a separate archive containing the original native-player
sources, their transitive native dependencies, original licenses and build
scripts. Collect it with `scripts/Collect-PlayerSources.py`.
