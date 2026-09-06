# YouTube setup for Musium

1. In Google Cloud Console, enable **YouTube Data API v3** and create a restricted Android API key for package `com.musium.app`.
2. Add this line to `local.properties` (do not commit this file):

   ```properties
   YOUTUBE_API_KEY=your_key_here
   ```

3. Rebuild the application with `./gradlew.bat assembleDebug`.
4. In Musium, open **Explore**, enter a search term, and tap **Go**.

The app uses YouTube Data API v3 for metadata and thumbnails. Playback opens an official YouTube embed in an Android WebView; it does not extract streams or download audio.
