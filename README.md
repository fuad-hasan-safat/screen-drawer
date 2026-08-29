# Screen Drawer (personal-use Android app)

A minimal Android app that lets you draw on top of your screen — over any other
app — with a small floating toolbar (pen toggle, color, undo, clear, exit).

## Why there's no .apk file attached

Building a real, installable Android `.apk` requires the Android SDK build
tools (`aapt2`, `d8`, `apksigner`), which are distributed by Google's servers.
The sandbox this project was written in cannot reach those servers, so the
APK itself cannot be compiled here. What you have is the **complete,
ready-to-build source project** plus a GitHub Actions workflow that compiles
it in the cloud for you — no local setup needed. This gets you the real
`.apk` in about 5 minutes.

## Option A — Build the APK on GitHub (no Android Studio needed)

1. Create a new **public or private** repo on GitHub.
2. Upload everything in this folder to that repo (drag-and-drop on the
   GitHub web UI works, or `git push`).
3. Go to the repo's **Actions** tab. A workflow called "Build APK" runs
   automatically on push (or click **Run workflow** to trigger it manually).
4. Wait for the green checkmark (~3–5 minutes).
5. Open the finished run → **Artifacts** section at the bottom →
   download **ScreenDrawer-debug-apk.zip**. Unzip it to get `app-debug.apk`.

## Option B — Build with Android Studio on your computer

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open this folder as a project.
3. Let it sync (downloads the SDK automatically).
4. `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.
5. The APK appears in `app/build/outputs/apk/debug/`.

## Installing the APK on your phone

1. Copy `app-debug.apk` to your phone (via cable, Google Drive, Telegram, etc).
2. Tap the file. Android will ask to allow installs from that source
   (Settings → "Install unknown apps") — allow it once.
3. Install and open **Screen Drawer**.
4. Tap **"Grant overlay permission"** → allow "display over other apps".
5. Tap **"Start drawing overlay"**. A small dark toolbar appears.
6. Draw with your finger anywhere on screen.
   - **Pen / Move** button toggles between drawing and letting touches pass
     through to the app underneath.
   - **Color** cycles through colors.
   - **Undo** removes the last stroke, **Clear** wipes everything.
   - **Exit** stops the overlay completely.
   - Drag the toolbar by its background (between the buttons) to reposition it.

This app is unsigned/debug-only and meant for installing on your own device,
which is exactly what a debug APK is for — no Play Store account or signing
key needed.
