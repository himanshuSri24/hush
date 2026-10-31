/**
 * Frame-exact capture of launch.html (1920x1080).
 *
 * Drives headless Chrome over the DevTools Protocol with zero npm
 * dependencies (Node's built-in WebSocket + http). For each frame it calls
 * window.seekTo(t) and screenshots — so the output is a deterministic
 * function of the timeline, not a real-time screen recording. Nothing
 * drops frames, and re-running produces identical bytes.
 *
 *   node capture.mjs                  # every frame -> .frames/
 *   node capture.mjs --still 7.6      # one frame -> stills/t7.60.png
 *   node capture.mjs --still 2,5,9    # several stills at once
 *   node capture.mjs --from 11 --to 16
 *   node capture.mjs --scale 2        # supersample (slower, crisper)
 */
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync, readdirSync } from "node:fs";
import { join, extname } from "node:path";

const DIR = import.meta.dirname;
const args = process.argv.slice(2);
const flag = (n, d) => { const i = args.indexOf("--" + n); return i < 0 ? d : args[i + 1]; };
const has  = (n) => args.includes("--" + n);

const SCALE = parseFloat(flag("scale", "1"));
const STILL = flag("still", null);
const FROM  = parseFloat(flag("from", "0"));
const TO    = flag("to", null) === null ? null : parseFloat(flag("to"));
const PAGE  = flag("page", "demo.html");
const W     = parseInt(flag("w", "1920")), H = parseInt(flag("h", "1080"));
const OUT   = join(DIR, ".frames");
const STILLS= join(DIR, "stills");

/* ---------- locate a Chromium ---------- */
const CANDIDATES = [
  ...(existsSync(join(process.env.LOCALAPPDATA || "", "ms-playwright"))
      ? readdirSync(join(process.env.LOCALAPPDATA, "ms-playwright"))
          .filter(d => d.startsWith("chromium-"))
          .map(d => join(process.env.LOCALAPPDATA, "ms-playwright", d, "chrome-win64", "chrome.exe"))
      : []),
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
  join(process.env.LOCALAPPDATA || "", "Google/Chrome/Application/chrome.exe"),
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "/usr/bin/google-chrome", "/usr/bin/chromium",
];
const CHROME = (flag("chrome", null)) || CANDIDATES.find(p => p && existsSync(p));
if (!CHROME) { console.error("No Chrome/Chromium found. Pass --chrome <path>."); process.exit(1); }

/* ---------- serve the reel over http (file:// + fonts is fussy) ---------- */
const MIME = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".png": "image/png", ".json": "application/json" };
const server = createServer((req, res) => {
  const p = join(DIR, decodeURIComponent(req.url.split("?")[0]).replace(/^\/+/, "") || PAGE);
  let body;
  try { body = readFileSync(p); }
  catch { res.writeHead(404); return res.end("not found"); }
  res.writeHead(200, { "content-type": MIME[extname(p)] || "application/octet-stream" });
  res.end(body);
});
await new Promise(r => server.listen(0, "127.0.0.1", r));
const PORT = server.address().port;

/* ---------- launch chrome ---------- */
const profile = join(DIR, ".chrome-profile");
rmSync(profile, { recursive: true, force: true });
const chrome = spawn(CHROME, [
  "--headless=new", "--remote-debugging-port=0", "--remote-allow-origins=*",
  `--user-data-dir=${profile}`,
  "--no-first-run", "--no-default-browser-check", "--disable-extensions",
  "--hide-scrollbars", "--mute-audio", "--disable-lcd-text",
  "--font-render-hinting=none", "--force-color-profile=srgb",
  "--disable-background-timer-throttling", "--disable-renderer-backgrounding",
  "about:blank",
], { stdio: ["ignore", "pipe", "pipe"] });

const wsUrl = await new Promise((res, rej) => {
  let buf = "";
  const to = setTimeout(() => rej(new Error("Chrome did not expose a DevTools port")), 25000);
  chrome.stderr.on("data", d => {
    buf += d;
    const m = buf.match(/ws:\/\/[^\s]+/);
    if (m) { clearTimeout(to); res(m[0]); }
  });
  chrome.on("exit", c => { clearTimeout(to); rej(new Error("Chrome exited: " + c)); });
});

/* ---------- minimal CDP client ---------- */
const ws = new WebSocket(wsUrl);
await new Promise((r, j) => { ws.onopen = r; ws.onerror = e => j(new Error("CDP socket failed")); });
let msgId = 0;
const pending = new Map();
const waiters = [];
ws.onmessage = ev => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) {
    const { res, rej } = pending.get(m.id); pending.delete(m.id);
    m.error ? rej(new Error(m.error.message)) : res(m.result);
  } else if (m.method) {
    for (let i = waiters.length - 1; i >= 0; i--)
      if (waiters[i].method === m.method) { waiters[i].res(m.params); waiters.splice(i, 1); }
  }
};
const send = (method, params = {}, sessionId) => new Promise((res, rej) => {
  const id = ++msgId; pending.set(id, { res, rej });
  ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
});
const once = (method, ms = 20000) => new Promise((res, rej) => {
  waiters.push({ method, res });
  setTimeout(() => rej(new Error("timeout waiting for " + method)), ms);
});

/* ---------- open the page ---------- */
const { targetId } = await send("Target.createTarget", { url: "about:blank" });
const { sessionId } = await send("Target.attachToTarget", { targetId, flatten: true });
await send("Page.enable", {}, sessionId);
await send("Runtime.enable", {}, sessionId);
await send("Emulation.setDeviceMetricsOverride",
  { width: W, height: H, deviceScaleFactor: SCALE, mobile: false }, sessionId);

const loaded = once("Page.loadEventFired");
await send("Page.navigate", { url: `http://127.0.0.1:${PORT}/${PAGE}` }, sessionId);
await loaded;

const evalJs = async (expr) => {
  const r = await send("Runtime.evaluate",
    { expression: expr, returnByValue: true, awaitPromise: true }, sessionId);
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || "eval failed");
  return r.result.value;
};

// Wait until fonts are resolved and the first paint has settled.
for (let i = 0; i < 300; i++) {
  if (await evalJs("window.__ready === true")) break;
  await new Promise(r => setTimeout(r, 50));
}
if (!await evalJs("window.__ready === true")) throw new Error("reel.html never became ready");

const DURATION = await evalJs("window.DURATION");
// The capture rate must equal the rate the frames are later encoded at,
// or the film plays back in slow motion / fast forward.
const FPS = flag("fps", null) ? parseFloat(flag("fps")) : await evalJs("window.FPS");

const shoot = async (path) => {
  const { data } = await send("Page.captureScreenshot",
    { format: "png", captureBeyondViewport: false, fromSurface: true }, sessionId);
  writeFileSync(path, Buffer.from(data, "base64"));
};

/* ---------- stills mode: quick art-direction checks ---------- */
if (STILL !== null) {
  mkdirSync(STILLS, { recursive: true });
  for (const raw of String(STILL).split(",")) {
    const t = parseFloat(raw);
    await evalJs(`seekTo(${t})`);
    const p = join(STILLS, `t${t.toFixed(2)}.png`);
    await shoot(p);
    console.log("still", t.toFixed(2), "->", p);
  }
} else {
  /* ---------- full render ---------- */
  rmSync(OUT, { recursive: true, force: true });
  mkdirSync(OUT, { recursive: true });
  const end = TO === null ? DURATION : TO;
  const first = Math.round(FROM * FPS), last = Math.round(end * FPS);
  const total = last - first;
  console.log(`rendering ${total} frames @ ${FPS}fps  (${FROM}s -> ${end}s)  scale ${SCALE}x`);
  const t0 = Date.now();
  for (let i = first; i < last; i++) {
    await evalJs(`seekTo(${(i / FPS).toFixed(6)})`);
    await shoot(join(OUT, `f${String(i - first).padStart(5, "0")}.png`));
    if ((i - first) % 60 === 0) {
      const done = i - first + 1, pct = ((done / total) * 100).toFixed(0);
      const eta = ((Date.now() - t0) / done) * (total - done) / 1000;
      process.stdout.write(`\r  ${pct}%  frame ${done}/${total}  eta ${eta.toFixed(0)}s   `);
    }
  }
  console.log(`\n  done in ${((Date.now() - t0) / 1000).toFixed(1)}s -> ${OUT}`);
}

ws.close(); chrome.kill(); server.close();
// Chrome releases the profile lock asynchronously; failing to delete a temp
// dir must never fail a render that already succeeded.
try { rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }); } catch {}
process.exit(0);
