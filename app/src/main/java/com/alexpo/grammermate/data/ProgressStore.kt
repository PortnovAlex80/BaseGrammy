package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

class ProgressStore(private val context: Context) {
    private val yaml = Yaml()
    private val file = File(context.filesDir, "progress.yaml")

    fun load(): TrainingProgress {
        if (!file.exists()) return TrainingProgress()
        val map = yaml.load<Map<String, Any>>(file.readText()) ?: return TrainingProgress()
        return TrainingProgress(
            languageId = map["languageId"] as? String ?: "en",
            mode = TrainingMode.valueOf(map["mode"] as? String ?: TrainingMode.LESSON.name),
            lessonId = map["lessonId"] as? String,
            currentIndex = (map["currentIndex"] as? Number)?.toInt() ?: 0,
            correctCount = (map["correctCount"] as? Number)?.toInt() ?: 0,
            incorrectCount = (map["incorrectCount"] as? Number)?.toInt() ?: 0,
            incorrectAttemptsForCard = (map["incorrectAttemptsForCard"] as? Number)?.toInt() ?: 0,
            activeTimeMs = (map["activeTimeMs"] as? Number)?.toLong() ?: 0L,
            state = SessionState.valueOf(map["state"] as? String ?: SessionState.PAUSED.name)
        )
    }

    fun save(progress: TrainingProgress) {
        val data = linkedMapOf(
            "languageId" to progress.languageId,
            "mode" to progress.mode.name,
            "lessonId" to progress.lessonId,
            "currentIndex" to progress.currentIndex,
            "correctCount" to progress.correctCount,
            "incorrectCount" to progress.incorrectCount,
            "incorrectAttemptsForCard" to progress.incorrectAttemptsForCard,
            "activeTimeMs" to progress.activeTimeMs,
            "state" to progress.state.name
        )
        file.writeText(yaml.dump(data))
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
