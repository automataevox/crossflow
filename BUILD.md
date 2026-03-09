# CrossFlow Build Instructions

Comprehensive build guide for all platforms.

## Platform Overview

| Platform | Language | Build Tool | OS Required | Output |
|----------|----------|-----------|-------------|--------|
| **Windows** | Kotlin/JVM | Gradle | Windows/Any | `.exe` installer |
| **Android** | Kotlin | Gradle | Windows/Mac/Linux | `.apk` (debug/release) |
| **macOS** | Swift | SwiftPM | macOS only | `.app` bundle + `.dmg` |

## Windows Build

Build the Windows Desktop Application:

```bash
cd windows
gradlew packageMsi
```

Output: `windows/build/compose/binaries/main/msi/CrossFlow-1.0.0.msi` (MSI Installer)

Users can:
- **Install**: Double-click MSI for system integration, Start Menu shortcuts
- **Portable**: Use the batch launcher script with the JAR file (no installation)

See [WINDOWS_DISTRIBUTION.md](WINDOWS_DISTRIBUTION.md) for distribution options.

## Android Build

Build the Android APK:

```bash
cd android
# Debug build (faster, larger)
gradlew :app:assembleDebug

# Release build (optimized, smaller)
gradlew :app:assembleRelease
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

Install on device:
```bash
gradlew :app:installDebug
```

## macOS Build ⚠️ **Requires macOS**

macOS builds **must be performed on a macOS machine**. Windows cannot natively build Swift applications.

### Option 1: Build on macOS Directly

If you have macOS available:

```bash
cd /path/to/crossflow
bash scripts/build-macos.sh
```

Output:
- App Bundle: `macos/build/CrossFlow.app`
- DMG Installer: `macos/build/CrossFlow-1.0.0.dmg`

### Option 2: Use Cross-Compilation (Advanced)

For CI/CD pipelines, use a macOS GitHub Actions runner.

### Option 3: Swift on Linux (Experimental)

Swift is available on Linux, but the macOS-specific frameworks (AppKit, Network) won't work. You'd need to rewrite UI using GTK or Qt.

## Quick Reference: All Platforms

### From Windows

```powershell
# Android
cd android; .\gradlew :app:assembleDebug

# Windows
cd windows; .\gradlew packageExe

# macOS (not possible on Windows)
# Must be done on macOS machine
# See MACOS_BUILD.md for details
```

### From macOS

```bash
# Android (requires Android SDK)
cd android && ./gradlew :app:assembleDebug

# Windows (requires Wine or cross-compilation setup)
# Generally not recommended; build on Windows instead

# macOS
cd / && bash scripts/build-macos.sh
```

### From Linux

```bash
# Android (requires Android SDK)
cd android && ./gradlew :app:assembleDebug

# Windows (requires Wine)
# Not recommended; build on Windows instead

# macOS (not possible)
```

## Environment Setup

### Windows

- Java 11+ (JDK)
- Android SDK (for Android builds)
- Gradle (provided via wrapper)

### macOS

- Xcode Command Line Tools: `xcode-select --install`
- Swift 5.9+
- macOS 12.0+

### Android (Cross-platform)

- Android SDK 33+
- Android Build Tools 34+

## CI/CD Configuration

For automated builds on GitHub Actions:

```yaml
---
# build-all.yml
name: Build All Platforms

on: [push]

jobs:
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with: { java-version: 11 }
      - run: cd android && ./gradlew :app:assembleDebug

  windows:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with: { java-version: 11 }
      - run: cd windows && .\gradlew packageExe

  macos:
    runs-on: macos-13  # or latest
    steps:
      - uses: actions/checkout@v3
      - run: bash scripts/build-macos.sh
```

## Troubleshooting

### Android

**Issue**: "SDK location not found"

**Solution**: Set `ANDROID_SDK_ROOT` environment variable or create `local.properties`:
```properties
sdk.dir=/path/to/Android/Sdk
```

### Windows

**Issue**: Gradle build hangs

**Solution**: Check available disk space (~2GB needed). Delete `.gradle` cache:
```powershell
rm -r .gradle
.\gradlew clean build
```

### macOS

**Issue**: "swift: command not found"

**Solution**: Install Xcode Command Line Tools:
```bash
xcode-select --install
```

See [MACOS_BUILD.md](MACOS_BUILD.md) for detailed troubleshooting.

## Platform-Specific Testing

### After Building

**Windows**:
```powershell
# Run the EXE installer
.\windows\build\compose\binaries\main\exe\CrossFlow-1.0.0.exe
```

**Android**:
```bash
# Install and test
adb install android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.crossflow.android/.MainActivity
```

**macOS**:
```bash
# Run the app
open macos/build/CrossFlow.app
```

## Distribution Checklist

- [ ] **Windows**: Signed/unsigned EXE ready for distribution
- [ ] **Android**: Signed APK uploaded to Play Store (or distributed via APK)
- [ ] **macOS**: Signed + notarized app ready for release (if distributing outside organization)
- [ ] Version numbers synchronized across all platforms
- [ ] Tests passing on all platforms
- [ ] README updated with known issues

---

**See Also**:
- [Android Build Details](../android/README.md)
- [Windows Build Details](../windows/README.md)
- [macOS Build Details](MACOS_BUILD.md)
