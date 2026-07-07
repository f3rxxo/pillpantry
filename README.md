# PillPantry (Kotlin / Jetpack Compose)

A native, Android-only rewrite of PillPantry using Kotlin, Jetpack Compose,
CameraX + ML Kit for barcode scanning, Firebase (Firestore + anonymous auth)
for storage, and Open Food Facts for grocery name lookups. Functionally the
same app as the earlier React Native/Expo version, but a real Android
project instead of a cross-platform one — which also means GitHub can
compile it directly with just the JDK, no Expo/EAS cloud build service and
no device-code login flow involved at all.

## What's included

- `MainActivity.kt` — bottom nav (Scanner / Pantry / Shopping List), anonymous
  sign-in, notification permission request
- `ui/scanner/ScannerScreen.kt` + `BarcodeAnalyzer.kt` — CameraX preview,
  ML Kit on-device barcode detection, grocery/vitamin toggle, new-item
  dialog with an Open Food Facts lookup that pre-fills the product name;
  for vitamins, also collects **pills per dose** and **refill threshold**
  at the moment you scan a new one
- `ui/pantry/PantryScreen.kt` — groceries list (tap the cart icon to add/
  remove an item from your shopping list) and vitamins list with a
  "Take Dose" button (fires a local notification when a vitamin drops
  to/below its refill threshold)
- `ui/shoppinglist/ShoppingListScreen.kt` — groceries you've flagged from
  the Pantry tab; check one off to clear it from the list
- `data/FirebaseRepository.kt` — all Firestore reads/writes
- `data/OpenFoodFactsApi.kt` — Retrofit client for barcode → product name
- `notifications/NotificationHelper.kt` — local refill notifications
- `firestore.rules` — locks each user's data to their own anonymous uid

## 1. Firebase setup

1. Go to the [Firebase console](https://console.firebase.google.com) and
   create a project (free Spark plan).
2. Click **Add app > Android**, and register it with the package name
   `com.yourname.pillpantry` (or change `applicationId`/`namespace` in
   `app/build.gradle.kts` and the manifest package first, if you want your
   own).
3. Download the `google-services.json` file it gives you and place it at
   `app/google-services.json`. This file is what wires the app to your
   specific Firebase project — the app won't build without it.
4. In **Authentication > Sign-in method**, enable **Anonymous**.
5. In **Firestore Database**, click **Create database**, then go to the
   **Rules** tab and paste in the contents of `firestore.rules`, then
   **Publish**.

## 2. Open in Android Studio (recommended)

Just open the `pillpantry-android` folder in Android Studio. It will:
- Auto-generate the Gradle wrapper (`gradlew`) if it's missing
- Download the correct Gradle/Android Gradle Plugin/SDK versions
- Let you run the app straight to a physical Android device over USB (needed
  anyway, since barcode scanning needs a real camera)

## 3. Push to GitHub

```bash
cd pillpantry-android
git init
git add .
git commit -m "Initial native PillPantry app"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/pillpantry.git
git push -u origin main
```

`google-services.json` is **not** excluded in `.gitignore` by default since
this is meant to stay a private single-user repo — if you'd rather keep it
out of git, uncomment the line in `.gitignore` and instead inject it via CI
secret (see below), which is required either way if you want GitHub Actions
to build it.

## 4. Build the APK on GitHub (no local Android Studio/Gradle needed)

`.github/workflows/android-build.yml` builds a debug APK on every push to
`main` using a plain Ubuntu runner with the JDK — this is a completely
standard Android/Gradle build, not a third-party cloud build service, so
there's no separate account, token, or device-code login involved beyond
one GitHub secret:

1. Base64-encode your `google-services.json`:
   ```bash
   base64 -w 0 app/google-services.json
   ```
   (On macOS: `base64 -i app/google-services.json`.)
2. In your GitHub repo: **Settings → Secrets and variables → Actions → New
   repository secret**. Name it `GOOGLE_SERVICES_JSON_BASE64`, paste the
   base64 output as the value.
3. Push to `main`. Check the **Actions** tab — when the build finishes, the
   APK is attached as a downloadable workflow artifact named
   `pillpantry-debug-apk`.
4. Download it, transfer it to your Android phone, and install it (you may
   need to allow "install unknown apps" for whatever app you use to open the
   file, since it isn't from the Play Store).

This produces a **debug** build, which is fine for personal use. If you later
want a signed **release** build (smaller, no debug logging), you'd generate
a signing keystore and add a release-signing step — ask if you want that
added.

## Data model

```text
users/{userId}
  /groceries/{itemId}
      name: string
      barcode: string
      quantity: number
      onShoppingList: boolean
  /vitamins/{vitaminId}
      name: string
      barcode: string
      currentPills: number
      dailyDosage: number
      refillThreshold: number
      lastTaken: timestamp | null
```

## Notes on this version vs. the React Native one

- No Expo/EAS involved at all — this is a plain Gradle Android project.
- Barcode scanning uses Google's ML Kit on-device model (fast, offline)
  instead of a JS barcode library.
- Local notifications use `NotificationCompat` directly; on Android 13+ the
  app requests `POST_NOTIFICATIONS` at launch (if denied, refill alerts just
  won't show — everything else still works).
- The optional daily Cloud Function idea from the original plan still
  applies the same way if you want it later; it's Firebase-side and doesn't
  care which client app is writing to Firestore.
