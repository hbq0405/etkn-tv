"""Build ETKN's ISO reader/demuxer; no network protocols, menus or decryption.

Requires the same NDK r27c, Git Bash and MSYS make as Build-PlayerNative.py.
Sources are downloaded from their upstream releases and verified before use.
"""
import argparse
import ctypes
from concurrent.futures import ThreadPoolExecutor
import hashlib
import os
from pathlib import Path
import platform
import re
import shlex
import shutil
import subprocess
import tarfile
import urllib.request

SOURCES = {
    "libbluray-1.3.4.tar.bz2": ("https://download.videolan.org/pub/videolan/libbluray/1.3.4/libbluray-1.3.4.tar.bz2", "478ffd68a0f5dde8ef6ca989b7f035b5a0a22c599142e5cd3ff7b03bbebe5f2b"),
    "libudfread-1.1.2.tar.bz2": ("https://download.videolan.org/pub/videolan/libudfread/libudfread-1.1.2.tar.bz2", "d5946f19b27288ce0911eb11fe583c136767421220ece3bafc96245efd208915"),
    "ffmpeg-6.0.tar.xz": ("https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz", "57be87c22d9b49c112b6d24bc67d42508660e6b718b3db89c44e47e289137082"),
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--bash", default="bash")
    parser.add_argument("--make", default="make")
    parser.add_argument("--abis", nargs="+", default=["x86_64", "arm64-v8a", "armeabi-v7a", "x86"])
    args = parser.parse_args()
    if platform.system() == "Windows":
        os.environ["PATH"] = str(Path(args.bash).parent.parent / "usr/bin") + os.pathsep + os.environ["PATH"]
    root = Path(__file__).resolve().parents[1]
    args.work.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in SOURCES.items():
        archive = args.work / name
        if not archive.exists():
            urllib.request.urlretrieve(url, archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Source checksum mismatch: " + name)
        folder = args.work / name.split(".tar.")[0]
        if not folder.exists():
            with tarfile.open(archive) as content:
                content.extractall(args.work, filter="data")
    bluray, udf, ffmpeg = [args.work / n for n in ("libbluray-1.3.4", "libudfread-1.1.2", "ffmpeg-6.0")]
    host = {"Windows": "windows-x86_64", "Linux": "linux-x86_64", "Darwin": "darwin-x86_64"}[platform.system()]
    suffix = ".exe" if platform.system() == "Windows" else ""
    toolbin = args.ndk / "toolchains/llvm/prebuilt" / host / "bin"
    cc, cxx = [toolbin / (name + suffix) for name in ("clang", "clang++")]
    targets = {"x86_64": ("x86_64", "x86_64-linux-android24"), "arm64-v8a": ("aarch64", "aarch64-linux-android24"), "armeabi-v7a": ("arm", "armv7a-linux-androideabi24"), "x86": ("x86", "i686-linux-android24")}
    # Use upstream's core source list, with the BD-J implementation disabled.
    listing = (bluray / "Makefile.am").read_text(encoding="utf-8").split("# bd-j")[0]
    files = [bluray / n for n in re.findall(r"\bsrc/[\w/]+\.c\b", listing)]
    files += [bluray / n for n in ["src/libbluray/disc/udf_fs.c", "src/libbluray/bdj/bdjo_parse.c", "src/file/dir_posix.c", "src/file/dirs_xdg.c", "src/file/dl_posix.c", "src/file/file_posix.c", "src/file/mount.c"]]
    files += [udf / "src" / n for n in ["udfread.c", "ecma167.c", "default_blockinput.c"]]
    files += [root / "third_party/bluray/bdj_disabled.c"]
    for abi in args.abis:
        arch, target = targets[abi]
        build = args.work / abi
        build.mkdir(exist_ok=True)
        ffbuild = build / "ffmpeg"
        ffbuild.mkdir(exist_ok=True)
        options = [ffmpeg.as_posix() + "/configure", "--target-os=android", "--enable-cross-compile", f"--arch={arch}",
            f"--cc={cc.as_posix()} --target={target}", f"--cxx={cxx.as_posix()} --target={target}",
            *[f"--{tool}={(toolbin / ('llvm-' + tool + suffix)).as_posix()}" for tool in ["ar", "nm", "ranlib", "strip"]],
            "--disable-programs", "--disable-doc", "--disable-debug", "--disable-autodetect", "--disable-everything", "--disable-network",
            "--disable-avdevice", "--disable-avfilter", "--disable-swscale", "--disable-swresample", "--disable-postproc",
            "--disable-shared", "--enable-static", "--enable-pic", "--disable-x86asm", "--extra-cflags=-O2 -fPIC",
            "--enable-demuxer=mpegts", "--enable-decoder=pcm_bluray", "--enable-parser=h264,hevc,mpegvideo,vc1,ac3,dca,mlp,aac,aac_latm,mpegaudio"]
        if abi == "x86":
            options.append("--disable-asm")
        configuration = ffbuild / "ffbuild/config.mak"
        if not configuration.exists() or "--enable-decoder=pcm_bluray" not in configuration.read_text(encoding="utf-8"):
            with (build / "configure.log").open("w", encoding="utf-8") as log:
                subprocess.run([args.bash, "-c", shlex.join(options)], cwd=ffbuild, stdout=log, stderr=subprocess.STDOUT, check=True)
        if platform.system() == "Windows":
            # Git Bash uses /d/, whereas the separate MSYS make uses /cygdrive/d/.
            for generated in [ffbuild / "Makefile", ffbuild / "ffbuild/config.mak"]:
                value = generated.read_text(encoding="utf-8")
                value = re.sub(r"(?<![\w])/[a-zA-Z]/", lambda match: match.group()[1].upper() + ":/", value)
                generated.write_text(value, encoding="utf-8", newline="\n")
        print("Compiling ISO transport/demux", abi, flush=True)
        make_shell = str(Path(args.bash).as_posix())
        if platform.system() == "Windows":
            short_path = ctypes.create_unicode_buffer(32768)
            if not ctypes.windll.kernel32.GetShortPathNameW(make_shell, short_path, len(short_path)):
                raise RuntimeError("Cannot resolve make shell path")
            make_shell = short_path.value
        with (build / "ffmpeg-build.log").open("w", encoding="utf-8") as log:
            make_args = [args.make, "SHELL=" + make_shell]
            if platform.system() == "Windows":
                # Execute the upstream make plan directly: mixed MSYS runtimes can
                # corrupt shell quoting when make launches the Git Bash executable.
                plan = subprocess.run([*make_args, "-n", "V=1"], cwd=ffbuild, capture_output=True, text=True, check=True).stdout
                (build / "make-plan.txt").write_text(plan, encoding="utf-8")
                compiles, archives = [], []
                for line in plan.splitlines():
                    command = shlex.split(line)
                    if not command:
                        continue
                    if command[0] == "mkdir":
                        (ffbuild / command[-1]).mkdir(parents=True, exist_ok=True)
                    elif command[0] == "rm":
                        continue
                    elif command[0].endswith("clang.exe") and "-c" in command:
                        output = ffbuild / command[command.index("-o") + 1]
                        output.parent.mkdir(parents=True, exist_ok=True)
                        compiles.append(command)
                    elif command[0].endswith(("llvm-ar.exe", "llvm-ranlib.exe")):
                        archives.append(command)
                    elif "pkgconfig_generate.sh" in command[0]:
                        continue
                    else:
                        subprocess.run([args.bash, "-c", line], cwd=ffbuild, stdout=log, stderr=subprocess.STDOUT, check=True)
                def compile_ffmpeg(command):
                    result = subprocess.run(command, cwd=ffbuild, capture_output=True, text=True)
                    if result.returncode:
                        raise RuntimeError(result.stderr)
                with ThreadPoolExecutor(max_workers=8) as pool:
                    list(pool.map(compile_ffmpeg, compiles))
                for command in archives:
                    subprocess.run(command, cwd=ffbuild, stdout=log, stderr=subprocess.STDOUT, check=True)
            else:
                subprocess.run([*make_args, "-j", str(min(8, os.cpu_count() or 4))], cwd=ffbuild, stdout=log, stderr=subprocess.STDOUT, check=True)
        defines = ["HAVE_UNISTD_H", "HAVE_FCNTL_H", "HAVE_DLFCN_H", "HAVE_DLADDR", "HAVE_PTHREAD_H", "HAVE_DIRENT_H", "HAVE_SYS_STAT_H", "HAVE_INTTYPES_H", "HAVE_STDINT_H", "HAVE_STDLIB_H", "HAVE_STRING_H", "HAVE_STRINGS_H", "HAVE_MNTENT_H", "HAVE_STRUCT_DIRENT_D_TYPE", "HAVE_CLOCK_GETTIME", "HAVE_TIME_H"]
        common = [f"--target={target}", "-fPIC", "-O2", "-D_GNU_SOURCE", "-D_FILE_OFFSET_BITS=64", "-D_ISOC99_SOURCE", "-D_POSIX_C_SOURCE=200809L", "-DHAVE_SYS_TIME_H=1", "-DVERSION=\"1.3.4\"", *["-D" + n + "=1" for n in defines], "-I" + str(bluray / "src"), "-I" + str(bluray / "src/libbluray"), "-I" + str(udf / "src")]
        def compile_one(item):
            index, source = item
            obj = build / f"disc-{index}.o"
            command = [str(cc), *common, "-std=c99", "-c", str(source), "-o", str(obj)]
            result = subprocess.run(command, capture_output=True, text=True)
            if result.returncode:
                raise RuntimeError(str(source) + "\n" + result.stderr)
            return obj
        with ThreadPoolExecutor(max_workers=8) as pool:
            objects = list(pool.map(compile_one, enumerate(files)))
        out = root / "app/src/main/jniLibs" / abi / "libetkn_bluray.so"
        out.parent.mkdir(parents=True, exist_ok=True)
        command = [str(cxx), *common, "-shared", "-std=c++17", "-idirafter", str(ffbuild), "-idirafter", str(ffmpeg), str(root / "third_party/bluray/bluray_jni.cpp"), *map(str, objects), *[str(ffbuild / ("lib" + n) / ("lib" + n + ".a")) for n in ["avformat", "avcodec", "avutil"]], "-ldl", "-lm", "-lz", "-llog", "-Wl,--no-undefined", "-Wl,--exclude-libs,ALL", "-Wl,-z,max-page-size=16384", "-Wl,-soname,libetkn_bluray.so", "-o", str(out)]
        subprocess.run(command, check=True)
        subprocess.run([str(toolbin / ("llvm-strip" + suffix)), "--strip-unneeded", str(out)], check=True)
        print(abi, out.stat().st_size, hashlib.sha256(out.read_bytes()).hexdigest(), flush=True)

if __name__ == "__main__":
    main()
