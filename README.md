# app

Android Native starter project (Empty Activity, Kotlin, Jetpack Compose) prepared as baseline for Story 0.1.

## Local setup

- Ensure Java 17 is installed and available in `PATH`.
- Ensure Android SDK is installed with platform tools (`adb`) and API 36 platform packages.
- Verify tools:
  - `java -version`
  - `adb version`

## Build and run

- Build debug APK:
  - `./gradlew :app:assembleDebug`
- Optional cleanup when an old debug signature is present:
  - `adb uninstall io.github.mateovich.allergotracker || true`
- Install on connected emulator/device:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch app:
  - `adb shell am start -n io.github.mateovich.allergotracker/.MainActivity`

### Container/remote ADB note

- If `adb devices` in your shell cannot see emulator/device directly, point to host ADB server:
  - `ADB_SERVER_SOCKET=tcp:host.docker.internal:5037 adb devices`
  - `ADB_SERVER_SOCKET=tcp:host.docker.internal:5037 adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - `ADB_SERVER_SOCKET=tcp:host.docker.internal:5037 adb shell am start -n io.github.mateovich.allergotracker/.MainActivity`

## Notes

- This baseline intentionally avoids non-essential backend/network dependencies.
- Future stories will scaffold architecture layers and persistence foundations.

## Project workflow

- Git workflow quick reference: `docs/git-workflow.md`
