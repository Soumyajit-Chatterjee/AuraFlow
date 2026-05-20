# Development guide

Instructions for building, running, and extending AuraFlow locally.

---

## Prerequisites

| Tool | Version (project) |
|------|-----------------|
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1 (wrapper) |
| Kotlin | 2.0.0 |
| KSP | 2.0.0-1.0.21 |
| JDK | 11 |
| compileSdk | 36 |
| minSdk | 32 |

---

## Open and sync

1. Open `AuraFlow` in Android Studio.
2. Wait for Gradle sync (`settings.gradle.kts` includes only `:app`).
3. Ensure an emulator or device runs **API 32+**.

---

## Build commands

From the project root:

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (minify disabled in current config)
./gradlew :app:assembleRelease

# Unit tests
./gradlew :app:testDebugUnitTest

# Instrumented tests (device/emulator required)
./gradlew :app:connectedDebugAndroidTest
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

---

## Run configuration

- **Module:** `app`
- **Launch activity:** `com.example.auraflow.MainActivity`
- **Application ID:** `com.example.auraflow`

Internet permission is declared in `AndroidManifest.xml`; the emulator must have network access for Backboard calls.

---

## Source layout

```
app/src/main/java/com/example/auraflow/
├── MainActivity.kt
├── data/
│   ├── BackboardDatabase.kt
│   ├── dao/MemoryDao.kt
│   └── model/MemoryState.kt
├── network/
│   ├── AuraFlowApiService.kt
│   ├── LlmModels.kt
│   ├── LlmRepository.kt
│   └── RetrofitClient.kt
└── ui/
    ├── chat/
    │   ├── AuraFlowViewModel.kt
    │   └── ShadowOSScreen.kt      # Large UI file (~880 lines)
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

Generated code (do not edit):

- `app/build/generated/ksp/.../BackboardDatabase_Impl.kt`
- `app/build/generated/ksp/.../MemoryDao_Impl.kt`

---

## Dependencies

### Version catalog (`gradle/libs.versions.toml`)

Core AndroidX, Compose BOM `2024.06.00`, Lifecycle, Activity Compose, JUnit, Espresso.

### Direct declarations (`app/build.gradle.kts`)

| Dependency | Version | Use |
|------------|---------|-----|
| Room runtime + KTX | 2.7.0 | Local memory |
| Room compiler (KSP) | 2.7.0 | DAO implementation |
| Retrofit | 2.11.0 | HTTP API |
| converter-gson | 2.11.0 | JSON |
| OkHttp | 4.12.0 | Client + interceptors |
| iText7 core | 7.2.5 | PDF text extraction |
| lifecycle-viewmodel-compose | 2.8.7 | ViewModel in Compose |
| navigation-compose | 2.8.5 | On classpath (single-activity app today) |
| material-icons-extended | (BOM) | Extended Material icons |

Repositories: Google, Maven Central, JitPack (`settings.gradle.kts`).

---

## Compose & UI tooling

- `buildFeatures { compose = true }`
- Debug: `ui-tooling`, `ui-test-manifest` for `@Preview` and layout inspection
- Theme: fixed **dark** scheme in `AuraFlowTheme` (no dynamic color)

---

## Room & KSP

After changing `MemoryDao` or `MemoryState`:

1. Bump `@Database(version = …)` if schema changes
2. Provide a `Migration` or use destructive fallback in debug only
3. Rebuild to regenerate KSP outputs

Current schema version: **1**, `exportSchema = false`.

---

## Logging

Use Logcat filters:

| Tag | Area |
|-----|------|
| `AuraFlow` | PDF extraction |
| `AuraFlowUpload` | Multipart / thread recovery |
| `AuraFlowDoc` | Document-as-message pipeline |

---

## ProGuard

Release builds reference `proguard-android-optimize.txt` and `app/proguard-rules.pro`, but **`isMinifyEnabled = false`** — no shrinking in release today. Enable minify before production and add keep rules for Gson models and Room.

---

## Testing

| Type | Location | Notes |
|------|----------|-------|
| Unit | `app/src/test/.../ExampleUnitTest.kt` | Placeholder |
| Instrumented | `app/src/androidTest/.../ExampleInstrumentedTest.kt` | Placeholder |

Recommended additions:

- ViewModel tests with fake `LlmRepository` and in-memory Room
- `extractAssistantText` unit tests for varied `JsonElement` shapes
- Compose UI tests for mode switching and message rendering

---

## Common issues

### Gradle sync fails on SDK 36

Install Android SDK Platform 36 via SDK Manager.

### `minSdk 32` blocks older devices

Emulators must use API 32+ system images.

### Backboard 401 / 403

Verify API key headers in `RetrofitClient` (see [Configuration](CONFIGURATION.md)).

### Empty LLM responses

Check thread exists: send one chat message in the target mode before uploading documents.

### PDF “no readable text”

Scanned PDFs without a text layer will fail extraction; OCR is not implemented.

---

## Code style

- Official Kotlin style (`kotlin.code.style=official` in `gradle.properties`)
- Monospace font used heavily in UI for terminal aesthetic
- Prefer extending existing `glassCard` and tab patterns in `ShadowOSScreen.kt` for new UI

---

## Related docs

- [Configuration](CONFIGURATION.md) — API keys and prefs
- [API integration](API_INTEGRATION.md) — endpoint reference
- [Architecture](ARCHITECTURE.md) — design overview
