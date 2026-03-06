# anttp_android
Android wrapper for AntTP

## Build Requirements

To build this project, you need:
- **Android NDK**: (Recommended r29)
- **Rust**: With `aarch64-linux-android` and `x86_64-linux-android` targets.
- **cargo-ndk**: Install via `cargo install cargo-ndk`.

### Environment Variables
Set `ANDROID_NDK_HOME` to your NDK installation path:
```bash
export ANDROID_NDK_HOME=/path/to/android-ndk
```

## How to Build

### 1. Build Native Libraries Only
You can build the Rust shared libraries separately:
```bash
./scripts/build_native.sh
```
This will place the `.so` files in `app/src/main/jniLibs/`.

### 2. Build Android App
The Gradle build is configured to automatically call the native build script:
```bash
./gradlew assembleDebug
```
The APK will include the native libraries for `arm64-v8a` and `x86_64`.
