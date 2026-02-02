# Road Hazard Detection - Project File Structure

## 📂 Complete File Tree

```
Road Hazard/
├── .gitignore
├── README.md
├── gradlew
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── google-services.json
    ├── src/
    │   └── main/
    │       ├── AndroidManifest.xml
    │       ├── java/com/roadhazard/app/
    │       │   ├── BaseApplication.kt
    │       │   ├── MainActivity.kt
    │       │   ├── data/
    │       │   │   └── local/
    │       │   │       └── AppDatabase.kt
    │       │   ├── di/
    │       │   │   └── AppModule.kt
    │       │   ├── navigation/
    │       │   │   ├── Screen.kt
    │       │   │   └── NavGraph.kt
    │       │   ├── ui/
    │       │   │   ├── theme/
    │       │   │   │   ├── Color.kt
    │       │   │   │   ├── Type.kt
    │       │   │   │   └── Theme.kt
    │       │   │   └── screens/
    │       │   │       └── auth/
    │       │   │           ├── LoginScreen.kt
    │       │   │           ├── LoginViewModel.kt
    │       │   │           ├── SignupScreen.kt
    │       │   │           ├── SignupViewModel.kt
    │       │   │           └── ForgotPasswordScreen.kt
    │       │   └── util/
    │       │       └── Constants.kt
    │       └── res/
    │           ├── values/
    │           │   ├── strings.xml
    │           │   └── themes.xml
    │           └── xml/
    │               ├── backup_rules.xml
    │               ├── data_extraction_rules.xml
    │               └── file_paths.xml
```

## 📊 File Count Summary

### Build Configuration: 7 files
- `gradle/libs.versions.toml` - Version Catalog
- `settings.gradle.kts` - Project settings
- `build.gradle.kts` - Root build config
- `app/build.gradle.kts` - App build config
- `gradle.properties` - Gradle properties
- `gradlew` - Gradle wrapper
- `.gitignore` - Git exclusions

### Kotlin Source Files: 15 files
**Application Core:**
- `BaseApplication.kt`
- `MainActivity.kt`
- `Constants.kt`

**Navigation:**
- `Screen.kt`
- `NavGraph.kt`

**Data Layer:**
- `AppDatabase.kt`

**Dependency Injection:**
- `AppModule.kt`

**UI Theme:**
- `Color.kt`
- `Type.kt`
- `Theme.kt`

**Authentication:**
- `LoginScreen.kt`
- `LoginViewModel.kt`
- `SignupScreen.kt`
- `SignupViewModel.kt`
- `ForgotPasswordScreen.kt`

### XML Configuration: 8 files
- `AndroidManifest.xml`
- `strings.xml`
- `themes.xml`
- `file_paths.xml`
- `data_extraction_rules.xml`
- `backup_rules.xml`
- (2 more for future use)

### Other Files: 4 files
- `README.md` - Project documentation
- `proguard-rules.pro` - ProGuard configuration
- `google-services.json` - Firebase config (placeholder)

**Total Project Files: 34+**
