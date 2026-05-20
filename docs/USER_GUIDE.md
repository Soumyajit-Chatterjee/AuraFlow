# User guide

How to use AuraFlow day to day.

---

## Overview

AuraFlow is a personal assistant split into two domains:

- **The Iron** — training, workouts, recovery, physical performance
- **The Ink** — study, exams, focus, cognitive work

**IRONK** combines both lanes when you need plans that balance gym and academics.

---

## Getting started

1. Install the app on a device running **Android 12L or newer** (API 32+).
2. Ensure you have a working internet connection.
3. Open AuraFlow — the **Command** tab is shown by default.
4. Select a mode at the top: **IRON**, **INK**, or **IRONK**.
5. Type in the input bar and tap send (arrow icon).

Each mode keeps its **own chat history** while the app is open. Switching modes restores that mode’s messages.

---

## Main screen layout

### Top bar

- **AuraFlow** title
- **Aura: N** — gamified score (0–100) for the current mode; increases with productive messages, decreases with negative keywords or errors

### Mode pills

Tap **IRON**, **INK**, or **IRONK** to switch context. The assistant’s behavior and memory lane change with the mode.

### Bottom navigation

| Tab | Icon | Purpose |
|-----|------|---------|
| Command | Dashboard | Chat and file attachments |
| Focus | Insights | Aura meter, rank, fatigue |
| Memory | Bolt | Recall snippet from recent activity |
| System | Tune | Web Search, Deep Think, model |

---

## Chat (Command tab)

### Sending messages

1. Enter text in the **Command...** field.
2. Tap the cyan **send** button.
3. While waiting, **Synthesizing...** appears with a spinner.

Assistant replies may include:

- **Plain text** for greetings and simple questions
- **Structured cards** for workout/study planning:
  - **Status** — Phys / Cog / Momentum (low, med, high)
  - **Focus Now** — single priority
  - **Next Block** — timeboxed steps
  - **Risk Check** — risk + countermeasure
- **REASONING >>** block when Deep Think is on and the API returns reasoning text

### Attaching documents

1. Tap the **attach** (paperclip) icon.
2. Pick a file from the device.
3. Supported: text-like files and **PDF** (text layer required).
4. Max size: **5 MB**.

Status messages appear above the input (e.g. analyzing, unsupported type, file too large).

For PDFs, only the first **10 pages** are extracted. Scanned image-only PDFs may fail with “no readable text.”

**Tip:** Send at least one chat message in a mode before relying on uploads — the app needs an active Backboard thread for that lane.

---

## Focus tab

Displays your current mode’s progress aesthetic:

| Aura range | Rank |
|------------|------|
| 0–9 | Initiate |
| 10–24 | Adept |
| 25–49 | Mastery |
| 50–100 | Apex |

- **Fluid bar** — animated fill reflecting Aura score (color shifts purple → cyan → mint)
- **Fatigue %** — derived from how often you’ve logged IRON vs INK activity in the last 48 hours; high fatigue steers the AI toward lighter plans
- **Mode** — current pill selection

Aura changes when you send messages (keywords like “completed”, “PR”, “burnout” affect the score). Assistant messages can also nudge Aura up or down.

---

## Memory tab

Shows a **recall anchor**: a short snippet from stored messages, often from activity at least ~3 days ago (or the oldest available). This is local memory on your device, not the full Backboard history.

Every user message you send is saved locally under the current mode category for context in future prompts.

---

## System tab

### Sub-routines

| Toggle | Effect |
|--------|--------|
| **Web Search** | Allows the model to use web search when the API supports it (`Auto`) |
| **Deep Think** | Requests higher reasoning effort from the model |

### Core engine

| Option | Model |
|--------|-------|
| Flash Core | Faster, default (`gemini-3.1-flash`) |
| Pro Core | Heavier model (`gemini-3.1-pro-preview`) |

If a model fails on your account, the app may automatically retry with Backboard’s default model.

---

## Mode guide

### IRON

Use for:

- Workout plans, sets/reps, lifts (squat, deadlift, bench)
- Recovery and fatigue
- Physical PRs and consistency

Aura scoring favors fitness-related vocabulary.

### INK

Use for:

- Study sessions, exams, revision
- Math, reasoning, homework, research
- Focus and deep work blocks

Aura scoring favors study-related vocabulary.

### IRONK

Use when:

- You need one plan that accounts for **both** gym and study
- You ask cross-domain questions (e.g. scheduling lifting around exam prep)

The app routes these to a **synthesis** lane and may pull recent context from both IRON and INK threads.

---

## Tips for best results

1. Be specific: goals, time available, constraints (injury, exam date).
2. For tactical plans, ask performance-style questions to trigger structured **Status / Focus / Next Block** sections.
3. If fatigue is high on the Focus tab, ask for a lighter session — the system prompt already biases toward lower load.
4. Keep messages concise; the assistant is tuned for answers under ~160 words unless you ask for detail.
5. Use **Deep Think** for harder planning; use **Flash** for quick check-ins.

---

## Troubleshooting

| Symptom | What to try |
|---------|-------------|
| `COM_LINK_ERROR` in chat | Check internet; verify API key configuration (developer) |
| Upload says no thread | Send a normal message in that mode first |
| PDF failed | Use a text-based PDF or paste text into chat |
| Empty or odd replies | Switch Flash/Pro; disable Web Search temporarily |
| Aura not updating | Send a message in the active mode; check Focus tab |

---

## Privacy notes

- Messages you send are stored **locally** in the app database (recent items used as context).
- Full conversation history and documents are processed by **Backboard** and the configured LLM provider (Google Gemini when provider is set).
- Review Backboard and Google policies for data handling in your deployment.

---

## Related docs

- [Architecture](ARCHITECTURE.md) — how modes and memory work internally
- [Configuration](CONFIGURATION.md) — API setup for developers
