# Graph Report - .  (2026-07-23)

## Corpus Check
- 65 files · ~139,251 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 434 nodes · 673 edges · 31 communities (28 shown, 3 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 37 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Token Encoder (o200k_base)|Token Encoder (o200k_base)]]
- [[_COMMUNITY_Threshold & Status UI Widgets|Threshold & Status UI Widgets]]
- [[_COMMUNITY_Session Manager|Session Manager]]
- [[_COMMUNITY_Monitor Mode Screens|Monitor Mode Screens]]
- [[_COMMUNITY_Browser Extension Manifest|Browser Extension Manifest]]
- [[_COMMUNITY_Clawd Mascot Rendering|Clawd Mascot Rendering]]
- [[_COMMUNITY_Background Polling Service|Background Polling Service]]
- [[_COMMUNITY_Extension Usage Bar UI|Extension Usage Bar UI]]
- [[_COMMUNITY_Usage API & History Repository|Usage API & History Repository]]
- [[_COMMUNITY_Login WebView & Main Activity|Login WebView & Main Activity]]
- [[_COMMUNITY_Extension SSE Event Handling|Extension SSE Event Handling]]
- [[_COMMUNITY_Polling Settings|Polling Settings]]
- [[_COMMUNITY_Settings Screen|Settings Screen]]
- [[_COMMUNITY_Usage Trend Tile|Usage Trend Tile]]
- [[_COMMUNITY_WebView Usage Fetcher|WebView Usage Fetcher]]
- [[_COMMUNITY_Hourly Heatmap Tile|Hourly Heatmap Tile]]
- [[_COMMUNITY_Extension Bridge Client|Extension Bridge Client]]
- [[_COMMUNITY_Display Settings|Display Settings]]
- [[_COMMUNITY_Usage Analytics Engine|Usage Analytics Engine]]
- [[_COMMUNITY_Notification Helper|Notification Helper]]
- [[_COMMUNITY_Usage ViewModel|Usage ViewModel]]
- [[_COMMUNITY_Injected Page Bridge|Injected Page Bridge]]
- [[_COMMUNITY_Boot Receiver|Boot Receiver]]
- [[_COMMUNITY_Stick Typography|Stick Typography]]
- [[_COMMUNITY_Stick Colors|Stick Colors]]
- [[_COMMUNITY_Stick Dimens|Stick Dimens]]
- [[_COMMUNITY_VSCode Config|VSCode Config]]

## God Nodes (most connected - your core abstractions)
1. `SessionManager` - 21 edges
2. `CounterUI` - 18 edges
3. `UsagePollingService` - 17 edges
4. `MonitorContent()` - 17 edges
5. `d` - 17 edges
6. `Mascot()` - 14 edges
7. `UsageCardStick()` - 11 edges
8. `MonitorRoot()` - 10 edges
9. `SettingsScreen()` - 10 edges
10. `TileAgora()` - 10 edges

## Surprising Connections (you probably didn't know these)
- `LoginPromptScreen()` --calls--> `Mascot()`  [INFERRED]
  app/src/main/java/com/example/claudecounter/MainActivity.kt → app/src/main/java/com/example/claudecounter/ui/brand/Mascot.kt
- `MomentOverlay()` --calls--> `Mascot()`  [INFERRED]
  app/src/main/java/com/example/claudecounter/ui/MomentOverlay.kt → app/src/main/java/com/example/claudecounter/ui/brand/Mascot.kt
- `MomentOverlay()` --calls--> `stageFor()`  [INFERRED]
  app/src/main/java/com/example/claudecounter/ui/MomentOverlay.kt → app/src/main/java/com/example/claudecounter/ui/brand/Mascot.kt
- `MonitorRoot()` --calls--> `MomentOverlay()`  [INFERRED]
  app/src/main/java/com/example/claudecounter/ui/MonitorRoot.kt → app/src/main/java/com/example/claudecounter/ui/MomentOverlay.kt
- `MonitorRoot()` --calls--> `stageFor()`  [INFERRED]
  app/src/main/java/com/example/claudecounter/ui/MonitorRoot.kt → app/src/main/java/com/example/claudecounter/ui/brand/Mascot.kt

## Import Cycles
- None detected.

## Communities (31 total, 3 thin omitted)

### Community 0 - "Token Encoder (o200k_base)"
Cohesion: 0.11
Nodes (17): addToMergeCache(), binarySearch(), bytePairEncode(), bytePairMerge(), countNative(), d, decodeNative(), decodeNativeAsyncIterable() (+9 more)

### Community 1 - "Threshold & Status UI Widgets"
Cohesion: 0.09
Nodes (28): Long, Modifier, Float, Int, Modifier, Color, Modifier, String (+20 more)

### Community 2 - "Session Manager"
Cohesion: 0.13
Nodes (19): Boolean, ClaudeApiService, Context, Long, SharedPreferences, StateFlow, String, getInstance() (+11 more)

### Community 3 - "Monitor Mode Screens"
Cohesion: 0.09
Nodes (25): Int, Modifier, Boolean, Float, List, Long, MascotStage, Modifier (+17 more)

### Community 4 - "Browser Extension Manifest"
Cohesion: 0.07
Nodes (27): action, default_icon, default_title, browser_specific_settings, gecko, content_scripts, required, 128 (+19 more)

### Community 5 - "Clawd Mascot Rendering"
Cohesion: 0.15
Nodes (25): Color, Float, FloatArray, Int, Modifier, Boolean, Float, Long (+17 more)

### Community 6 - "Background Polling Service"
Cohesion: 0.12
Nodes (12): Boolean, ClaudeApiService, Int, Intent, Long, SessionManager, String, UsagePollingService (+4 more)

### Community 7 - "Extension Usage Bar UI"
Cohesion: 0.19
Nodes (5): CounterUI, formatResetCountdown(), formatSeconds(), makeTooltip(), setupTooltip()

### Community 8 - "Usage API & History Repository"
Cohesion: 0.15
Nodes (14): String, Context, Float, List, Long, ClaudeApiService, UsageData, UsageResult (+6 more)

### Community 9 - "Login WebView & Main Activity"
Cohesion: 0.13
Nodes (11): Context, Boolean, Bundle, createIntent(), LoginWebViewActivity, LoginPromptScreen(), MainActivity, ComponentActivity (+3 more)

### Community 10 - "Extension SSE Event Handling"
Cohesion: 0.26
Nodes (13): applyUsageUpdate(), getConversationId(), getOrgIdFromCookie(), handleConversationPayload(), handleMessageLimit(), handleUrlChange(), parseUsageFromMessageLimit(), parseUsageFromUsageEndpoint() (+5 more)

### Community 11 - "Polling Settings"
Cohesion: 0.18
Nodes (10): Boolean, Context, Long, SharedPreferences, StateFlow, String, formatInterval(), getInstance() (+2 more)

### Community 12 - "Settings Screen"
Cohesion: 0.24
Nodes (14): Boolean, List, Long, Modifier, String, DisplayConfig, PollingConfig, PollMode (+6 more)

### Community 13 - "Usage Trend Tile"
Cohesion: 0.24
Nodes (13): Float, List, Long, Modifier, UsageSample, Long, String, TileTrend() (+5 more)

### Community 14 - "WebView Usage Fetcher"
Cohesion: 0.26
Nodes (7): Bundle, Context, String, getInstance(), JsBridge, WebViewUsageFetcher, WebView

### Community 15 - "Hourly Heatmap Tile"
Cohesion: 0.20
Nodes (10): Boolean, String, List, Long, Modifier, UsageSample, HeatPeriod, startOfDay() (+2 more)

### Community 16 - "Extension Bridge Client"
Cohesion: 0.24
Nodes (4): BridgeClient, getRuntime(), injectBridgeOnce(), makeRequestId()

### Community 17 - "Display Settings"
Cohesion: 0.27
Nodes (7): Boolean, Context, SharedPreferences, StateFlow, DisplayConfig, DisplaySettings, getInstance()

### Community 18 - "Usage Analytics Engine"
Cohesion: 0.40
Nodes (5): FloatArray, List, Long, UsageSample, UsageAnalytics

### Community 19 - "Notification Helper"
Cohesion: 0.36
Nodes (3): Context, NotificationHelper, UsageWindow

### Community 20 - "Usage ViewModel"
Cohesion: 0.29
Nodes (4): AndroidViewModel, SessionManager, StateFlow, UsageViewModel

### Community 21 - "Injected Page Bridge"
Cohesion: 0.38
Nodes (3): handleConversationResponse(), handleEventStream(), post()

### Community 22 - "Boot Receiver"
Cohesion: 0.33
Nodes (4): Context, Intent, BroadcastReceiver, BootReceiver

### Community 23 - "Stick Typography"
Cohesion: 0.40
Nodes (4): Int, FontWeight, montserrat(), StickType

## Knowledge Gaps
- **103 isolated node(s):** `java.configuration.updateBuildConfiguration`, `Context`, `Intent`, `Bundle`, `CookieManager` (+98 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MonitorContent()` connect `Monitor Mode Screens` to `Threshold & Status UI Widgets`, `Usage Trend Tile`, `Clawd Mascot Rendering`, `Hourly Heatmap Tile`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **Why does `MonitorRoot()` connect `Monitor Mode Screens` to `Login WebView & Main Activity`, `Settings Screen`, `Clawd Mascot Rendering`, `Threshold & Status UI Widgets`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `MonitorContent()` (e.g. with `TileAgora()` and `TileClawd()`) actually correct?**
  _`MonitorContent()` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `java.configuration.updateBuildConfiguration`, `Context`, `Intent` to the rest of the system?**
  _103 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Token Encoder (o200k_base)` be split into smaller, more focused modules?**
  _Cohesion score 0.11095305832147938 - nodes in this community are weakly interconnected._
- **Should `Threshold & Status UI Widgets` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Session Manager` be split into smaller, more focused modules?**
  _Cohesion score 0.12873563218390804 - nodes in this community are weakly interconnected._