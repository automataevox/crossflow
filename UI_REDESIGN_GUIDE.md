# CrossFlow Windows UI Redesign - Complete Implementation Guide

## Overview
The Windows UI has been completely redesigned to remove manual IP entry and add device connection status cards with expandable logs and maintenance controls.

## Key Changes

### ✅ Removed Features
- **Manual IP Dialog**: No longer need to manually type device IP addresses
- **Manual IP Button**: Removed from the UI header

### ✨ New Features

#### 1. **Device Status Sections**
The app now displays devices in two distinct sections:

**Connected Devices** (Green section)
- Shows devices currently reachable on the network
- Green indicator with "Connected" status
- Displayed at the top

**Disconnected Devices** (Orange section)
- Shows devices that were previously discovered but are now offline
- Orange indicator with "Offline" status
- Allows you to maintain history of devices

#### 2. **Expandable Device Cards**
Each device card features:
- **Device Name** with connection status (Connected/Offline)
- **Status Indicator**: Green dot (connected) or orange dot (offline)
- **Click to Expand**: Click anywhere on the card to expand/collapse logs
- **Logs Section**: When expanded, shows timestamped event history

#### 3. **Device Logs**
Each device maintains a log of events:
- **Format**: `[HH:MM:SS] Event type: Details`
- **Event Types**:
  - `Connected` - Device appeared on network
  - `Disconnected` - Device left network
  - `Clipboard sent: [preview]` - Clipboard sync sent to device
  - `Clipboard received: [preview]` - Clipboard update received
  - `Send failed: [error]` - Failed to reach device
- **Capacity**: Up to 50 logs per device (oldest automatically removed)

#### 4. **Android App Kill Button**
- **Power Icon**: Red power button on each device card
- **Purpose**: Force-stop the Android app for quick reset/maintenance
- **Implementation**: Uses ADB (Android Debug Bridge) to execute `am force-stop dev.crossflow.android`
- **Logs**: Kill action is recorded in device logs as "Android app force-stopped"

#### 5. **Auto-Discovery (Unchanged)**
- UDP broadcast continues to work on port 35647
- Devices are automatically discovered every 5 seconds
- No manual configuration needed

## UI Layout

```
┌─────────────────────────────────────────┐
│        CrossFlow (with status dot)      │
├─────────────────────────────────────────┤
│  ✓ Sync active — waiting for devices    │
│  Port 35647 • mDNS auto-discovery       │
├─────────────────────────────────────────┤
│  ✓ Connected devices (1)                │
├─────────────────────────────────────────┤
│  ⬤ DEVICE-NAME            Connected     │
│    [Power] [Expand ▼]                   │
│  ┌─────────────────────────────────────┐│
│  │ [18:35:42] Connected                 ││
│  │ [18:35:43] Clipboard sent: First 30  ││
│  │ [18:35:50] Clipboard received: Text  ││
│  └─────────────────────────────────────┘│
├─────────────────────────────────────────┤
│  ✓ Clipboard history                    │
├─────────────────────────────────────────┤
│  First clipboard text entry...          │
│  Second clipboard text entry...         │
└─────────────────────────────────────────┘
```

## Usage Workflow

### 1. **Discovering Devices**
- Start the CrossFlow app on both Windows and Android
- Android device will automatically discover Windows via UDP broadcast
- Windows will show the Android device in "Connected devices" section
- Status dot will be green when connected

### 2. **Monitoring Sync Activity**
- Click on a device card to expand the logs
- Watch real-time events as clipboard changes occur
- See timestamp, event type, and preview of clipboard content

### 3. **Troubleshooting Connections**
- If a device goes offline, it appears in "Disconnected devices"
- View its logs to see the last activities before disconnect
- Can re-check the logs to understand what happened

### 4. **Resetting Android Client**
- Click the red power button on a device card
- This sends ADB command to force-stop the Android app
- App will auto-restart on Android device (via BootReceiver)
- Useful for quickly resetting the connection during development/testing

## Backend Implementation

### SyncManager.kt Changes
```kotlin
// New data structures
data class DeviceInfo(
    val name: String,
    val logs: MutableList<String> = mutableListOf(),
    var lastSeen: LocalDateTime = LocalDateTime.now()
)

// State management
val connectedDevices = mutableStateListOf<String>()
val disconnectedDevices = mutableStateListOf<String>()
val deviceInfo = mutableMapOf<String, DeviceInfo>()

// New methods
fun getDeviceInfo(name: String): DeviceInfo?
fun killAndroidApp(deviceName: String) // ADB force-stop
```

### App.kt Changes
```kotlin
// Device card now accepts:
- name: Device name
- isConnected: Connection status
- isExpanded: Log visibility state
- onToggleExpand: Expand/collapse callback
- onKillApp: Kill button callback
- manager: SyncManager reference

// UI Components
@Composable WinDeviceCard(...)  // Expanded with features
// Removed: WinManualIPDialog
// Removed: Manual IP Button
```

## Code Structure

### Files Modified
1. **windows/src/main/kotlin/dev/crossflow/windows/SyncManager.kt**
   - Added DeviceInfo data class
   - Added device status tracking
   - Added logging system
   - Added killAndroidApp method

2. **windows/src/main/kotlin/dev/crossflow/windows/App.kt**
   - Redesigned CrossFlowApp composable
   - Updated WinDeviceCard with expand/logs/kill features
   - Removed WinManualIPDialog
   - Added status sections (Connected/Disconnected)

### Files Unchanged
- Android side (separate SyncManager.kt)
- Protocol.kt
- Network configuration
- USB/ADB communication

## Build Status
✅ Windows: BUILD SUCCESSFUL
✅ Android: BUILD SUCCESSFUL in 1s

## Testing Checklist

- [ ] **Discovery**: Android device appears in Connected section
- [ ] **Expand Logs**: Click device card to expand/collapse logs
- [ ] **Log Content**: Verify timestamps and event descriptions
- [ ] **Clipboard Sync**: Send clipboard from one device, verify in logs
- [ ] **Kill Button**: Click power icon, verify app force-stops
- [ ] **Reconnect**: Android app auto-restarts after kill, reconnects
- [ ] **Offline Transitions**: Move device offline, verify section change
- [ ] **History**: Clipboard history shows recent entries
- [ ] **Background**: Close Android app UI, verify sync continues

## Advanced Features

### Log Format Examples
```
[18:35:42] Connected
[18:35:43] Clipboard sent: Hello, World! This is a test...
[18:35:50] Clipboard received: Some text from Android device...
[18:36:05] Send failed: Connection timeout
[18:36:10] Disconnected
[18:36:15] Android app force-stopped
```

### Device State Transitions
```
Scanning → Connected (on UDP broadcast)
       ↓
   Connected (normal operation)
       ↓
   Disconnected (send fails or device offline)
       ↓
   Connected (reconnect detected)
```

### ADB Kill Command
When you click the power button:
```powershell
adb shell am force-stop dev.crossflow.android
```
This is logged as: `[HH:MM:SS] Android app force-stopped`

## Known Limitations
- Requires ADB installed for kill button to work
- ADB must be able to reach the device via USB
- Kill button works only if ADB recognizes the device
- Logs are session-based (lost on app restart)

## Future Enhancements
- [ ] Persistent log storage to file
- [ ] Export logs as CSV/JSON
- [ ] Log filtering by event type
- [ ] Automatic reconnect logic
- [ ] Custom device nicknames
- [ ] Per-device settings
