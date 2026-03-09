# Windows Distribution Guide

## Available Distribution Formats

CrossFlow for Windows is available in multiple formats to suit your needs:

### 1. MSI Installer (Recommended for Most Users)

**File**: `CrossFlow-1.0.0.msi` (~96 MB)  
**Location**: `windows/build/compose/binaries/main/msi/`

**Advantages**:
- Professional installer with system integration
- Adds Start Menu shortcuts
- Can add to Programs and Features for easy uninstall
- Handles dependencies and permissions automatically

**Installation**:
1. Double-click `CrossFlow-1.0.0.msi`
2. Follow the installer wizard
3. App starts automatically on first launch
4. Optionally add to auto-start via Windows Settings

**Uninstall**:
- Settings → Apps → Installed apps → CrossFlow → Uninstall
- Or Control Panel → Programs and Features → CrossFlow → Uninstall

### 2. Portable Launcher (For Tech Users)

**File**: `CrossFlow-Portable.bat`  
**Requires**: `CrossFlow.jar` in same directory

**Advantages**:
- No installation required
- Run from USB drive or any directory
- Full control over Java settings
- Can modify JVM options for performance tuning

**Usage**:
1. Extract/copy `CrossFlow-Portable.bat` and the JAR file to any folder
2. Double-click `CrossFlow-Portable.bat`
3. App launches in a new window

**Requirements**:
- Java 11+ installed and available in PATH
- Download from: https://www.oracle.com/java/technologies/downloads/

### 3. Manual JAR Execution (Advanced)

If the batch script doesn't work, run manually:

```powershell
java -Xmx512m -jar CrossFlow.jar
```

Or with more verbose logging:

```powershell
java -Xmx512m -Xlog:all -jar CrossFlow.jar
```

## Build Process

### Rebuilding the MSI

From the Windows project directory:

```bash
cd windows
.\gradlew packageMsi
```

Output: `build/compose/binaries/main/msi/CrossFlow-1.0.0.msi`

### Rebuilding the JAR

```bash
cd windows
.\gradlew jar
```

Output: `build/libs/CrossFlowWindows-all.jar` or similar

## System Requirements

| Component | Requirement |
|-----------|-------------|
| OS | Windows 10 / Windows 11 |
| Java | 11 or later (for portable launcher) |
| RAM | 512 MB minimum (allocated by launcher) |
| Network | Local network (LAN) for peer discovery |
| Ports | 35647 (TCP) - for clipboard sync |

## Firewall Configuration

The automatically installed MSI handles firewall exceptions. For manual deployment:

```powershell
# Add firewall rule for CrossFlow
netsh advfirewall firewall add rule name="CrossFlow" ^
    dir=in action=allow protocol=tcp localport=35647 ^
    program="C:\Program Files\CrossFlow\CrossFlow.exe"
```

## Troubleshooting

### "Java not found"

**Issue**: Portable launcher says Java not found  
**Solution**: 
1. Download Java from: https://www.oracle.com/java/technologies/downloads/
2. Install it (use default installation path)
3. Restart your system or open a new Command Prompt
4. Try running the launcher again

### "Failed to connect to other devices"

**Issue**: Can't discover Android/macOS peers  
**Solution**:
1. Ensure all devices are on the same WiFi network
2. Check Windows firewall allows port 35647:
   ```powershell
   netsh advfirewall firewall show rule name="CrossFlow"
   ```
3. Try disabling Windows firewall temporarily (for testing):
   ```powershell
   netsh advfirewall set allprofiles state off
   ```
4. Restart the app and try again

### "App closes immediately"

**Issue**: CrossFlow console window closes without starting the app  
**Solution**:
1. Right-click `CrossFlow-Portable.bat` → Edit
2. Remove `exit /b 0` from the last line (temporarily)
3. Save and run again to see error messages
4. Fix the underlying issue (usually Java-related)

### "Out of memory" errors

**Issue**: App crashes with memory errors  
**Solution**: Increase heap size in the launcher script:
```batch
REM Old:
java -Xmx512m -jar CrossFlow.jar

REM New (increase to 1GB):
java -Xmx1024m -jar CrossFlow.jar
```

## Distribution via Network/Web

To distribute CrossFlow to your team:

### Option 1: Share MSI Link

```
\\network\share\CrossFlow-1.0.0.msi
```

Users can install with one click.

### Option 2: Portable Package

Create a ZIP file with both the batch script and JAR:
```powershell
Compress-Archive -Path CrossFlow-Portable.bat, CrossFlow.jar `
    -DestinationPath CrossFlow-Portable.zip
```

Share via email, OneDrive, or network drive.

### Option 3: Auto-Deploy via Group Policy (Enterprise)

For Windows domain environments, use GP Software Installation to auto-deploy MSI.

## Performance Tuning

The portable launcher uses `-Xmx512m` (512 MB heap). Adjust for:

- **Low-power devices** (~256MB clipboard history): `-Xmx256m`
- **High-traffic syncing** (large pastes): `-Xmx1024m`

Larger heap = more memory used but better performance for large clipboard operations.

## Security Notes

- The MSI installer runs with administrator privileges (standard for Windows installers)
- The portable launcher runs with user privileges (safe to run from untrusted sources)
- No credentials or personal data are stored locally
- All clipboard sync is over LAN only (not cloud, no external servers)

## Known Issues

| Issue | Status | Workaround |
|-------|--------|-----------|
| EXE packaging blocked by file locks | Known | Use MSI instead |
| Windows Defender UAC prompt on first run | Expected | Click "Run anyway" or whitelist the jar |
| High CPU when multiple peers active | Rare | Close other apps, reduce polling interval |

## Next Steps

After installation:

1. Start CrossFlow (appears in system tray)
2. Start Android/macOS app on same network
3. Copy text on one device → paste on another
4. Done! Sync works automatically

For issues or questions, check the main [README.md](../README.md) or [CROSSPLATFORM.md](../CROSSPLATFORM.md).
