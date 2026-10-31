# Hush: launch film

A 30-second launch film for LinkedIn, X and the GitHub release: 1920×1080, 60fps.
Picture and sound are both rendered from code.

- **`film.html`**: the picture. `render(t)` is a pure function of time.
- **Persistent actors:** the phone, and an amber dot (the thing that gets through).
- **Music:** Mixkit "House 02", time-stretched to 120 BPM so cuts land on the beat. The music stops dead at 4.5s, when the noise stops, and comes back on the Hush logo.
- **`mix.py`**: the music edit, sound effects on real on-screen actions only, a synthesized ring, and ducking under each ring. It writes `mix.wav` and a music-only `music_only.wav`.
- **`capture.mjs`**: steps headless Chrome frame by frame (zero npm dependencies).
- **`critic1/`, `critic2/`**: the two independent review rounds and what changed after each.

## Rebuild

```bash
mkdir -p music && curl -L https://assets.mixkit.co/music/744/744.mp3 -o music/744.mp3
# fonts.css (Inter, base64) is not committed. Any @font-face for "Inter" works.
# Sound effects: point SFX_DIR at a folder with manifest.json ({name: {file}}) and the files
# (click, click-soft, pop, chime, notification, whoosh-short, whoosh-cinematic, impact-bass-1/2, sparkle).
node capture.mjs --fps 60
py -3.12 mix.py                          # numpy + scipy + ffmpeg
ffmpeg -framerate 60 -i .frames/f%05d.png -i mix.wav -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p \
  -af "volume=2.4dB,alimiter=limit=0.80:attack=1:release=80:level=0,aresample=48000" \
  -c:a aac -b:a 256k -shortest -movflags +faststart out/hush-launch-film.mp4
```

To check single frames, run `node capture.mjs --still 10.5,13.2`.
