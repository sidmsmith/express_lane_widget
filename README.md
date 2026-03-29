# Express Lanes Widget

Android widget that displays I-75 Northwest Corridor (75B) Express Lanes status from the **511 GA API**, with a **Peach Pass** fallback when 511 is unavailable, unusable, or stale.

## Features

- **Status icons**: **Green** up/down when 511 GA returns fresh data; **yellow** up/down when status comes from Peach Pass fallback; red X (Closed)
- **Periodic updates**: Configurable 1, 3, 5, 15, 30, or 60 minute intervals via AlarmManager
- **Widget taps** (one icon, no separate camera button):
  - **Single tap**: Opens the **main URL** (default `https://511ga.org`). There is a short delay (~350 ms) so a second tap can count as a double tap.
  - **Double tap** (two taps within ~350 ms): Opens the **double-tap URL** (default SRTA traffic camera image). Configure both URLs in settings.
- **Notifications**: Optional alerts for status changes, stale 511 data, odd 511 responses, and **unexpected Peach Pass fallback** responses (unparseable JSON or missing lane cues)
- **Settings**: Tap the gear icon to configure update frequency, API key, main and double-tap URLs, and notification toggles
- **Configuration on add**: Settings screen appears when the widget is first added

## Building

Requires **Java 11 or newer** and Android SDK.

```bash
cd express_lanes_widget
npm run build
```

Or:
```bash
./gradlew assembleDebug
```

## Adding to Home Screen

1. Long-press on home screen
2. Select "Widgets"
3. Find "Express Lanes"
4. Drag to home screen (configuration screen will appear)
5. Configure update frequency, API key, and notification preferences; tap Save

## Configuration

- **Update frequency**: 1, 3, 5, 15, 30, or 60 minutes (1 and 3 min for troubleshooting; 5 min recommended for production)
- **API key**: Default 511 GA API key is pre-filled; change if needed
- **Main tap URL**: Default https://511ga.org; opens on a **single tap** of the widget icon
- **Double-tap URL**: Default SRTA camera image URL; opens when you **double-tap** the icon (two quick taps)
- **Notifications**: Each notification type can be enabled or disabled independently (no code changes required)

## Reliability

The app uses several mechanisms to keep updates running:

- **Boot receiver**: Re-schedules the update alarm when the device reboots (alarms don't survive reboots).
- **App open**: Opening the config screen re-establishes the alarm, so opening the app periodically helps ensure updates continue.
- **setAlarmClock**: Uses Android's most reliable alarm type so updates run even when the device is in Doze. You may see a "next alarm" or clock icon in the status bar—this is normal and helps ensure the widget keeps updating.

**Manual refresh**: Tap the refresh icon in the title bar to call the API on demand and verify it's working. This also re-schedules the alarm and updates the API response display.

**If updates stop**: Some devices aggressively restrict background apps. Try:
1. Open the app and tap the refresh icon (or just opening re-schedules the alarm).
2. In **Settings → Apps → Express Lanes Widget → Battery**, set to "Unrestricted" or "Don't optimize".
3. Ensure the widget is on your home screen; the alarm is only active when at least one widget instance exists.

## Troubleshooting

The config screen shows the **last 3 API responses** with timestamps (e.g., "Sunday 3/22 1:35 AM: {response}"). Scroll down the config page to see all entries. The raw `LastUpdated` field includes a human-readable suffix (e.g., "Mon 3:45 PM ET"). Error responses (network failures, HTTP errors, parse errors) appear in red.

## Notifications

All notification types are optional and controlled by toggles in the config screen. **Tapping a notification opens the app** (config screen with API response history).

- **Suppress repeated notifications** (default: ON): When enabled, Stale, Odd (511), and Peach Pass unexpected notifications fire only when you *transition into* that state—not on every poll while the condition stays true. Status change already fires only on actual changes.
- **Repeat every time** (Suppress OFF): Stale, Odd, and Peach Pass unexpected notifications fire on every check that meets the condition.

| Notification | Toggle | When it fires |
|--------------|--------|---------------|
| **Status change** | Notify when status changes | Lane status changes between Northbound, Southbound, or Closed |
| **Stale data** | Notify when data &gt; 24 hours old | 511 GA `LastUpdated` is older than 24 hours (suppressed when a valid Peach Pass fallback is in use) |
| **Odd 511 response** | Notify on odd/unexpected 511 GA response | 75B entry does not match expected Description/Status patterns; includes raw snippet |
| **Peach Pass unexpected** | Notify when Peach Pass fallback cannot be parsed | Fallback JSON/body is missing `data.north`, has no clear **open** or **closed** wording, says both open and closed, **open** without north/south direction, HTTP error, invalid JSON, or `success: false` |

## Status Parsing Logic (75B, 511 GA primary)

The widget parses the 511 GA API response using `Description` and `Status` fields. Logic is applied in order:

| Condition | Result | Odd? |
|-----------|--------|------|
| Description contains "in transition", "closed", or "to closed" | Closed | No |
| Description does not contain "open" | Closed | No |
| Description contains "northbound" OR Status is "northbound" | Northbound | No |
| Description contains "southbound" OR Status is "southbound" | Southbound | No |
| Any other case (unexpected state) | Closed | Yes |

When the result is marked **Odd? Yes**, an **odd 511** notification is shown (if enabled), and the raw API JSON is saved for display in the config screen.

## Peach Pass fallback

### Fallback URL (in code)

```
https://peachpass.com/wp-admin/admin-ajax.php?action=pp_lane_status
```

### Why this fallback exists

511 GA is used whenever it returns a **fresh** (within 24 hours), **parseable** 75B row. The widget switches to Peach Pass when any of the following is true:

- Network or HTTP failure talking to 511 GA
- Response is not valid JSON or has no **75B** (`Id == "75B"`) entry
- 75B row parses as **odd** (unexpected Description/Status)
- 75B **`LastUpdated`** is **older than 24 hours** (data treated as stale)

Peach Pass publishes a lightweight AJAX endpoint that includes a `data.north` string describing northwest corridor / I-75 express lane status. It is **not** a government API contract; the app treats it as a best-effort backup when 511 is down or stale.

### Parsing `data.north` (Peach Pass)

Applied after `success: true` and a non-null `data` object:

| Condition | Widget status | Unexpected? (notification) |
|-----------|---------------|----------------------------|
| Missing `data`, missing `north` key, empty `north`, or invalid JSON | Closed | Yes |
| `success` is false | Closed | Yes |
| HTTP error or network error | Closed | Yes |
| `north` has neither **open** nor **closed** (case-insensitive) | Closed | Yes |
| **closed** and no **open** | Closed | No |
| Both **open** and **closed** | Closed | Yes (ambiguous) |
| **open** and **north** (substring) | Northbound | No |
| **open** and **south** (substring) | Southbound | No |
| **open** but neither north nor south direction | Closed | Yes |

When **Unexpected?** is **Yes**, the widget shows a **yellow** closed-style outcome (same as other fallback failures), logs the combined 511 snapshot + Peach Pass body in history, and can fire the **Peach Pass unexpected** notification (if enabled).

## Development

Project rules (Cursor `.cursor/rules/express-lanes-build-sync.mdc`): after any change under `express_lanes_widget/`:

1. Run `npm run build`
2. `git add` changed files (`.gitignore` excludes build artifacts)
3. `git commit -m "..."` with a clear message
4. `git push origin main`

```bash
cd express_lanes_widget && npm run build && git add -A && git status
git commit -m "Descriptive message" && git push origin main
```

## Requirements

- Android 5.0 (API 21) or higher
- Internet permission
