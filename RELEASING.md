# Releasing MusiPedia

This guide creates a signed APK and publishes a versioned GitHub Release such as `v1.0.1`.

## 1. Choose the version

MusiPedia uses two Android version values in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 2
    versionName = "1.0.1"
}
```

- `versionName` is the public version shown to users.
- `versionCode` is a positive integer used by Android to order updates. Increase it for every release, even when `versionName` changes only slightly.
- The Git tag should be `v` followed by the same `versionName`, for example `v1.0.1`.

## 2. Update and verify

Edit `versionCode` and `versionName`, then run:

```powershell
.\gradlew.bat clean test assembleDebug
```

Open the debug build and check startup, Home, search, playback, lyrics, downloads, dark mode, and background playback.

## 3. Generate a signed release APK

In Android Studio:

1. Select **Build → Generate Signed App Bundle or APK**.
2. Choose **APK**, then select the `app` module.
3. Select an existing release keystore or create one.
4. Store the keystore and passwords securely. Never commit them to Git.
5. Choose the `release` build variant and enable both V1 and V2 signatures.
6. Finish the wizard.

Android Studio reports the output location when the build completes. Rename the artifact clearly, for example:

```text
MusiPedia-v1.0.1.apk
```

Before publishing, install that signed APK on a device and test it. Keep the same signing key for every future release; Android cannot install an update signed with a different key over the existing app.

## 4. Commit and tag

Replace `1.0.1` below with the release version:

```powershell
git add app/build.gradle.kts
git commit -m "Release v1.0.1"
git push origin main
git tag -a v1.0.1 -m "MusiPedia v1.0.1"
git push origin v1.0.1
```

Do not reuse or move a published version tag. If a release needs another build, increment the version and create a new tag.

## 5. Publish on GitHub

1. Open [MusiPedia Releases](https://github.com/shwephyonaing/MusiPedia/releases).
2. Select **Draft a new release**.
3. Choose the tag created above.
4. Use a title such as **MusiPedia v1.0.1**.
5. Add release notes using the template below.
6. Upload `MusiPedia-v1.0.1.apk` under **Assets**.
7. Mark it as the latest release and publish.

Suggested release-note format:

```markdown
## New features

- Describe new user-facing capabilities.

## Improvements

- Describe design, performance, or usability improvements.

## Fixes

- Describe bugs fixed in this version.

## Installation

Download `MusiPedia-v1.0.1.apk` below and install it on Android 7.0 or newer.
```

## 6. Verify the published release

- Confirm the tag and release version match.
- Confirm the APK appears under release assets and downloads correctly.
- Install the downloaded release asset on a device.
- Confirm the README's **latest release** link opens the new version.

