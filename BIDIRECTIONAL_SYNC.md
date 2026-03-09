# CrossFlow Bidirectional Clipboard Sync Setup

## What's New ✨
CrossFlow now supports **bidirectional clipboard syncing** - both Windows → Android and Android → Windows work seamlessly even when apps are not visible/open.

### Before (Windows → Android only)
- Windows app had to stay running and open to sync to Android
- Android always synced when app/service was running

### After (Bidirectional) ✓
- **Windows ↔ Android**: Clipboard changes sync in BOTH directions automatically
- **Works with apps closed**: Both platforms continue syncing in background
- **Auto-starts on boot**: Services automatically re-launch on system restart

---

## Windows Setup

### 1. Install the App
```bash
cd windows
.\gradlew installDist
# Or: .\gradlew build --continue
```

### 2. Auto-start on Windows Login (Optional)
Run the startup registration script:
```bash
cd windows
setup_autostart.bat
```
Or manually add to Windows startup:
- **Option A**: Registry
  ```cmd
  reg add "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run" ^
    /v CrossFlow /t REG_SZ /d "[INSTALL_PATH]\CrossFlow.exe" /f
  ```
- **Option B**: Startup Folder
  - Press `Win+R`, type: `shell:startup`
  - Create shortcut to `CrossFlow.exe`

### 3. Run the App
The app will:
- ✓ Minimize to system tray on close (doesn't actually stop)
- ✓ Continue syncing in background indefinitely
- ✓ Only stop when explicitly exited via tray menu

---

## Android Setup

### 1. Install the App
```bash
cd android
.\gradlew :app:installDebug
# Or from Android Studio: Run > Run 'app'
```

### 2. Enable Auto-start (Already Built-in)
The app automatically:
- ✓ Launches `ClipboardSyncService` on boot via `BootReceiver`
- ✓ Runs as foreground service (persistent background syncing)
- ✓ Continues syncing even after app is closed

### 3. Verify Service is Running
- Open CrossFlow app
- Look for "Passive Clipboard Sharing" badge in green
- Close app → Service keeps running
- Check system services: `adb shell "ps" | grep ClipboardSyncService`

---

## How Bidirectional Sync Works

### Windows → Android
1. User copies text on Windows
2. Clipboard monitor detects change (400ms polling)
3. Broadcast to Android devices over TCP
4. Android receives and auto-sets clipboard
5. Works even if Windows is minimized ✓

### Android → Windows
1. User copies text on Android
2. Clipboard monitor detects change (1s polling + listener)
3. Broadcast to Windows devices over TCP
4. Windows receives and auto-sets clipboard
5. **NEW**: Works even if Windows app is minimized to tray ✓

### Background Peer Discovery
- **Windows**: Uses mDNS to discover Android devices on LAN
- **Android**: Uses mDNS (NSD) to discover Windows + periodically announces itself every 10 seconds
- Both maintain connection status and retry broken connections

---

## Troubleshooting

### "Devices not showing up?"
1. **Ensure same Wi-Fi network**: Both devices on same LAN
2. **Check firewall**: Port 35647 (TCP) must be open
3. **Wait 5-10 seconds**: mDNS discovery can take time
4. **Manual connection** (Windows):
   - In CrossFlow UI, add device manually if discovered

### "Clipboard not syncing?"
- **Windows**: Check taskbar/tray - app should show running status
- **Android**: 
  - Open app → "Passive Clipboard Sharing" should show "Active"
  - Check notification for "Background Syncing" status
- **Check logs**:
  - Windows: `adb logcat -s ClipboardSyncService`
  - Android logs in app (if debug mode enabled)

### "App keeps closing?"
- **Windows**: Close via tray menu only; Alt+F4/window X button just minimizes
- **Android**: Service auto-restarts; manually check: `adb shell "am start -n dev.crossflow.android/.MainActivity"`

---

## Architecture

### Windows
- **Main.kt**: UI app that minimizes to tray, keeps sync running
- **SyncManager.kt**: Orchestrates mDNS discovery, TCP server, clipboard monitoring
- **ClipboardMonitor.kt**: Polls system clipboard every 400ms
- **TcpServer.kt**: Receives clipboard messages from Android

### Android
- **ClipboardSyncService.kt**: Foreground service (runs in background always)
- **BootReceiver.kt**: Auto-starts service on device boot
- **TcpServer.kt**: Receives clipboard messages from Windows
- **Clipboard listener + polling**: Detects clipboard changes
- **Peer re-announcement**: Every 10s announces to Windows for reliability

---

## Future Improvements
- [ ] Text filtering/size limits
- [ ] Encryption for clipboard content
- [ ] Cloud sync for offline scenarios
- [ ] Clipboard history search
- [ ] Mobile app (iOS)

