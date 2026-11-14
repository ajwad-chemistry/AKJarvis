# AK Jarvis 🤖

A native Android AI assistant with a hybrid **Cloud + Local/Offline** architecture, built with Kotlin and Jetpack Compose.

| | |
|---|---|
| **Platform** | Android 8.0+ (API 26), optimized for 6 GB RAM devices |
| **Language** | Kotlin 2.0, Jetpack Compose (Material 3) |
| **AI** | Cloud: any OpenAI-compatible API (default: Z.ai GLM, free-tier `glm-4.5-flash`) · Offline: rule-based engine that keeps device commands working with zero network |
| **Voice** | Android `SpeechRecognizer` (input) + `TextToSpeech` (output) |
| **Languages** | English, Urdu (اردو), Roman Urdu |
| **Storage** | Room database + DataStore — all data stays on the device |

## Features

- 💬 **AI chat assistant** with persistent history (Room)
- 🎙 **Voice input & spoken replies** (push-to-talk mic button)
- 🌐 **Urdu, Roman Urdu and English** — chat, commands, and replies
- 👂 **Optional wake word** ("Jarvis…") via a foreground service you explicitly enable
- 🚀 **App launching** by voice ("open WhatsApp" / "kholo youtube")
- ⚙️ **Settings shortcuts** (Wi-Fi, Bluetooth, battery, display, volume, airplane mode…)
- 🔔 **Notification reading** — only after you explicitly grant Notification Listener access
- ⏰ **Task reminders** with exact alarms + reboot-safe rescheduling
- 📝 **Notes & task management** with a dedicated screen
- 🔍 **Web search** commands
- 📁 **File access** only through the system file picker (Storage Access Framework)
- 🎭 **Customizable personality** — name, style (professional / friendly / funny), custom instructions
- 🌗 **Dark & light themes** (plus system default)
- 🛡 **Privacy-first**: no analytics, no tracking, API key stays on-device and out of backups

## Architecture

```
app/src/main/java/com/akjarvis/app/
├── AKJarvisApp.kt            # Application + DI init
├── MainActivity.kt           # Single activity, Compose navigation
├── di/ServiceLocator.kt      # Manual dependency graph
├── ai/                       # AI logic (independent of UI)
│   ├── AiEngine.kt           # Common engine interface
│   ├── CloudEngine.kt        # OpenAI-compatible cloud client (Retrofit + kotlinx-serialization)
│   ├── OfflineEngine.kt      # Rule-based offline fallback
│   ├── AiRouter.kt           # Cloud → offline graceful degradation
│   └── CloudDtos.kt
├── data/
│   ├── db/                   # Room: chat messages, notes, reminders
│   ├── prefs/                # DataStore settings (theme, language, API key…)
│   └── repo/                 # Repositories
├── device/                   # Android system integration
│   ├── AppLauncher.kt        # App list + fuzzy launch
│   ├── DeviceActionExecutor.kt # Executes commands, safety rules
│   ├── NotificationListener.kt # Opt-in notification reading
│   ├── Reminders.kt          # AlarmManager + receivers
│   └── FileManager.kt        # SAF helpers
├── domain/
│   ├── IntentRouter.kt       # EN / Roman Urdu / Urdu command parser
│   └── model/                # Pure domain models
├── voice/
│   ├── SpeechInputManager.kt # SpeechRecognizer wrapper
│   ├── TtsManager.kt         # TextToSpeech wrapper
│   ├── TtsSingleton.kt
│   └── WakeWordService.kt    # Optional foreground wake-word service
└── ui/
    ├── theme/                # Dark/light futuristic theme
    ├── components/           # Animated orb, message bubbles, voice indicator
    ├── chat/                 # Chat screen + ViewModel
    ├── settings/             # Settings screen + ViewModel
    ├── tasks/                # Notes & reminders screen + ViewModel
    └── permissions/          # Permission management screen
```

**Layering:** `ui → domain/ai/data → device`. The UI never talks to Android system services directly; AI engines never touch the UI.

## Quick start (build in Android Studio)

1. Install [Android Studio](https://developer.android.com/studio) (Koala or newer).
2. `File → Open…` and select this `AKJarvis` folder.
3. Let Gradle sync (it downloads AGP 8.5.2, Kotlin 2.0.20 and libraries automatically).
4. Press **Run** ▶ on your device or emulator (minSdk 26).

Or from the command line — see [docs/BUILDING.md](docs/BUILDING.md).

## Configure the AI

1. Get a free API key from [Z.ai](https://z.ai) (model `glm-4.5-flash` is free-tier) or use any OpenAI-compatible provider.
2. Open **AK Jarvis → Settings → Cloud AI configuration**.
3. Paste the key, adjust base URL / model if needed.
4. Done — the app calls `{base-url}chat/completions` with your key in the `Authorization` header.

Without a key the app stays fully usable in **Offline Mode** (device commands + basic conversation).

## Security model

See [docs/PERMISSIONS.md](docs/PERMISSIONS.md) for every permission and why it exists, and [docs/LIMITATIONS.md](docs/LIMITATIONS.md) for known constraints. Summary of hard rules baked into the code:

- Microphone is used **only** during an explicit listening session or the opt-in wake-word service.
- Calls/SMS commands only **open the dialer/messaging app** — nothing is sent without you pressing send.
- No passwords/OTPs are ever requested, stored or transmitted.
- Notification reading requires the separate, revocable Notification Listener access.
- The API key is stored in DataStore and excluded from cloud backups.

## License

For personal use. GLM model responses are generated by the provider you configure.
