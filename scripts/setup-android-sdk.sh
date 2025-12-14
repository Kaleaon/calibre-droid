#!/usr/bin/env bash
set -euo pipefail

# Local developer convenience script.
# Downloads Android commandline tools into ./.android-sdk (ignored by git)
# and writes android_app/local.properties so Gradle can build.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ROOT_DIR}/.android-sdk"
CMDLINE_DIR="${SDK_DIR}/cmdline-tools/latest"

mkdir -p "${SDK_DIR}"

if [[ ! -d "${CMDLINE_DIR}" ]]; then
  echo "Downloading Android commandline-tools..."
  TMP_ZIP="${SDK_DIR}/cmdline-tools.zip"
  # Linux command line tools (official distribution)
  curl -L -o "${TMP_ZIP}" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "${SDK_DIR}/cmdline-tools"
  unzip -q -o "${TMP_ZIP}" -d "${SDK_DIR}/cmdline-tools"
  rm -f "${TMP_ZIP}"
  # The zip extracts to cmdline-tools/cmdline-tools; normalize to latest/
  if [[ -d "${SDK_DIR}/cmdline-tools/cmdline-tools" ]]; then
    mv "${SDK_DIR}/cmdline-tools/cmdline-tools" "${CMDLINE_DIR}"
  fi
fi

export ANDROID_SDK_ROOT="${SDK_DIR}"
export PATH="${CMDLINE_DIR}/bin:${SDK_DIR}/platform-tools:${PATH}"

# sdkmanager exits after it consumes enough input, which can SIGPIPE `yes`.
# With `set -o pipefail`, that would fail the script; treat it as success.
yes | sdkmanager --licenses >/dev/null || true

echo "Installing SDK components (platform 34, build-tools 34.0.0)..."
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "sdk.dir=${SDK_DIR}" > "${ROOT_DIR}/android_app/local.properties"
echo "Done. You can now build with: (cd android_app && ./gradlew assembleDebug)"

