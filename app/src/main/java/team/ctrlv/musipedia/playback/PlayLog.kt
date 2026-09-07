package team.ctrlv.musipedia

import android.util.Log

object PlayLog {
    const val TAG = "MusiPediaPlay"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message) else Log.e(TAG, message, error)
    }

    fun chain(error: Throwable?): String {
        val parts = mutableListOf<String>()
        var current = error
        var depth = 0
        while (current != null && depth < 6) {
            parts += "${current.javaClass.simpleName}: ${current.message}"
            current = current.cause
            depth++
        }
        return parts.joinToString(" <- ")
    }
}
