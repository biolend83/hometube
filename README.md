<div align="center">

# TubeHaus for Windows, macOS and Linux

**YouTube on your own terms — and everything runs on your PC.**

No server, no account, no Google sign-in, no tracking. The app talks to YouTube directly,
keeps your subscriptions, history and playlists on the device, and works out recommendations
there too.

**[⬇ Windows 10/11](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7.msi)** · **[⬇ macOS](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7-mac-arm64.dmg)** · **[⬇ Linux .deb](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7-linux-amd64.deb)** (0.3.7, beta) · [Android app](https://github.com/biolend83/tubehaus/tree/android-app) · [All app releases](https://github.com/biolend83/tubehaus/releases) · [TubeHaus web app (main branch)](https://github.com/biolend83/tubehaus)

</div>

## What it does

- **Home that learns on your device** — built from videos related to what you watched, new uploads
  from your subscriptions and the topics you like, with room for something new so it doesn't
  collapse into one subject. Settings → *What TubeHaus learned about you* shows every score and
  lets you change or wipe it.
- **Shorts with their own engine** — learns from skipping, finishing, looping and likes.
- **Subscriptions without an account** — from each channel's public feed. Import from YouTube
  (Google Takeout), NewPipe, FreeTube, Invidious, TubeHaus or OPML.
- **A real player** — adaptive quality up to 4K/8K (AV1, VP9, H.264 picked by what your hardware
  decodes), captions with auto-translate, speed, resume where you stopped.
  - Android: background play with notification controls, picture-in-picture, opens YouTube links.
  - Windows: mpv with GPU decoding, the same controls as on the phone plus a volume bar,
    keyboard shortcuts (space/K, arrows, J/L, F, M) and the mouse wheel for Shorts.
- **Comments beside the video** — in a side panel next to the video, which keeps playing.
- **Calm mode** — no view counts, no Shorts, nothing starts by itself.
- **Focus switches** — hide thumbnails, comments, Shorts or related videos, and turn off the home
  feed or the subscription feed, each on its own (Settings → Focus).
- **Your data moves freely** — export a TubeHaus backup (also readable by the TubeHaus web app),
  Invidious, CSV or OPML.

## How it works

The app has its own small YouTube engine (`common/src/app/tubehaus/engine`). It calls InnerTube,
the JSON API YouTube's own apps use: the web client for search, channels, playlists, related
videos and comments; for streams, clients whose answers carry ready-to-play URLs. Nothing goes
through a TubeHaus server, because there isn't one.

One set of Kotlin sources (`common/src`) is compiled twice: by the Android app with Jetpack
Compose and by the desktop app with Compose for Desktop. Only the player differs —
ExoPlayer (Media3) on Android, libmpv on Windows.

## Install

- **Android 8.0+** — [the APK](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7.apk)
  (beta). Open it on the phone and allow installing from your browser when asked.
  Updates: [Obtainium](https://obtainium.imranr.dev) with `https://github.com/biolend83/tubehaus`
  (each "TubeHaus 0.x" release has every platform's file).
- **Windows 10/11 (64-bit)** — [the installer](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7.msi)
  (beta, mpv built in). Windows may warn that the publisher is unknown, because the installer
  isn't code-signed: choose *More info* → *Run anyway*.
- **macOS (Apple Silicon)** — [the .dmg](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7-mac-arm64.dmg) (beta, new and barely tested).
  It uses Homebrew's mpv: `brew install mpv`. The app isn't signed by Apple yet, so the first time
  right-click it → *Open* → *Open*.
- **Linux (x86-64)** — [.deb](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7-linux-amd64.deb) for Debian/Ubuntu, [.rpm](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7-linux-x86_64.rpm)
  for Fedora/openSUSE, or [a tar.gz](https://github.com/biolend83/tubehaus/releases/download/app-v0.3.7/TubeHaus-0.3.7-linux-x64.tar.gz) to unpack anywhere and run `TubeHaus/bin/TubeHaus`.
  It uses your system's mpv: `sudo apt install libmpv2` (Fedora: `sudo dnf install mpv-libs`, Arch: `mpv`). New and not yet tested much: reports welcome.

There is no iOS version: apps outside the App Store can't be installed on an iPhone without
re-signing them every week. On iPhone and iPad, use the TubeHaus web app.

## Build

JDK 21+ and the Android SDK.

```sh
./gradlew :android:assembleRelease     # android/build/outputs/apk/release
./gradlew :desktop:run                 # needs libmpv-2.dll in desktop/resources/windows
./gradlew :desktop:packageMsi          # needs a JDK with jpackage
```

libmpv for Windows comes from [shinchiro's mpv builds](https://github.com/shinchiro/mpv-winbuild-cmake/releases)
(`mpv-dev-x86_64-*.7z`, the file `libmpv-2.dll`). The release workflow downloads it.

## License

GPL-3.0. Not affiliated with YouTube or Google.
