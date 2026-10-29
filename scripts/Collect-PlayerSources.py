"""Collect the pinned native-player sources beside the APK for redistribution."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--cache", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.cache.mkdir(parents=True, exist_ok=True)
args.output.parent.mkdir(parents=True, exist_ok=True)
sources = {
    "nextlib": ("anilbeesetti/nextlib", "1.8.0-0.9.0"),
    "libass-android": ("peerless2012/libass-android", "v0.5.1"),
    "libass-cmake": ("peerless2012/libass-cmake", "d3f00a43ca66e42a2c34de964b1a7dbbfa9dbc8b"),
    "libass": ("libass/libass", "bbb3c7f1570a4a021e52683f3fbdf74fe492ae84"),
    "expat": ("libexpat/libexpat", "f9a3eeb3e09fbea04b1c451ffc422ab2f1e45744"),
    "fontconfig": ("fontconfig/fontconfig", "daa175d234b8a362eedd4c18c33537cc2d19cd98"),
    "freetype": ("freetype/freetype", "42608f77f20749dd6ddc9e0536788eaad70ea4b5"),
    "fribidi": ("fribidi/fribidi", "68162babff4f39c4e2dc164a5e825af93bda9983"),
    "harfbuzz": ("harfbuzz/harfbuzz", "c3fcbffa651cea70400552f2a8bd695ad11023c1"),
    "libunibreak": ("adah1972/libunibreak", "304585d8e2d63187507368d612c3d5fff1486368"),
    "libvpx": ("webmproject/libvpx", "v1.13.0"),
    "mbedtls": ("Mbed-TLS/mbedtls", "v3.4.1"),
    "androidx-media": ("androidx/media", "1.8.0"),
}
urls = {name + ".zip": f"https://codeload.github.com/{repo}/zip/{ref}"
        for name, (repo, ref) in sources.items()}
urls["ffmpeg-6.0.tar.xz"] = "https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz"
urls["kotlin-stdlib-2.2.0-sources.jar"] = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.2.0/kotlin-stdlib-2.2.0-sources.jar"
urls["okhttp-4.12.0-sources.jar"] = "https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0-sources.jar"
urls["okio-jvm-3.6.0-sources.jar"] = "https://repo.maven.apache.org/maven2/com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0-sources.jar"

def fetch(item):
    name, url = item
    path = args.cache / name
    if not path.exists():
        urllib.request.urlretrieve(url, path)
    value = {"url": url, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    if name == "androidx-media.zip":
        # Upstream's test videos/golden images are hundreds of MB and are not
        # needed to build the library. Retain all code, resources and scripts.
        filtered = args.cache / "androidx-media-source.zip"
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(filtered,"w",compression=zipfile.ZIP_DEFLATED) as code:
            for entry in original.infolist():
                if "/libraries/test_data/" not in entry.filename:
                    code.writestr(entry,original.read(entry))
        value["upstream_sha256"] = value["sha256"]
        value["omitted"] = "libraries/test_data synthetic test fixtures"
        path = filtered
        name = filtered.name
        value["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    print(name, value["sha256"], flush=True)
    return name, value

with ThreadPoolExecutor(max_workers=4) as pool:
    manifest = dict(pool.map(fetch, urls.items()))
with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_STORED) as bundle:
    for name in manifest:
        bundle.write(args.cache / name, name)
    bundle.writestr("sources.json", json.dumps(manifest, indent=2) + "\n")
    bundle.writestr("README.txt", """Native-player corresponding sources for ETKN TV 0.1.14.
The app and modified JNI sources are in the matching ETKN TV source tag.
ETKN builds FFmpeg 6.0 decoders statically with scripts/Build-PlayerNative.py.
NextLib Java/JNI changes are included in the matching ETKN source tag.
The original NextLib source and its historical dependency sources are also retained.
libass-android v0.5.1 contains libass-cmake at the commit listed in sources.json.
Restore its src/* git submodules from the matching named archives in this bundle.
Use each upstream build script and its pinned toolchain. All original license
and copyright files are retained inside each source archive.
Rebuild ETKN's complete decoder/JNI library using scripts/Build-PlayerNative.py.
AndroidX Media3 1.8.0 source: https://github.com/androidx/media/tree/1.8.0
AndroidX/Kotlin dependencies use their respective Apache-2.0 licenses and source
artifacts published alongside each Maven dependency.
""")
print("Saved", args.output, flush=True)
