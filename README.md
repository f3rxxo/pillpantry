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
- `ui/pantry/PantryScreen.kt` — groceries list (blue rows, tap the cart icon
  to add/remove an item from your shopping list) and vitamins list (orange
  rows, `#FFA85D`) with a "Take Dose" button. Each row has an edit (pencil)
  icon to change its settings after creation — refill threshold and
  portions-per-unit for groceries, dose size and refill threshold for
  vitamins. Swipe any row **right to restock** manually (no scanning
  needed) or **left to delete** it (with a confirmation dialog) — the swipe
  needs a deliberate, mostly-full drag to trigger, to avoid accidental
  swipes. Tapping "Take Dose" a second time on the same day prompts a
  confirmation instead of silently double-logging. A floating **+** button
  on the Groceries tab adds an item manually, no barcode needed. Two icons
  next to the tabs export/import your full data as a JSON backup file.
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
      quantity: number              — units/packages on hand
      portions: number              — individual portions remaining
      portionsPerUnit: number       — portions one unit restocks (auto-computed at creation)
      portionsThreshold: number     — auto-adds to shopping list at/below this
      lastPortionUpdate: timestamp
      onShoppingList: boolean
  /vitamins/{vitaminId}
      name: string
      barcode: string
      currentPills: number
      dailyDosage: number
      refillThreshold: number       — auto-adds to shopping list at/below this
      lastTaken: timestamp | null
      onShoppingList: boolean
```

## Dark mode & scan feedback

- **Dark mode** follows the system setting automatically (`isSystemInDarkTheme()`
  in `ui/theme/Theme.kt`) — no in-app toggle, matching standard Android
  behavior. Grocery (blue) and vitamin (orange) row colors stay fixed in
  both themes so the two lists stay visually distinct; surrounding chrome
  (tab chips, empty states, dialog helper text) adapts via
  `MaterialTheme.colorScheme`.
- **Scan feedback**: a 200ms vibration (explicit amplitude, not the
  system default) fires the instant a barcode is detected
  (`util/ScanFeedback.kt`), before the Firestore lookup even starts.
- **App opens to the Pantry tab** by default, not Scanner, since checking
  stock is the more common reason to open the app day-to-day.
- **Background shopping-list notifications**: previously, a grocery item
  crossing its refill threshold via the daily portion decay never sent a
  notification at all — it only updated silently in Firestore. Now
  `applyMissedPortionDecrements()` fires a local notification for any item
  newly added to the shopping list, and this is called with a `Context`
  both on app launch *and* from `PortionDecayWorker`'s background run, so
  it also works when the app is fully closed (subject to the same
  best-effort WorkManager timing caveats as the decay itself).
- **Import/export**: two icons next to the Pantry tabs let you save your
  full groceries + vitamins list to a JSON file (via Android's normal
  "Save As" file picker) or restore from one. Import matches by barcode and
  skips anything already tracked, so it's safe to re-run.



- **Restocking**: re-scanning a grocery barcode you already track increments
  `quantity` by 1 and adds `portionsPerUnit` back to `portions` (e.g. buying
  a second carton of eggs with `portionsPerUnit = 3` takes portions from 0
  back up to 3), automatically clearing the shopping-list flag if that
  brings it back above the threshold. `portionsPerUnit` is computed once at
  creation time as `portions / quantity` and stored on the item, so it isn't
  editable later without directly changing the Firestore document.
- **Auto-add to shopping list**: both groceries (`portions <= portionsThreshold`)
  and vitamins (`currentPills <= refillThreshold`) flip `onShoppingList = true`
  automatically — groceries when the daily decay pushes them under the
  threshold, vitamins when you tap "Take Dose". You can still add/remove a
  grocery manually via the cart icon in Pantry at any time.
- **Daily portion decay**: rather than relying on a background job firing at
  an exact time (Android won't reliably do that for a personal app — Doze
  mode and battery optimization can delay `WorkManager` jobs by hours),
  `FirebaseRepository.applyMissedPortionDecrements()` runs on every app
  launch and decrements each grocery's `portions` by 1 for every 7am
  America/Chicago boundary that's passed since its `lastPortionUpdate`. This
  means it's always correct whenever you open the app, even after several
  days away. A `PortionDecayWorker` (`WorkManager`, `work/` package) also
  runs this in the background once a day as a best-effort supplement, so
  counts can stay roughly current even if you don't open the app — but the
  launch-time catch-up is what actually guarantees accuracy.

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
