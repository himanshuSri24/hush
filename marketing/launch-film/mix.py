"""
Audio for the Hush launch film (business-motion-film rules):
- library music (Mixkit "House 02", Mixkit Stock Music Free License) edited to picture:
  time-stretched 123 -> 120 BPM so cuts land on x.0 / x.5, a filtered intro,
  a 0.5 s dead stop at 4.5 s, the groove arriving on the logo at 5.0 s (the noise stops, then Hush), fade on the end card.
- clean bundled SFX (HyperFrames library), high-passed 180 Hz / low-passed 10 kHz,
  only on real actions and real transitions, levels capped against the local music.
Writes mix.wav (full) and music_only.wav (fallback).   py -3.12 mix.py
"""
import os, json, subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

HERE = os.path.dirname(os.path.abspath(__file__))
SR = 48000
DUR = 30.0
N = int(SR * DUR)
SFX_DIR = os.environ.get("SFX_DIR", os.path.join(HERE, "sfx"))  # folder with manifest.json + effect files


def load(path, start=0.0, dur=None, extra=""):
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
    if dur:
        cmd += ["-t", str(dur)]
    if extra:
        cmd += ["-af", extra]
    cmd += ["-f", "f32le", "-ac", "2", "-ar", str(SR), "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").reshape(-1, 2).T.copy()


def filt(x, kind, fc, order=4):
    return sosfilt(butter(order, fc, kind, fs=SR, output="sos"), x, axis=-1)


def ramp(n, up=True):
    r = np.linspace(0, 1, n)
    return r if up else r[::-1]


# ------------------------------------------------------------------ music
SRC_BEAT = 18.575                      # a beat in the source; becomes t = 0
src_dur = DUR * 123.046875 / 120 + 1
m = load(os.path.join(HERE, "music", "744.mp3"), SRC_BEAT, src_dur, "atempo=0.975238")
m = m[:, :N] if m.shape[1] >= N else np.pad(m, ((0, 0), (0, N - m.shape[1])))

i45, i50 = int(4.5 * SR), int(5.0 * SR)
# intro 0-4.5: music heard "through the wall" (low-passed), opening up a little as the wall builds
intro = filt(m[:, :i45], "low", 700)
intro_open = filt(m[:, :i45], "low", 2400)
w = np.clip((np.arange(i45) / SR - 2.0) / 2.5, 0, 1)
m[:, :i45] = intro * (1 - w) + intro_open * w
m[:, :i45] *= 0.75
# 2 s high-pass build before the stop so the drop lands (thin -> full)
b0 = int(2.5 * SR)
hp_build = filt(m[:, b0:i45], "high", 180)
wb = np.linspace(0, 1, i45 - b0)
m[:, b0:i45] = m[:, b0:i45] * (1 - wb) + hp_build * wb
# dead stop 4.5-5.0 with clean edges
m[:, i45 - 240:i45] *= ramp(240, False)
m[:, i45:i50] = 0
m[:, i50:i50 + 240] *= ramp(240, True)
# first 5 ms fade-in, end: fade from 28.3 to 30
m[:, :240] *= ramp(240)
f0 = int(28.3 * SR)
m[:, f0:] *= np.linspace(1, 0, N - f0) ** 1.6

music = m * 0.42

# ------------------------------------------------------------------ sfx
manifest = json.load(open(os.path.join(SFX_DIR, "manifest.json")))
cache = {}


def sfx(name):
    if name not in cache:
        x = load(os.path.join(SFX_DIR, manifest[name]["file"]))
        x = filt(filt(x, "high", 180), "low", 10000)
        x[:, :96] *= ramp(96)
        x[:, -480:] *= ramp(480, False)
        cache[name] = x / (np.max(np.abs(x)) + 1e-9)
    return cache[name]


EVENTS = [  # time, name, gain (relative peak) - only on real actions / real transitions
    *[(0.02 + k * 0.24, "notification", .26) for k in range(5)],        # the noise piles up
    (1.66, "click", .35), (1.72, "whoosh-short", .25),                   # DND on, cards dim
    *[(2.62 + k * 0.3, "click-soft", .16) for k in range(3)],            # missed, missed, missed
    (3.55, "impact-bass-1", .42),                                        # the 4th missed call
    (4.05, "whoosh-short", .28),                                         # phone falls away
    (5.0, "impact-bass-2", .65), (5.12, "sparkle", .16),                 # Hush
    (6.15, "whoosh-short", .25), (7.0, "pop", .26), (7.5, "pop", .24),   # people added
    *[(7.9 + k * 0.18, "click-soft", .12) for k in range(3)],            # apps added
    (8.95, "click", .34), (9.2, "whoosh-short", .25),
    (10.15, "click", .34), (10.6, "click", .4), (10.65, "chime", .3),    # 1h, hush on
    (11.2, "whoosh-short", .25),
    *[(12.37 + k * 0.32, "click-soft", .10) for k in range(5)],          # noise absorbed, silently
    (13.75, "whoosh-short", .25), (14.9, "impact-bass-1", .5), (14.92, "notification", .42),  # Slack rings through
    (16.25, "whoosh-cinematic", .3),                # Mom's call rings
    (19.6, "click-soft", .2), (20.75, "impact-bass-1", .45),
    (21.75, "whoosh-short", .25), (22.6, "click", .4), (22.8, "pop", .3),
    (24.3, "whoosh-short", .25), *[(24.5 + k * 0.1, "click-soft", .12) for k in range(4)],
    (26.7, "whoosh-short", .25), (27.0, "impact-bass-2", .5), (27.1, "chime", .3),
]
def remap(t):
    return t


EVENTS = sorted((remap(t), n, g) for t, n, g in EVENTS)
fx = np.zeros((2, N))
last = -9
for t, name, g in EVENTS:
    x = sfx(name)
    if name == "typing":
        x = x[:, : int(0.6 * SR)]
        x[:, -2400:] *= ramp(2400, False)
    if t - last < 0.15:
        g *= 0.6                      # soften clusters
    last = t
    i0 = int(t * SR)
    n = min(x.shape[1], N - i0)
    # cap: no effect peaks more than ~6 dB over the local music peak (8 dB for the signature hits)
    loc = np.max(np.abs(music[:, max(0, i0 - SR // 4):i0 + SR // 4])) + 1e-4
    cap = loc * (3.2 if "impact" in name else 2.6)
    if name == "impact-bass-1" and abs(t - 14.9) < 0.01:
        cap = 1.0                     # the hero breakthrough: never capped by a music trough
    if t >= 4.5 and t < 5.0:
        cap = 0.5                     # the dead stop has no music to measure against
    fx[:, i0:i0 + n] += x[:, :n] * min(g, cap)

# synthesized ringtone under the three rings, music ducked beneath
def ring(t0, t1):
    out = np.zeros((2, N)); tt = np.arange(int(0.16 * SR)) / SR
    notes = [1318.5, 1568.0, 1318.5, 1046.5]
    k = 0; t = t0
    while t < t1:
        f = notes[k % 4]
        tone = (np.sin(2 * np.pi * f * tt) + .35 * np.sin(2 * np.pi * 2 * f * tt)) * np.exp(-tt * 18)
        i0 = int(t * SR); n = min(len(tone), N - i0)
        out[:, i0:i0 + n] += tone[:n]
        k += 1; t += 0.16 if k % 4 else 0.62
    return out * 0.16


rt = ring(14.92, 15.9) + ring(16.95, 19.4) + ring(20.75, 21.9)
duck = np.ones(N)
for a, b in [(14.85, 15.95), (16.9, 19.45), (20.7, 21.95)]:
    duck[int(a * SR):int(b * SR)] = 0.55
duck = np.convolve(duck, np.ones(2400) / 2400, mode="same")
music = music * duck
mix = music + fx + rt
peak = np.max(np.abs(mix))
mix *= 0.89 / peak
music_only = music * (0.89 / peak)


def write(path, x):
    pcm = (np.clip(x, -1, 1).T * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())


write(os.path.join(HERE, "mix.wav"), mix)
write(os.path.join(HERE, "music_only.wav"), music_only)
print("mix.wav + music_only.wav", DUR, "s")
