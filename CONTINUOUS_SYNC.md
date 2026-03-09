# CrossFlow: Apple-Style Continuous Sync (No Manual Activation Needed)

## ✨ What's New
CrossFlow now supports **true continuous background sync** like Apple AirDrop/Handoff - no need to open any app to enable syncing!

- ✅ **Auto-starts on boot** (both Windows & Android)
- ✅ **Continuous peer discovery** (even after devices go offline/online)  
- ✅ **Always syncing** (in background, no UI needed)
- ✅ **Bidirectional** (Windows ↔ Android)

---

## Windows Setup (Auto-Start Continuous Sync)

### Option 1: Simple Registry (Recommended)
```cmd
REM Run this ONCE to enable auto-start on login:
reg add "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run" ^
  /v CrossFlow /t REG_SZ /d "C:\Program Files\CrossFlow\CrossFlow.exe" /f

REM Then just run the app once
```

### Option 2: Use NSSM (Windows Service)
For more robust experience with automatic restart on crash:

1. **Download NSSM** from https://nssm.cc/download
2. **Extract and run** from PowerShell (as Administrator):
   ```powershell
   cd C:\path\to\nssm\
   .\nssm install CrossflowSync "C:\Program Files\CrossFlow\CrossFlow.exe"
   .\nssm set CrossflowSync Start SERVICE_AUTO_START
   .\nssm start CrossflowSync
   ```
3. **Verify**: Run `services.msc` and look for "CrossflowSync"

### Option 3: Startup Folder (Simple)
1. Press `Win+R`, type: `shell:startup`
2. Create **Shortcut** to `CrossFlow.exe`
3. Done! App launches on next login

### Testing
After setting up auto-start:
1. **Restart Windows** - CrossFlow should launch automatically
2. **Don't open any window** - it runs in system tray
3. **Copy text on Android** - should instantly appear on Windows clipboard
4. Open CrossFlow UI anytime to **see connected devices**

---

## Android Setup (Auto-Start Foreground Service)

### Installation
```bash
cd android
.\gradlew :app:installDebug
# Or: .\gradlew :app:installRelease
```

### Auto-Start Verification
- App auto-launches via `BootReceiver` on device boot ✓
- Runs as **Foreground Service** (persistent, appears in notifications)
- Continues syncing even after closing the app ✓

### Testing  
1. **Restart Android device** - sync service starts automatically
2. **Close the app** - notification stays visible, syncing continues
3. **Copy text on Windows** - should instantly appear on Android clipboard
4. Open CrossFlow app anytime to **see connected devices**

---

## How Continuous Discovery Works

### Windows (Every 5 Seconds)
```
┌─ Continuous Peer Health Check
│  ├─ Try to reach all known peers
│  ├─ Send keep-alive announcements
│  ├─ Detect disconnected devices
│  └─ Mark as offline if unreachable >30s
│
└─ Every 15 Seconds: Re-scan mDNS
   └─ Check for new devices on network
```

### Android (Every 10 Seconds)
```
┌─ Announce Self to Windows
│  ├─ Send: "I'm here at [IP:PORT]"
│  └─ Ensures Windows can find us
│
├─ Every 1 Second: Poll Clipboard
│  └─ Detect when user copies text
│
└─ Every 30 Seconds: Re-start NSD Discovery
   └─ Catch new Windows devices that come online
```

---

## Troubleshooting: Why Sync Isn't Working Without Opening App

### Windows Issue ❌
**Problem**: Clipboard not syncing even though Windows has restarted

**Solution**:
1. Check if CrossFlow.exe is running:
   ```powershell
   Get-Process | grep CrossFlow
   ```
   
2. If not running, check auto-start setup:
   ```powershell
   # Check registry
   reg query "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run" /v CrossFlow
   ```

3. Manually start for now:
   ```powershell
   C:\Program Files\CrossFlow\CrossFlow.exe
   ```

4. Verify it's syncing:
   - Look for tray icon with "✓" indicator
   - Double-click tray icon to open UI and verify "Connected: X" shows devices

### Android Issue ❌
**Problem**: Clipboard not syncing even though Android has restarted

**Solution**:
1. Check if service is running:
   ```bash
   adb shell "ps | grep ClipboardSyncService"
   ```

2. Check if auto-start is enabled:
   ```bash
   adb shell "dumpsys package | grep crossflow" 
   ```

3. Manually open app once to activate service
4. Close app - service keeps running
5. Check Android notification bar - should show "Passive Clipboard Sharing Active ✓"

### Devices Not Discovering Each Other ❌
**Problem**: Both apps running but devices don't show up

**Solution**:
1. **Verify same Wi-Fi network**: Both on exactly the same WiFi (not guest/5GHz only)
2. **Check firewall**: Port 35647 (TCP) must be open
   ```cmd
   netsh advfirewall firewall add rule name="CrossFlow" dir=in action=allow protocol=tcp localport=35647
   ```
3. **Wait 10-15 seconds**: Discovery takes time
4. **Check IP ranges**: Both should be on 10.x or 192.168.x
   - Windows: `ipconfig`  
   - Android: Settings → Wi-Fi → Connected network → Details

### Clipboard Not Syncing (Devices Connected) ❌
**Problem**: Devices show as connected but clipboard doesn't sync

**Solution**:
1. **Try copying large text**: Text must be > 1 character, < 1MB
2. **Check logs** (Windows):
   ```powershell
   Get-Content "$env:USERPROFILE\.crossflow_sync.log"
   ```
3. **Try copying again on both platforms**: Sometimes first copy fails
4. **Restart both apps**: Clear any stuck state

---

## Architecture

### Windows
- **Main.kt**: Launches sync on startup, minimizes to tray (not close)
- **SyncManager.kt**: 
  - Starts sync immediately when app launches
  - Runs continuous peer health check (every 5s)
  - Runs continuous mDNS re-scan (every 15s)
- **ClipboardMonitor.kt**: Polls system clipboard (400ms interval)
- **TcpServer.kt**: Listens for messages from Android (port 35647)

### Android  
- **ClipboardSyncService.kt**: Foreground service (auto-starts on boot via BootReceiver)
  - Runs continuous peer announcements (every 10s)
  - Polls clipboard (every 1s)
  - Runs continuous NSD re-discovery (every 30s)
- **TcpServer.kt**: Listens for messages from Windows (port 35647)
- **BootReceiver.kt**: Auto-starts service on device boot

---

## Key Differences from Previous Version

| Feature | Before | Now |
|---------|--------|-----|
| **Auto-start** | ❌ Manual open needed | ✅ Auto-starts on boot |
| **Discovery** | One-time only | ✅ Continuous (every 5-30s) |
| **Sync without window** | Windows ❌ | ✅ Both platforms |
| **Device detection** | Might miss devices | ✅ Re-scans automatically |
| **Reconnect** | Manual | ✅ Automatic |
| **Keep-alive** | None | ✅ Every 5s health check |

---

## Testing Steps (Full Workflow)

### First Time Setup
1. **Install on both** (both must be on same Wi-Fi):
   ```bash
   # Windows
   .\gradlew build
   
   # Android  
   .\gradlew :app:installDebug
   ```

2. **Enable Windows auto-start**:
   ```cmd
   reg add "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run" ^
     /v CrossFlow /t REG_SZ /d "C:\Program Files\CrossFlow\CrossFlow.exe" /f
   ```

3. **Open both apps once** to initialize

4. **Close both apps** (minimize Windows to tray; close Android app)

5. **Copy text on Windows** → Should appear on Android
   - If not: Open Android app → Service starts
   - Then try copying again

### After Restart
1. **Restart Windows** (CrossFlow auto-launches)
2. **Restart Android** (Service auto-launches)
3. **No manual interaction needed** ✓
4. **Copy on either device** → Syncs instantly

---

## Tips & Tricks

### Force Immediate Discovery
- **Windows**: Open CrossFlow window → Forces health check immediately
- **Android**: Open app → Service re-initializes discovery

### Monitor Background Sync (Windows)
Right-click tray icon → Shows number of connected devices

### Monitor Background Sync (Android)  
Notification bar shows "Passive Clipboard Sharing Active ✓"

### Debug Logs
- **Windows**: Check `.crossflow_sync.log` in user home directory
- **Android**: `adb logcat -s ClipboardSyncService | grep "CrossFlow"`

---

## Limitations & Known Issues

1. **First device might be missed**: After initial Android NSD registration, Windows might not discover it for 5-10 seconds (normal for mDNS)
2. **Clipboard size limit**: 1MB max (typical clipboard limit)
3. **Local network only**: Must be on same Wi-Fi subnet
4. **Text only**: Binary clipboard content (images, files) not supported yet

---

## Future Improvements
- [ ] Persistent device reconnection after network change
- [ ] Clipboard content filtering/size management
- [ ] Wired network sync (Ethernet)
- [ ] Cloud backup for missed syncs
- [ ] Cross-network sync (VPN)

