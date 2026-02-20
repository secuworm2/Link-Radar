# Link Radar

Link Radar is a Burp Suite extension that extracts endpoint URLs from HTTP response bodies and helps you triage and replay them quickly.

## Key Features

- Collect endpoints from selected Proxy HTTP history items.
- Trigger collection from:
  - Proxy history context menu: `Send to Link Radar`
  - Hotkey: `Ctrl+G` 
- Parse endpoint candidates from:
  - Absolute URLs
  - Root-relative paths (`/api/...`)
  - relative paths (`../`, `../../`, `./`)
  - JavaScript patterns (`fetch`, `axios`, `XHR.open`, `new URL`, framework route signatures)
- Normalize and clean endpoints:
  - HTML entity decoding (`&amp;`, `&#39;`, `&#x27;`, `&#47;`, etc.)
  - Dynamic token normalization (`:id`, `${var}`, `[id]` -> `{...}`)
  - Regex/noise false-positive suppression
- Filter results by keyword.
- Export results to CSV.
- Send selected endpoints to Repeater (multi-select supported, source method/headers reused when possible, `Ctrl+R` in tab).

## Compatibility

- Burp Suite (Montoya API-based extension)
- Java 17+
- Built against `montoya-api:2026.2`

Note: Hotkey registration is runtime-dependent. If hotkey registration is not available in your Burp version, use the context menu action.

## Build

```bash
./gradlew clean jar
```

Windows:

```powershell
.\gradlew.bat clean jar
```

Output artifact:

- `build/libs/link-radar-1.0.jar`

## Load in Burp

1. Open Burp Suite.
2. Go to `Extensions` -> `Installed`.
3. Click `Add`.
4. Choose extension type `Java`.
5. Select `build/libs/link-radar-1.0.jar`.

   <img width="700" alt="image" src="https://github.com/user-attachments/assets/0033d1aa-d252-40ca-b1a6-dbafe32b789f" />

6. Confirm it loads and check the `Link Radar` tab.

## Usage

1. In `Proxy` -> `HTTP history`, select one or more messages.
2. Right-click and choose `Send to Link Radar` (or use `Ctrl+G` if enabled).
3. Review collected endpoints in the `Link Radar` tab.
4. Optionally filter with the search box.
5. Select one or more rows and click `Send to Repeater` (or `Ctrl+R` inside the tab).
6. Optionally export with `Export CSV`.

## Project Commands

- Build jar: `./gradlew clean jar`
- Run regression suite: `./gradlew runTaskM05Regression`

