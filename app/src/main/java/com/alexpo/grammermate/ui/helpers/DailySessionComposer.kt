package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabWord
import com.alexpo.grammermate.data.WordMasteryStore
import java.io.File

/**
 * Pure builder that constructs a daily practice session (List<DailyTask>)
 * for a given lesson level and pack.
 *
 * Block 1 (Translation): 5 SentenceCards, SRS-priority.
 * Block 2 (Vocab): 5 VocabWords by rank range, SRS-due first.
 * Block 3 (Verb Drill): 5 VerbDrillCards from cumulative tenses, weak-first.
 */
class DailySessionComposer(
    private val lessonStore: LessonStore,
    private val verbDrillStore: VerbDrillStore,
    private val wordMasteryStore: WordMasteryStore
) {

    companion object {
        const val CARDS_PER_BLOCK = 10
        const val SENTENCE_COUNT = CARDS_PER_BLOCK
        const val VOCAB_COUNT = CARDS_PER_BLOCK
        const val VERB_COUNT = CARDS_PER_BLOCK

        val TENSE_LADDER: Map<Int, List<String>> = mapOf(
            1  to listOf("Presente"),
            2  to listOf("Presente", "Imperfetto"),
            3  to listOf("Presente", "Imperfetto", "Passato Prossimo"),
            4  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice"),
            5  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente"),
            6  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto"),
            7  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente"),
            8  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo"),
            9  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore"),
            10 to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore", "Congiuntivo Imperfetto"),
            11 to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore", "Congiuntivo Imperfetto", "Condizionale Passato"),
            12 to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore", "Congiuntivo Imperfetto", "Condizionale Passato", "Congiuntivo Passato")
        )
    }

    /**
     * @param cumulativeTenses active tenses for this lesson level, built from
     *   manifest lessons 1..lessonLevel. Caller reads from LessonPackManifest.
     */
    fun buildSession(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList()
    ): List<DailyTask> {
        val tasks = mutableListOf<DailyTask>()

        // Block 1: Translation sentences
        tasks.addAll(buildSentenceBlock(lessonLevel, packId, languageId, lessonId))

        // Block 2: Vocab flashcards
        tasks.addAll(buildVocabBlock(lessonLevel, packId, languageId))

        // Block 3: Verb drill
        val tenses = if (cumulativeTenses.isNotEmpty()) cumulativeTenses
                     else TENSE_LADDER[lessonLevel] ?: emptyList()
        tasks.addAll(buildVerbBlock(packId, languageId, tenses))

        return tasks
    }

    fun rebuildBlock(
        blockType: DailyBlockType,
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList()
    ): List<DailyTask> {
        val tenses = if (cumulativeTenses.isNotEmpty()) cumulativeTenses
                     else TENSE_LADDER[lessonLevel] ?: emptyList()
        return when (blockType) {
            DailyBlockType.TRANSLATE -> buildSentenceBlock(lessonLevel, packId, languageId, lessonId)
            DailyBlockType.VOCAB -> buildVocabBlock(lessonLevel, packId, languageId)
            DailyBlockType.VERBS -> buildVerbBlock(packId, languageId, tenses)
        }
    }

    private fun buildSentenceBlock(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String
    ): List<DailyTask.TranslateSentence> {
        val lessons = lessonStore.getLessons(languageId)
        val lesson = lessons.firstOrNull { it.id == lessonId } ?: return emptyList()

        val cards = lesson.cards
        if (cards.isEmpty()) return emptyList()

        val selected = cards.shuffled().take(SENTENCE_COUNT)

        return selected.mapIndexed { index, card ->
            val mode = when (index % 3) {
                0 -> InputMode.VOICE
                1 -> InputMode.KEYBOARD
                else -> InputMode.WORD_BANK
            }
            DailyTask.TranslateSentence(
                id = "sent_${card.id}",
                card = card,
                inputMode = mode
            )
        }
    }

    private fun buildVocabBlock(
        lessonLevel: Int,
        packId: String,
        languageId: String
    ): List<DailyTask.VocabFlashcard> {
        val rankMin = (lessonLevel - 1) * 10 + 1
        val rankMax = lessonLevel * 10

        val allWords = loadVocabWords(packId, languageId)
        val inRange = allWords.filter { it.rank in rankMin..rankMax }

        if (inRange.isEmpty()) return emptyList()

        // Only include words that are due (never reviewed or nextReviewDateMs <= now)
        val allMastery = wordMasteryStore.loadAll()
        val now = System.currentTimeMillis()
        val availableWords = inRange.filter { word ->
            val mastery = allMastery[word.id]
            mastery == null || mastery.nextReviewDateMs <= now
        }

        if (availableWords.isEmpty()) {
            // All words in range are scheduled for future review — pick least recently reviewed
            val fallback = inRange.sortedBy { word ->
                allMastery[word.id]?.lastReviewDateMs ?: 0L
            }.take(VOCAB_COUNT)
            if (fallback.isEmpty()) return emptyList()
            return fallback.mapIndexed { index, word ->
                val direction = if (index % 2 == 0) VocabDrillDirection.IT_TO_RU else VocabDrillDirection.RU_TO_IT
                DailyTask.VocabFlashcard(id = "voc_${word.id}", word = word, direction = direction)
            }
        }

        // Prioritize due words, then fill with new (never reviewed)
        val dueInRange = availableWords.filter { word ->
            val mastery = allMastery[word.id]
            mastery != null && mastery.nextReviewDateMs <= now
        }

        val selected = if (dueInRange.size >= VOCAB_COUNT) {
            dueInRange.sortedBy { it.rank }.take(VOCAB_COUNT)
        } else {
            val newWords = availableWords.filter { word ->
                allMastery[word.id] == null
            }
            (dueInRange.sortedBy { it.rank } + newWords.sortedBy { it.rank }).take(VOCAB_COUNT)
        }

        return selected.mapIndexed { index, word ->
            val direction = if (index % 2 == 0) {
                VocabDrillDirection.IT_TO_RU
            } else {
                VocabDrillDirection.RU_TO_IT
            }
            DailyTask.VocabFlashcard(
                id = "voc_${word.id}",
                word = word,
                direction = direction
            )
        }
    }

    private fun buildVerbBlock(
        packId: String,
        languageId: String,
        activeTenses: List<String>
    ): List<DailyTask.ConjugateVerb> {
        if (activeTenses.isEmpty()) return emptyList()
        val allCards = loadVerbDrillCards(packId, languageId)

        val filtered = allCards.filter { card ->
            card.tense != null && card.tense in activeTenses
        }

        if (filtered.isEmpty()) return emptyList()

        val progressMap = verbDrillStore.loadProgress()

        // Score each card by weakness: more unshown = weaker
        val scored = filtered.map { card ->
            val shownInCombo = progressMap.values.filter { it.tense == card.tense }
                .sumOf { it.everShownCardIds.size }
            val comboTotal = filtered.count { it.tense == card.tense }
            val weakness = if (comboTotal == 0) 0f else 1f - (shownInCombo.toFloat() / comboTotal)
            card to weakness
        }

        // Weak-first order, stable within same weakness (preserves CSV/frequency order)
        val selected = scored
            .sortedByDescending { it.second }
            .take(VERB_COUNT)

        return selected.mapIndexed { index, (card, _) ->
            val mode = if (index % 2 == 0) InputMode.KEYBOARD else InputMode.WORD_BANK
            DailyTask.ConjugateVerb(
                id = "verb_${card.id}",
                card = card,
                inputMode = mode
            )
        }
    }

    private fun loadVocabWords(packId: String, languageId: String): List<VocabWord> {
        val files = lessonStore.getVocabDrillFiles(packId, languageId)
        val words = mutableListOf<VocabWord>()

        for (file in files) {
            try {
                val stream = file.inputStream()
                val rows = com.alexpo.grammermate.data.ItalianDrillVocabParser.parse(stream, file.name)
                stream.close()

                val pos = file.name
                    .removePrefix("${languageId}_")
                    .removePrefix("drill_")
                    .removeSuffix(".csv")

                for (row in rows) {
                    words.add(VocabWord(
                        id = "${pos}_${row.rank}_${row.word}",
                        word = row.word,
                        pos = pos,
                        rank = row.rank,
                        meaningRu = row.meaningRu,
                        collocations = row.collocations,
                        forms = row.forms
                    ))
                }
            } catch (_: Exception) {
                // Skip unreadable files
            }
        }

        return words.sortedBy { it.rank }
    }

    private fun loadVerbDrillCards(packId: String, languageId: String): List<VerbDrillCard> {
        val files = lessonStore.getVerbDrillFiles(packId, languageId)
        val cards = mutableListOf<VerbDrillCard>()

        for (file in files) {
            try {
                val content = file.readText()
                val (_, parsed) = VerbDrillCsvParser.parse(content)
                cards.addAll(parsed)
            } catch (_: Exception) {
                // Skip unreadable files
            }
        }

        return cards
    }
}
