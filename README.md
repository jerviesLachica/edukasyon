# SchedMate 🎓✨

**SchedMate** is an offline-first, AI-powered student companion app built for Android to help students effortlessly manage their academic lives, schedules, tasks, assignments, and study goals.

---

## 🌟 Key Features

- **Smart Home Widgets (2x2 & 2x3)**: Keep your classes, tasks, and upcoming exams right on your home screen. Widgets automatically refresh every day at midnight to stay in sync with your life.
- **Offline-First Architecture**: Your data is stored locally using Room database, ensuring lightning-fast performance and full functionality even without internet.
- **AI Assignment Intelligence**: Automatically break down complex assignments into manageable subtasks using our intelligent AI assistant.
- **Reliable Reminders & DND Bypass**: Never miss a class or exam with high-importance notifications that include custom sounds and can bypass Do Not Disturb mode when configured.
- **Unified Academic Planner**: Manage your class schedule, homework assignments, exams, grades, and notes all in one clean place.
- **Secure AI Proxy Backend**: Integrated Node.js backend proxy ensuring safe AI interactions without exposing API keys.

---

## 📱 Project Structure

`
edukasyon/
├── androidApp/          # Primary Android application (SchedMate)
│   └── src/main/kotlin/com/edukasyon/studentai/
│       ├── core/        # AI, network, sync, utilities
│       ├── data/        # Room, repositories, preferences
│       ├── domain/      # Models, repository interfaces, use cases
│       ├── di/          # Hilt modules
│       └── ui/          # Compose screens, theme, navigation
└── backend/             # Secure AI proxy API (Node.js)
`

---

## 🚀 Getting Started & Build

### Prerequisites
- Android Studio / JDK 17+ (JDK 25 fully compatible with Gradle 8.9)

### Building the APK
`ash
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.2
gradlew.bat :androidApp:assembleDebug
`

### Running the AI Backend
`ash
cd backend
npm install
npm start
`

---

## 📦 Application ID & Firebase

- **Application ID**: com.edukasyon.studentai (Debug: com.edukasyon.studentai.debug)
- **Firebase Project**: dukasyon-studentai (246011040847) supporting anonymous guest auth and Firestore sync.
