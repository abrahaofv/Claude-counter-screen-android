# Claude Counter (Android)

Android app that tracks Claude.ai usage limits (5-hour session and 7-day weekly windows) in real time, with a persistent notification and a landscape "monitor mode" screen. Companion to a browser extension in `extension_src/` (Chrome/Firefox) that does the same tracking client-side via SSE interception — the two are independent, no shared runtime.

## Origin and credits

- Base app forked from [ignitedvisions/Claude-Counter-Android](https://github.com/ignitedvisions/Claude-Counter-Android) (akashnathgarg).
- Visual design ("Stick" theme, Clawd mascot, segment meters, tile layout) adapted from [benevid/claude-usage-stick-SVGL](https://github.com/benevid/claude-usage-stick-SVGL), a physical ESP32 usage-monitor stick.
- See the README's Credits section for details.

## Stack

- Kotlin + Jetpack Compose, single module (`:app`), `namespace`/`applicationId` = `com.example.claudecounter`.
- `minSdk 26`, `compileSdk`/`targetSdk 35`, Gradle version catalog in `gradle/libs.versions.toml`.
- No local test suite; verification is manual (`./gradlew assembleDebug` + install on device/emulator).

## Why usage is fetched via WebView, not a plain HTTP client

Anthropic's bot detection blocks the `/usage` endpoint for HTTP clients whose TLS/HTTP fingerprint doesn't match a real browser (`HttpURLConnection`/OkHttp requests get a 403). The fix: [`WebViewUsageFetcher`](app/src/main/java/com/example/claudecounter/WebViewUsageFetcher.kt) loads a hidden `WebView` on `claude.ai` and issues the request through Chromium's real network stack instead. If Anthropic tightens detection further, the app is expected to detect the 403, stop background polling (battery), and show an "API Access Restricted" banner rather than crash — see `ClaudeApiService.kt` / `UsagePollingService.kt`.

## Package layout (`app/src/main/java/com/example/claudecounter/`)

- **Core / lifecycle**: `MainActivity.kt`, `LoginWebViewActivity.kt`, `BootReceiver.kt`, `NotificationHelper.kt`.
- **Data fetch & session**: `ClaudeApiService.kt`, `WebViewUsageFetcher.kt`, `SessionManager.kt`, `UsagePollingService.kt` (adaptive interval: slow when idle, fast for 3 min after usage rises — configurable in Settings).
- **`data/`** — local persistence and derived stats: `PollingSettings.kt`, `DisplaySettings.kt`, `UsageHistoryRepository.kt`, `UsageAnalytics.kt` (hourly burn profile, exhaustion projection).
- **`ui/theme/`** — `Stick*` files (`StickColors`, `StickDimens`, `StickTheme`, `StickTypography`): the dark, coral-accented visual system. There is no light mode — this is intentional, not an oversight.
- **`ui/brand/`** — `Mascot.kt` ("Clawd"), reused across the login screen, tiles, and threshold overlay.
- **`ui/tiles/`** — `TileAgora` (current usage), `TileTrend` (usage-over-time + projection), `TileHeat` (24h heatmap), `TileClawd` (mascot display).
- **`ui/`** (screens/widgets) — `MonitorRoot`/`MonitorScaffold`/`MonitorPager` (landscape "monitor mode" pager + portrait scroll fallback), `SettingsScreen`, `MomentOverlay` (25/50/70/100% threshold animation), `SegmentMeter`, `StatusChip`, `PeriodPill`, `StickHeader`.
- **`util/Formatting.kt`** — shared number/time formatting helpers.
- **`UsageViewModel.kt`** — top-level state holder wiring session, polling, and history together for the composables above.

## Building

```bash
./gradlew assembleDebug
```

## Knowledge graph (Graphify)

`graphify-out/` holds a generated knowledge graph of this codebase (`graph.html` for interactive browsing, `GRAPH_REPORT.md` for the plain-language audit). Regenerate after large refactors with `/graphify --update`.
