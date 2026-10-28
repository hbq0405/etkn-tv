"""Generate synthetic HEVC + five audio codecs + animated ASS; no user media."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("ffmpeg")
args = parser.parse_args()
output = Path(__file__).resolve().parents[1] / "app/src/androidTest/assets/player-codecs.mkv"
with tempfile.TemporaryDirectory(prefix="etkn-player-fixture-") as temporary:
    subtitles = Path(temporary) / "effect.ass"
    subtitles.write_text(r"""[Script Info]
ScriptType: v4.00+
PlayResX: 640
PlayResY: 360
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,sans-serif,30,&H0000FFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,24,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.00,0:00:12.00,Default,,0,0,0,,{\move(80,80,540,80)\t(0,5000,\frz360)}ASS animation
Dialogue: 0,0:00:00.00,0:00:12.00,Default,,0,0,0,,{\pos(320,280)\c&H00FF00&}Native subtitle rendering
""", encoding="utf-8")
    command = [args.ffmpeg, "-hide_banner", "-loglevel", "warning", "-y", "-f", "lavfi", "-i",
               "testsrc2=size=320x180:rate=24:duration=12", "-f", "lavfi", "-i",
               "sine=frequency=440:sample_rate=48000:duration=12", "-i", str(subtitles), "-map", "0:v"]
    for _ in range(5):
        command += ["-map", "1:a"]
    command += ["-map", "2:s", "-c:v", "libx265", "-preset", "ultrafast", "-x265-params", "pools=2:log-level=error", "-pix_fmt", "yuv420p", "-ac", "2"]
    for index, codec in enumerate(["aac", "ac3", "eac3", "dca", "truehd"]):
        command += [f"-c:a:{index}", codec, f"-metadata:s:a:{index}", f"title=raw-{codec}"]
    command += ["-strict", "-2", "-c:s", "ass", "-metadata:s:s:0", "title=raw-effect", "-t", "12", str(output)]
    subprocess.run(command, check=True)
print(output)
