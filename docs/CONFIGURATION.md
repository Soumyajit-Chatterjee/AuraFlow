# Configuration

How to configure API access, models, and local storage for AuraFlow.

---

## Backboard API key

### Current implementation

The API key is defined as a constant in:

```
app/src/main/java/com/example/auraflow/network/RetrofitClient.kt
```

Both headers are sent on every request:

- `X-API-Key: <key>`
- `Authorization: Bearer <key>`

### Recommended setup (do before sharing code)

**Option A — `local.properties` + BuildConfig**

1. Add to `local.properties` (gitignored):

   ```properties
   BACKBOARD_API_KEY=your_key_here
   ```

2. In `app/build.gradle.kts`:

   ```kotlin
   android {
       defaultConfig {
           val props = project.rootProject.file("local.properties")
           val apiKey = if (props.exists()) {
               props.readLines()
                   .firstOrNull { it.startsWith("BACKBOARD_API_KEY=") }
                   ?.substringAfter("=") ?: ""
           } else ""
           buildConfigField("String", "BACKBOARD_API_KEY", "\"$apiKey\"")
       }
       buildFeatures { buildConfig = true }
   }
   ```

3. In `RetrofitClient.kt`:

   ```kotlin
   private const val API_KEY = BuildConfig.BACKBOARD_API_KEY
   ```

**Option B — Environment variable in CI**

Inject the key via Gradle property or secret store; never commit the value.

### Rotating a compromised key

If a key was committed to git history:

1. Revoke it in the Backboard dashboard
2. Issue a new key
3. Update local/CI configuration only
4. Consider `git filter-repo` to purge history before making the repo public

---

## LLM provider and models

Configured in the **System** tab and `AuraFlowViewModel`:

| UI label | `model_name` sent to API |
|----------|--------------------------|
| Flash Core | `gemini-3.1-flash` |
| Pro Core | `gemini-3.1-pro-preview` |

`llm_provider` is fixed to `"google"` in `sendMessage`.

If the account does not support a model, the repository **fallback** clears provider/model and retries (see [API integration](API_INTEGRATION.md)).

---

## Feature toggles

| Toggle | Request effect |
|--------|----------------|
| **Web Search** | `web_search`: `"Auto"` vs `"off"` |
| **Deep Think** | `thinking`: `{ "effort": "high" }` vs omitted |

Defaults: both **off** at app start.

---

## SharedPreferences

| File name | Keys | Purpose |
|-----------|------|---------|
| `auraflow_threads` | `thread_iron`, `thread_ink`, `thread_synthesis` | Backboard thread IDs per lane |

Clear app data or uninstall to reset thread mapping (a new thread may be created on next send).

---

## Room database

| Property | Value |
|----------|-------|
| File name | `backboard_database` |
| Entity | `memory_state` |
| Version | 1 |

No migration classes ship with v1. Schema changes require a version bump and `Migration` implementation.

### Clearing memories

`MemoryDao.clearAllMemories()` exists but is not exposed in the UI. Call from debug code or add a settings action if needed.

---

## Network & manifest

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

No `ACCESS_NETWORK_STATE` or cleartext config — all traffic is HTTPS to Backboard.

---

## Document limits (UI-enforced)

| Limit | Value |
|-------|-------|
| Max file size | 5 MB |
| PDF pages extracted | First 10 |
| Supported MIME (text) | plain, json, xml, html, csv |
| PDF | Via iText7 text extraction |

---

## Backup

`android:allowBackup="true"` with `backup_rules.xml` and `data_extraction_rules.xml`. Thread prefs and Room DB may be included in device backup depending on OEM rules. Exclude sensitive prefs in backup rules for production.

---

## Build variants

Only **debug** and **release** build types are defined. No `productFlavors` — single environment per build.

To add staging/production flavors:

1. Define `productFlavors` with different `BASE_URL` or keys
2. Inject via `BuildConfig` per flavor
3. Document flavor names in README

---

## Related docs

- [API integration](API_INTEGRATION.md)
- [Development guide](DEVELOPMENT.md)
