# Express Lanes Widget

Android widget that displays I-75 Northwest Corridor (75B) Express Lanes status from the **511 GA API**, with a **Peach Pass** fallback when 511 fails or `LastUpdated` is older than 24 hours.

## Features

- **Status icons**: **Green** up/down when 511 GA returns fresh data; **yellow** up/down when status comes from Peach Pass fallback; red X (Closed)
- **Periodic updates**: Configurable 1, 3, 5, 15, 30, or 60 minute intervals via AlarmManager
- **Main tap** (arrow): Opens 511ga.org (configurable URL)
- **Camera icon**: Opens a configurable camera image or page (default: SRTA I-75 camera JPG)
- **Notifications**: Optional alerts for status changes, stale data, and odd API responses
- **Settings**: Tap the gear icon to configure update frequency, API key, main tap URL, camera URL, and notification toggles
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
- **Main tap URL**: Default https://511ga.org; opens when you tap the status arrow
- **Camera tap URL**: Default SRTA camera image URL; opens when you tap the small camera icon beside the arrow
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

- **Suppress repeated notifications** (default: ON): When enabled, Stale and Odd notifications fire only when you *transition into* that state—e.g., one Stale notification when data first goes stale, not every minute while it stays stale. Status change already fires only on actual changes.
- **Repeat every time** (Suppress OFF): Stale and Odd notifications fire on every check that meets the condition.

| Notification | Toggle | When it fires |
|--------------|--------|---------------|
| **Status change** | Notify when status changes | Lane status changes between Northbound, Southbound, or Closed |
| **Stale data** | Notify when data &gt; 24 hours old | API `LastUpdated` is older than 24 hours |
| **Odd response** | Notify on odd/unexpected API response | Response doesn't match expected patterns; includes raw API snippet in the notification for debugging |

## Status Parsing Logic (75B)

The widget parses the 511 GA API response using `Description` and `Status` fields. Logic is applied in order:

| Condition | Result | Odd? |
|-----------|--------|------|
| Description contains "in transition", "closed", or "to closed" | Closed | No |
| Description does not contain "open" | Closed | No |
| Description contains "northbound" OR Status is "northbound" | Northbound | No |
| Description contains "southbound" OR Status is "southbound" | Southbound | No |
| Any other case (unexpected state) | Closed | Yes |

When the result is marked **Odd? Yes**, an "odd response" notification is shown (if enabled), and the raw API JSON is saved for display in the config screen.

## Peach Pass fallback

If 511 GA is unreachable, returns HTTP error, has no 75B entry, parse is odd, or **75B `LastUpdated` is older than 24 hours**, the app calls:

`https://peachpass.com/wp-admin/admin-ajax.php?action=pp_lane_status`

It reads `data.north` (case-insensitive): requires **"open"** plus **"north"** or **"south"** to set direction; otherwise closed/odd. Fallback responses are logged in the config troubleshooting area (511 snapshot + Peach Pass body).

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
