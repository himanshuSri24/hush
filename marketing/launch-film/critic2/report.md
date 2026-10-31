# Critic 2: verification of v3 (ONE MORE PASS)

The independent critic couldn't write this file itself, so this is a summary of its verdict on v3.

| Previous item | Status |
|---|---|
| Persistent phone | PARTLY: the phone stays on screen from 9.5 to 24.5s, but jumps in a single frame at the cuts |
| Kicks on x.0 / x.5 | FIXED: the three rings start at 14.92, 16.95 and 20.75, all on beats |
| Holds | PARTLY: every hold has camera motion, but the content is static at 17.2-19.2s and 25.4-26.8s |
| Notifications fly into Held | FIXED: Held counts up 9 → 16 → 20 → 23 |
| Rings audible | FIXED: +15 to +18 dB over the ducked music in the 1-3.3 kHz band |
| Clipping | FIXED |
| Readable at phone size | FIXED |
| Clock continuity | FIXED |

**Its three fixes, all applied in the final cut:**
1. **Phone pops at cuts.** The cause was the per-scene camera resetting at each cut. The camera is now one continuous move, screen swaps cross-fade, and the dot no longer sits over Mom's avatar.
2. **Gaps at 6.3s and 24.4s.** Removed, and the Slack banner leaves fast instead of fading see-through.
3. **Copy.**
   - The SIM call's button says "Answer" instead of "Open WhatsApp".
   - The summary says "23 held from 5 apps" and lists all five (9 + 7 + 4 + 2 + 1).
   - The Apps count is consistent.

**Final measurements:** -14.4 LUFS, true peak -1.3 dBFS, loudness range 3.0 LU. Near-frozen time was 0.0s on two runs of `frozen-time.sh`; one earlier run reported 2.2s.
