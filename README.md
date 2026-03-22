# Express Lanes Widget

Android widget that displays I-75 Northwest Corridor (75B) Express Lanes status from the 511 GA API.

## Features

- **Status icons**: Green up arrow (Northbound), yellow down arrow (Southbound), red X (Closed)
- **Periodic updates**: Configurable 1, 3, 5, 15, 30, or 60 minute intervals via AlarmManager
- **Click**: Opens 511ga.org (configurable URL)
- **Notifications**: Optional alerts for status changes, stale data, and odd API responses
- **Settings**: Tap the gear icon to configure update frequency, API key, click URL, and notification toggles
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
- **Click URL**: Default https://511ga.org; change to open a different link when tapping the widget
- **Notifications**: Each notification type can be enabled or disabled independently (no code changes required)

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

## Requirements

- Android 5.0 (API 21) or higher
- Internet permission
