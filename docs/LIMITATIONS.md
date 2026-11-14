# Known limitations (honest list)

## Voice

- **Wake word** uses Android's `SpeechRecognizer` in a restart loop inside a foreground service. Android does not guarantee continuous background recognition on every device; some vendor ROMs throttle or stop it. Expect higher battery drain while enabled, and occasional missed wake phrases. A production-grade solution would use an always-on hotword model (e.g. openWakeWord/Porcupine — Porcupine has a free tier for personal use) compiled to run on-device; this is the top candidate for a future update.
- **Urdu speech recognition** (`ur-PK`) quality depends on the Google app version on the device; on some devices it falls back to English recognition.
- **Urdu TTS** voices are missing on many devices; the app automatically falls back to an English voice (which can't pronounce Urdu script properly).

## AI

- **Local LLM:** the project ships an `AiEngine` interface and a functional offline fallback, but does **not** bundle a llama.cpp runtime. Running llama.cpp on Android requires the NDK (a JNI build of `llama.cpp`'s `examples/llama.android`) and a quantized model file (Q4_K_M, 1–2 B parameters, e.g. Qwen2.5-1.5B or Llama-3.2-1B, ~1 GB). On a 6 GB RAM phone this is feasible (the model + ~600 MB of RAM for inference) but slow (a few tokens/second on mid-range SoCs) and it would roughly double the APK/engine size. The interface is ready for it — see docs/UPDATING.md for the integration plan.
- **Cloud AI** requires an API key. The default config targets Z.ai's free-tier `glm-4.5-flash`; other free/cheap OpenAI-compatible endpoints work by editing base URL + model in Settings.
- **Streaming:** replies arrive as a complete message (no token-by-token streaming) to keep the first version simple and robust.

## Commands

- The command parser is keyword/regex based: it understands a practical set of phrasings in English, Roman Urdu and Urdu, but not arbitrary grammar. Unrecognized phrasings go to the AI instead.
- **Calls/SMS** only open the dialer/messaging app pre-filled (by design). Contact-name → number resolution is not implemented (would require reading contacts, a sensitive permission that was deliberately left out).
- **Reminders** created by voice support relative times ("in 10 minutes", "at 17:30") and Roman Urdu ("10 minute baad"); complex phrases like "next Monday at 9" are not parsed yet.
- **File management** is intentionally limited to the system file picker and Downloads, to avoid broad storage permissions.

## Platform

- minSdk 26 (Android 8.0). Devices older than that are not supported.
- The app targets Android 14+ (targetSdk 35) foreground-service rules; the wake-word service shows a permanent notification as required.
