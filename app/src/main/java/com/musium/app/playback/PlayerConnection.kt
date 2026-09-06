package com.musium.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerConnection(val service: MusicService) : Player.Listener {
    var current by mutableStateOf(service.currentSong())
        private set
    var playing by mutableStateOf(service.player.isPlaying)
        private set
    var duration by mutableLongStateOf(service.player.duration.coerceAtLeast(0L))
        private set
    var queue by mutableStateOf(service.queue())
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var sleepEndsAt by mutableLongStateOf(0L)
        private set
    var sleepEndOfTrack by mutableStateOf(false)
        private set
    var sleepRemaining by mutableLongStateOf(0L)
        private set
    private var sleepUiJob: Job? = null

    val player: Player get() = service.player
    val sleepActive: Boolean get() = sleepEndOfTrack || sleepEndsAt > 0L

    fun currentPosition(): Long {
        return runCatching { service.player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
    }

    init {
        runCatching { service.player.addListener(this) }
        refresh()
        refreshSleep()
        sleepUiJob = service.scope.launch {
            while (true) {
                delay(500)
                refreshSleep()
            }
        }
    }

    fun release() {
        sleepUiJob?.cancel()
        runCatching { service.player.removeListener(this) }
    }

    fun play(songs: List<PlayableSong>, index: Int = 0) {
        lastError = null
        PlayLog.d("ui play index=$index size=${songs.size} first=${songs.getOrNull(index)?.title}")
        service.playQueue(OfflineDownloads.attachAll(songs), index)
    }

    fun togglePlay() {
        runCatching {
            if (service.player.isPlaying) service.player.pause() else service.player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        runCatching { service.player.seekTo(positionMs) }
    }

    fun next() {
        runCatching {
            if (service.player.hasNextMediaItem()) service.player.seekToNextMediaItem()
        }
    }

    fun previous() {
        runCatching {
            if (service.player.hasPreviousMediaItem()) service.player.seekToPreviousMediaItem()
        }
    }

    fun playAt(index: Int) {
        lastError = null
        service.playAt(index)
    }

    fun removeFromQueue(index: Int) {
        service.removeFromQueue(index)
        refresh()
    }

    fun setSleepMinutes(minutes: Int) {
        service.setSleepMinutes(minutes)
        refreshSleep()
    }

    fun setSleepEndOfSong() {
        service.setSleepEndOfSong()
        refreshSleep()
    }

    fun clearSleep() {
        service.clearSleep()
        refreshSleep()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playing = isPlaying
        PlayLog.d("isPlaying=$isPlaying song=${current?.id}")
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        refresh()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        PlayLog.d("state=$playbackState song=${runCatching { service.currentSong()?.id }.getOrNull()}")
        refresh()
    }

    override fun onPlayerError(error: PlaybackException) {
        lastError = "${error.errorCodeName}: ${error.message ?: PlayLog.chain(error)}"
        PlayLog.e("ui lastError=$lastError", error)
        refresh()
    }

    private fun refresh() {
        runCatching {
            current = service.currentSong()
            playing = service.player.isPlaying
            duration = service.player.duration.coerceAtLeast(0L)
            queue = service.queue()
            currentIndex = service.currentIndex()
        }
    }

    private fun refreshSleep() {
        sleepEndsAt = service.sleepEndsAtMs
        sleepEndOfTrack = service.sleepUntilTrackEnd
        sleepRemaining = service.sleepRemainingMs()
    }
}
