package team.ctrlv.musipedia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.ctrlv.musipedia.innertube.Lyrics
import java.io.IOException

@UnstableApi
class MusicService : MediaSessionService() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var player: ExoPlayer
        private set
    private var session: MediaSession? = null
    private val localBinder = MusicBinder()
    private var retriedCurrent = false
    lateinit var recentStore: RecentStore
        private set
    @Volatile var sleepEndsAtMs: Long = 0L
        private set
    @Volatile var sleepUntilTrackEnd: Boolean = false
        private set
    private var sleepJob: Job? = null
    private var lyricsJob: Job? = null
    private var lyricTickerJob: Job? = null
    @Volatile private var activeLyrics: Lyrics? = null
    @Volatile private var postedLyricLine: String? = null
    @Volatile private var lyricsSongId: String? = null
    private var startedAsService = false
    lateinit var equalizer: EqualizerEngine
        private set
    private lateinit var lyricsOffsetStore: LyricsOffsetStore

    inner class MusicBinder : Binder() {
        val service: MusicService get() = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MusiumApplication
        recentStore = app.recentStore
        lyricsOffsetStore = app.lyricsOffsetStore
        equalizer = EqualizerEngine(app.equalizerStore)
        val playHttp = StreamResolver.http.newBuilder()
            .addInterceptor { chain ->
                val incoming = chain.request()
                if (!incoming.url.host.contains("googlevideo") && !incoming.url.toString().contains("m3u8")) {
                    return@addInterceptor chain.proceed(incoming)
                }
                chain.proceed(
                    incoming.newBuilder()
                        .removeHeader("Accept-Encoding")
                        .removeHeader("Origin")
                        .removeHeader("Referer")
                        .header("User-Agent", incoming.header("User-Agent") ?: StreamResolver.ANDROID_USER_AGENT)
                        .build(),
                )
            }
            .addNetworkInterceptor { chain ->
                val outgoing = chain.request()
                val started = System.currentTimeMillis()
                val response = chain.proceed(outgoing)
                PlayLog.d(
                    "http ${outgoing.method} ${outgoing.url.host} code=${response.code} " +
                        "ms=${System.currentTimeMillis() - started} range=${outgoing.header("Range")}",
                )
                if (response.code !in 200..299) {
                    PlayLog.e("http FAIL ${outgoing.url.host} code=${response.code} range=${outgoing.header("Range")}")
                }
                response
            }
            .build()
        val seekableFactory = OkHttpDataSource.Factory(playHttp)
            .setUserAgent(StreamResolver.ANDROID_USER_AGENT)
        val chunkedFactory = YoutubeChunkDataSource.Factory(playHttp)
        val httpFactory = PlaybackDataSource.Factory(seekableFactory, chunkedFactory)
        val upstream = DefaultDataSource.Factory(this, httpFactory)
        val resolving = ResolvingDataSource.Factory(upstream) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme != PlayableSong.STREAM_SCHEME) return@Factory dataSpec
            val videoId = uri.lastPathSegment ?: throw IOException("Missing video id")
            PlayLog.d("resolving stream videoId=$videoId")
            val audio = try {
                StreamResolver.resolve(videoId)
            } catch (error: IOException) {
                throw error
            } catch (error: Throwable) {
                throw IOException("Stream resolve failed for $videoId", error)
            }
            PlayLog.d(
                "resolved videoId=$videoId client=${audio.client} kind=${audio.kind} itag=${audio.itag}",
            )
            dataSpec.buildUpon()
                .setUri(audio.url.toUri())
                .setHttpRequestHeaders(
                    mapOf(
                        "User-Agent" to audio.userAgent,
                        "X-Video-Id" to videoId,
                        "X-Stream-Kind" to audio.kind.name,
                    ),
                )
                .build()
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(resolving)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retriedCurrent = false
                val song = PlayableSong.from(mediaItem ?: return)
                PlayLog.d("transition reason=$reason id=${song?.id} title=${song?.title}")
                song?.let(recentStore::add)
                if (song != null && song.id != lyricsSongId) {
                    reloadLyricsForCurrent()
                } else {
                    postPlaybackNotification()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) ensureLyricTicker() else lyricTickerJob?.cancel()
                postPlaybackNotification()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                refreshLyricLine(force = true)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizer.attach(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                val song = currentSong()
                PlayLog.e(
                    "playerError code=${error.errorCode} name=${error.errorCodeName} " +
                        "song=${song?.id} title=${song?.title} retried=$retriedCurrent " +
                        "chain=${PlayLog.chain(error)}",
                    error,
                )
                val recoverable = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
                if (recoverable && song != null && !song.isLocal && !retriedCurrent) {
                    retriedCurrent = true
                    PlayLog.w("retry same song after IO error id=${song.id}")
                    StreamResolver.invalidate(song.id)
                    runCatching {
                        player.prepare()
                        player.play()
                    }
                    return
                }
                retriedCurrent = false
                if (player.hasNextMediaItem()) {
                    PlayLog.w("skip to next after error song=${song?.id}")
                    runCatching {
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    }
                } else {
                    PlayLog.e("pause after error, not stopping. song=${song?.id} title=${song?.title}")
                    runCatching { player.pause() }
                }
            }
        })
        createPlaybackChannel()
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(launch)
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(this)))
            .build()
        equalizer.attach(player.audioSessionId)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                PlayLog.e("foreground service start not allowed")
                postPlaybackNotification()
            }
        })
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        postPlaybackNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlaybackNotification.ACTION_PLAY -> runCatching { player.play() }
            PlaybackNotification.ACTION_PAUSE -> runCatching { player.pause() }
            PlaybackNotification.ACTION_NEXT -> runCatching {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            }
            PlaybackNotification.ACTION_PREV -> runCatching {
                if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            }
        }
        postPlaybackNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.mediaItemCount == 0 || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        val mediaBinder = super.onBind(intent)
        return if (intent?.action == SERVICE_INTERFACE) mediaBinder else localBinder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    fun playQueue(songs: List<PlayableSong>, index: Int) {
        if (songs.isEmpty()) return
        val safeIndex = index.coerceIn(0, songs.lastIndex)
        PlayLog.d("playQueue size=${songs.size} index=$safeIndex id=${songs[safeIndex].id} title=${songs[safeIndex].title}")
        player.setMediaItems(songs.map { it.toMediaItem() }, safeIndex, 0L)
        player.prepare()
        player.play()
        recentStore.add(songs[safeIndex])
        postPlaybackNotification()
    }

    fun appendToQueue(songs: List<PlayableSong>) {
        if (songs.isEmpty()) return
        val existing = (0 until player.mediaItemCount).mapNotNull { i ->
            PlayableSong.from(player.getMediaItemAt(i))?.id
        }.toHashSet()
        val fresh = songs.filter { it.id !in existing }
        if (fresh.isEmpty()) return
        player.addMediaItems(fresh.map { it.toMediaItem() })
        postPlaybackNotification()
    }

    fun currentIndex(): Int = player.currentMediaItemIndex.coerceAtLeast(0)

    fun playAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
        postPlaybackNotification()
    }

    fun addToQueue(song: PlayableSong) {
        val item = OfflineDownloads.attachAll(listOf(song)).first().toMediaItem()
        if (player.mediaItemCount == 0) {
            playQueue(listOf(PlayableSong.from(item) ?: song), 0)
            return
        }
        player.addMediaItem(item)
        postPlaybackNotification()
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from == to) return
        if (from !in 0 until player.mediaItemCount) return
        if (to !in 0 until player.mediaItemCount) return
        player.moveMediaItem(from, to)
        postPlaybackNotification()
    }

    fun removeFromQueue(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        if (index == player.currentMediaItemIndex) return
        player.removeMediaItem(index)
    }

    fun setSleepMinutes(minutes: Int) {
        sleepUntilTrackEnd = false
        sleepEndsAtMs = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        watchSleep()
    }

    fun setSleepEndOfSong() {
        sleepEndsAtMs = 0L
        sleepUntilTrackEnd = true
        watchSleep()
    }

    fun clearSleep() {
        sleepEndsAtMs = 0L
        sleepUntilTrackEnd = false
    }

    fun sleepRemainingMs(): Long {
        if (sleepUntilTrackEnd) {
            val left = player.duration - player.currentPosition
            return if (left > 0) left else 0L
        }
        return (sleepEndsAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun watchSleep() {
        if (sleepJob?.isActive == true) return
        sleepJob = scope.launch {
            while (isActive) {
                delay(400)
                val timedOut = sleepEndsAtMs > 0L && System.currentTimeMillis() >= sleepEndsAtMs
                val trackDone = sleepUntilTrackEnd &&
                    player.duration > 0L &&
                    player.currentPosition >= player.duration - 400L
                if (timedOut || trackDone) {
                    runCatching { player.pause() }
                    clearSleep()
                }
            }
        }
    }

    fun currentSong(): PlayableSong? = player.currentMediaItem?.let(PlayableSong::from)

    fun queue(): List<PlayableSong> = buildList {
        for (i in 0 until player.mediaItemCount) {
            PlayableSong.from(player.getMediaItemAt(i))?.let(::add)
        }
    }

    private fun postPlaybackNotification() {
        val song = currentSong()
        val mediaSession = session
        if (song == null || mediaSession == null) return
        val notification = PlaybackNotification.build(
            this,
            mediaSession,
            song,
            player.isPlaying,
            lyricLine = postedLyricLine,
        )
        runCatching {
            if (!startedAsService) {
                startedAsService = true
                ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java))
            }
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            PlayLog.d("notification posted title=${song.title} playing=${player.isPlaying}")
        }.onFailure { PlayLog.e("notification failed: ${it.message}", it) }
    }

    private fun reloadLyricsForCurrent() {
        lyricsJob?.cancel()
        lyricTickerJob?.cancel()
        activeLyrics = null
        postedLyricLine = null
        val song = currentSong()
        lyricsSongId = song?.id
        if (song == null) {
            syncSessionSecondaryText(null)
            postPlaybackNotification()
            return
        }
        syncSessionSecondaryText(null)
        lyricsJob = scope.launch {
            var duration = player.duration.coerceAtLeast(0L)
            if (duration <= 0L) {
                repeat(24) {
                    delay(250)
                    if (currentSong()?.id != song.id) return@launch
                    duration = player.duration.coerceAtLeast(0L)
                    if (duration > 0L) return@repeat
                }
            }
            val lyrics = runCatching {
                LyricsResolver.load(song, duration)
            }.getOrNull()?.takeIf { it.synced && !it.isEmpty }
            if (currentSong()?.id != song.id) return@launch
            activeLyrics = lyrics
            PlayLog.d(
                "lyrics loaded id=${song.id} synced=${lyrics != null} lines=${lyrics?.lines?.size ?: 0}",
            )
            refreshLyricLine(force = true)
            if (player.isPlaying) ensureLyricTicker()
        }
        postPlaybackNotification()
    }

    private fun ensureLyricTicker() {
        if (activeLyrics == null) return
        if (lyricTickerJob?.isActive == true) return
        lyricTickerJob = scope.launch {
            while (isActive) {
                refreshLyricLine()
                delay(400L)
            }
        }
    }

    private fun refreshLyricLine(force: Boolean = false) {
        val lyrics = activeLyrics
        val song = currentSong()
        if (lyrics == null || song == null) {
            if (postedLyricLine != null) {
                postedLyricLine = null
                syncSessionSecondaryText(null)
                postPlaybackNotification()
            } else if (force) {
                syncSessionSecondaryText(null)
                postPlaybackNotification()
            }
            return
        }
        val offset = lyricsOffsetStore.get(song.id)
        val line = lyrics.lines
            .lastOrNull { it.timeMs <= player.currentPosition - offset }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (!force && line == postedLyricLine) return
        postedLyricLine = line
        // Android 13+ System UI media card reads MediaMetadata.artist, not setContentText.
        syncSessionSecondaryText(line)
        postPlaybackNotification()
    }

    /**
     * Puts the live lyric on the session artist line (what System UI shows under the title).
     * Real artist stays in MediaItem extras via [PlayableSong.from].
     */
    private fun syncSessionSecondaryText(lyricLine: String?) {
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        val song = PlayableSong.from(item) ?: return
        val secondary = lyricLine?.takeIf { it.isNotBlank() } ?: song.artist
        if (item.mediaMetadata.artist?.toString() == secondary) return
        val updated = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setTitle(song.title)
                    .setArtist(secondary)
                    .setSubtitle(if (lyricLine != null) song.artist else null)
                    .build(),
            )
            .build()
        runCatching { player.replaceMediaItem(index, updated) }
            .onFailure { PlayLog.e("lyric metadata update failed: ${it.message}", it) }
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    override fun onDestroy() {
        clearListener()
        scope.cancel()
        equalizer.release()
        session?.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "musipedia_now_playing"
        const val NOTIFICATION_ID = 1001
    }
}
