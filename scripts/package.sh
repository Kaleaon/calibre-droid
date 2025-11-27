#!/bin/bash
# Packaging script for Calibre Kotlin

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "Building Calibre Kotlin..."

# Build desktop app
cd kotlin_app
./gradlew build
cd ..

# Build Android app (if Android SDK is available)
if [ -d "android_app" ] && [ -n "$ANDROID_HOME" ]; then
    echo "Building Android app..."
    cd android_app
    ./gradlew assembleRelease
    cd ..
    echo "Android APK: android_app/app/build/outputs/apk/release/app-release.apk"
fi

# Create distribution package
DIST_DIR="dist"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# Copy desktop JAR
cp kotlin_app/build/libs/*.jar "$DIST_DIR/" 2>/dev/null || true

# Copy documentation
cp README_KOTLIN_PORT.md "$DIST_DIR/" 2>/dev/null || true
cp ROADMAP.md "$DIST_DIR/" 2>/dev/null || true

echo "Packaging complete. Distribution files in: $DIST_DIR"
