# Future updates — roadmap and how-to

## 1. Real on-device LLM (llama.cpp via JNI)

The AI layer is engine-based, so adding a local LLM means adding one class:

1. Build `llama.cpp` for Android with its NDK toolchain (the repo's `examples/llama.android` shows the exact CMake setup) or use a prebuilt AAR.
2. Add the produced `.so` files under `app/src/main/jniLibs/arm64-v8a/`.
3. Implement `class LocalLlamaEngine : AiEngine` — load a quantized model (Q4_K_M, 1–2 B params) from `filesDir`, run inference on `Dispatchers.Default`, enforce a ~2 GB memory ceiling for 6 GB RAM phones.
4. Add a model-download screen (files are ~1 GB; download once from Hugging Face, store privately).
5. Register it in `AiRouter` between cloud and offline fallback.

## 2. Better wake word

Replace/augment the SpeechRecognizer loop with openWakeWord (ONNX, runs on CPU) or Picovoice Porcupine (free for personal use) for reliable always-on detection at low power.

## 3. Streaming cloud replies

Switch `CloudEngine` to OkHttp SSE (server-sent events) and emit partial text into the chat bubble.

## 4. Contact lookup (opt-in)

Add `READ_CONTACTS` (runtime) to resolve "call mom" to a number. Keep it opt-in and revocable like everything else.

## 5. Version bumps

All versions live in `gradle/libs.versions.toml`. Bump `agp`, `kotlin`, `composeBom` etc. there — no code changes needed for routine upgrades. Keep AGP and Gradle compatible (AGP 8.5.x needs Gradle 8.7+; this project pins Gradle 8.9).

## 6. Localization

User-facing strings live in `res/values/strings.xml` (English) and `res/values-ur/strings.xml` (Urdu). Add more locales as `values-<lang>/` folders; command keywords live in `domain/IntentRouter.kt` and confirmations in `device/DeviceActionExecutor.kt` (PhraseBook).
