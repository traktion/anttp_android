# Project Development Guidelines

This document provides project-specific information for developers working on the AntTP Android Wrapper.

## 1. Build & Configuration

### Rust Cross-Compilation for Android
The project uses Rust's `cdylib` to generate shared libraries (`.so`) for Android.

#### Prerequisites
- **Android NDK**: Required for linking. Ensure `ANDROID_NDK_HOME` is set.
- **cargo-ndk**: Highly recommended for managing cross-compilation.
  ```bash
  cargo install cargo-ndk
  ```
- **Rust Targets**: Install the targets for the desired ABIs:
  ```bash
  rustup target add aarch64-linux-android x86_64-linux-android
  ```

#### Build Commands
To build the native library for a specific ABI:
```bash
cargo ndk -t arm64-v8a -t x86_64 -o ./app/src/main/jniLibs build --release
```
This command automatically places the resulting `.so` files into the correct Android project directories.

### Android Project Structure
The Android side is a standard Gradle project. The JNI bridge expects the native library to be named `libanttp_android.so` (derived from `Cargo.toml`).

## 2. Testing

### Rust Unit Tests
Standard Rust unit tests can be run using `cargo test`. These tests should focus on core logic independent of the JNI environment.

```bash
cargo test
```

### JNI Integration Testing
Because JNI functions require a Java environment, they are harder to unit test in Rust.
- **Mocking JNI**: For complex JNI logic, consider using the `jni` crate's mocking capabilities if necessary, but prefer moving logic into JNI-agnostic functions.
- **Android Instrumentation Tests**: Use `androidTest` in the Kotlin project to verify the end-to-end JNI bridge on an emulator or device.

### Example Test Verification
A simple test is included in `src/lib.rs` to verify the build system:
```rust
#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
        assert_eq!(2 + 2, 4);
    }
}
```

## 3. Development Information

### JNI Naming Conventions
JNI functions MUST follow the naming convention `Java_<package_name>_<class_name>_<method_name>`.
Example: `Java_com_anttp_Native_start`.

### Threading & Runtime
- **Tokio Runtime**: The Rust side manages its own Tokio runtime. When starting from JNI, spawn the runtime on a background thread to avoid blocking the Android Main Thread.
- **Graceful Shutdown**: Always ensure `stop()` triggers a graceful shutdown of the Actix server and joins the background thread.

### Code Style
- **Rust**: Follow standard `rustfmt` guidelines.
- **Safety**: Use `#[unsafe(no_mangle)]` for JNI exports in Rust 2024 edition.
- **JNI**: Minimize the logic within JNI wrappers. Delegate to pure Rust functions as much as possible.

### Logging
Integrate Rust logging with Android Logcat using the `android_logger` crate (recommended for future implementation).
