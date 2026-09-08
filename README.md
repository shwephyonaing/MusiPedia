<p align="center">
  <img src="brand/musipedia-lockup-dark.png" alt="MusiPedia" width="360" />
</p>

<p align="center">
  A clean Android music player for discovery, streaming, lyrics, and offline listening.
</p>

<p align="center">
  <a href="https://github.com/shwephyonaing/MusiPedia/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/shwephyonaing/MusiPedia?style=flat-square&color=42E4CE" /></a>
  <a href="LICENSE"><img alt="GPL-3.0 license" src="https://img.shields.io/github/license/shwephyonaing/MusiPedia?style=flat-square" /></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-42E4CE?style=flat-square&logo=android" />
</p>

## About

MusiPedia is a lightweight music discovery and playback app for Android. It combines personalized recommendations, trending music, search, background playback, synchronized lyrics, favorites, and offline downloads in a focused interface.

## Features

- Personalized **For You** recommendations and trending music
- Search for songs, artists, albums, and playlists
- Background playback with queue controls
- Synced and plain lyrics with seek support
- Favorites and recently played music
- Offline song downloads
- Repeat-one playback and sleep timer
- Light and dark themes
- Animated startup with Home feed preloading
- In-player equalizer with presets and bass boost

## Installation

1. Open the [latest GitHub Release](https://github.com/shwephyonaing/MusiPedia/releases/latest).
2. Download the attached APK.
3. Allow installation from your browser or file manager when Android asks.
4. Install and open MusiPedia.

> APK downloads are only published on this repository's Releases page. Verify the repository before installing an update.

## Build from source

Requirements:

- Android Studio with JDK 17
- Android SDK 36
- Git

Clone and build a debug APK:

```bash
git clone https://github.com/shwephyonaing/MusiPedia.git
cd MusiPedia
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
git clone https://github.com/shwephyonaing/MusiPedia.git
cd MusiPedia
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For YouTube client configuration and troubleshooting, see [YOUTUBE_SETUP.md](YOUTUBE_SETUP.md).

## Verified artists catalog

Taste personalization uses a curated artist list (no live YouTube Music artist search). The source of truth is:

[`catalog/verified-artists.json`](catalog/verified-artists.json)

Installed apps fetch that file from GitHub (`main`) at launch and cache it. Offline installs fall back to the copy bundled in the APK.

To add an artist without shipping a new APK:

1. Append an entry to `catalog/verified-artists.json` (`id` = YouTube channel UC…, `name`, optional `thumbnailUrl` / `handle` / `region`).
2. Commit and push to `main`.
3. Open the app (or Settings → Personalize); it refreshes within a few hours, or immediately on next cold start after the cache TTL.

Example:

```json
{
  "id": "UCiRZvmSslB0MVU5NL7iYnrw",
  "name": "Hsu Rinna",
  "handle": "@Hsu_Rinaa",
  "thumbnailUrl": "https://yt3.googleusercontent.com/…=s900-c-k-c0x00ffffff-no-rj",
  "region": "mm"
}
```

## Creating a release

Maintainers should follow [RELEASING.md](RELEASING.md). It covers version numbers, signed APK generation, Git tags, release notes, and uploading the APK to GitHub Releases.

## Feedback

Found a bug or have a feature request? Open a [GitHub issue](https://github.com/shwephyonaing/MusiPedia/issues) or email [team.ctrl.v@gmail.com](mailto:team.ctrl.v@gmail.com?subject=MusiPedia%20Feedback).

## Disclaimer

MusiPedia is an independent open-source project. It is not affiliated with, sponsored by, or endorsed by YouTube, YouTube Music, or Google. Users are responsible for complying with the terms and laws that apply in their region.

## License

MusiPedia is licensed under the [GNU General Public License v3.0](LICENSE).

