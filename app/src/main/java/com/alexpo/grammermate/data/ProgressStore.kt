package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

class ProgressStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "progress.yaml")
    private val schemaVersion = 1

    fun load(): TrainingProgress {
        if (!file.exists()) return TrainingProgress()
        val raw = yaml.load<Any>(file.readText()) ?: return TrainingProgress()
        val data = when (raw) {
            is Map<*, *> -> raw
            else -> return TrainingProgress()
        }
        val payload = (data["data"] as? Map<*, *>) ?: data
        return TrainingProgress(
            languageId = payload["languageId"] as? String ?: "en",
            mode = TrainingMode.valueOf(payload["mode"] as? String ?: TrainingMode.LESSON.name),
            lessonId = payload["lessonId"] as? String,
            currentIndex = (payload["currentIndex"] as? Number)?.toInt() ?: 0,
            correctCount = (payload["correctCount"] as? Number)?.toInt() ?: 0,
            incorrectCount = (payload["incorrectCount"] as? Number)?.toInt() ?: 0,
            incorrectAttemptsForCard = (payload["incorrectAttemptsForCard"] as? Number)?.toInt() ?: 0,
            activeTimeMs = (payload["activeTimeMs"] as? Number)?.toLong() ?: 0L,
            state = SessionState.valueOf(payload["state"] as? String ?: SessionState.PAUSED.name),
            bossLessonRewards = (payload["bossLessonRewards"] as? Map<*, *>)?.mapNotNull { (key, value) ->
                val lessonId = key as? String ?: return@mapNotNull null
                val reward = value as? String ?: return@mapNotNull null
                lessonId to reward
            }?.toMap() ?: emptyMap(),
            bossMegaReward = payload["bossMegaReward"] as? String
        )
    }

    fun save(progress: TrainingProgress) {
        val payload = linkedMapOf(
            "languageId" to progress.languageId,
            "mode" to progress.mode.name,
            "lessonId" to progress.lessonId,
            "currentIndex" to progress.currentIndex,
            "correctCount" to progress.correctCount,
            "incorrectCount" to progress.incorrectCount,
            "incorrectAttemptsForCard" to progress.incorrectAttemptsForCard,
            "activeTimeMs" to progress.activeTimeMs,
            "state" to progress.state.name,
            "bossLessonRewards" to progress.bossLessonRewards,
            "bossMegaReward" to progress.bossMegaReward
        )
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "data" to payload
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
