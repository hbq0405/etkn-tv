"""Rebuild the small NextLib JNI layer; FFmpeg shared libraries remain upstream binaries.

Usage: python scripts/Build-NextlibCompat.py --ndk <NDK r27c> --work <scratch directory>
The checked-in JNI binaries allow normal Gradle builds without an NDK.
"""
import argparse
import hashlib
from pathlib import Path
import platform
import subprocess
import tarfile
import urllib.request
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--ndk", type=Path, required=True)
parser.add_argument("--work", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
args.work.mkdir(parents=True, exist_ok=True)

def download(name, url, expected):
    path = args.work / name
    if not path.exists():
        urllib.request.urlretrieve(url, path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != expected:
        raise ValueError(f"Source checksum mismatch: {name}")
    print(name, digest, flush=True)
    return path

aar = download("nextlib.aar", "https://repo.maven.apache.org/maven2/io/github/anilbeesetti/nextlib-media3ext/1.8.0-0.9.0/nextlib-media3ext-1.8.0-0.9.0.aar", "f18b041a8fe823f86b23af6270f1ddd617036dff5753edf7b2a21ed6d806ca11")
with zipfile.ZipFile(aar) as archive:
    archive.extractall(args.work / "nextlib")
headers = download("ffmpeg-6.0.tar.xz", "https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz", "57be87c22d9b49c112b6d24bc67d42508660e6b718b3db89c44e47e289137082")
with tarfile.open(headers) as archive:
    archive.extractall(args.work, filter="data")
ffmpeg = args.work / "ffmpeg-6.0"
# These public-header configuration values match the four little-endian Android ABIs.
(ffmpeg / "libavutil/avconfig.h").write_text(
    "#define AV_HAVE_BIGENDIAN 0\n#define AV_HAVE_FAST_UNALIGNED 1\n", encoding="utf-8")
host = {"Windows": "windows-x86_64", "Linux": "linux-x86_64", "Darwin": "darwin-x86_64"}[platform.system()]
toolchain = args.ndk / "toolchains/llvm/prebuilt" / host
compiler = toolchain / "bin" / ("clang++.exe" if platform.system() == "Windows" else "clang++")
sources = sorted((root / "third_party/nextlib_compat").glob("*.cpp"))
targets = {"armeabi-v7a": "armv7a-linux-androideabi24", "arm64-v8a": "aarch64-linux-android24",
           "x86": "i686-linux-android24", "x86_64": "x86_64-linux-android24"}
for abi, target in targets.items():
    out = root / "app/src/main/jniLibs" / abi / "libetkn_media3ext.so"
    out.parent.mkdir(parents=True, exist_ok=True)
    command = [str(compiler), f"--target={target}", f"--sysroot={toolchain / 'sysroot'}",
               "-shared", "-fPIC", "-O2", "-std=c++17", "-Wno-deprecated-declarations",
               "-idirafter", str(ffmpeg), *map(str, sources),
               f"-L{args.work / 'nextlib/jni' / abi}", "-lavcodec", "-lavutil", "-lswresample",
               "-lswscale", "-llog", "-landroid", "-Wl,-z,max-page-size=16384",
               "-Wl,-soname,libetkn_media3ext.so", "-o", str(out)]
    subprocess.run(command, check=True)
    print(abi, hashlib.sha256(out.read_bytes()).hexdigest(), flush=True)
