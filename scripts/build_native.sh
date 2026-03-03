#!/bin/bash
set -e

# Ensure ANDROID_NDK_HOME is set
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME is not set."
    echo "Please set it to your Android NDK path, e.g.:"
    echo "export ANDROID_NDK_HOME=/path/to/android-ndk"
    exit 1
fi

# Define targets
TARGETS=("arm64-v8a" "x86_64")
# Map targets to Rust triples (cargo-ndk handles this usually, but we can be explicit or let it do its magic)
# For cargo-ndk, we just pass the ABI names.

echo "Building native libraries for Android..."

# Create jniLibs directory if it doesn't exist
mkdir -p app/src/main/jniLibs

# Build for each target
for TARGET in "${TARGETS[@]}"; do
    echo "Building for target: $TARGET"
    cargo ndk -t "$TARGET" -o ./app/src/main/jniLibs build --release
done

echo "Native build complete. Libraries located in app/src/main/jniLibs"
