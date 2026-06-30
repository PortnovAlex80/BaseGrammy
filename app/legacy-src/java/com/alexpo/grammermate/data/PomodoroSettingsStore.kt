package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PomodoroSettingsStore(context: Context) {
    private val yaml = Yaml()
    private val file = File(context.filesDir, "grammarmate/pomodoro_settings.yaml")
    private val lock = ReentrantLock()

    fun load(): Int {
        return lock.withLock {
            if (!file.exists() || file.length() == 0L) return DEFAULT_DURATION
            try {
                val raw = yaml.load<Any>(file.readText())
                val map = raw as? Map<*, *> ?: return DEFAULT_DURATION
                (map[KEY_DURATION] as? Number)?.toInt() ?: DEFAULT_DURATION
            } catch (_: Exception) {
                DEFAULT_DURATION
            }
        }
    }

    fun save(durationMinutes: Int) {
        lock.withLock {
            val data = mapOf<String, Any>(KEY_DURATION to durationMinutes)
            try {
                AtomicFileWriter.writeText(file, yaml.dump(data))
                Log.i("PomodoroSettingsStore", "Successfully saved pomodoro settings: ${file.name} (${file.length()} bytes)")
            } catch (e: IOException) {
                Log.e("PomodoroSettingsStore", "Failed to save pomodoro settings: ${file.name}", e)
                throw e
            } catch (e: Exception) {
                Log.e("PomodoroSettingsStore", "Unexpected error saving pomodoro settings: ${file.name}", e)
                throw IOException("Failed to save pomodoro settings", e)
            }
        }
    }

    companion object {
        private const val KEY_DURATION = "lastDurationMinutes"
        private const val DEFAULT_DURATION = 20
    }
}
