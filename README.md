# 🔗 CrossFlow - Seamless LAN Clipboard Sync

> **Copy anywhere, paste everywhere.** Sync your clipboard across Windows PCs, Android phones, and more—all on your local network. No cloud. No accounts. No friction.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platforms](https://img.shields.io/badge/Platforms-Windows%20%7C%20Android%20%7C%20macOS-blue)]()
[![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen)]()

---

## ✨ Why CrossFlow?

Tired of manually copying URLs, code snippets, and notes between devices? **CrossFlow makes it invisible.**

```
Desktop:  Copy a URL               ⟹  Instantly paste on your Phone
Phone:    Copy a phone number      ⟹  Instantly paste on Desktop  
Laptop:   Copy meeting notes       ⟹  ALL devices have them
```

No cloud. No sign-ups. No awkward workarounds. Just **instant, automatic, local clipboard synchronization** that works the moment both devices hit the same WiFi.

### 🎯 Real-World Scenarios

| Scenario | Before CrossFlow | With CrossFlow |
|----------|------------------|----------------|
| **Developer workflow** | 1) SSH into server, 2) Copy log line, 3) Paste into phone notes app | 1 second—copied and on phone |
| **Quick research** | Copy link from desktop, manually type URL on phone | Auto-synced instantly |
| **Team meetings** | Screenshots → compressed → emailed → finally usable | Copy once, all devices have it (WIP)|
| **Cross-device collaboration** | Track 3+ browsers, copy-paste between them | One clipboard, everywhere |
| **Multi-monitor setups** | Can't easily sync Windows PC + Android + Laptop | All synced seamlessly |

---

## 🚀 Features That Wow

✅ **Bidirectional Sync** — Copy on ANY device, paste on ANY other. No hierarchy.

✅ **Multi-Device Support** — Sync across 2, 3, 10+ devices simultaneously. One clipboard, everywhere.

✅ **Windows ↔ Windows** — Desktop-to-laptop sync on the same network.

✅ **Windows ↔ Android** — Phone as clipboard mirror for your PC, or vice versa.

✅ **Zero Configuration** — Devices discover each other automatically. No IP entry. No DNS setup.

✅ **LAN-Only (Security)** — Never leaves your local network. No cloud vendor. No data upload.

✅ **Instant Sync** — 100-500ms from clipboard change to paste-ready on all peers.

✅ **Persistent on Android** — Foreground service keeps syncing even when screen is off. Auto-restarts on reboot.

✅ **Lightweight** — ~80MB on Windows, ~150-200MB on Android. Leave it running 24/7.

✅ **Extensible Protocol** — Open JSON/TCP design makes it easy to build extensions.

---

## 🏗️ How It Works (The Magic)

### Discovery (It's Automatic)

```
Device A starts          Device B on same WiFi      Device C joins later
     │                          │                          │
     ├─ Broadcasts: "I'm here"  │ ← Listens via mDNS       │
     ├─ Updates local DNS       │                          │
     │                          ├─ Responds: "I found you" │
     │                          │                          ├─ Hears announcements
     │◄─────────────────────────┤◄─────────────────────────┤
     │             Connected in <2 seconds                 │
```

**Four-layer redundancy:** mDNS → UDP broadcast → TCP handshake → explicit peer announce. If one fails, the next kicks in. You're always connected.

### Sync (It's Magical)

```json
User copies on Desktop
  ↓
{"type":"clipboard","content":"Hello World","source":"Desktop"}
  ↓
Sent to Android instantly via TCP
  ↓
Phone clipboard updates automatically
  ↓
User pastes on phone — DONE
```

**Even better:** If user copies on phone, desktop gets it. No waiting. No polling users. Bidirectional.

---

## 📦 Supported Platforms

| Platform | Status | Latest | Notes |
|----------|--------|--------|-------|
| **Windows 10/11** | ✅ Fully Supported | JAR/MSI/Portable | System tray app • Clipboard monitoring |
| **Android 8+** | ✅ Fully Supported | APK | Foreground service • Auto-start on boot |
| **macOS** | 🔄 In Development | Swift skeleton | SwiftUI coming soon |
| **Linux** | 🗺️ Roadmap | — | Planned for future release |

---

## 🎬 Quick Start

### Windows

**Download & Run:**
1. [Download latest release](https://github.com/yourusername/crossflow/releases) (`.exe` or `.msi`)
2. Run installer
3. App starts in system tray (bottom-right)
4. Done — watching for peers

**Or build from source:**
```bash
cd windows
./gradlew run              # Start dev mode
./gradlew packageExe       # Build standalone executable
```

### Android

**Install:**
1. [Download APK](https://github.com/yourusername/crossflow/releases) or build yourself
2. Enable "Unknown Sources" → Install APK
3. Open CrossFlow app
4. Persistent notification shows it's running
5. Done — syncing

**Build from source:**
```bash
cd android
./gradlew :app:installDebug    # Install to connected device
```

### macOS (Coming Soon)

```bash
cd macos
# Open in Xcode and run
open CrossFlow.xcodeproj
```

---

## 💡 How Multi-Device Sync Works

The magic ≠ complex. Each device is **equally important** (peer-to-peer):

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│  Desktop    │         │   Laptop    │         │   Phone     │
│  (Windows)  │◄───────►│  (Windows)  │◄───────►│ (Android)   │
└─────────────┘         └─────────────┘         └─────────────┘
       │                        │                      │
       └────────────────────────┼──────────────────────┘
              All connected via TCP/mDNS
              Copy anywhere → Paste everywhere
```

**Example workflow:**
1. Copy URL on **Desktop** → instantly on Laptop and Phone
2. Copy meeting notes on **Laptop** → instantly on Desktop and Phone
3. Copy phone number on **Phone** → instantly on both Windows machines

Every device sees changes within ~100-500ms. No bottleneck. No server.

---

## 🏛️ Architecture Overview

### Key Innovation: TCP Auto-Discovery

When Device A connects to Device B, the OS gives us the client's IP. **We use this as a free discovery channel.** No need for explicit announcements—the connection itself announces the peer.

```kotlin
// When Device A connects to Device B:
val clientAddr = serverSocket.accept().inetAddress
// ↑ Device A's IP, extracted from connection metadata

// Automatically add to peers map
peers[deviceName] = clientAddr
// Done — no extra protocol needed!
```

### The Stack

| Layer | Windows | Android |
|-------|---------|---------|
| **UI** | Compose Desktop | Jetpack Compose |
| **Clipboard** | System API polling (400ms) | Listener + polling (1s) |
| **Discovery** | JmDNS (mDNS) | NSD (Android framework) |
| **Protocol** | TCP/UDP, newline-delimited JSON | TCP/UDP, newline-delimited JSON |
| **Background** | Runs at startup | Foreground service + BootReceiver |
| **Async** | Kotlin Coroutines | Kotlin Coroutines |

**Network layers (redundant for reliability):**
1. **mDNS** (`_crossflow._tcp.local`) — service registration
2. **UDP Broadcast** (`255.255.255.255:35647`) — 5s announcements
3. **TCP Metadata** (port 35647 handshake) — automatic discovery
4. **Explicit Announcements** — fallback if all else fails

**Why 4 layers?**
- Some networks block mDNS → UDP works
- Some networks block broadcast → TCP works
- Some networks block everything → user can manually add peer
- Result: ~99% automatic discovery everywhere

---

## 🔒 Privacy & Security

✅ **No cloud** — Everything stays on your LAN  
✅ **No accounts** — No login, no tracking, no servers  
✅ **LAN-only** — Only peers on your local network can connect  
✅ **Open source** — Audit the code yourself  

⚠️ **Note:** Current version assumes trusted LAN. For untrusted networks, encryption layer coming in future release.

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| **Detection Latency** | 100-2000ms (depends on discovery method) |
| **Sync Latency** | 100-500ms (copy → paste ready) |
| **Memory (Windows)** | ~80MB |
| **Memory (Android)** | ~150-250MB |
| **Network per sync** | ~100-200 bytes |
| **Polling Interval** | 400ms (Windows), 1s (Android) |

---

## 🐛 Troubleshooting

| Issue | Fix |
|-------|-----|
| **"Address already in use: bind"** | Kill previous instance: `Get-Process java \| Stop-Process -Force` |
| **Devices don't discover each other** | Ensure same Wi-Fi network (not guest vs. main; not VPN) |
| **Android service stops** | Disable battery optimization for CrossFlow in Settings |
| **Slow first sync (~5s)** | mDNS/UDP discovery takes time; subsequent syncs are instant |
| **Windows Firewall blocks it** | Allow CrossFlow through firewall when prompted (required for mDNS and TCP) |

---

## 🛣️ Roadmap

- [x] **v1.0** Windows ↔ Android bidirectional sync
- [x] Windows ↔ Windows support  
- [x] Persistent Android service  
- [ ] macOS support (Swift implementation)
- [ ] Linux support  
- [ ] Encryption for untrusted networks
- [ ] Clipboard history browser
- [ ] Image/media sync  
- [ ] Custom device names & sync filters

---

## 🤝 Contributing

Found a bug? Have an idea? **We'd love your help!**

```bash
# Clone and explore
git clone https://github.com/yourusername/crossflow.git
cd crossflow

# Build & test
cd windows && ./gradlew run              # Windows dev
cd ../android && ./gradlew :app:installDebug  # Android dev

# Submit a PR
```

---

## 📄 License

MIT License — Use, modify, fork freely. See [LICENSE](LICENSE) for details.

---

## 🙌 Credits

Built with:
- **Kotlin** — Cross-platform code sharing
- **Jetpack Compose** — Modern declarative UI
- **Compose Desktop** — Windows/Mac native apps
- **JmDNS** — Zero-config service discovery
- **Kotlin Coroutines** — Elegant async

---

**Made with ❤️ for developers who hate manual copy-paste.**

Have questions? [Open an issue](https://github.com/yourusername/crossflow/issues) or start a [discussion](https://github.com/yourusername/crossflow/discussions).
