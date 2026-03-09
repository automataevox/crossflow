# Background Sync Configuration

## Overview

Both Windows and macOS now support **continuous background clipboard synchronization** even when the app window is closed.

## Platform-Specific Behavior

### Windows

**Status**: ✓ Background sync enabled

**How it works**:
- Closing the main window **minimizes to system tray** instead of closing the app
- Sync service runs continuously in the background
- Right-click system tray icon to:
  - **Open CrossFlow**: Restore the window
  - **Background Syncing Active**: Status indicator (read-only)
  - **Exit**: Stop sync and close the app completely

**System tray features**:
- Single-click: Open window
- Right-click: Context menu
- Hover: Shows "CrossFlow — Continuous Sync ✓"

**To disable background sync**:
Right-click system tray → Exit (or close via notification area)

### macOS

**Status**: ✓ Background sync enabled

**How it works**:
- Closing the info window (red close button) **keeps the app running** in the menu bar
- Clipboard sync continues automatically
- Menu bar icon shows clipboard symbol (always visible)

**Menu bar options**:
- Click menu bar icon → dropdown menu appears
- **Show Window**: Re-open the info/status window
- **Devices**: List of discovered devices (scrollable)
- **Quit CrossFlow**: Stop sync and close the app completely

**To check sync status**:
Click menu bar icon → View connected devices and status

**To disable background sync**:
Click menu bar icon → Quit CrossFlow

## Persistence After Restart

Both platforms can optionally auto-start on system boot (requires configuration):

### Windows
1. Press `Win + R`, type `shell:startup`
2. Create shortcut to `CrossFlow-1.0.0.exe` or use Task Scheduler
3. App will start with Windows and begin syncing immediately

### macOS
1. System Preferences → General → Login Items
2. Add CrossFlow to auto-start apps
3. App will launch at login and run in menu bar

## Background Sync Behavior

When running in the background:

| Function | Active | Details |
|----------|--------|---------|
| **Clipboard Monitoring** | ✓ | Continuously polls local clipboard for changes |
| **Peer Discovery** | ✓ | mDNS + UDP broadcast every 5sec (Android) |
| **Message Reception** | ✓ | Listens on port 35647 for incoming sync messages |
| **Message Broadcasting** | ✓ | Sends clipboard changes to all discovered peers |
| **Thread Pools** | ✓ | Background threads for networking, no UI blocking |
| **Power Efficiency** | ✓ | Polling intervals optimized (400ms macOS, 1s Android, 1s Windows) |

## Known Limitations

### Windows
- **First Launch**: User may see UAC (User Account Control) prompt—this is normal for Windows installers
- **Windows 11 Battery Saver**: May limit background activity on battery; disable if syncing pauses
- **Firewall**: Requires firewall exception for port 35647 (installer handles this)

### macOS
- **Sandboxing**: App sandbox disabled (required for local network access); re-enabling would break mDNS
- **Gatekeeper**: On first launch, may show "Cannot open unknown developer" warning
  - Solution: Right-click app → Open (or add to exceptions)
- **Local Network Permission**: macOS 12+ requests permission on first launch—grant it

## Testing Background Sync

### Windows
1. Start CrossFlow (app opens)
2. Copy text on Android or macOS
3. Verify text appears on Windows clipboard
4. Close CrossFlow window to system tray (no sync interruption)
5. Copy more text on remote device
6. Verify Windows still receives updates (sync active in background)

### macOS
1. Start CrossFlow (menu bar icon appears)
2. Copy text on Android or Windows
3. Verify text appears on macOS clipboard
4. Close info window (Cmd+W or red button)
5. Copy more text on remote device
6. Verify macOS still receives updates (sync active in menu bar)
7. Click menu bar icon → Devices to see connected peers

## Troubleshooting

### "App closes when I close the window"
**Android/other**: Not applicable (Android runs foreground service)
**Windows**: Check system tray—app may have minimized
  - Solution: Click system tray icon in taskbar to restore
**macOS**: Verify `applicationShouldTerminateWhenLastWindowClosed()` returns false
  - Rebuild: `bash scripts/build-macos.sh`

### "Sync stops after closing window"
**Windows/macOS**: Restart the app
**Windows**: Check if system tray icon still visible (may be hidden in icon tray)
  - Right-click taskbar → Taskbar settings → Notification area → Select which icons appear
**macOS**: Check menu bar for clipboard icon
  - If not visible: Relounch from Applications folder

### "Port 35647 already in use"
**Windows**: Another instance may be running
  - Solution: Check Task Manager → End previous CrossFlow process
**macOS**: Another instance may be running
  - Solution: Activity Monitor → Kill previous CrossFlow process

## Architecture Notes

### Thread Model
- **Main thread**: Handles UI updates, menu interactions
- **Background threads**: Polling threads, network listeners, discovery threads
- **Async operations**: All I/O is non-blocking to prevent UI freezing

### Memory Management
- **Long-lived**: Background sync threads persist for app lifetime
- **Resources**: TCP listeners keep ports open; cleaned up on exit
- **Leaks**: Properly closed connections and cancelled timers on shutdown

### Network Resilience
- **Reconnection**: Auto-discovers peers every 5-30 seconds
- **Failover**: mDNS fails → UDP broadcast kicks in instantly
- **Reachability**: Peers marked as "disconnected" after 3 failed sends, then re-discovered

## Future Enhancements

Possible improvements:
1. **Notification on Sync**: Toast/notification when clipboard is updated remotely
2. **Sync History UI**: View detailed sync history (timestamps, source devices)
3. **Selective Sync**: Exclude certain apps from syncing (e.g., passwords, secrets)
4. **Data Limits**: Automatic clearing of sync history after 24 hours
5. **Wake-on-LAN**: Wake peer devices before syncing to multi-computer homes
