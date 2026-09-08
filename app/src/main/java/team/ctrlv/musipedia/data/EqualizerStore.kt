package team.ctrlv.musipedia

import android.content.Context

/**
 * Persists equalizer on/off, named preset, normalized band levels (-1..1),
 * and bass boost strength (0..1000).
 */
class EqualizerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var presetId: String
        get() = prefs.getString(KEY_PRESET, EqPreset.FLAT.id) ?: EqPreset.FLAT.id
        set(value) = prefs.edit().putString(KEY_PRESET, value).apply()

    var bassStrength: Int
        get() = prefs.getInt(KEY_BASS, 0).coerceIn(0, 1000)
        set(value) = prefs.edit().putInt(KEY_BASS, value.coerceIn(0, 1000)).apply()

    /** Normalized band gains in -1..1. Empty means flat / use preset curve. */
    fun bandLevels(): FloatArray {
        val raw = prefs.getString(KEY_BANDS, null) ?: return FloatArray(0)
        return raw.split(',')
            .mapNotNull { it.toFloatOrNull()?.coerceIn(-1f, 1f) }
            .toFloatArray()
    }

    fun saveBandLevels(levels: FloatArray) {
        prefs.edit()
            .putString(KEY_BANDS, levels.joinToString(",") { "%.3f".format(it.coerceIn(-1f, 1f)) })
            .apply()
    }

    fun clearBandLevels() {
        prefs.edit().remove(KEY_BANDS).apply()
    }

    companion object {
        private const val PREFS = "musium_equalizer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_BANDS = "bands"
        private const val KEY_BASS = "bass"
    }
}

enum class EqPreset(val id: String, val label: String, val curve: FloatArray) {
    FLAT("flat", "Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("bass", "Bass", floatArrayOf(0.72f, 0.48f, 0.18f, 0f, 0f)),
    TREBLE("treble", "Treble", floatArrayOf(0f, 0f, 0.12f, 0.42f, 0.68f)),
    VOCAL("vocal", "Vocal", floatArrayOf(-0.28f, 0.08f, 0.55f, 0.35f, 0.12f)),
    ROCK("rock", "Rock", floatArrayOf(0.45f, 0.22f, -0.12f, 0.28f, 0.42f)),
    POP("pop", "Pop", floatArrayOf(-0.12f, 0.28f, 0.42f, 0.22f, -0.08f)),
    ELECTRONIC("electronic", "Electronic", floatArrayOf(0.58f, 0.32f, 0f, 0.28f, 0.48f)),
    CLASSICAL("classical", "Classical", floatArrayOf(0f, 0.18f, 0f, 0.22f, 0.32f)),
    CUSTOM("custom", "Custom", floatArrayOf(0f, 0f, 0f, 0f, 0f));

    companion object {
        fun fromId(id: String): EqPreset = entries.firstOrNull { it.id == id } ?: FLAT
    }
}

data class EqualizerBand(
    val index: Int,
    val centerHz: Int,
    /** Normalized -1..1 relative to device band range. */
    val level: Float,
)

data class EqualizerUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val presetId: String = EqPreset.FLAT.id,
    val bands: List<EqualizerBand> = emptyList(),
    /** 0..1000 android BassBoost strength. */
    val bassStrength: Int = 0,
    val bassAvailable: Boolean = false,
)
