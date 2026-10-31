"""Demo soundtrack: a quiet music bed (Mixkit "House 02", Mixkit Stock Music Free License),
soft clicks on every recorded tap, a chime on each breakthrough, and Hush's ring under the
full-screen call. Event times come from frames/app/marks.json, so audio follows the recording.
    py -3.12 mix.py   ->  mix.wav
"""
import json, os, subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

HERE = os.path.dirname(os.path.abspath(__file__))
SR = 48000
SRC = 30
SFX_DIR = os.environ.get("SFX_DIR", os.path.join(HERE, "sfx"))  # folder with manifest.json + effect files
marks = json.load(open(os.path.join(HERE, "frames/app/marks.json")))
meta = json.load(open(os.path.join(HERE, "frames/meta.json")))
split = [m for m in marks if m["k"] == "through"][-1]["f"] + 18
ALERT = meta["alert"]
TOTAL = meta["app"] + ALERT
DUR = TOTAL / SRC + 3
N = int(DUR * SR)


def out_t(f):  # app frame -> output seconds (alert clip is spliced in at `split`)
    return (f if f < split else f + ALERT) / SRC


def load(path, start=0.0, dur=None):
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
    if dur:
        cmd += ["-t", str(dur)]
    cmd += ["-f", "f32le", "-ac", "2", "-ar", str(SR), "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").reshape(-1, 2).T.copy()


def filt(x, kind, fc):
    return sosfilt(butter(4, fc, kind, fs=SR, output="sos"), x, axis=-1)


music = load(os.path.join(HERE, "../launch-film/music/744.mp3"), 30.0, DUR + 1)[:, :N]
music = np.pad(music, ((0, 0), (0, max(0, N - music.shape[1]))))
music = filt(music, "low", 5000) * 0.30
music[:, :SR] *= np.linspace(0, 1, SR)
f0 = int((DUR - 2.5) * SR)
music[:, f0:] *= np.linspace(1, 0, N - f0) ** 1.5

manifest = json.load(open(os.path.join(SFX_DIR, "manifest.json")))


def sfx(name):
    x = load(os.path.join(SFX_DIR, manifest[name]["file"]))
    x = filt(filt(x, "high", 180), "low", 10000)
    return x / (np.max(np.abs(x)) + 1e-9)


fx = np.zeros((2, N))


def put(t, x, g):
    i0 = int(t * SR); n = min(x.shape[1], N - i0)
    if n > 0:
        fx[:, i0:i0 + n] += x[:, :n] * g


click, chime = sfx("click-soft"), sfx("chime")
for m in marks:
    if m["k"] == "tap":
        put(out_t(m["f"] + 5), click, .22)       # the click lands when the tap does
    if m["k"] == "through" and m["f"] < split - 18:
        put(out_t(m["f"]), chime, .22)

# Hush's ring under the full-screen call
tt = np.arange(int(0.16 * SR)) / SR
t, k, a0 = split / SRC, 0, split / SRC
while t < a0 + ALERT / SRC - .2:
    f = [1318.5, 1568.0, 1318.5, 1046.5][k % 4]
    tone = (np.sin(2 * np.pi * f * tt) + .35 * np.sin(2 * np.pi * 2 * f * tt)) * np.exp(-tt * 18)
    put(t, np.vstack([tone, tone]), .12)
    k += 1; t += 0.16 if k % 4 else 0.62

mix = music + fx
mix *= 0.89 / np.max(np.abs(mix))
pcm = (np.clip(mix, -1, 1).T * 32767).astype("<i2")
with wave.open(os.path.join(HERE, "mix.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print("mix.wav", round(DUR, 2), "s")
