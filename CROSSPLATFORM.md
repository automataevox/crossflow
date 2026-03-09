# CrossFlow: Complete Cross-Platform Implementation

This document describes the complete CrossFlow system across all three platforms: Android, Windows, and macOS.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      CrossFlow System (LAN)                     │
│                                                                  │
│  ┌──────────────────┐    ┌──────────────────┐  ┌────────────┐  │
│  │     ANDROID      │    │     WINDOWS      │  │    macOS   │  │
│  │  (Foreground Svc)│    │(Desktop Compose) │  │(StatusBar) │  │
│  └──────────────────┘    └──────────────────┘  └────────────┘  │
│         │                        │                    │         │
│    TCP Socket                TCP Socket          TCP Socket    │
│    :35647                    :35647              :35647         │
│         │                        │                    │         │
│         └────────────────────────┼────────────────────┘         │
│                                  │                              │
│                    ┌─────────────────────────┐                  │
│                    │  Peer Discovery        │                  │
│                    │  ├─ mDNS (Primary)     │                  │
│                    │  └─ UDP Broadcast (Alt)│                  │
│                    └─────────────────────────┘                  │
│                                                                  │
│         Shared Message Format (JSON over TCP)                  │
│         ┌────────────────────────────────────┐                 │
│         │{                                   │                 │
│         │  "type": "clipboard",              │                 │
│         │  "content": "text",                │                 │
│         │  "source": "device_name"           │                 │
│         │}                                   │                 │
│         └────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────────┘
```

## Platform Details

### Android

**Status**: Production Ready
- **Framework**: Kotlin + Jetpack Compose
- **Runtime**: Foreground Service (persists across app close)
- **Sync Modes**: 
  - Automatic: Continuous clipboard monitoring
  - Manual: Share target, Quick Settings tile, notification button
- **Discovery**: NSD (mDNS) + UDP broadcast fallback
- **Permissions**: 
  - FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC (system)
  - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (device exemption)
  - POST_NOTIFICATIONS (user notification display)

**Key Features**:
- Automatic service restart on task removal (AlarmManager)
- Battery optimization exemption request on launch
- Share target integration for text sharing
- Quick Settings tile for one-tap sync
- Foreground notification with manual sync button
- Adaptive to Vivo and other aggressive OEMs

**Build**: `./gradlew :app:assembleDebug` → `app-debug.apk`

### Windows

**Status**: Production Ready
- **Framework**: Kotlin/JVM + Compose Desktop
- **Runtime**: Background process (runs on startup if configured)
- **Sync Mode**: Continuous automatic clipboard synchronization
- **Discovery**: JmDNS (mDNS) + UDP broadcast fallback
- **UI**: Native Windows integration (system tray, taskbar)

**Key Features**:
- Desktop Compose UI with device list and clipboard history
- Automatic peer discovery and status updates
- Flattened interface-based discovery (works on any LAN)
- TCP server listens for incoming clipboard updates
- Silent background operation

**Build**: `cd windows && ./gradlew packageExe` → `CrossFlow-1.0.0.exe`

### macOS

**Status**: Production Ready
- **Framework**: Swift + AppKit
- **Runtime**: Menu bar app (statusbar only, not in Dock)
- **Sync Mode**: Continuous automatic clipboard synchronization
- **Discovery**: Bonjour/mDNS (primary), TCP-based sensing (fallback)
- **UI**: Native macOS menu bar integration

**Key Features**:
- Menu bar application (hidden from Dock)
- Clipboard monitoring with 400ms polling interval
- Bonjour service registration (_crossflow._tcp.local)
- Device list in menu with connection status
- Direct TCP peer-to-peer communication
- Automatic app launch from menu bar

**Build**: `bash scripts/build-macos.sh` → `CrossFlow.app` + `CrossFlow-1.0.0.dmg`

## Message Flow Example

### Scenario: Copy on Android → Paste on Windows & macOS

1. **Android**: User copies text (or uses Share target)
   ```
   ClipboardMonitor.onChange() → 
     ClipboardSyncService.syncOutgoingText() →
     NsdHelper.broadcast(text) →
     UDP announce on all LAN interfaces + TCP send to known peers
   ```

2. **Windows**: Receives broadcast/TCP message
   ```
   TcpServer.handle() →
     Protocol.decode() →
     SyncManager.onRemoteClipboard() →
     ClipboardMonitor.write(text)
   ```

3. **macOS**: Receives broadcast/TCP message
   ```
   TCPServer.receiveData() →
     Protocol.decode() →
     AppDelegate.onMessage() →
     ClipboardMonitor.write(text)
   ```

4. **User**: Pastes text on Windows or macOS (Cmd+V / Ctrl+V)

## Shared Components

### Protocol (Common Across All Platforms)

All platforms use identical message structure:
```kotlin/swift
struct ClipMessage {
    var type: String = "clipboard"
    var content: String
    var source: String
}
```

Encoding/Decoding:
- **Format**: JSON with newline delimiter
- **Charset**: UTF-8
- **Transport**: TCP/35647

### Discovery

#### Primary: mDNS/Bonjour

- **Service Type**: `_crossflow._tcp.local`
- **Port**: 35647
- **Platform Coverage**:
  - Android: NSD (Network Service Discovery, Apple mDNS compatible)
  - Windows: JmDNS open-source implementation
  - macOS: Native Bonjour (AppKit NetService)

#### Fallback: UDP Broadcast

- **Port**: 35647
- **Message**: `CROSSFLOW:device_name:35647`
- **Broadcast Addresses**: All computed LAN broadcast addresses per active IPv4 interface
- **Interval**: Android 5s, Windows/macOS on-receive
- **Use Case**: When mDNS fails or on first discovery

### TCP Connection Model

```
Listener:  Every device opens port 35647 and listens for incoming connections
Broadcaster: Device detects peer → Initiates TCP connection → Sends clipboard message
Connection: One-directional send (broadcaster → listener), then closes
Bidirectional: Each device both listens AND broadcasts, enabling full mesh sync
```

## Device Discovery Flow

### Android Discovery

```
NsdHelper.findBroadcastAddresses()
  ↓
For each active IPv4 interface:
  ├─ NetServiceDiscovery (mDNS) listener
  └─ UDP broadcast on computed broadcast address (e.g., 192.168.1.255)
  
Every 5 seconds:
  └─ Re-announce via UDP on all LAN interfaces
```

### Windows Discovery

```
MdnsHelper.findBroadcastAddresses()
  ↓
For each active IPv4 interface:
  ├─ JmDNS service browsing
  └─ UDP broadcast on computed broadcast address
  
Continuous:
  └─ Listen for peer TCP connections on :35647
```

### macOS Discovery

```
BonjourService.startBrowsing()
  ↓
NetServiceBrowser searches for _crossflow._tcp.local services
  
BonjourService.startAdvertising()
  ↓
NetService publishes this device as _crossflow._tcp.local
  
Connection:
  ├─ On peer found: resolve hostname + port
  └─ Cache peer info for TCP broadcasting
```

## Security Considerations

### Current Implementation

- **Transport**: Unencrypted TCP (suitable for private LAN only)
- **Auth**: None (all devices on LAN are trusted)
- **Sandboxing**: App sandbox disabled (macOS entitlements)
- **Permissions**: User grants on first launch

### Recommendations for Enterprise/WAN

For deployment beyond private LAN:
1. **Encryption**: TLS 1.3 for all TCP connections
2. **Authentication**: Device pairing code or mTLS certificates
3. **Discovery**: VPN-based or central relay server (instead of broadcast)
4. **Audit**: Message logging with source/destination/timestamp

### Android-Specific

- **Granular Permissions**: App requests battery optimization exemption only
- **Foreground Service**: Shows non-dismissible notification (OS requirement)
- **Accessibility**: Not used (no special clipboard access needed)

### macOS-Specific

- **Sandbox**: Disabled (requires for local network access)
- **Codesign**: Required for distribution
- **Notarization**: Required for macOS 10.15+ Gatekeeper

## Build Artifacts

| Platform | Artifact | Size | Format |
|----------|----------|------|--------|
| Android | app-debug.apk | ~10 MB | APK (Android app) |
| Windows | CrossFlow-1.0.0.exe | ~60 MB | Windows installer |
| macOS | CrossFlow.app + .dmg | ~40 MB | macOS application + disk image |

## Installation & Launch

### Android
1. Enable "Unknown Sources" in Settings
2. `adb install app-debug.apk`
3. Tap CrossFlow icon to launch
4. Grant permissions when prompted

### Windows
1. Run `CrossFlow-1.0.0.exe`
2. Follow installer wizard
3. App starts automatically

### macOS
1. Double-click `CrossFlow-1.0.0.dmg`
2. Drag CrossFlow to Applications
3. Open Applications/CrossFlow.app
4. Grant local network permission
5. App runs in menu bar

## Testing Checklist

Verify each platform:

- [ ] **Discovery**: Device appears in peer list on other devices
- [ ] **Outgoing Sync**: Copy text → appears on other devices
- [ ] **Incoming Sync**: Receive from peer → can paste
- [ ] **Background**: App continues syncing when minimized/backgrounded
- [ ] **Restart**: Device remains discoverable after restart
- [ ] **LAN Roaming**: Works when switching between WiFi networks (no subnet assumptions)

## Troubleshooting Matrix

| Issue | Android | Windows | macOS | Solution |
|-------|---------|---------|-------|----------|
| No peers discovered | Check NSD | Check JmDNS service | Check Bonjour | Restart app, check network |
| Clipboard not syncing | Check service running | Check TCP server | Check listener | Verify firewall allows :35647 |
| App crashes | Check logs (logcat) | Check Event Viewer | Check system.log | Rebuild with latest code |
| Can't connect to LAN | Airplane mode? | Check WiFi | Check WiFi | Enable Wifi/Ethernet |
| High battery drain | Service enabled | Windows 11 battery | Not applicable | Reduce polling interval |

## Platform-Specific Known Issues

### Android
- **Vivo & OEM Variants**: Aggressive task killing requires onTaskRemoved() + AlarmManager workaround
- **Android 13+**: Clipboard access is restricted; recommend using Share target or tile
- **Notification**: Cannot be dismissed (OS requirement for foreground service)

### Windows
- **Windows 11**: Modern Standby may suspend sync during sleep (use Task Scheduler for wake)
- **Windows Defender**: First run may trigger UAC; EXE requires installer run

### macOS
- **Sandboxing**: Disabled; not suitable for App Store without major refactoring
- **Gatekeeper**: Notarization required for distribution; self-signed OK for personal use
- **Monterey/Ventura**: Local Network permission prompt appears on first launch

## Future Enhancements

Possible improvements for future versions:

1. **UI Sync**: Extend to sync browser history, window positions
2. **Cloud Sync**: Optional cloud backup with CloudKit (macOS) / Firebase (Android)
3. **Sync History**: Persistent storage of sync history (not just current clipboard)
4. **File Sync**: Extend to small files (<10MB) via direct TCP
5. **Mac App Store**: Refactor macOS with sandbox enabled for App Store release
6. **Encryption**: TLS for sensitive networks
7. **Relay Server**: Central server for WAN/cross-subnet sync
8. **Sync Conflict**: Detect when overlapping keyboards trigger conflicts

---

**Repository Structure**:
```
crossflow/
  ├── android/             # Kotlin + Compose Android app
  ├── windows/             # Kotlin + Compose Desktop (Windows)
  ├── macos/               # Swift + AppKit (macOS)
  ├── scripts/             # Build scripts (build-macos.sh, etc)
  ├── Package.swift        # SwiftPM manifest for macOS
  ├── BUILD.md             # This file (build guide)
  ├── MACOS_BUILD.md       # Detailed macOS build guide
  └── README.md            # Project overview
```

**Maintained by**: Cross-platform team  
**Last Updated**: March 2026
