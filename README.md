# Screen Drawer (personal-use Android app)

An Android app that lets you draw on top of your screen — over any other
app — with a modern floating toolbar and a full color picker.

**What's in this version:**
- Dark, modern Material 3 UI on the main screen with a live permission-status card
- Redesigned floating toolbar: pill-shaped, draggable by its own handle (so it
  never conflicts with tapping the buttons), custom minimalist icon set
- Full HSV color wheel — tap the color swatch to pick **any** color, not just
  a fixed list, plus a brightness slider, 12 quick-pick presets, and a brush
  size slider, all in one popup panel
- A real pixel eraser — a dedicated toggle that punches transparent holes
  wherever you drag, not just a "clear everything" button. The brush-size
  slider controls eraser thickness too, whichever tool is active
- Stylus-only mode (palm rejection) as a dedicated icon toggle right on the
  panel — active, only a stylus/S-Pen tip draws; off, both finger and pen draw
- A glassy, gradient-and-glow visual style: active tools light up with a soft
  accent glow, the panel has a gradient "glass" card with an accent stripe,
  and the currently-picked preset color gets a highlighted ring
- Per-stroke undo (color/width included), so undo stays correct even if you
  change color, brush size, or switch between pen and eraser mid-session

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
6. Draw with your finger (or a stylus) anywhere on screen.
   - The **pen icon** (leftmost button) toggles between drawing and letting
     touches pass through to the app underneath — it glows purple when
     active and greys out when pass-through is on.
   - The **eraser icon** toggles a real eraser — drag over any part of your
     drawing to erase just that part (not the whole thing). It glows purple
     while active. Tap it again to go back to the pen.
   - The **colored circle** opens the pen settings panel: drag on the wheel
     to pick any color, use the brightness slider for lighter/darker shades,
     tap a quick-pick swatch for instant common colors (the currently active
     one gets a highlighted ring), or drag the brush-size slider — it
     resizes the pen when drawing, or the eraser when erasing. At the
     bottom, tap the **stylus icon** to turn on stylus-only mode — while it's
     glowing, the canvas ignores finger touches completely and only your
     S-Pen/stylus tip draws; tap it again to let both finger and pen draw.
     Tap the X to close the panel.
   - **Undo** removes the last stroke (pen or eraser), **Clear** wipes everything.
   - The **X** on the far right stops the overlay completely.
   - Drag the small **dotted handle** on the far left of the toolbar to move
     it anywhere on screen — the other buttons stay tap-only, so dragging
     never accidentally triggers them.

This app is unsigned/debug-only and meant for installing on your own device,
which is exactly what a debug APK is for — no Play Store account or signing
key needed.
