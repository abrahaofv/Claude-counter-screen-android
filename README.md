# Claude Counter

A usage tracking toolkit for Claude.ai containing a browser extension and a (deprecated) Android application.

## ⚠️ Important Status Update (April 2026)

As of April 2026, **Anthropic has actively blocked third-party and automated access to the `/usage` API endpoint**. Direct fetches using session cookies from background services now result in HTTP 403 Forbidden errors. 

Because of this, **the Android application's background tracking no longer works**. The recommended way to track usage is using the **Browser Extension**, which relies on real-time SSE stream interception (`message_limit` events) while you actively use Claude.

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

## 📱 Android App (Gracefully Deprecated)

The Android app was originally built to poll the Claude API in the background and show persistent Android notifications with your 5-hour and 7-day usage.

Due to the API restrictions mentioned above, the app has been gracefully deprecated (v1.1.0). 
- It will detect the 403 error and stop aggressive background polling to save battery.
- The UI will display a warning banner indicating that API access is restricted.
- The app will show your last successfully scraped data (if any).

You can still build and run the app to view historical data or in case the restriction is lifted in the future.

### Building
```bash
./gradlew assembleDebug
```
