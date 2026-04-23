# Smart Overlay AI - Android Application

An intelligent overlay application that works across all apps, captures on-screen content, detects and analyzes questions, and displays answers instantly.

## 🎯 Features

### 1. Screen Capture & Content Analysis
- **MediaProjection API** for capturing screenshots
- **Accessibility Service** for extracting visible text
- **ML Kit Text Recognition** for OCR
- Smart detection of question areas (ignores buttons, ads, menus)

### 2. Local RAG System (Retrieval-Augmented Generation)
- **FAISS-like vector indexing** for fast similarity search
- Lightweight embedding model support (all-MiniLM-L6-v2 compatible)
- Language Model integration ready (Phi-3 Mini / MobileBERT)
- **Golden Rule**: Answers derived strictly from local sources
- Displays "Not found in source" when no answer exists

### 3. Mathematical & Logical Solver
- Automatic equation/numerical problem detection
- **SymPy-like solving capabilities** via JavaScript engine
- Expression simplification and verification
- Result correction before display

### 4. Voice Commands & Security
- Supported Commands: "Solve", "Hide", "Stop"
- **Voice Authentication** using fingerprinting
- Embedding-based voice profile comparison
- Similarity threshold enforcement (0.8)

### 5. Gesture Control System

#### Device Gestures:
- **Shake device** → Capture screen + analyze + solve
- **Flip device** → Hide overlay
- **Lift quickly** → Show last answer

#### Touch Gestures:
- **Double tap** → Enable/disable system
- **Swipe up** → Show solution
- **Swipe down** → Close overlay
- **Long press** → Capture and analyze

### 6. Activation & Verification System
- Unique Device ID generation (SHA-256 hashed Android ID)
- Activation code validation with expiry support
- Device binding (code works on one device only)
- Local encrypted validation

### 7. Anti-Repetition System (Smart Cache)
- SHA-256 hash generation for each question
- SQLite database storage
- Instant cached answer return for duplicate questions
- Resource-saving skip processing

### 8. Overlay UI (Floating Window)
- Transparent floating window (System Alert Window)
- Displays detected question and final answer
- Auto-hide after 3-5 seconds
- Manual hide via gesture/voice command
- Draggable interface

### 9. Advanced Features
- Smart question type detection (MCQ, math, text)
- Confidence score for each answer
- Stealth Mode (minimal visibility)
- Battery optimization (on-demand processing)
- Multi-control system (gesture + voice + manual)

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  (Activation, Permissions, Service Control)             │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│ScreenCapture │ │   Overlay    │ │  Accessibility   │
│   Service    │ │   Service    │ │     Service      │
└──────┬───────┘ └──────┬───────┘ └────────┬─────────┘
       │                │                   │
       │         ┌──────┴───────┐           │
       │         │              │           │
       ▼         ▼              ▼           ▼
┌─────────────────────────────────────────────────────────┐
│                  Core Processing Engine                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌───────────────┐ │
│  │   OCR   │ │   RAG   │ │  Math   │ │    Cache      │ │
│  │ Engine  │ │ Engine  │ │ Solver  │ │    System     │ │
│  └─────────┘ └─────────┘ └─────────┘ └───────────────┘ │
└─────────────────────────────────────────────────────────┘
       │                │                   │
       ▼                ▼                   ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│   Voice      │ │   Gesture    │ │   Activation     │
│Authenticator │ │  Controller  │ │     System       │
└──────────────┘ └──────────────┘ └──────────────────┘
```

## 📁 Project Structure

```
SmartOverlayAI/
├── app/
│   ├── src/main/
│   │   ├── java/com/smartoverlay/ai/
│   │   │   ├── MainActivity.kt
│   │   │   ├── SmartOverlayApplication.kt
│   │   │   ├── cache/
│   │   │   │   ├── CachedQuestion.kt
│   │   │   │   ├── QuestionCacheDao.kt
│   │   │   │   └── QuestionCacheDatabase.kt
│   │   │   ├── ocr/
│   │   │   │   └── OcrEngine.kt
│   │   │   ├── rag/
│   │   │   │   └── RagEngine.kt
│   │   │   ├── solver/
│   │   │   │   └── MathSolver.kt
│   │   │   ├── voice/
│   │   │   │   └── VoiceAuthenticator.kt
│   │   │   ├── gesture/
│   │   │   │   └── GestureController.kt
│   │   │   ├── service/
│   │   │   │   ├── ScreenCaptureService.kt
│   │   │   │   ├── OverlayService.kt
│   │   │   │   └── SmartAccessibilityService.kt
│   │   │   ├── receiver/
│   │   │   │   └── BootReceiver.kt
│   │   │   └── util/
│   │   │       ├── PreferenceManager.kt
│   │   │       └── HashUtils.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   └── overlay_window.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── themes.xml
│   │   │   ├── drawable/
│   │   │   │   └── overlay_background.xml
│   │   │   └── xml/
│   │   │       └── accessibility_service_config.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## 🔧 Dependencies

- **AndroidX Core & Lifecycle**
- **ML Kit Text Recognition** - OCR
- **TensorFlow Lite** - Embedding models
- **Room Database** - Caching
- **Kotlin Coroutines** - Async operations
- **Material Components** - UI

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 26+ (Android 8.0)
- Kotlin 1.9+

### Build & Run

1. Open project in Android Studio
2. Sync Gradle files
3. Build and run on device/emulator

### Required Permissions

The app requires the following permissions:
- `SYSTEM_ALERT_WINDOW` - For overlay display
- `FOREGROUND_SERVICE` - For background processing
- `MEDIA_PROJECTION` - For screen capture
- `RECORD_AUDIO` - For voice commands
- `BIND_ACCESSIBILITY_SERVICE` - For text extraction

### Activation

1. Launch the app to get your Device ID
2. Generate activation code using the provided algorithm
3. Enter the code in the app
4. Start the service

## ⚠️ Important Notes

### Platform Limitations
- Some apps may block screen capture (banking apps, DRM content)
- Overlay windows may be restricted in certain contexts
- Always comply with Android platform policies

### Privacy & Security
- All processing happens locally on device
- No data is sent to external servers
- Voice profiles are stored encrypted
- Activation codes are device-bound

### Performance Optimization
- SHA-256 caching prevents reprocessing
- Cooldown periods between captures (2s)
- Lazy loading of ML models
- Efficient sensor polling

## 📝 License

This project is provided as-is for educational purposes.

## 🤝 Contributing

Contributions welcome! Please ensure:
- Code follows Kotlin best practices
- All features work offline-first
- Privacy is maintained
- Platform policies are respected
