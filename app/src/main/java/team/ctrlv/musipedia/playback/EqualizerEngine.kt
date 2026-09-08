package team.ctrlv.musipedia

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlin.math.roundToInt

/**
 * Owns the Android [Equalizer] + [BassBoost] attached to ExoPlayer's audio session.
 * Band levels are stored normalized (-1..1) and mapped to the device millibel range.
 */
class EqualizerEngine(private val store: EqualizerStore) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var sessionId: Int = 0

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) {
            release()
            return
        }
        if (audioSessionId == sessionId && equalizer != null) {
            applyFromStore()
            return
        }
        releaseEffectsOnly()
        sessionId = audioSessionId
        equalizer = runCatching {
            Equalizer(0, audioSessionId).also { it.enabled = store.enabled }
        }.getOrNull()
        bassBoost = runCatching {
            BassBoost(0, audioSessionId).also {
                it.enabled = store.enabled && store.bassStrength > 0
                if (it.strengthSupported) {
                    it.setStrength(store.bassStrength.toShort())
                }
            }
        }.getOrNull()
        applyFromStore()
    }

    @Synchronized
    fun release() {
        releaseEffectsOnly()
        sessionId = 0
    }

    private fun releaseEffectsOnly() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        store.enabled = enabled
        equalizer?.enabled = enabled
        val bass = bassBoost
        if (bass != null) {
            bass.enabled = enabled && store.bassStrength > 0
        }
    }

    @Synchronized
    fun applyPreset(preset: EqPreset) {
        if (preset == EqPreset.CUSTOM) {
            store.presetId = EqPreset.CUSTOM.id
            return
        }
        store.presetId = preset.id
        store.clearBandLevels()
        writeCurve(preset.curve)
    }

    @Synchronized
    fun setBandNormalized(band: Int, normalized: Float) {
        val eq = equalizer ?: return
        if (band !in 0 until eq.numberOfBands) return
        val level = normalized.coerceIn(-1f, 1f)
        val range = eq.bandLevelRange
        val mB = ((level + 1f) / 2f * (range[1] - range[0]) + range[0]).roundToInt().toShort()
        runCatching { eq.setBandLevel(band.toShort(), mB) }
        val count = eq.numberOfBands.toInt()
        val levels = FloatArray(count) { i ->
            if (i == band) level else readNormalized(eq, i)
        }
        store.saveBandLevels(levels)
        store.presetId = EqPreset.CUSTOM.id
    }

    @Synchronized
    fun setBassStrength(strength: Int) {
        val value = strength.coerceIn(0, 1000)
        store.bassStrength = value
        val bass = bassBoost ?: return
        if (bass.strengthSupported) {
            runCatching { bass.setStrength(value.toShort()) }
        }
        bass.enabled = store.enabled && value > 0
    }

    @Synchronized
    fun snapshot(): EqualizerUiState {
        val eq = equalizer
        if (eq == null) {
            return EqualizerUiState(
                available = false,
                enabled = store.enabled,
                presetId = store.presetId,
                bassStrength = store.bassStrength,
            )
        }
        val count = eq.numberOfBands.toInt()
        val bands = (0 until count).map { i ->
            EqualizerBand(
                index = i,
                centerHz = eq.getCenterFreq(i.toShort()) / 1000,
                level = readNormalized(eq, i),
            )
        }
        val bass = bassBoost
        return EqualizerUiState(
            available = true,
            enabled = store.enabled,
            presetId = store.presetId,
            bands = bands,
            bassStrength = store.bassStrength,
            bassAvailable = bass != null && bass.strengthSupported,
        )
    }

    private fun applyFromStore() {
        val eq = equalizer ?: return
        eq.enabled = store.enabled
        val saved = store.bandLevels()
        val preset = EqPreset.fromId(store.presetId)
        when {
            saved.isNotEmpty() -> writeCurve(saved)
            preset != EqPreset.CUSTOM -> writeCurve(preset.curve)
            else -> writeCurve(FloatArray(eq.numberOfBands.toInt()) { 0f })
        }
        val bass = bassBoost
        if (bass != null) {
            val strength = store.bassStrength
            if (bass.strengthSupported) {
                runCatching { bass.setStrength(strength.toShort()) }
            }
            bass.enabled = store.enabled && strength > 0
        }
    }

    private fun writeCurve(curve: FloatArray) {
        val eq = equalizer ?: return
        val count = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        for (i in 0 until count) {
            val source = when {
                curve.isEmpty() -> 0f
                curve.size == count -> curve[i]
                else -> sampleCurve(curve, i, count)
            }.coerceIn(-1f, 1f)
            val mB = ((source + 1f) / 2f * (range[1] - range[0]) + range[0]).roundToInt().toShort()
            runCatching { eq.setBandLevel(i.toShort(), mB) }
        }
    }

    /** Resample a 5-point preset curve onto an arbitrary device band count. */
    private fun sampleCurve(curve: FloatArray, band: Int, bandCount: Int): Float {
        if (curve.size == 1) return curve[0]
        if (bandCount <= 1) return curve[0]
        val t = band.toFloat() / (bandCount - 1).toFloat()
        val pos = t * (curve.size - 1)
        val i = pos.toInt().coerceIn(0, curve.lastIndex - 1)
        val frac = pos - i
        return curve[i] * (1f - frac) + curve[i + 1] * frac
    }

    private fun readNormalized(eq: Equalizer, band: Int): Float {
        val range = eq.bandLevelRange
        val span = (range[1] - range[0]).toFloat().coerceAtLeast(1f)
        val level = eq.getBandLevel(band.toShort()).toFloat()
        return ((level - range[0]) / span * 2f - 1f).coerceIn(-1f, 1f)
    }
}
