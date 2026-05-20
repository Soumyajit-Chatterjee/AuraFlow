# 🌌 AuraFlow

AuraFlow is an intelligent Android personal performance assistant that bridges the gap between **physical training** (The Iron) and **cognitive work** (The Ink) through a single, seamless conversational interface. 

Powered by the [Backboard](https://app.backboard.io) API for advanced LLM completions, AuraFlow utilizes local Room storage for short-term memory and features a sleek Jetpack Compose UI with a dark, glassmorphism-inspired aesthetic.

---

## ⚡ Features

*   **Tri-Mode Architecture:** 
    *   `IRON`: Tailored for fitness, strength, and physical training tracking.
    *   `INK`: Optimized for deep study, writing, and cognitive tasks.
    *   `IRONK`: Cross-lane synthesis that balances and connects both worlds.
*   **Structured Tactical AI:** Switch between structured responses (**Status**, **Focus**, **Next Block**, **Risk Check**) for high-efficiency planning, or plain text for casual queries.
*   **Dynamic Aura System:** Tracks a per-mode performance score (0–100) across rank tiers, visualized via an animated fluid meter on the Focus tab.
*   **Heuristic Fatigue Tracking:** Automatically calculates fatigue from recent activity and dynamically reduces suggested workout or study intensity via LLM prompting.
*   **Context-Aware Local Memory:** Retains the last 20 messages in local SQLite storage, injecting lane-specific and global context into LLM runs.
*   **Document Processing:** Seamlessly upload text files and PDFs (up to 5 MB or the first 10 pages) for in-context analysis.
*   **Advanced Assistant Settings:** Toggle Web Search, Deep Think, and Flash vs. Pro model configurations directly from the UI.
*   **Tool Calling:** Fully implements a Backboard `REQUIRES_ACTION` handling loop using robust mock tool implementations.

---

## 📋 Requirements

*   **Android Studio:** Ladybug (2024.2+) or newer recommended
*   **JDK:** 11
*   **Minimum SDK:** `32` (Android 12L+)
*   **Compile / Target SDK:** `36`
*   **API Access:** An active **Backboard API key** with valid assistant and thread permissions.

---

## 🚀 Quick Start

1. **Clone the repository** and open the project in Android Studio.
2. **Configure your API credentials** as outlined in the [Configuration Guide](docs/CONFIGURATION.md).
3. **Sync Gradle** and deploy the `app` configuration to an emulator or physical device (API 32+).
4. **Select a mode** (`IRON` / `INK` / `IRONK`) on the **Command** tab and start interacting with your assistant.

---

## 📂 Project Structure

```text
AuraFlow/
├── app/
│   └── src/main/java/com/example/auraflow/
│       ├── MainActivity.kt        # Application entry point
│       ├── data/                  # Room database, DAOs, and entities
│       ├── network/               # Retrofit client, API definitions, LlmRepository
│       └── ui/
│           ├── chat/              # ViewModel + ShadowOSScreen UI components
│           └── theme/             # Compose colors, typography, and glassmorphic themes
├── docs/                          # In-depth architectural & user documentation
├── gradle/
│   └── libs.versions.toml         # Centralized Gradle version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ Tech Stack

*   **Language:** [Kotlin 2.0](https://kotlinlang.org/)
*   **UI Framework:** Jetpack Compose (Material 3) with custom glassmorphic shaders
*   **State Management:** Architecture Components (`ViewModel` + `StateFlow`)
*   **Persistence:** Room 2.7 (utilizing KSP)
*   **Networking:** Retrofit 2.11, OkHttp 4.12, and Gson
*   **PDF Processing:** iText7 7.2.5
*   **AI Backend:** Backboard API (`https://app.backboard.io/api/`)

---

## 📑 Documentation Index

For detailed guides on specific components, please refer to the internal documentation:

| Document | Description |
| :--- | :--- |
| 🏗️ [Architecture](docs/ARCHITECTURE.md) | Structural layers, data flow diagrams, and mode/lane routing. |
| 💻 [Development Guide](docs/DEVELOPMENT.md) | Build instructions, dependency configurations, and testing protocols. |
| 🔌 [API Integration](docs/API_INTEGRATION.md) | Backboard endpoint contracts, error handling, and the tool execution loop. |
| ⚙️ [Configuration](docs/CONFIGURATION.md) | Managing API keys, model variables, SharedPreferences, and database instances. |
| 📱 [User Guide](docs/USER_GUIDE.md) | Navigating tabs, modes, document uploads, and reading structured responses. |
| 📊 [Data Model](docs/DATA_MODEL.md) | Room schemas, memory constraints, and formulas for Aura & Fatigue metrics. |

---

## 🔐 Security Notice

> [!WARNING]  
> **Do not commit your API key to version control.**  
> The baseline codebase contains a temporary placeholder in `RetrofitClient.kt`. Before publishing or distributing this repository publicly, please migrate your secrets to `local.properties` or system environment variables via `BuildConfig`. Refer to the [Configuration Guide](docs/CONFIGURATION.md) for step-by-step instructions.

---

## 📄 License & Metadata

*   **Application ID:** `com.example.auraflow`
*   **Version:** `1.0` (versionCode 1)
*   **License:** No license is currently attached to this repository. Please ensure an appropriate license file (e.g., MIT, Apache 2.0) is added before public distribution or open-sourcing.
