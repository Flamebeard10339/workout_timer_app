# Circuit Clock

An interval timer built around **arbitrary nested loops**. Sections have a name, a
duration and a colour; loops have a name, a repeat count and a colour, and can contain
sections *or other loops*, to any depth.

**Fully offline.** One HTML file, no build step, no dependencies, no network access of any
kind — not at first run, not ever. Fonts are embedded as data URIs; workouts live in
`localStorage`. Nothing is uploaded, and there is nothing to sign in to.

## Files

| File | What it is |
| --- | --- |
| `workout-timer.html` | The whole app. Open it directly from disk, or serve it statically. |
| `licenses/OFL-1.1.txt` | SIL Open Font License 1.1, covering the three embedded typefaces. |
| `.claude/launch.json` | Serves this folder on `http://localhost:4173` via `node`, if you want to reach it from a phone on the same LAN. |

## The offline guarantee

The document carries a Content-Security-Policy that makes network access impossible rather
than merely absent:

```
default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline';
font-src data:; img-src data:; connect-src 'none'; object-src 'none';
frame-src 'none'; base-uri 'none'; form-action 'none'
```

`connect-src 'none'` blocks `fetch`, `XMLHttpRequest` and WebSockets outright; `default-src
'none'` blocks every other fetch class the page didn't explicitly allow, and the only two it
allows are `data:` URIs. So even a future edit that accidentally reached for the network
would fail loudly instead of quietly phoning home.

To confirm for yourself: open the file, then DevTools → Network, and use the app. The list
stays empty.

## Data model

```js
Workout = { id, name, updatedAt, nodes: Node[] }
Node = { id, type:'section', name, color, duration }          // duration in seconds
     | { id, type:'loop',    name, color, reps, children: Node[] }
```

`compile()` expands the tree into a flat schedule — one entry per interval — where each
entry carries a `trail`: the chain of enclosing loops (with `iter`/`total`) ending in the
section itself. The runner's bottom bars are that trail, one row per level.

Everything is stored under the single `localStorage` key `circuitclock.v1`, which also
holds the volume and voice-announcement preferences. Because storage is per-origin,
a copy opened from `file://` and a copy served over `http://localhost` keep separate
libraries — that is browser policy, not an app choice.

## Audio and voice

One `AudioContext`, unlocked by the Start button (browsers require a user gesture). A master
`GainNode` carries the in-app volume, which multiplies against system volume rather than
replacing it. Cues: a soft 660 Hz blip at 2 s and 1 s remaining, a louder 990 Hz + 1485 Hz
tone at 0, and a three-note figure at the end of the workout. All synthesised — no audio
files.

Section names are announced through `speechSynthesis` as each starts. When a loop rolls over
to a new iteration the announcement is prefixed with it — "Round 3. Push-ups." On Android
this uses whatever TTS engine the system provides; pick an offline voice in
*Settings → System → Languages & input → Text-to-speech* if you want it to work in airplane
mode. The timer itself is unaffected either way.

## Timing

The engine tracks an absolute `endAt` timestamp rather than accumulating deltas, so it does
not drift, and it catches up silently (no beeps) if the tab was suspended for more than a
second. A screen Wake Lock is held while a workout runs and re-acquired when the page
becomes visible again.

## Keyboard

- Editor: focus a row's grip handle, then **↑ ↓** to move it, **← →** to outdent/indent.
- Runner: **Space** pause/resume, **← →** previous/next section, **Esc** to exit.

## Embedded fonts

All three are under the SIL Open Font License 1.1 (see `licenses/OFL-1.1.txt`), latin
subsets only:

- **Archivo** — © The Archivo Project Authors — display, headings, countdown digits
- **Barlow** — © The Barlow Project Authors — body text
- **JetBrains Mono** — © JetBrains s.r.o. — durations, counters, labels

To re-embed or change them, download the `woff2` files and base64 them into the `@font-face`
rules at the top of the document. The app is a single file on purpose; there is no asset
pipeline to run.

## Android

`android/` is a minimal WebView wrapper around the same HTML file — buildable by you for
sideloading, and buildable by F-Droid from source.

**It holds no `INTERNET` permission.** That is the point: the offline guarantee stops being
a promise the page makes about itself and becomes something the OS enforces. The manifest
declares no permissions at all — keeping the screen awake uses `FLAG_KEEP_SCREEN_ON` (no
permission, unlike `WAKE_LOCK`) and speech goes through the system TTS engine.

### Building

Requires JDK 17 and the Android SDK (Android Studio provides both).

```bash
cd android
gradle wrapper          # once — the wrapper jar is not checked in
./gradlew assembleDebug
```

The APK lands in `android/app/build/outputs/apk/debug/`. For a release build you will need
to add your own signing config; F-Droid signs with its own key and does not need one.

Opening `android/` in Android Studio also works and generates the wrapper for you.

### Two details worth knowing

**Speech is bridged to native TTS.** Android WebView ships no Web Speech API — `window.
speechSynthesis` simply does not exist there, so the spoken section names would have gone
silent in an APK. `MainActivity` exposes a small `AndroidHost` object with `speak`,
`stopSpeaking` and `keepAwake`; the page uses it when present and falls back to web APIs in
a normal browser, so one HTML file serves both. Exposing a JS interface is only risky with
untrusted content — this content ships inside the APK, is served from an origin the app
itself answers, and all navigation is blocked.

**The page is served over https, not `file://`.** `WebViewAssetLoader` answers
`https://appassets.androidplatform.net/assets/...` from the APK's assets. That gives the
page a real origin, which makes `localStorage` durable, and makes it a secure context. No
network is involved — the loader intercepts before anything reaches a socket, which is why
this works without the `INTERNET` permission.

### Before you publish it anywhere

- `applicationId` is `org.circuitclock.timer`. F-Droid requires a globally unique id under a
  namespace you control — change it to something like `io.github.<yourname>.circuitclock`
  (in `android/app/build.gradle.kts`, `namespace` and `applicationId`, and the Kotlin
  package path).
- `LICENSE` is MIT with a placeholder copyright line. Put your name on it, or swap the
  licence — F-Droid needs a free one, and MIT is only a default I picked.
- The theme is dark-only in the APK by design; the HTML still follows the system theme in a
  browser.
- Screenshots for the F-Droid listing go in
  `android/fastlane/metadata/android/en-US/images/phoneScreenshots/`. They are optional.

`android/app/src/main/assets/workout-timer.html` is a copy of the file at the repo root. A
Gradle task re-copies it on every build, so edit the root file and the APK follows.
