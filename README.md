# Research App 🔬📚

[![Android CI](https://github.com/pandeysuryansh921-wq/research-app/actions/workflows/build-apk.yml/badge.svg)](https://github.com/pandeysuryansh921-wq/research-app/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/pandeysuryansh921-wq/research-app?include_prereleases&color=blue&label=APK%20Release)](https://github.com/pandeysuryansh921-wq/research-app/releases)

A local-first evidence, discovery, synthesis, and research-management layer for the personal software ecosystem.

---

## 📥 Direct APK Download & Artifacts

- **[GitHub Actions Latest Artifacts](https://github.com/pandeysuryansh921-wq/research-app/actions)** (Download `ResearchApp-Debug-APK` ZIP directly from the latest workflow run)
- **[GitHub Releases Page](https://github.com/pandeysuryansh921-wq/research-app/releases)**

### Installing on Android Device / Tablet:
```bash
adb install -r app-debug.apk
```
*Or download/transfer `app-debug.apk` directly to your phone/tablet and tap to install.*

## 🎯 Core Product Principle

> **Research should organize evidence, not merely documents.**

$$\text{Research Question} \longrightarrow \text{Discovery} \longrightarrow \text{Sources} \longrightarrow \text{Excerpts} \longrightarrow \text{Evidence} \longrightarrow \text{Claims} \longrightarrow \text{Concepts} \longrightarrow \text{Synthesis}$$

Research is **not** a generic PDF reader, a reference manager, a handwriting canvas, or an ungrounded AI chat. It models **evidence and propositions**, preserving backward-traceable provenance at every step.

---

## 🌐 Ecosystem Integration

| Companion App | Role | Research Bridge |
| :--- | :--- | :--- |
| **Likhoji** | Typing / IME Selection | `com.ecosystem.action.SAVE_RESEARCH` intent to Universal Inbox |
| **Boloji** | Voice Companion | `ACTION_VOICE_IDEA` capture broadcast to Research Inbox |
| **Stylus Notes** | Deep Reading & Handwriting | Hands off paper coordinate via `stylus://open?sourceId={id}&page={n}` |
| **Productivity**| Execution & Scheduling | Dispatches research task requests (`ACTION_CREATE_TASK`) |
| **DegreeTrack** | Curriculum & Learning | Links research evidence to course and module topics |
| **Health** | Physiological State | **Strictly Isolated**: Zero automatic access |

---

## ✨ Features Implemented (Phase 0 & Phase 1 Foundation)

1. **Research Command Center (Dashboard)**
   - Metric summaries (Active Projects, Pending Inbox Count).
   - High-level pipeline overview and quick navigation.
2. **Research Project Workspace & Question Builder**
   - Project containers with structured PICO research question framework (Population, Intervention, Comparator, Outcome).
   - Reading Queue triage with priority filters: `TO_SCREEN`, `TO_READ`, `READING`, `READ`, `KEY_PAPER`.
3. **Claim ↔ Evidence Board & Contradiction Finder**
   - First-class `Claim` proposition cards.
   - Linked `Evidence Cards` with exact page numbers, excerpt quotes, and relationship badges (`SUPPORTS`, `CONTRADICTS`, `MIXED`, `BACKGROUND`).
   - Automated contradiction alerts when studies report opposing empirical results on the same claim.
4. **Evidence Matrix with Cell-Level Provenance**
   - Multi-study comparative grid (Population/N, Study Design, Intervention, Comparator, Outcome, Key Result, Limitations).
   - Tap-to-inspect cell provenance revealing exact source coordinates.
5. **Universal Research Inbox**
   - Captures items from Likhoji (IME selection), Boloji (voice dictation), or manual typing.
   - Triage to project or convert to a new source.
6. **Live Literature Discovery**
   - Live querying across **PubMed** (NCBI E-utilities API) and **arXiv** (Export API).
   - 1-tap import with automatic **DOI & PMID duplicate detection**.
7. **Local-First Native Architecture**
   - Android-first UI built with Kotlin, Jetpack Compose Material 3, and native SQLite with reactive Coroutines/Flow.
   - BYOK AI interface with heuristic offline provider guaranteeing 100% functionality without internet or API keys.

---

## 🛠️ Build & Development

### Prerequisites
- JDK 17 or 21
- Android SDK Platform 35 & Build-Tools 35.0.0
- Gradle 8.14+

### Run Unit Tests:
```powershell
.\gradlew.bat testDebugUnitTest
```

### Build Debug APK:
```powershell
.\gradlew.bat assembleDebug --no-daemon
```

*The generated APK is located at: `C:\Users\pande\.gradle-builds\research_app\app\outputs\apk\debug\app-debug.apk`*
