# Claude Counter

A usage tracking toolkit for Claude.ai containing a browser extension and an Android application.

## ⚠️ Status Update (April 2026)

As of April 2026, **Anthropic's bot detection blocks the `/usage` endpoint for plain HTTP clients** — a background service hitting the API with `HttpURLConnection`/`OkHttp` gets a 403, because that request's TLS/HTTP fingerprint doesn't match a real browser's.

The Android app now works around this by fetching usage from inside a hidden `WebView` loaded on `claude.ai` (see [`WebViewUsageFetcher`](app/src/main/java/com/example/claudecounter/WebViewUsageFetcher.kt)) instead of a raw HTTP client. WebView's `fetch()` goes through Chromium's actual network stack, the same one a real browser tab uses — the same reason the browser extension's page-context `fetch()` (see [`bridge.js`](extension_src/src/injected/bridge.js)) was never blocked. If Anthropic tightens detection further, the app falls back gracefully: it shows an "API Access Restricted" banner and stops polling instead of crashing or burning battery.

---

## 🧩 Browser Extension (Recommended)

The browser extension injects usage tracking bars and token counts directly into the claude.ai web interface.

### Features
- **Token count** — Approximate token count for the current conversation, with a mini progress bar against the 200k context limit.
- **Cache timer** — Countdown showing how long the conversation remains cached.
- **Usage bars** — Session (5-hour) and weekly (7-day) usage progress bars and reset countdowns based on exact SSE limits.

### Installation
1. Open Chrome / Edge / Chromium and navigate to `chrome://extensions`
2. Enable **Developer mode**
3. Drag and drop the extension folder (or zip) onto the page.

Firefox users can use the `.xpi` file included in the releases or use the userscript equivalent. See `extension_src/README.md` for more details.

---

## 📱 Android App

Polls the Claude API in the background, shows a persistent Android notification with your 5-hour and 7-day usage, and alerts you when a window resets.

Usage is fetched from inside a hidden WebView on `claude.ai` rather than a raw HTTP client (see the status update above). If Anthropic's detection ever blocks that too:

- The app detects the 403 and stops background polling to save battery.
- The UI shows a warning banner indicating API access is restricted (Settings screen).
- The app keeps showing your last successfully fetched data.

### Monitor mode

As of v2.0.0, the app's visual style is ported from the [claude-usage-stick-SVGL](https://github.com/benevid/claude-usage-stick-SVGL) project — a coral "Claude Code" theme with a pixel-art mascot ("Clawd"), segment meters, and three swipeable tiles:

1. **Agora** — 5-hour and weekly usage cards (the same data the notifications are built on).
2. **Janela de 5h** — a usage-over-time graph with a linear projection to your next reset, built from samples this app records locally over time.
3. **Ritmo por hora** — a 24-hour heatmap of when you tend to burn quota.

(The stick's fourth tile, per-model live status, was intentionally left out — it only pings whether a model is reachable, not your actual usage, and would need a separate paid API token to probe `api.anthropic.com` for information that isn't very useful if you're on a Claude subscription plan.)

A "momento de limiar" animation pops up whenever a window crosses 25/50/70/100%.

### Adaptive polling (v2.1.0)

Polling is adaptive: the app sits on a slow interval (2 min by default) while nothing is happening, and drops to a short one (15 s by default) for 3 minutes after it sees your utilization rise — so the numbers stay live while you work without hammering the endpoint all night. Both intervals, and the whole behavior, are editable under Settings → Monitoramento, which also shows whether the app is currently in `ATIVO` or `OCIOSO` mode.

Note that reading `/usage` costs **no tokens** — it is a quota-metadata read, not an inference call. The cost of polling often is battery, mobile data, and exposure to the same bot detection described in the status update above, which is why the fastest option (5 s) carries a warning.

**Landscape ("monitor mode")** renders a pixel-accurate 480x320 canvas matching the original stick display — prop the phone up flat and it behaves like the physical device. The screen is also kept on while the app is in the foreground (like a game), so it won't sleep while acting as a monitor. **Portrait** stacks the same tiles into a normal scrollable phone layout.

### Building
```bash
./gradlew assembleDebug
```

---

## 🙏 Credits

This Android app started as a fork of [Claude-Counter-Android](https://github.com/ignitedvisions/Claude-Counter-Android) by [akashnathgarg](https://github.com/akashnathgarg) — the original background-polling app and notification system this project builds on.

The "Monitor mode" visual design (Stick theme, Clawd mascot, segment meters, tile layout) is adapted from [claude-usage-stick-SVGL](https://github.com/benevid/claude-usage-stick-SVGL) by [benevid](https://github.com/benevid), a physical ESP32 usage-monitor stick. Many thanks to both projects for the inspiration and groundwork.
