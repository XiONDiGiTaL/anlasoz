## AnlaSöz 1.0.0

A Turkish ↔ English companion for the messages you actually receive — what they mean, how the sentence is built, how it sounds, and how to answer.

**Download:** `anlasoz-1.0.0-release.apk` below (Android 8.0+, signed, 2.1 MB).

### First run

The app ships with no credentials and no default provider. Open **Settings** and add:

1. A base URL — any OpenAI-compatible endpoint (`https://api.openai.com/v1`, OpenRouter, Z.ai, your own proxy, or LM Studio/Ollama on your network)
2. A model name
3. Your API key — stored in the Android Keystore, never in the APK or its backups

**Test** in Settings confirms the endpoint before you use it.

### What's inside

- **Translate** — automatic direction detection, natural translation, literal structure, contextual interpretation, tone, word-by-word breakdown, grammar notes, vocabulary, alternative readings, suggested replies. Shares text in from any app and analyzes immediately.
- **Reply** — English in, natural Turkish out, across 10 tones, with an English back-translation and 1–3 alternatives. Copy copies only the Turkish sentence.
- **Learn** — vocabulary saved from real messages with pronunciation, examples and usage notes; grammar notes; a cloze practice deck built from your own words.
- **History** — searchable, filterable by direction/favorites/conversation, exportable as JSON (vocabulary as CSV), readable offline.
- **Speech** — Turkish TTS through a real `tr-TR` voice with pause/stop and adjustable speed; Turkish or English speech-to-text with microphone permission requested on demand.
- **Context** — conversation threads; earlier messages travel with a new one, and context can be disabled or cleared.

### Privacy

No analytics, telemetry, ads or third-party SDKs. Messages go only to the endpoint you configure, only when you ask. History, vocabulary and keys stay on the device; backups are excluded.

### Notes

- Turkish TTS needs a `tr-TR` voice on the device; the app reports when one is missing rather than reading Turkish with an English voice.
- A key on the device suits personal use. For a shared build, put a small server-side proxy in front of the provider and point the base URL at it.
- Analysis quality is the model's quality. Ambiguous messages are shown with their alternative readings rather than a single confident guess.

Built for XiØN · [xiondigital.net](https://xiondigital.net)
