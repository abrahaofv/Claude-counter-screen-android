# Fix Claude Counter — API & DOM Updates (v1.1.0)

## Problem Summary

The Claude Counter (both the Android app and the browser extension) is stuck because:

1. **API endpoint blocked**: As of April 2026, Anthropic has restricted the `claude.ai/api/organizations/{orgId}/usage` endpoint for third-party/automated access. The Android app's `ClaudeApiService` makes direct HTTP requests with a session cookie, which now returns **403 Forbidden**.

2. **DOM selectors likely stale**: The extension relies on `data-testid="model-selector-dropdown"` and `data-testid="chat-menu-trigger"` which Anthropic frequently changes during frontend deployments.

3. **No upstream fix**: The upstream `she-llac/claude-counter` extension (v0.4.2, last tagged Jan 31, 2026) has NOT been updated to address the April 2026 API restrictions. No relevant fix commits since March 21.

---

## User Review Required

> [!IMPORTANT]
> **The core `/usage` API endpoint is now blocked for automated access.** This fundamentally breaks the Android app's approach of polling the API from a background service using a session cookie. I need your input on the approach below.

> [!WARNING]
> **When you say "counter is stuck", can you clarify which component?**
> - **Android app** (polling service showing stale data)?
> - **Browser extension** (usage bars not updating on claude.ai)?
> - **Both?**
> 
> This affects the scope of the fix. The plan below covers both.

---

## Proposed Changes

### Component 1: Browser Extension (More Resilient Approach)

The extension has **two** data sources for usage:
- **Primary (broken)**: Direct fetch to `/api/organizations/{orgId}/usage` — now returns 403 for automated requests
- **Secondary (still working)**: SSE `message_limit` events that Claude sends in the response stream after each message

**Fix strategy**: Make the extension rely primarily on the SSE `message_limit` data and treat the direct `/usage` fetch as a best-effort fallback.

#### [MODIFY] [constants.js](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/extension_src/src/content/constants.js)
- Add fallback DOM selectors using ARIA attributes instead of fragile `data-testid` values
- Add `data-testid="chat-input-grid-container"` as additional anchor option

#### [MODIFY] [main.js](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/extension_src/src/content/main.js)
- Make `refreshUsage()` silently fail instead of leaving the UI stuck
- Increase reliance on SSE `message_limit` as the primary usage data source
- Add retry logic with exponential backoff for the `/usage` endpoint
- Add error state display ("Usage unavailable — send a message to update")

#### [MODIFY] [ui.js](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/extension_src/src/content/ui.js)
- Update `attachUsageLine()` and `attachHeader()` to use fallback selectors when primary `data-testid` selectors fail
- Add "stale data" indicator when usage data hasn't been updated recently

#### [MODIFY] [bridge.js](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/extension_src/src/injected/bridge.js)
- Add error handling that posts back error details to content script
- Handle 403 specifically to avoid retrying a blocked endpoint

#### [MODIFY] [manifest.json](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/extension_src/manifest.json)
- Bump version to `0.5.0`

---

### Component 2: Android App (Fundamental Architecture Change)

The Android app's approach of directly calling the `/usage` endpoint with a session cookie is **no longer viable** due to Anthropic's April 2026 restrictions. 

**Proposed approach**: Instead of polling the API, use the Android **WebView** as the primary mechanism — keep a hidden WebView that intercepts SSE `message_limit` events from Claude's response streams (same approach the browser extension uses).

#### [MODIFY] [ClaudeApiService.kt](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/app/src/main/java/com/example/claudecounter/ClaudeApiService.kt)
- Add retry logic with exponential backoff
- Handle 403 errors specifically (don't retry — the endpoint is blocked)
- Add a `fetchUsageViaWebView()` alternative path
- Add response logging to help debug API format changes

#### [MODIFY] [LoginWebViewActivity.kt](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/app/src/main/java/com/example/claudecounter/LoginWebViewActivity.kt)
- After login, inject JavaScript to intercept `message_limit` SSE events
- Extract usage data from the SSE stream and pass it back to the app via `@JavascriptInterface`

#### [MODIFY] [UsagePollingService.kt](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/app/src/main/java/com/example/claudecounter/UsagePollingService.kt)
- Keep the polling service but make it degrade gracefully on 403
- Add a "last successful fetch" indicator
- When the API returns 403, show a "Usage data requires opening Claude" message

#### [MODIFY] [SessionManager.kt](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/app/src/main/java/com/example/claudecounter/SessionManager.kt)
- Add `parseMessageLimitData()` for the SSE `message_limit` format (different from `/usage` format)
- Track data source (API vs SSE) for staleness indication

#### [MODIFY] [MainActivity.kt](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/app/src/main/java/com/example/claudecounter/MainActivity.kt)
- Add error state UI when API returns 403
- Show "Usage tracking limited — Anthropic has restricted API access" with guidance
- Add a "Source: API/SSE" indicator

#### [MODIFY] [app/build.gradle.kts](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/app/build.gradle.kts)
- Bump `versionCode` to 3, `versionName` to "1.1.0"

---

### Component 3: Git & Documentation

#### [NEW] Initialize Git repository
- `git init`
- Set up `.gitignore` (already exists)
- Initial commit of all current files
- Create remote (user needs to provide the GitHub repo URL)

#### [MODIFY] [extension README](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/extension_src/README.md)
- Update version references
- Add note about API changes and SSE-based tracking
- Update installation links

#### [NEW] [README.md](file:///c:/Users/akash/Downloads/Apps/Claude_Counter/README.md) (project root)
- Create comprehensive project README covering both the Android app and browser extension
- Add architecture overview, build instructions, changelog

---

## Open Questions

> [!IMPORTANT]
> 1. **Which component is stuck?** Android app, browser extension, or both?
> 2. **What's the GitHub repo URL?** I see there's no git repo initialized yet. Do you have an existing repo, or should I create a new one?
> 3. **Android app priority**: Given the API is now blocked, the Android app's core value proposition (background polling) is severely limited. Options:
>    - **Option A**: Keep polling but degrade gracefully (show last known data + "send a message in Claude to refresh")
>    - **Option B**: Refactor to use a persistent WebView in the background service to intercept SSE events (more complex, higher battery usage)
>    - **Option C**: Focus on the browser extension only and deprecate the Android app's background tracking
>    
>    Which approach do you prefer?
> 4. **Can you manually test the `/usage` endpoint?** Open claude.ai in your browser, log in, then open DevTools → Console and run:
>    ```javascript
>    fetch('/api/organizations/' + document.cookie.match(/lastActiveOrg=([^;]+)/)?.[1] + '/usage', {credentials: 'include'}).then(r => r.json()).then(console.log)
>    ```
>    This will tell us if the endpoint still works from a real browser session (as opposed to automated requests).

## Verification Plan

### Automated Tests
- Build the Android APK successfully
- Extension loads without errors in Chrome/Edge DevTools  

### Manual Verification
- Install updated extension in browser, verify usage bars appear after sending a message
- Install updated APK on device, verify graceful handling when API returns 403
- Push to GitHub and verify clean commit history
