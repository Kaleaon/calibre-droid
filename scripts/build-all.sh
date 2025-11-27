#!/bin/bash
# Build script for all platforms

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=== Building Calibre Kotlin ==="

# Build shared module (if separate)
if [ -d "shared" ]; then
    echo "Building shared module..."
    # Shared is included in kotlin_app sourceSets, so it builds with kotlin_app
fi

# Build desktop/CLI app
echo "Building desktop/CLI application..."
cd kotlin_app
if [ -f "gradlew" ]; then
    ./gradlew clean build
else
    echo "Gradle wrapper not found. Please run: gradle wrapper"
    exit 1
fi
cd ..

# Build Android app
if [ -d "android_app" ]; then
    echo "Building Android application..."
    cd android_app
    if [ -f "gradlew" ]; then
        ./gradlew clean assembleDebug
    else
        echo "Gradle wrapper not found. Skipping Android build."
    fi
    cd ..
fi

echo "=== Build complete ==="
echo "Desktop JAR: kotlin_app/build/libs/"
if [ -d "android_app/app/build/outputs/apk/debug" ]; then
    echo "Android APK: android_app/app/build/outputs/apk/debug/"
fi
