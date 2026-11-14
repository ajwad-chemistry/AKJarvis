# Building AK Jarvis

## Requirements

- JDK 17 (Temurin/Zulu)
- Android SDK: platform `android-35`, build-tools `34.0.0`, platform-tools
- Gradle 8.9 (or just use the included wrapper — see below)

## Android Studio (recommended)

1. Open the project folder in Android Studio.
2. Wait for Gradle sync.
3. **Run ▶** — installs the debug build on the selected device.

## Command line

```bash
# one-time: point Gradle at your SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

# debug APK
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# run the unit tests
./gradlew :app:testDebugUnitTest

# optimized release build (unsigned; add a signing config to distribute)
./gradlew :app:assembleRelease
```

## Install the APK on a phone

1. Copy `app-debug.apk` to the phone.
2. Tap it; allow "Install unknown apps" for your file manager when prompted.
3. Open AK Jarvis, grant the permissions you want (all optional, in-app explanations provided).

Or via ADB: `adb install app-debug.apk`.

## Memory notes for low-RAM build machines

`gradle.properties` already limits the Gradle JVM to 2 GB (`org.gradle.jvmargs=-Xmx2048m`), which is enough for this project size on a 6 GB machine.
