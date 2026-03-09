# Testing Android → Windows Clipboard Sync (No App Required)

## Quick Diagnosis Steps

### Step 1: Start Windows (with detailed logs)
```powershell
cd d:\development\crossflow\windows
.\gradlew run
```
**Watch for:**
- Device name and local IP at startup
- "TCP listening on port 35647"
- Peer announcements from Android  
- When you copy on Android, look for: "📨 onRemoteClipboard() called" + "📋 CLIPBOARD MESSAGE incoming"

### Step 2: Open Android App (one time to initialize)
- Launch CrossFlow on Android
- You should see "Passive Clipboard Sharing Active ✓"
- Should show Windows device in "Connected devices"
- **Close the app** (service keeps running in background)

### Step 3: Test Clipboard Sync
**Android → Windows (currently broken):**
1. On Android: Copy any text
2. Look at Windows logs for "📋 CLIPBOARD MESSAGE"
3. Check if text appears on Windows clipboard

**Windows → Android (should already work):**
1. On Windows: Copy any text  
2. On Android: Text should appear in clipboard

---

## Detailed Logging Reference

### Windows Console Output
```
[CrossFlow] ═══════════════════════════════════════════════════════
ℹ️ Windows local IP: 10.0.1.29
ℹ️ Device name: DESKTOP-SSB3DJR
✓ Listening on port 35647

[TcpServer] ✓ Connection from 10.0.1.22:35000
[TcpServer] ← Received 123 bytes from 10.0.1.22:35000
[TcpServer] ✓ Decoded: type=peer_announce, source=vivo_V2109

[SyncManager] 📨 onRemoteClipboard() called
[SyncManager] 📋 CLIPBOARD MESSAGE incoming from vivo_V2109: 'test text...'
[SyncManager] ✓ Clipboard updated successfully
```

### Android Logs (via adb)
```bash
adb logcat -s ClipboardSyncService | grep -E "📋|📤|✓|❌"
```

**Expected flow when user copies:**
```
📋 LISTENER: Clipboard changed to 'my text...' (length=7)
✓ LISTENER: Broadcasting new content to peers
📤 LISTENER: Starting async broadcast to peers...
📤 broadcastClipboard() entry - text.length=7, peers.size=1
→ Sending to: DESKTOP-SSB3DJR at 10.0.1.29:35647
Connected! Writing message...
✓ Successfully sent to DESKTOP-SSB3DJR: 'my text...'
```

---

## Common Issues & Solutions

### Issue: "No peers discovered yet!"
**Cause**: Android hasn't found Windows yet
**Solution**: 
1. Wait 10-15 seconds for mDNS discovery
2. Open Android app once to force peer discovery
3. Check if both on same Wi-Fi: Windows IP should start with Android IP prefix

### Issue: "Failed to reach Windows"
**Cause**: Network/firewall blocking port 35647
**Solution**:
```powershell
# Allow Windows Firewall
netsh advfirewall firewall add rule name="CrossFlow" dir=in action=allow protocol=tcp localport=35647
```

### Issue: Socket connection timeout
**Cause**: Windows IP isn't reachable from Android
**Solution**:
1. Check Windows IP: `ipconfig`
2. Check Android IP: Settings → Wi-Fi → Details
3. Ping from Android terminal:
   ```bash
   adb shell ping -c 1 10.0.1.29  # Use Windows IP
   ```

### Issue: Message received but clipboard not updated
**Cause**: ClipboardMonitor.write() failing
**Solution**:
- Check Windows logs for: "✗ write error:"
- Look for Java exception details
- Might need to dismiss any clipboard access popups

---

## Key Differences in Latest Build

✓ Enhanced peer discovery (every 5-30s)
✓ Detailed logging at every step
✓ Better error messages with stack traces
✓ Socket connection debugging
✓ Clipboard write verification

---

## Next Steps If Still Not Working

1. **Collect full logs from both platforms**
   ```bash
   # Windows
   cd windows && .\gradlew run > windows_log.txt 2>&1
   
   # Android
   adb logcat -s ClipboardSyncService > android_log.txt &
   ```

2. **Check if socket connection succeeds**
   - Look for "Connected!" message in Android logs
   - Look for connection accepted message in Windows logs

3. **Verify message format**
   - Android encoded message should be valid JSON
   - Windows should decode it successfully

