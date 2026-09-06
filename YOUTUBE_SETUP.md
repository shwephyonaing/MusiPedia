# YouTube Music playback for MusiPedia

MusiPedia talks to YouTube Music directly (Innertube), the same approach as InnerTune / Echo. There is **no Google API key** and **no VPN** as long as `youtube.com` / `music.youtube.com` already open in a normal browser on the device.

## What you need

1. Android Studio or JDK 17 + Android SDK
2. A device or emulator with internet (normal Myanmar mobile data is enough if YouTube loads)
3. Optional: MP3/M4A files on the phone for the Library tab

## Run

```bash
./gradlew assembleDebug
```

Open the app, skip or tap through login, then:

- **Home** loads live YouTube Music sections
- **Explore** searches songs, artists, albums, and playlists
- Tap a song to play audio in the background (notification controls)
- Open an artist / album / playlist and use **Play**
- **Library** keeps local files and recently played tracks in the same player

## Notes

- This is YouTube Music, not Spotify. YouTube can change their endpoints and break playback until the extractor is updated.
- Stream URLs come from [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0). MusiPedia is therefore GPL-3.0. See `LICENSE`.
- The app does not download tracks, does not log into a Google account, and does not use Piped/Invidious.
- Content country is `US` so the catalog is full; the connection still originates from the phone (no proxy).
