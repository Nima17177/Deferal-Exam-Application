# Duck Duck Defer

An Android app for managing deferred exams at a university. Students book deferred exam slots, faculty submit and approve deferrals, admins set up rooms and assign proctors, and proctors run the sessions and check students in.

Built by **Scrum and Done Enterprises**.

## Features

- **Four roles:** Admin, Faculty, Proctor and Student, each with its own dashboard and permissions
- **Bookings and sessions:** book exam slots, manage rooms, assign proctors (manually or automatically)
- **Attendance:** check students in with QR codes or an NFC tap
- **DuckBot:** an AI assistant (Google Gemini 2.5 Flash Lite) that can look things up and carry out role-based actions, with confirmation before anything changes. See [`functions/DUCKBOT_FEATURES.md`](functions/DUCKBOT_FEATURES.md)
- **Gamification:** XP, levels, daily streaks, badges, a leaderboard and a shop. See [`GAMIFICATION_README.md`](GAMIFICATION_README.md)
- **Messaging and notifications:** in-app chat, push notifications, email updates and daily reminders
- **Extras:** home screen widget for your next booking, biometric login, dark mode and accessibility settings

## Tech stack

- **App:** Kotlin, Jetpack Compose, Material 3
- **Backend:** Firebase Auth, Cloud Firestore, Cloud Storage, Cloud Messaging
- **Server code:** Firebase Cloud Functions (Node.js 20) in [`functions/`](functions/)
- **AI:** Google Gemini API

## Getting started

### 1. Firebase setup

1. Create a Firebase project and add an Android app with the package name `com.example.duckduckdefer`.
2. Download your own `google-services.json` and place it in `app/`. It is gitignored, so it stays on your machine.
3. Enable Email/Password and Google sign-in in Firebase Authentication, and create a Firestore database.

### 2. Gemini API key

Get a free key from [Google AI Studio](https://aistudio.google.com/apikey), then add it to `local.properties` in the project root (this file is gitignored, so the key never gets committed):

```properties
GEMINI_API_KEY=your_key_here
```

### 3. Run the app

Open the project in Android Studio, let Gradle sync, and run it on an emulator or device (Android 7.0 / API 24 or newer).

### 4. Cloud Functions (optional, needed for DuckBot actions and notifications)

```bash
cd functions
npm install
firebase deploy --only functions
```

Email notifications need SMTP settings in `functions/.env` (gitignored):

```properties
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=you@example.com
SMTP_PASS=your_app_password
SMTP_FROM=you@example.com
```

## Team

| Name | Role |
| --- | --- |
| Dr. Shokry Shamseldin | Product Manager |
| Nima Houshyar | Scrum Master / Lead Software Developer |
| Nathan Alex Sequeira | Lead Architect |
| Akmal Jeelani | Backend Software Developer |
| Kevin Kothiya | Frontend Software Developer |
| Vedsai Vangapanduv | Frontend Developer |
