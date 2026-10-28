"""Build the pinned FFmpeg decoders and ETKN JNI for all four Android ABIs.

Requires Python 3.12+, NDK r27c, POSIX bash/make (Git Bash + MSYS make on Windows).
No network/format libraries are built; ExoPlayer continues to handle media transport.
"""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import shlex
import shutil
import subprocess
import tarfile
import urllib.request

parser=argparse.ArgumentParser()
parser.add_argument('--ndk',type=Path,required=True)
parser.add_argument('--work',type=Path,required=True)
parser.add_argument('--bash',default='bash')
parser.add_argument('--make',default='make')
parser.add_argument('--abis',nargs='+',default=['x86_64','arm64-v8a','armeabi-v7a','x86'])
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
args.work.mkdir(parents=True,exist_ok=True)
archive=args.work/'ffmpeg-6.0.tar.xz'
if not archive.exists():urllib.request.urlretrieve('https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz',archive)
assert hashlib.sha256(archive.read_bytes()).hexdigest()=='57be87c22d9b49c112b6d24bc67d42508660e6b718b3db89c44e47e289137082'
source=args.work/'ffmpeg-6.0'
if not source.exists():
    with tarfile.open(archive) as content:content.extractall(args.work,filter='data')
host={'Windows':'windows-x86_64','Linux':'linux-x86_64','Darwin':'darwin-x86_64'}[platform.system()]
bin=args.ndk/'toolchains/llvm/prebuilt'/host/'bin'
suffix='.exe' if platform.system()=='Windows' else ''
cc=(bin/('clang'+suffix)).as_posix()
targets={'x86_64':('x86_64','x86_64-linux-android24'),'arm64-v8a':('aarch64','aarch64-linux-android24'),
         'armeabi-v7a':('arm','armv7a-linux-androideabi24'),'x86':('x86','i686-linux-android24')}
decoders='vorbis,opus,flac,alac,pcm_mulaw,pcm_alaw,mp3,amrnb,amrwb,aac,ac3,eac3,dca,mlp,truehd,h264,hevc,mpeg2video,mpegvideo,vp8,vp9,vc1,wmv3'
for abi in args.abis:
    arch,target=targets[abi]
    # FFmpeg 6's 32-bit x86 inline MMX emits absolute relocations in shared objects.
    build=args.work/('x86-noasm' if abi=='x86' else abi)
    build.mkdir(exist_ok=True)
    options=[str(source/'configure').replace('\\','/'),'--target-os=android','--enable-cross-compile',f'--arch={arch}',
             f'--cc={cc} --target={target}',f'--cxx={(bin/("clang++"+suffix)).as_posix()} --target={target}',
             *[f'--{tool}={(bin/("llvm-"+tool+suffix)).as_posix()}' for tool in ['ar','nm','ranlib','strip']],
             '--disable-programs','--disable-doc','--disable-debug','--disable-autodetect','--disable-everything',
             '--disable-avformat','--disable-avfilter','--disable-avdevice','--disable-postproc',
             '--disable-shared','--enable-static','--enable-pic','--enable-pthreads','--enable-swscale','--enable-swresample',
             '--disable-x86asm','--extra-cflags=-O3 -fPIC','--extra-ldflags=-Wl,-z,max-page-size=16384',
             '--enable-decoder='+decoders]
    if abi=='x86':options.append('--disable-asm')
    if not (build/'ffbuild/config.mak').exists():
        with (build/'configure.log').open('w',encoding='utf-8') as log:
            subprocess.run([args.bash,'-c',shlex.join(options)],cwd=build,stdout=log,stderr=subprocess.STDOUT,check=True)
    print('Compiling',abi,flush=True)
    with (build/'build.log').open('w',encoding='utf-8') as log:
        subprocess.run([args.make,'-j',str(min(8,os.cpu_count() or 4))],cwd=build,stdout=log,stderr=subprocess.STDOUT,check=True)
    out=root/'app/src/main/jniLibs'/abi/'libetkn_media3ext.so'
    libs=[build/name/('lib'+name[3:]+'.a') for name in ['libavcodec','libswresample','libswscale','libavutil']]
    command=[str(bin/('clang++'+suffix)),f'--target={target}','-shared','-fPIC','-O2','-std=c++17',
             '-Wno-deprecated-declarations','-idirafter',str(build),'-idirafter',str(source),
             *map(str,sorted((root/'third_party/nextlib_compat').glob('*.cpp'))),*map(str,libs),
             '-llog','-landroid','-lm','-lz','-Wl,--no-undefined','-Wl,--exclude-libs,ALL','-Wl,-z,max-page-size=16384',
             '-Wl,-soname,libetkn_media3ext.so','-o',str(out)]
    subprocess.run(command,check=True)
    subprocess.run([str(bin/('llvm-strip'+suffix)),'--strip-unneeded',str(out)],check=True)
    print(abi,hashlib.sha256(out.read_bytes()).hexdigest(),out.stat().st_size,flush=True)
