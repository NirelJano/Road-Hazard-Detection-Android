# Road Hazard Detection - Android App

An Android application built with Kotlin for detecting road hazards using machine learning.

## 🚀 Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM + Clean Architecture
- **Dependency Injection:** Hilt
- **Database:** Room
- **Camera:** CameraX
- **ML:** TensorFlow Lite
- **Maps:** Google Maps Compose
- **Backend:** Firebase (Auth, Firestore, Storage)
- **Background Processing:** WorkManager
- **Build System:** Gradle with Kotlin DSL & Version Catalog

## 📁 Project Structure

```
app/
├── data/              # Data layer (repositories, data sources)
├── domain/            # Domain layer (use cases, models)
├── di/                # Dependency injection modules
├── navigation/        # Navigation configuration
├── ui/
│   ├── theme/         # App theme (colors, typography)
│   ├── screens/       # Screen composables
│   └── components/    # Reusable UI components
└── util/              # Utilities and constants
```

## 🔧 Setup Instructions

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK with API 24+ (Android 7.0+)

### Getting Started

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd "Road Hazard"
   ```

2. **Configure Firebase**
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Download `google-services.json` from your Firebase project settings
   - Place it in the `app/` directory (replacing the placeholder file)
   - Enable Firebase Authentication, Firestore, and Storage in your Firebase console

3. **Sync Gradle**
   ```bash
   ./gradlew build
   ```

4. **Run the app**
   - Open the project in Android Studio
   - Select a device or emulator
   - Click Run (▶)

## 🔑 Key Features (In Development)

- ✅ Authentication with Firebase (Email/Password & Google Sign-In)
- 🔲 Real-time road hazard detection using TensorFlow Lite
- 🔲 CameraX integration for live camera feed
- 🔲 Google Maps integration for hazard location tracking
- 🔲 Offline-first architecture with Room database
- 🔲 Background sync with WorkManager

## 📦 Dependencies

All dependencies are managed via Version Catalog in `gradle/libs.versions.toml`:

- Compose BOM: 2024.12.01
- Firebase BOM: 33.7.0
- Hilt: 2.52
- Room: 2.6.1
- CameraX: 1.4.1
- TensorFlow Lite: 0.4.4
- Google Maps Compose: 6.2.1
- WorkManager: 2.10.0

## 🛠️ Build Variants

- **Debug:** Development build with logging enabled
- **Release:** Production build with ProGuard/R8 optimization

## 📝 License

[Add your license here]

## 👥 Contributors

[Add contributors here]
