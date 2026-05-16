package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File
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
            AtomicFileWriter.writeText(file, yaml.dump(data))
        }
    }

    companion object {
        private const val KEY_DURATION = "lastDurationMinutes"
        private const val DEFAULT_DURATION = 20
    }
}
