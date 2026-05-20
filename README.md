# AuraFlow

AuraFlow is an Android personal performance assistant that connects **physical training** (The Iron) and **cognitive work** (The Ink) through a single conversational interface. It uses the [Backboard](https://app.backboard.io) API for LLM completions, local Room storage for short-term memory, and a Jetpack Compose UI with a dark, glassmorphism-inspired design.

---

## Features

| Area | Description |
|------|-------------|
| **Three modes** | `IRON` (fitness), `INK` (study/cognition), `IRONK` (cross-lane synthesis) |
| **AI chat** | Structured tactical responses (Status, Focus, Next Block, Risk Check) or plain text for casual queries |
| **Aura system** | Per-mode score (0–100) with rank tiers and animated fluid meter on the Focus tab |
| **Fatigue tracking** | Heuristic score from recent activity; high fatigue reduces suggested intensity in prompts |
| **Local memory** | Last 20 messages stored in SQLite; lane-specific and global context sent to the LLM |
| **Document upload** | Text files and PDFs (up to 5 MB, first 10 PDF pages) analyzed via Backboard |
| **System settings** | Web Search, Deep Think, Flash vs Pro model selection |
| **Tool calling** | Handles Backboard `REQUIRES_ACTION` runs with mock tool implementations |

---

## Requirements

- **Android Studio** Ladybug (2024.2+) or newer recommended
- **JDK 11**
- **minSdk 32** (Android 12L+)
- **compileSdk / targetSdk 36**
- Active **Backboard API key** with at least one assistant and thread access

---

## Quick start

1. Clone or open the project in Android Studio.
2. Configure your Backboard API key (see [Configuration](docs/CONFIGURATION.md)).
3. Sync Gradle and run the `app` configuration on a device or emulator (API 32+).
4. Open the app, pick a mode (`IRON` / `INK` / `IRONK`), and send a message on the **Command** tab.

---

## Documentation

| Document | Contents |
|----------|----------|
| [Architecture](docs/ARCHITECTURE.md) | Layers, components, data flow, mode/lane routing |
| [Development guide](docs/DEVELOPMENT.md) | Build, dependencies, project layout, testing |
| [API integration](docs/API_INTEGRATION.md) | Backboard endpoints, requests, errors, tool loop |
| [Configuration](docs/CONFIGURATION.md) | API keys, models, SharedPreferences, database |
| [User guide](docs/USER_GUIDE.md) | Tabs, modes, uploads, structured responses |
| [Data model](docs/DATA_MODEL.md) | Room schema, memory, aura and fatigue formulas |

---

## Project structure

```
AuraFlow/
├── app/
│   └── src/main/java/com/example/auraflow/
│       ├── MainActivity.kt              # Entry point
│       ├── data/                        # Room database & entities
│       ├── network/                     # Retrofit, API, LlmRepository
│       └── ui/
│           ├── chat/                    # ViewModel + ShadowOSScreen UI
│           └── theme/                   # Compose colors & theme
├── docs/                                # Detailed documentation
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Tech stack

- **Kotlin** 2.0 · **Jetpack Compose** (Material 3) · **ViewModel** + **StateFlow**
- **Room** 2.7 (KSP) · **Retrofit** 2.11 · **OkHttp** 4.12 · **Gson**
- **iText7** 7.2.5 (PDF text extraction)
- **Backboard API** (`https://app.backboard.io/api/`)

---

## Security notice

The Backboard API key must **not** be committed to version control. The current codebase stores the key in `RetrofitClient.kt`; migrate it to `local.properties`, environment variables, or `BuildConfig` before sharing or publishing. See [Configuration](docs/CONFIGURATION.md).

---

## License

No license file is included in this repository. Add one before distribution if you plan to open-source or share the project.

---

## Application ID

`com.example.auraflow` · Version **1.0** (versionCode 1)
