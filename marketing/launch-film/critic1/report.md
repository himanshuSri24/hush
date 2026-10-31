# Critic 1: hush-launch-v2.mp4

Verdict: clean, on-brand and legible at desktop size, but it plays like 11 slides joined by one repeated blur-dissolve. Nearly every scene has a dead hold. There is no persistent phone actor, and two scenes clip off the right edge. It is below the Linear/Raycast bar.

## 1. Per scene
| Time | Content | Empty | Can lose | Problems, worst first |
|---|---|---|---|---|
| 0-2.5 | Notifs pile up, "So you hit DND" | 45% | 0.3s | Fine |
| 2.5-4.5 | "She's starred. Still missed." + 4 Mom misses | 60% | 0.5s | Headline about half the size of the others; eyebrow microscopic; static 3.0-4.2 |
| 4.5-5.0 | Black + "4 missed calls. One nap." | 97% | 0 | Grey text about 30px, roughly 6px on a phone, cannot be read |
| 5.0-6.5 | Logo + tagline | 80% | 0.5s | Tagline small |
| 6.5-9.5 | Allowed list fills | 40% | 1.0s | Empty APPS box until about 8.0; static 8.2-9.2 |
| 9.5-11.5 | "Tap. Nap." → ring 60 | 55% | 0.7s | Body copy LEFT of phone, headline RIGHT (split layout); static 10.8-11.4 |
| 11.5-14 | "Everything else waits." chips | 50% | 0.5s | Chips about 200px wide / 12px text; they fade in mid-air instead of entering the Held counter, so no cause→effect; timer stuck at 58, then jumps to 51 |
| 14-16.5 | Slack "prod is down" lands | 55% | 1.0s | Body fades in about 1s late, orphan "it."; static 15.0-16.2 |
| 16.5-19.5 | Mom WhatsApp full-screen | 40% | 1.0s | Static about 17.2-19.2; no audible ring |
| 19.5-22 | "Called twice in 15 min?" + 2 cards | 65% | 0.8s | No phone; cards clipped at the right edge; small cluster in a big void |
| 22-24.5 | "Back? See what waited." + summary | 45% | 0.8s | Summary, the payoff, is about 15px body; lower 40% of phone empty |
| 24.5-27 | 4 proof tiles | 40% | 0.8s | Tile 4 clipped right; bottom third dead; subtitles about 4px on a phone; static 25.2-26.6 |
| 27-30 | Logo + GitHub pill | 75% | 0.5s | CTA clear and singular (good); 2.4s frozen hold |

## 2. Defects (timestamps)
- **Clipping:**
  - Call cards run off x=1920 from 20.0 to 21.9.
  - Proof tile 4 is clipped from 24.6 to 27.0, with no right margin.
- **Glitch:** at 16.15-16.3, the Slack banner and a "Hush" banner overlap at the top of the phone.
- **Empty frames:**
  - 4.35-4.45: only a blurred phone exiting.
  - 6.4: only a faint moon.
  - 9.4: blank.
  - 19.65-19.8: blank paper, the worst instance.
  - 24.4: blank.
- **Transitions:** all 11 are the same blur-out, 2-4 blank frames, then a mask-rise headline. It reads as a template.
- **Continuity:**
  - The status bar shows 2:01 while the timer runs 60→51.
  - The cards say 2:20/2:24; the starred scene shows 2:31.
  - The phone switches sides from scene to scene (R, R, -, L, C, L, L, R, gone, R, gone).

## 3. Audio (measured)
- **Loudness:** -13.5 LUFS integrated, -1.2 dBTP, LRA 3.5 LU. After the drop the bed sits flat at -12 to -14 LUFS for 23s, with no arc.
- **Dead stop:** real, 4.5-5.0 at -35 to -46 dBFS, with the drop exactly at 5.00. Good.
- **Beat grid:** 120 BPM kicks land at x.2 and x.7s, but the scenes are cut at x.0 and x.5. Every cut except the drop is about 200ms (12 frames) off the beat.
- **SFX:** onsets exist at 1.7, 2.55, 3.6, 12.6, 20.6, 21.1 and 24.6. The Slack arrival and Mom's call have no distinct onset; Mom adds only about +1.5 LU.

## 4. Top changes by impact
1. **One persistent phone.** Use a single DOM phone tweened between scene poses (GSAP/FLIP: x, y, scale, rotation) instead of blurring it out and in. Keep it in 19.5-22, with the repeat call ringing on it and the cards beside it inside a 96px safe margin.
2. **Lock to the kick grid.** Shift the timeline +0.2s, or re-slice the music so kicks fall on x.0 and x.5. Land each event on a downbeat: notifs, Slack, Mom's ring, the 2nd call, the summary and the CTA.
3. **Kill the holds.** Trim the "can lose" seconds (to about 26s, or reinvest them). Where a beat breathes, keep something moving: a 2-3% camera push, a ticking timer, a live Held counter.
4. **Show the absorption.** Chips fly into the phone and the Held counter bumps on contact with a soft tick SFX. Make the chips 380px+ wide with 22px+ text.
5. **Make the rings audible.** Add a Slack knock at about 14.3 and a 2-pulse ringtone from 16.6, and duck the bed 4-6 dB under both. This also fixes the flat LRA.
6. **Fix clipping and the dead third.**
   - Right padding of at least 96px.
   - Proof tiles as a 2x2 grid or vertically centred, staggered on kicks.
7. **Readable at phone size.**
   - Nothing under 28px; key lines 36px+.
   - "4 missed calls. One nap." at 56px+ in white.
   - The 2.5-4.5 headline at standard size.
   - Push in to about 1.4x on the welcome-back summary.
8. **Vary the transitions and clean up.**
   - Use match cuts: DND toggle → moon logo, 1h chip → timer ring, summary card → proof tile.
   - Remove the blank frames at 6.4, 9.4 and 19.65-19.8.
   - Fix the "it." widow and the 16.2 double banner.
   - Advance or hide the status-bar clock.
   - Give the end card a slow pill glow or a 2% push.
