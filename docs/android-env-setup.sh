#!/usr/bin/env bash
# Setup script for a cloud environment used for Android development.
# Requires network access to dl.google.com and maven.google.com.
set -e
export ANDROID_HOME=/opt/android-sdk
if [ ! -d "$ANDROID_HOME/platforms/android-35" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools" && cd /tmp
  curl -sS -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
fi
echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.bashrc
