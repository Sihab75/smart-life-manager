# Smart Life Manager

## Firebase setup

The Android app includes Firebase Authentication, Google credential sign-in support,
Firestore user profiles, Cloud Storage uploads, and Firebase Cloud Messaging token
registration. Local Room data remains available when the device is offline.

1. Create an Android app in Firebase with package name
   `com.example.personal_financestudydaily_routine_assistant`.
2. Enable Email/Password and Google providers in Firebase Authentication.
3. Create Firestore and Storage, then add security rules that restrict documents and
   files to the authenticated user's UID.
4. Download `google-services.json` and place it in `app/google-services.json`.
5. Configure the Firebase project's SHA-1/SHA-256 fingerprints for Google sign-in.

`FirebaseCloudService` safely reports a configuration error until the JSON file is
present, so debug builds can still be installed before Firebase is configured.

## AI provider setup

The AI Assistant supports Gemini, OpenAI, DeepSeek, and Local/Offline AI. The
Android app sends only the selected provider ID and assistant context to the
backend; provider API keys are never included in the APK or sent from the app.

1. Copy `backend/.env.example` to `backend/.env`.
2. Add only the provider keys you plan to enable.
3. Deploy the backend and set the Android `ASSISTANT_BASE_URL` Gradle property to
   its HTTPS URL. For local Android emulator debugging, use `http://10.0.2.2:3000`
   instead of `127.0.0.1`.

If a remote provider is unavailable or not configured, the assistant falls back
to its local response engine.
