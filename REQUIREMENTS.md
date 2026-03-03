## Requirements Document: Android Foreground-Service Wrapper for AntTP (Rust/Actix) Local Proxy

### 1. Purpose & Scope
**Goal:** Package the existing AntTP Rust application (Actix-based HTTP proxy supporting `CONNECT`) as an **Android app** that runs **continuously** in the background as a **Foreground Service**, exposing a **loopback-only** local proxy endpoint for **proxy-aware apps**.

**In scope:**
- Android app with no user-facing UI beyond required system screens/notifications
- Foreground service lifecycle management (start/stop/restart)
- Rust `cdylib` embedded in Android APK
- Local proxy listeners bound to `127.0.0.1` on fixed ports
- QUIC routing handled by existing AntTP logic and its connection pool

**Out of scope (for initial version):**
- System-wide traffic interception (no `VpnService`)
- Transparent proxying, packet capture, or per-app routing
- TLS MITM / certificate installation workflows
- Complex configuration UI

---

### 2. Functional Requirements

#### 2.1 Proxy Behavior
- **FR-PROXY-1:** The service MUST start AntTP and listen on `127.0.0.1:18888` (HTTP proxy).
- **FR-PROXY-2:** The service MUST start AntTP and listen on `127.0.0.1:18889` (second proxy port; treated equivalently unless AntTP distinguishes).
- **FR-PROXY-3:** The proxy MUST support **HTTPS tunneling via HTTP `CONNECT`**.
- **FR-PROXY-4:** The proxy MUST NOT bind to non-loopback interfaces (no `0.0.0.0`, no LAN exposure).
- **FR-PROXY-5:** Proxy-aware clients MUST be able to configure proxy host `127.0.0.1` with either port and route traffic through AntTP.

#### 2.2 Service Lifecycle
- **FR-SVC-1:** The proxy MUST run as an Android **Foreground Service** with a persistent notification.
- **FR-SVC-2:** The service MUST be able to start AntTP exactly once (idempotent start).
- **FR-SVC-3:** The service MUST be able to stop AntTP cleanly when the service is stopped.
- **FR-SVC-4:** On process/service restart, the system MUST be able to start the service again and re-launch AntTP.

#### 2.3 Configuration
- **FR-CFG-1:** Initial version MUST use fixed ports: `18888` and `18889`.
- **FR-CFG-2:** Initial version MUST use loopback-only bind address `127.0.0.1`.
- **FR-CFG-3:** AntTP MUST NOT read CLI arguments on Android; configuration MUST be supplied programmatically (hardcoded defaults or structured config passed from Kotlin).

---

### 3. Non-Functional Requirements

#### 3.1 Reliability & Robustness
- **NFR-REL-1:** The service MUST tolerate Android killing the process; restart behavior should be compatible with `START_STICKY`.
- **NFR-REL-2:** Start/stop MUST be safe under repeated calls (no crashes, no double-start panics).

#### 3.2 Performance
- **NFR-PERF-1:** The proxy engine SHOULD run primarily in Rust and reuse AntTP’s existing async/QUIC pooling model.
- **NFR-PERF-2:** JNI calls MUST return quickly and MUST NOT block the Android main thread.

#### 3.3 Security
- **NFR-SEC-1:** The proxy MUST only listen on `127.0.0.1`.
- **NFR-SEC-2:** The app MUST NOT expose any admin endpoints on non-loopback interfaces.
- **NFR-SEC-3:** No hardcoded sensitive secrets (keys/tokens) may be embedded; use placeholders/configurable sources if needed later.

#### 3.4 Compatibility
- **NFR-COMP-1:** Target Android version SHOULD be Android 11+ (API 30) initially for simplest support.
- **NFR-COMP-2:** Support at minimum `arm64-v8a`; `x86_64` SHOULD be supported for emulator testing.

---

### 4. Kotlin/Android Component Requirements

#### 4.1 Application Structure
- **AR-APP-1:** Android project MUST include:
    - A Foreground `Service` (service-only app acceptable)
    - Minimal optional Activity only if needed to start/stop service or request permissions (may be omitted if service start is triggered in another supported way)

#### 4.2 Foreground Service
- **AR-SVC-1:** Service MUST create a Notification Channel (Android 8+).
- **AR-SVC-2:** Service MUST call `startForeground(notificationId, notification)` promptly.
- **AR-SVC-3:** Service MUST call `Native.start(...)` in `onStartCommand`.
- **AR-SVC-4:** Service MUST call `Native.stop()` in `onDestroy`.

#### 4.3 Permissions & Manifest
- **AR-MAN-1:** Manifest MUST request `android.permission.INTERNET`.
- **AR-MAN-2:** Manifest MUST request `android.permission.FOREGROUND_SERVICE`.
- **AR-MAN-3 (Optional future):** Boot autostart may require `RECEIVE_BOOT_COMPLETED` + receiver; not required for initial version.

#### 4.4 JNI Binding Layer
- **AR-JNI-1:** Kotlin MUST declare a `Native` object/class with `external` methods:
    - `start(httpPort: Int, httpsPort: Int)` OR `start()` (ports fixed)
    - `stop()`
- **AR-JNI-2:** Kotlin MUST load the Rust library via `System.loadLibrary("<libname>")`.

---

### 5. Rust Component Requirements

#### 5.1 Build Artifact
- **RR-RUST-1:** AntTP MUST be buildable as a shared library: `crate-type = ["cdylib"]`.
- **RR-RUST-2:** The Rust library MUST export JNI-callable symbols for start/stop.

#### 5.2 Runtime & Threading Model
- **RR-RT-1:** The Rust side MUST create/own a Tokio runtime on a dedicated thread when started from Android.
- **RR-RT-2:** The Rust side MUST ensure “single running instance” behavior (idempotent start).
- **RR-RT-3:** Rust MUST implement a shutdown mechanism that stops Actix cleanly and allows the thread to join.

#### 5.3 Actix Server Control
- **RR-ACTIX-1:** AntTP MUST store and expose an Actix `ServerHandle` such that `stop()` can trigger shutdown.
- **RR-ACTIX-2:** Shutdown SHOULD be graceful (finish active requests if feasible), but MUST complete reliably.

#### 5.4 Network Binding
- **RR-NET-1:** AntTP MUST bind HTTP listener to `127.0.0.1:18888`.
- **RR-NET-2:** AntTP MUST bind HTTPS/TLS listener (if used) or second proxy listener to `127.0.0.1:18889`.
- **RR-NET-3:** If AntTP’s “HTTPS port” is implemented via `rustls` server binding, requirements must clarify whether it is:
    - a TLS-wrapped HTTP API endpoint, OR
    - a plain HTTP proxy port intended for CONNECT.

  **Decision required:** For proxy-aware client compatibility, both ports SHOULD accept standard HTTP proxy semantics including CONNECT (plain TCP). If one port is truly TLS-wrapped server HTTP, many clients won’t use it as a proxy.

#### 5.5 Logging
- **RR-LOG-1:** Rust logging SHOULD integrate with Android logcat (implementation-defined), or at minimum remain accessible via standard logging facilities for debugging builds.

---

### 6. Build & Packaging Requirements

#### 6.1 Android Build System
- **BR-GRADLE-1:** The Android app MUST be built with Gradle (Android Studio standard).
- **BR-NDK-1:** The project MUST include NDK support to package native `.so` libraries.

#### 6.2 Rust Cross-Compilation
- **BR-RUST-1:** The build MUST produce `.so` for at least:
    - `arm64-v8a`
    - (Optional but recommended) `x86_64`
- **BR-RUST-2:** The `.so` artifacts MUST be placed in the correct ABI directories so Gradle packages them into the APK:
    - `app/src/main/jniLibs/arm64-v8a/lib<name>.so`, etc.

#### 6.3 Build Reproducibility
- **BR-REP-1:** The build process MUST be scriptable (documented commands) to produce the same artifacts on Linux dev machines.

---

### 7. Testing Requirements

#### 7.1 Local Proxy Validation
- **TR-PROXY-1:** From an Android device/emulator, a proxy-aware client MUST be able to:
    - Set proxy to `127.0.0.1:18888`
    - Load an HTTPS site via CONNECT successfully (tunnel established)
- **TR-PROXY-2:** Confirm the proxy is unreachable from LAN (cannot connect via device IP).

#### 7.2 Lifecycle Testing
- **TR-LIFE-1:** Start service → proxy reachable
- **TR-LIFE-2:** Stop service → ports closed / connection fails
- **TR-LIFE-3:** Force-stop / kill process → service restart behavior verified (as designed)

#### 7.3 Emulator/Device Matrix
- **TR-MAT-1:** Validate on at least one real device (ARM64).
- **TR-MAT-2:** Validate on one emulator (x86_64) if supported.

---

### 8. Deployment & Operational Requirements

#### 8.1 User Interaction
- **OR-UX-1:** Persistent notification MUST indicate that the proxy is running and show the listening endpoint/ports.
- **OR-UX-2:** Provide a clear stop action (notification action or minimal control screen), optional for v1 but recommended.

#### 8.2 Observability
- **OR-OBS-1:** Provide a way to collect basic status: running/stopped, current ports, basic error state.
    - Implementation options: logcat only, or a simple status endpoint bound to loopback.

---

### 9. Open Decisions / Risks (Track as tasks)
- **OD-1:** Clarify whether the `18889` listener is meant to be:
    - a second *plain proxy port*, or
    - a TLS-wrapped HTTP server port (which many proxy clients won’t use directly).
- **OD-2:** Decide whether to support a single proxy port configuration to maximize client compatibility.
- **OD-3:** Decide autostart behavior (boot receiver vs manual start).
- **RISK-1:** OEM background restrictions can still kill services; foreground mitigates but cannot guarantee permanence.
- **RISK-2:** Some apps ignore Android proxy settings; per-app configuration may be required.

---

### 10. Deliverables
- **D-1:** Android project (Kotlin) containing Foreground Service + JNI bridge
- **D-2:** Rust `cdylib` build integrated into APK
- **D-3:** Documented build steps (Rust cross-compile + Gradle build)
- **D-4:** Basic test checklist and validation instructions for configuring a proxy-aware app

---

If you want, I can also produce a **task breakdown template** aligned to these sections (Epic → Stories → Tasks), but I’ll keep it out until you ask so it stays easy to paste into your tracker.