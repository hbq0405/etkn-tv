"""Generate the synthetic 20-second ISO used by DetailFlowTest.
Requires FFmpeg with libx264/AC3/TrueHD encoders and tsMuxer 2.7.0.
Only synthetic pictures, sine waves and PGS rectangles are included.
Pass an empty dedicated --work directory, then copy disc-tracks.iso to
app/src/androidTest/assets/ after verifying its tracks.
"""
from pathlib import Path
import argparse,subprocess,struct
parser=argparse.ArgumentParser()
parser.add_argument('--ffmpeg',required=True)
parser.add_argument('--tsmuxer',required=True)
parser.add_argument('--work',required=True)
args=parser.parse_args()
ffmpeg=str(Path(args.ffmpeg).resolve())
fixture=Path(args.work).resolve();fixture.mkdir(parents=True,exist_ok=True)
log=(fixture/'generate.log').open('w',encoding='utf-8')
def run(args):
    subprocess.run([str(x) for x in args],stdout=log,stderr=subprocess.STDOUT,check=True)
run([ffmpeg,'-y','-f','lavfi','-i','testsrc2=size=1280x720:rate=24','-t','20','-c:v','libx264','-preset','ultrafast','-crf','38','-g','24','-pix_fmt','yuv420p','-an',fixture/'video.h264'])
for name,freq in [('english',440),('mandarin',880)]:
    run([ffmpeg,'-y','-f','lavfi','-i',f'sine=frequency={freq}:sample_rate=48000','-t','20','-ac','2','-c:a','ac3','-b:a','192k',fixture/(name+'.ac3')])
run([ffmpeg,'-y','-f','lavfi','-i','sine=frequency=220:sample_rate=48000','-t','20','-ac','2','-c:a','truehd','-strict','-2',fixture/'hd.thd'])
sup=bytearray()
def segment(pts,kind,data):
    sup.extend(b'PG'+struct.pack('>IIBH',pts,0,kind,len(data))+data)
for n,second in enumerate([1,5,9,13,17]):
    pts=second*90000
    segment(pts,0x16,struct.pack('>HHBHBBBBHBBHH',1280,720,0x10,n*2,0x80,0,0,1,0,0,0,540,640))
    segment(pts,0x17,struct.pack('>BBHHHH',1,0,540,640,128,32))
    segment(pts,0x14,bytes([0,0,0,0,128,128,0,1,235,128,128,255]))
    rle=bytes([0,0xc0,128,1,0,0])*32
    segment(pts,0x15,bytes([0,0,0,0xc0])+(len(rle)+4).to_bytes(3,'big')+struct.pack('>HH',128,32)+rle)
    segment(pts,0x80,b'')
    segment(pts+180000,0x16,struct.pack('>HHBHBBBB',1280,720,0x10,n*2+1,0,0,0,0))
    segment(pts+180000,0x80,b'')
(fixture/'subtitle.sup').write_bytes(sup)
meta='MUXOPT --blu-ray --mplsOffset=801 --m2tsOffset=84 --constant-iso-hdr\n'
meta+=f'V_MPEG4/ISO/AVC, "{fixture / "video.h264"}", fps=24, insertSEI, contSPS\n'
meta+=f'A_MLP, "{fixture / "hd.thd"}", lang=eng\n'
meta+=f'A_AC3, "{fixture / "english.ac3"}", lang=eng\n'
meta+=f'A_AC3, "{fixture / "mandarin.ac3"}", lang=zho\n'
meta+=f'S_HDMV/PGS, "{fixture / "subtitle.sup"}", lang=zho\n'
(fixture/'disc.meta').write_text(meta,encoding='utf-8')
run([Path(args.tsmuxer).resolve(),fixture/'disc.meta',fixture/'disc-tracks.iso'])
log.close()
print('Synthetic ISO bytes:',(fixture/'disc-tracks.iso').stat().st_size)
