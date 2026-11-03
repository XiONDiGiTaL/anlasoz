# AnlaSöz 1.0.2 — on-device analyses, minutes down to seconds

1.0.1 put a GGUF model inside the app. 1.0.2 makes it usable: the same model, on the same phone, now finishes an analysis in well under a minute instead of never, and the release build no longer dies the moment a local request starts.

## Fixed: the crash that 1.0.1 shipped

Every on-device request in the 1.0.1 release APK ended in `java.lang.NoSuchMethodError: no non-static method "…onProgress(I)V"`. The model loaded, generation began, and the process was killed on the first progress callback: the minified build had renamed the callback method that the native code looks up by name, so the lookup threw from inside JNI.

That is fixed two ways, because one alone is a promise:

- a keep rule for the token-progress callback, so R8 leaves its name alone;
- a guard in the native loop, so a failed lookup drops the progress counter instead of leaving an exception pending that crashes the process.

Verified on the shipped artifact, not the debug build: the signed, minified APK installed over 1.0.1, loaded a model, generated a full analysis, parsed it and persisted it — no crash.

## Faster without changing the model

Same 1.5B model, same phone (Galaxy S23 Ultra, 8 GB, Snapdragon 8 Gen 2):

| | 1.0.1 | 1.0.2 |
| --- | --- | --- |
| Prompt processing | 37 tokens/s | 105 tokens/s |
| Generation | 8–13 tokens/s | 15–20 tokens/s |
| Whole analysis | did not finish in 4 minutes | 50–70 s |

Two of those are not the model's fault, and neither looked like slowness:

- **The scheduler was splitting CPU work with the GPU.** A build carrying the Vulkan backend registers a `Vulkan_Host` device, and llama.cpp will split a graph between it and the CPU even when every layer is on the CPU. Hundreds of splits, each with a copy, and the host device did not get the context's thread count — so a 28-layer model ran on roughly one core. A CPU-only load now names the CPU device explicitly, and the scheduler has nothing else to choose from.
- **Threads included the little cores.** A barrier makes every layer wait for the slowest core, so the three 2.0 GHz cores set the pace for the whole graph. The engine now uses the performance cores only (5 on this SoC).
- **Micro-batches were too small.** Prompt processing at a 256-token batch measured 105 tokens/s against 37 at 128; that is most of the difference between reading a 650-token message in 20 s and in 55 s.

## New and optional: GPU offload

Settings → _Use the GPU_ hands the model's layers to llama.cpp's Vulkan backend. It is opt-in at build time — point `VULKAN_SDK` at an SDK root and Gradle compiles the shader backend in — and the switch only appears when such a build finds a device. **The APK below is the CPU-only build**, so it never shows the switch and still installs on Android 8.0.

Phone drivers are uneven here and the release notes say so plainly: on this phone's Adreno 740 the driver rejects every quantised matmul pipeline with `VK_ERROR_UNKNOWN` (a [known Adreno issue](https://github.com/ggml-org/llama.cpp/issues/28635)), so pipeline creation fails, the exception is caught rather than aborting the process, and the model reloads on the CPU inside the same request. The card reports that instead of pretending the GPU is in use. NNAPI is not an option: llama.cpp removed that backend upstream.

## Install

Download `anlasoz-1.0.2-release.apk` below. Android 8.0+ (API 26), arm64 device, 8.2 MB. Same signing key as 1.0.0 and 1.0.1 — it installs in place, and history, vocabulary, settings and downloaded models are kept.

SHA-256 of the APK is below and can be checked with `sha256sum anlasoz-1.0.2-release.apk` before installing.

## Known limitations

- **No successful GPU generation has been observed on real hardware.** No driver here accepts the shaders, so the offload path is implemented, and its fallback is tested, but it is unproven where it would actually run. CPU is the default and the supported path.
- A small model is still a small model: a 1.5B reads tone more thinly than a hosted one, and its grammar notes are occasionally wrong. Cloud endpoints are unchanged and remain the better tutor.
- The 0.5B model in the app is an engine smoke test; it tends to echo the response schema instead of filling it.
- The first request after a load still pays a one-off weight-repack.
- A model keeps downloading only while the app is in the foreground.

**SHA-256**

```
1131e6384d61b55bec55e0d88f7dcbbd01589697f56e3ae80242e8b265700b15  anlasoz-1.0.2-release.apk
```
