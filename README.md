# Circuit Clock

An interval timer built around **arbitrary nested loops**. Sections have a name, a duration
and a colour; loops have a name, a repeat count and a colour, and can contain sections *or
other loops*, to any depth. Drag a row sideways in the editor to change what it sits inside.

While a workout runs, the countdown fills the screen and the full nesting path is stacked
along the bottom in each level's own colour — so a glance tells you which round of which
block you are in.

**Fully offline.** No network access of any kind, no accounts, no telemetry. Everything is
stored on the device.

## Layout

```
workout-timer.html    the entire app — open it directly in any browser
app/                  Android WebView wrapper (same HTML, bundled as an asset)
fastlane/             F-Droid / store listing text
fdroid/               the F-Droid build recipe, mirrored from fdroiddata
PUBLISHING.md         how this gets into F-Droid
```

`app/src/main/assets/workout-timer.html` is a copy of the root file. A Gradle task re-copies
it on every build, so edit the root file and the APK follows.

## The offline guarantee

Two independent mechanisms, so neither has to be trusted alone.

**In the page**, a Content-Security-Policy makes network access impossible rather than
merely absent:

```
default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline';
font-src data:; img-src data:; connect-src 'none'; object-src 'none';
frame-src 'none'; base-uri 'none'; form-action 'none'
```

`connect-src 'none'` blocks `fetch`, `XMLHttpRequest` and WebSockets; `default-src 'none'`
blocks every other fetch class. So even a future edit that accidentally reached for the
network would fail loudly instead of quietly phoning home.

**In the APK**, the manifest declares no permissions at all. Without
`android.permission.INTERNET` the WebView cannot open a socket, so the guarantee is enforced
by the OS rather than asserted by the app. Keeping the screen awake uses
`FLAG_KEEP_SCREEN_ON`, which needs no permission (unlike `WAKE_LOCK`), and speech goes
through the system TTS engine, which needs none either.

To check the browser copy yourself: open it, then DevTools → Network, and use the app. The
list stays empty.

## Data model

```js
Workout = { id, name, updatedAt, nodes: Node[] }
Node = { id, type:'section', name, color, duration }          // duration in seconds
     | { id, type:'loop',    name, color, reps, children: Node[] }
```

`compile()` expands the tree into a flat schedule — one entry per interval — where each
entry carries a `trail`: the chain of enclosing loops (with `iter`/`total`) ending in the
section itself. The runner's bottom bars are that trail, one row per level.

Everything lives under the single `localStorage` key `circuitclock.v1`, which also holds the
volume and voice preferences. Storage is per-origin, so a copy opened from `file://` and a
copy served over `http://localhost` keep separate libraries — browser policy, not an app
choice.

## Audio and voice

One `AudioContext`, unlocked by the Start button (browsers require a user gesture). A master
`GainNode` carries the in-app volume, which multiplies against system volume rather than
replacing it. Cues: a soft 660 Hz blip at 2 s and 1 s remaining, a louder 990 Hz + 1485 Hz
tone at 0, and a three-note figure at the end. All synthesised — no audio files.

Section names are announced as each starts; when a loop rolls over to a new iteration the
announcement is prefixed with it — "Round 3. Push-ups."

Android WebView ships no Web Speech API — `window.speechSynthesis` does not exist there — so
`MainActivity` exposes a small `AndroidHost` bridge (`speak`, `stopSpeaking`, `keepAwake`)
backed by the system TTS engine. The page uses it when present and falls back to web APIs in
a normal browser, so one HTML file serves both. For spoken names in airplane mode, choose an
offline voice under *Settings → System → Languages & input → Text-to-speech*.

## Timing

The engine tracks an absolute `endAt` timestamp rather than accumulating deltas, so it does
not drift, and it catches up silently (no beeps) if the tab was suspended for more than a
second. The screen is kept awake while a workout runs.

## Keyboard

- Editor: focus a row's grip handle, then **↑ ↓** to move it, **← →** to outdent/indent.
- Runner: **Space** pause/resume, **← →** previous/next section, **Esc** to exit.

## Building the Android app

Requires JDK 17 and the Android SDK (Android Studio provides both).

```bash
gradle wrapper          # once — the wrapper jar is not committed
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. F-Droid signs release builds with its own
key, so no signing config is needed here.

The APK theme is dark-only by design; the HTML still follows the system theme in a browser.

See [PUBLISHING.md](PUBLISHING.md) for the F-Droid submission process.

## Typography

System font stacks only — no bundled or downloaded fonts, which is what keeps the whole app
at ~59 KB. Hierarchy comes from weight, size and letter-spacing rather than from a display
face; numerals use `font-variant-numeric: tabular-nums` so the countdown does not jitter.
