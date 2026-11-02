# AnlaSöz 1.0.1 — on-device Turkish translation

The same app, with a second brain. Until now AnlaSöz needed a hosted chat-completions endpoint and a key of your own. Now a GGUF model can run **inside the app**, on the phone, with no key and no network at all.

## New: on-device models

- **Settings → Model → On device.** Install or import a model, tap **Use**, then use the app exactly as before. Nothing leaves the device.
- **Download inside the app.** Vexium Core Light 1.5B (1.12 GB), Vexium Core 3B (2.10 GB) and a 0.5B smoke-test model are offered, with progress, cancel and delete.
- **Verified, not trusted.** Each download is hashed while it streams and only becomes usable when the byte count and the publisher's SHA-256 both match. A partial file stays `.part` and is discarded. Imported files must start with the `GGUF` magic.
- **Bring your own model.** _Import .gguf_ takes any GGUF from storage; _Browse models_ opens Hugging Face filtered to GGUF instruct models.
- **A memory guard instead of a crash.** The engine checks free memory before loading and refuses with a readable message rather than letting the native allocator abort the process.
- **Shared machinery.** Parsing, the JSON repair round, Room history, vocabulary, practice and every screen are the same for both engines; only the call behind `TranslationRepository` changes.

## Measured on a Galaxy S23 Ultra (8 GB, Snapdragon 8 Gen 2, 6 threads)

| Stage | Vexium Core Light 1.5B |
| --- | --- |
| Load (memory-mapped) | 1–2 s |
| Prompt (~560 tokens, compressed device prompt) | 40–55 s |
| Generation | 8–13 tokens/s |
| Whole analysis | 60–120 s |

The 3B model loads and runs on the same phone but is memory-bound at 8 GB, so 1.5B is the recommended default. A 12 GB phone handles the 3B comfortably.

## Under the hood

- llama.cpp is compiled into the app (pinned `b12059`, arm64), behind a small JNI surface: load, generate once, cancel.
- Native code is built with ARMv8.2 dot-product kernels — without them quantised matmul is several times slower — and linked for 16 KB pages so Android 15's check passes.
- On-device analyses use a compressed prompt of the same JSON contract, roughly halving the tokens a small model has to read.
- Only ggml warnings and errors reach logcat; the per-graph INFO firehose measurably stalled generation.

## What this does not change

- Cloud endpoints still work exactly as before, and stay the better tutor: a 1.5B model reads tone more thinly and occasionally gets a grammar note wrong.
- No analytics, no telemetry, no new permissions. The model files live in app-private storage.
- Our own signing key, unchanged from 1.0.0 — this installs over the previous version and keeps your history, vocabulary and settings.

## Install

Download `anlasoz-1.0.1-release.apk` below. Android 8.0+ (API 26), arm64 device. Updates the 1.0.0 build in place; no uninstall needed.

SHA-256 of the APK is posted in the release notes and can be checked with `sha256sum anlasoz-1.0.1-release.apk` before installing.

## Known limitations

- On-device generation is CPU only (no NNAPI/GPU delegate) and arm64 only, so x86 emulators cannot run a local model.
- The first request after loading pays a one-off ~10 s weight-repack.
- A model keeps downloading only while the app is in the foreground.
- Sentence-by-sentence playback for long messages is still on the list.

**SHA-256**

```
07663713359033bd3011e8a486160460a3cf71145b69d0086ab401ae86d90e1c  anlasoz-1.0.1-release.apk
```
