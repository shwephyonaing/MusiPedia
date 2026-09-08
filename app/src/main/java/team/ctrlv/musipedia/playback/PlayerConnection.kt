package team.ctrlv.musipedia

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
    var repeatOne by mutableStateOf(service.player.repeatMode == Player.REPEAT_MODE_ONE)
        private set
    var sleepEndsAt by mutableLongStateOf(0L)
        private set
    var sleepEndOfTrack by mutableStateOf(false)
        private set
    var sleepRemaining by mutableLongStateOf(0L)
        private set
    var equalizerState by mutableStateOf(service.equalizer.snapshot())
        private set
    var karaokeActive by mutableStateOf(false)
        private set
    var karaokeLoading by mutableStateOf(false)
        private set
    var karaokeMessage by mutableStateOf<String?>(null)
        private set
    fun release() {
        radioJob?.cancel()
        karaokeJob?.cancel()
        sleepUiJob?.cancel()
        runCatching { service.player.removeListener(this) }
    }

    private var radioJob: Job? = null
    private var karaokeJob: Job? = null
    private var sleepUiJob: Job? = null
    private var karaokeOriginal: PlayableSong? = null
    private var karaokeTrackId: String? = null

    val player: Player get() = service.player
    val sleepActive: Boolean get() = sleepEndOfTrack || sleepEndsAt > 0L

    fun currentPosition(): Long {
        return runCatching { service.player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
    }

    init {
        runCatching { service.player.addListener(this) }
        refresh()
        refreshSleep()
        refreshEqualizer()
        sleepUiJob = service.scope.launch {
            while (true) {
                delay(500)
                refreshSleep()
            }
        }
    }

    fun play(songs: List<PlayableSong>, index: Int = 0) {
        lastError = null
        clearKaraokeState()
        radioJob?.cancel()
        PlayLog.d("ui play index=$index size=${songs.size} first=${songs.getOrNull(index)?.title}")
        service.playQueue(OfflineDownloads.attachAll(songs), index)
        refresh()
    }

    /** Play one song, then append YouTube radio / related tracks. */
    fun playSong(song: PlayableSong) {
        lastError = null
        clearKaraokeState()
        radioJob?.cancel()
        PlayLog.d("ui playSong id=${song.id} title=${song.title}")
        service.playQueue(OfflineDownloads.attachAll(listOf(song)), 0)
        refresh()
        if (song.isLocal) return
        radioJob = service.scope.launch {
            val related = runCatching { MusicRepository.related(song.id) }.getOrDefault(emptyList())
            if (related.isEmpty()) return@launch
            if (service.currentSong()?.id != song.id) return@launch
            service.appendToQueue(OfflineDownloads.attachAll(related))
            refresh()
        }
    }

    fun togglePlay() {
        runCatching {
            if (service.player.isPlaying) service.player.pause() else service.player.play()
        }
    }

    fun toggleRepeatOne() {
        service.player.repeatMode = if (repeatOne) {
            Player.REPEAT_MODE_OFF
        } else {
            Player.REPEAT_MODE_ONE
        }
        repeatOne = service.player.repeatMode == Player.REPEAT_MODE_ONE
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
        clearKaraokeState()
        service.playAt(index)
    }

    fun addToQueue(song: PlayableSong) {
        lastError = null
        service.addToQueue(song)
        refresh()
    }

    fun moveInQueue(from: Int, to: Int) {
        service.moveInQueue(from, to)
        refresh()
    }

    fun removeFromQueue(index: Int) {
        service.removeFromQueue(index)
        refresh()
    }

    fun toggleKaraoke() {
        if (karaokeLoading) return
        if (karaokeActive) {
            exitKaraoke()
            return
        }
        val song = service.currentSong() ?: return
        if (song.isLocal) {
            karaokeMessage = "Karaoke isn’t available for local files"
            return
        }
        karaokeJob?.cancel()
        karaokeLoading = true
        karaokeMessage = null
        val original = song
        karaokeJob = service.scope.launch {
            val found = runCatching { MusicRepository.findKaraoke(original) }.getOrNull()
            karaokeLoading = false
            if (found == null) {
                karaokeMessage = "No karaoke / instrumental version found"
                return@launch
            }
            if (service.currentSong()?.id != original.id) {
                karaokeMessage = "Track changed — karaoke cancelled"
                return@launch
            }
            karaokeOriginal = original
            karaokeTrackId = found.id
            karaokeActive = true
            karaokeMessage = null
            service.replaceCurrent(found)
            refresh()
        }
    }

    fun exitKaraoke() {
        karaokeJob?.cancel()
        karaokeLoading = false
        val original = karaokeOriginal
        clearKaraokeState()
        if (original != null) {
            service.replaceCurrent(original)
            refresh()
        }
    }

    private fun clearKaraokeState() {
        karaokeJob?.cancel()
        karaokeActive = false
        karaokeLoading = false
        karaokeOriginal = null
        karaokeTrackId = null
        karaokeMessage = null
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

    fun setEqualizerEnabled(enabled: Boolean) {
        service.equalizer.setEnabled(enabled)
        refreshEqualizer()
    }

    fun applyEqualizerPreset(preset: EqPreset) {
        service.equalizer.applyPreset(preset)
        refreshEqualizer()
    }

    fun setEqualizerBand(band: Int, normalized: Float) {
        service.equalizer.setBandNormalized(band, normalized)
        refreshEqualizer()
    }

    fun setBassBoost(strength: Int) {
        service.equalizer.setBassStrength(strength)
        refreshEqualizer()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playing = isPlaying
        PlayLog.d("isPlaying=$isPlaying song=${current?.id}")
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        refreshEqualizer()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val nextId = mediaItem?.let(PlayableSong::from)?.id
        if (karaokeActive && nextId != null && nextId != karaokeTrackId && nextId != karaokeOriginal?.id) {
            karaokeActive = false
            karaokeOriginal = null
            karaokeTrackId = null
            karaokeMessage = null
        }
        refresh()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        PlayLog.d("state=$playbackState song=${runCatching { service.currentSong()?.id }.getOrNull()}")
        refresh()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        repeatOne = repeatMode == Player.REPEAT_MODE_ONE
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
            repeatOne = service.player.repeatMode == Player.REPEAT_MODE_ONE
        }
    }

    private fun refreshSleep() {
        sleepEndsAt = service.sleepEndsAtMs
        sleepEndOfTrack = service.sleepUntilTrackEnd
        sleepRemaining = service.sleepRemainingMs()
    }

    private fun refreshEqualizer() {
        equalizerState = service.equalizer.snapshot()
    }
}
