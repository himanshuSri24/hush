# Hush: demo video

A 40-second walkthrough. Every phone frame is the **real app UI**: Robolectric renders it from Hush's own
Compose code on the JVM, while `DemoRecording` taps through the app at 30 fps of virtual time. This page adds
only the phone frame, the captions and the tap rings, which use the recorded tap positions.
During the recording, the notifications are injected into the app's store, and the video says so on screen.

```bash
cd ../.. && ./gradlew testDebugUnitTest -PhushDemo=1 --tests "*DemoRecording*"   # -> marketing/demo/frames/
cd marketing/demo
printf '{"app":%d,"alert":%d}' $(ls frames/app/*.png | wc -l) $(ls frames/alert/*.png | wc -l) > frames/meta.json
node capture.mjs --fps 30 && py -3.12 mix.py
ffmpeg -framerate 30 -i .frames/f%05d.png -i mix.wav -c:v libx264 -crf 18 -pix_fmt yuv420p \
  -af loudnorm=I=-16:TP=-1.5 -c:a aac -shortest out/hush-demo.mp4
```
