# CrossFlow macOS Build Guide

This guide explains how to build and distribute the CrossFlow macOS app.

## Prerequisites

- macOS 12.0 or later
- Xcode Command Line Tools (swift compiler)
- bash shell

## Quick Start

### 1. Install Xcode Command Line Tools

```bash
xcode-select --install
```

### 2. Build the App

On your macOS machine, run:

```bash
cd /path/to/crossflow
bash scripts/build-macos.sh
```

This will:
1. Compile the Swift code
2. Create an app bundle (CrossFlow.app)
3. Code-sign the bundle
4. Create a DMG distribution package

### 3. Run the App

```bash
open macos/build/CrossFlow.app
```

The app will start as a menu bar application (not in Dock).

## Build Artifacts

- **App Bundle**: `macos/build/CrossFlow.app` — Ready to run or distribute via zip
- **DMG**: `macos/build/CrossFlow-1.0.0.dmg` — Mac installer image (double-click to mount)

## Signing and Notarization

For distribution outside your organization:

### Self-Signed (Development)

```bash
codesign -s - --deep macos/build/CrossFlow.app
```

### Developer Account (Release)

```bash
# Sign with your Developer ID
codesign -s "Developer ID Application: Your Name (TEAM_ID)" \
         --deep \
         --options=runtime \
         macos/build/CrossFlow.app

# Notarize (required for Gatekeeper on macOS 10.15+)
# See: https://developer.apple.com/documentation/security/notarizing_your_app_before_distribution
```

## Distribution Methods

### Method 1: App Bundle (Zip)

```bash
ditto -c -k --sequesterRsrc macos/build/CrossFlow.app CrossFlow-1.0.0.zip
```

Users can download and double-click the zip to extract the app.

### Method 2: DMG (Disk Image)

The DMG is already created by the build script. Users double-click to mount and drag CrossFlow to Applications.

### Method 3: Mac App Store

Requires Developer Account enrollment and App Store review process.

## Troubleshooting

### Build fails: "swift: command not found"

Install Xcode Command Line Tools:
```bash
xcode-select --install
```

### Code signing fails

For development, skip signing:
```bash
# Remove the codesign line from build-macos.sh
# Or run without signing:
swift build -c release --product CrossFlow
```

### App won't start on macOS 10.15 or later

The app needs to be notarized. See "Signing and Notarization" section above.

### Network issues (can't discover peers)

Verify:
1. Firewall allows port 35647 (TCP) for incoming connections
2. All devices on the same WiFi network
3. App has local network permission (System Preferences > Security & Privacy > Local Network)

## Architecture Notes

### Menu Bar App

- **LSUIElement**: Set to true (Info.plist) — runs only in menu bar, not in Dock
- **Status Item**: Shows clipboard icon in menu bar
- **Menu**: Displays discovered devices and quit option

### Background Operation

- Clipboard monitor polls every 400ms
- mDNS performs service discovery
- TCP server listens on port 35647
- All operations continue while app is in background

### Permissions

The app requires:
- **Local Network Discovery** (mDNS) — Allows Bonjour/mDNS browsing
- **Network Connections** — For TCP communication and peer discovery

These are automatically granted on first launch.

## Development

### Project Structure

```
macos/
  Sources/
    AppDelegate.swift        — Main app lifecycle
    BonjourService.swift     — mDNS registration and discovery
    ClipboardMonitor.swift   — Clipboard polling
    TCPServer.swift          — Incoming message listener
    StatusBarController.swift — Menu bar UI
    Protocol.swift           — Shared message format
  Resources/
    Info.plist              — App metadata
  CrossFlow.entitlements    — Sandbox/security settings
```

### Modifying the Code

Edit files in `macos/Sources/` and rebuild:

```bash
bash scripts/build-macos.sh
```

### Testing

1. Build the app
2. Run it in a terminal to see debug output: `macos/build/CrossFlow.app/Contents/MacOS/CrossFlow`
3. Open another terminal and check logs: `log stream --predicate 'process == "CrossFlow"'`
4. Copy text on one device, verify it appears on macOS within seconds

## Cross-Platform Notes

The message format is identical across Android, Windows, and macOS:

```json
{
  "type": "clipboard",
  "content": "text to sync",
  "source": "device_name"
}
```

Device discovery uses:
- **Primary**: mDNS (_crossflow._tcp.local)
- **Fallback**: UDP broadcast on port 35647 (if mDNS unavailable)

This allows seamless sync between any combination of platforms on the same LAN.
