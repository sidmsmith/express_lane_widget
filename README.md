# Express Lanes Widget

Android widget that displays I-75 Northwest Corridor Express Lanes status from the 511 GA API.

## Features

- **Status icons**: Green up arrow (Northbound), yellow down arrow (Southbound), red X (Closed)
- **Periodic updates**: Configurable 15, 30, or 60 minute intervals via WorkManager
- **Click**: Opens 511ga.org (configurable URL)
- **Settings**: Tap the gear icon to configure update frequency, API key, and click URL
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
5. Set update frequency and save

## Configuration

- **Update frequency**: 15, 30, or 60 minutes (Android enforces minimum 15 min for WorkManager)
- **API key**: Default 511 GA API key is pre-filled; change if needed
- **Click URL**: Default https://511ga.org; change to open a different link when tapping the widget

## Requirements

- Android 5.0 (API 21) or higher
- Internet permission
