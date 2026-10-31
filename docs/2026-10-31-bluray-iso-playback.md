# Blu-ray ISO playback

ETKN TV now recognizes a `.iso` playback URI and opens the original resource through bounded HTTP Range reads. The native component reads the UDF image, selects the main Blu-ray playlist, and exposes the playlist's MPEG-TS packets to the existing Media3 renderers. It does not proxy media through ETKN and does not transcode the resource.

The Java player keeps the existing playback lifecycle, resume position, seek controls, audio selection and subtitle selection. Non-ISO playback continues through the existing Media3 source factory. For ISO sources, it passes the media source Path (the path stored inside a STRM file) to the player so the .iso suffix selects this native disc path; ordinary sources continue using DirectStreamUrl.

The native reader rejects encrypted discs and does not execute disc Java applications. It supports the selected main title and the streams that the playlist exposes to the pinned FFmpeg/Media3 pipeline.

Validation completed for this change:

- `scripts/Build-BlurayNative.py` built `libetkn_bluray.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`.
- Release 0.1.41 (versionCode 42) passed 63 release unit tests, lint, assembleRelease, and APK v2 signature verification with the existing certificate.
- The signed APK is `outputs/verify-release-0.1.41/etkn-tv-0.1.41.apk` (SHA-256 `fc42d891aa7fc9e4908b698934d2e69413e0f4cfdd92bb2581edb47c7fb2d697`).
- The Android emulator was offline, so device playback, startup latency, and a real ISO session remain for physical-TV validation.
