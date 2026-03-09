#!/bin/bash
# CrossFlow macOS build script
# Run on macOS: bash scripts/build-macos.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/macos/build"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "CrossFlow macOS Build"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Build using Swift Package Manager
echo "Building CrossFlow with Swift Package Manager..."
swift build -c release --product CrossFlow

BUILD_OUTPUT=$(swift build -c release --product CrossFlow --show-bin-path)
EXECUTABLE="$BUILD_OUTPUT/CrossFlow"

echo "✓ Build successful: $EXECUTABLE"

# Create app bundle structure
APP_BUNDLE="$BUILD_DIR/CrossFlow.app"
CONTENTS="$APP_BUNDLE/Contents"
MACOS_DIR="$CONTENTS/MacOS"
RESOURCES_DIR="$CONTENTS/Resources"

echo "Creating app bundle at $APP_BUNDLE..."
mkdir -p "$MACOS_DIR"
mkdir -p "$RESOURCES_DIR"

# Copy executable
cp "$EXECUTABLE" "$MACOS_DIR/CrossFlow"
chmod +x "$MACOS_DIR/CrossFlow"

# Copy Info.plist
cp "$PROJECT_ROOT/macos/Resources/Info.plist" "$CONTENTS/Info.plist"

# Copy entitlements (optional, used for signing)
cp "$PROJECT_ROOT/macos/CrossFlow.entitlements" "$RESOURCES_DIR/CrossFlow.entitlements" 2>/dev/null || true

echo "✓ App bundle created at: $APP_BUNDLE"

# Code sign (required for macOS apps, can be self-signed for development)
echo "Code signing the app..."
codesign --deep --force --verify --verbose --sign - "$APP_BUNDLE" 2>/dev/null || echo "⚠ Code signing skipped (requires identity)"

# Create DMG (disk image) for distribution
echo "Creating DMG distribution package..."
DMG_OUTPUT="$BUILD_DIR/CrossFlow-1.0.0.dmg"

# Clean up any existing DMG
[ -f "$DMG_OUTPUT" ] && rm "$DMG_OUTPUT"

# Create a temporary DMG
hdiutil create -volname "CrossFlow" -srcfolder "$APP_BUNDLE" -ov -format UDZO "$DMG_OUTPUT" 2>/dev/null || {
    echo "⚠ DMG creation failed (requires macOS). App bundle is ready at: $APP_BUNDLE"
    exit 0
}

echo "✓ DMG created at: $DMG_OUTPUT"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Build Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "App Bundle: $APP_BUNDLE"
echo "DMG: $DMG_OUTPUT"
echo ""
echo "To run the app:"
echo "  open $APP_BUNDLE"
echo ""
echo "To distribute:"
echo "  - Zip: ditto -c -k --sequesterRsrc $APP_BUNDLE CrossFlow-1.0.0.zip"
echo "  - DMG: $DMG_OUTPUT"
echo ""
