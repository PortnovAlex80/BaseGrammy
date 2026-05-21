package com.alexpo.grammermate.feature.daily

import android.util.Log
import com.alexpo.grammermate.data.DailyBlock
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyCursorState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Pure builder that constructs a daily practice session (List<DailyTask>)
 * for a given lesson level and pack, using cursor-based card selection.
 *
 * Block 1 (Translation): Cursor-driven by lesson index + sentence offset.
 *   Takes next 10 cards in order from the current lesson (no shuffle).
 * Block 2 (Vocab): Pure SRS selection across the full word list.
 *   Most-overdue first, then new words, then least-recently-reviewed fallback.
 * Block 3 (Verb Drill): Weak-first ordering, excluding previously shown cards.
 */
class DailySessionComposer(
    private val lessonStore: LessonStore,
    private val verbDrillStore: VerbDrillStore,
    private val wordMasteryStore: WordMasteryStore,
    private val sessionSize: Int = 10
) {

    // Cached parsed data keyed by "$packId:$languageId". Files never change at runtime.
    private var cachedVocabWords: Pair<String, List<VocabWord>>? = null
    private var cachedVerbDrillCards: Pair<String, List<VerbDrillCard>>? = null

    companion object {

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
     * @param cursor cursor state tracking lesson index and sentence offset.
     */
    suspend fun buildSession(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList(),
        cursor: DailyCursorState = DailyCursorState()
    ): List<DailyTask> {
        val tenses = if (cumulativeTenses.isNotEmpty()) cumulativeTenses
                     else TENSE_LADDER[lessonLevel] ?: emptyList()

        // Build all 3 blocks in parallel — they read from independent data sources
        return coroutineScope {
            val translateBlock = async { buildSentenceBlock(lessonLevel, packId, languageId, lessonId, cursor) }
            val vocabBlock = async { buildVocabBlock(packId, languageId) }
            val verbBlock = async { buildVerbBlock(packId, languageId, tenses, cursor) }

            val tasks = mutableListOf<DailyTask>()
            tasks.addAll(translateBlock.await())
            tasks.addAll(vocabBlock.await())
            tasks.addAll(verbBlock.await())
            tasks
        }
    }

    /**
     * Build a structured list of [DailyBlock] for the daily practice session.
     * Returns 3 blocks in order: TRANSLATE, VOCAB, VERBS.
     * Empty blocks (no tasks) are omitted.
     */
    suspend fun buildBlocks(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList(),
        cursor: DailyCursorState = DailyCursorState()
    ): List<DailyBlock> {
        val tenses = if (cumulativeTenses.isNotEmpty()) cumulativeTenses
                     else TENSE_LADDER[lessonLevel] ?: emptyList()

        return coroutineScope {
            val translateTasks = async { buildSentenceBlock(lessonLevel, packId, languageId, lessonId, cursor) }
            val vocabTasks = async { buildVocabBlock(packId, languageId) }
            val verbTasks = async { buildVerbBlock(packId, languageId, tenses, cursor) }

            val blocks = mutableListOf<DailyBlock>()
            val translate = translateTasks.await()
            if (translate.isNotEmpty()) {
                blocks.add(DailyBlock(type = DailyBlockType.TRANSLATE, tasks = translate))
            }
            val vocab = vocabTasks.await()
            if (vocab.isNotEmpty()) {
                blocks.add(DailyBlock(type = DailyBlockType.VOCAB, tasks = vocab))
            }
            val verbs = verbTasks.await()
            if (verbs.isNotEmpty()) {
                blocks.add(DailyBlock(type = DailyBlockType.VERBS, tasks = verbs))
            }
            blocks
        }
    }

    /**
     * Rebuild a single block by type.
     * Returns a [DailyBlock] with fresh tasks.
     */
    fun rebuildBlockAsBlock(
        blockType: DailyBlockType,
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList(),
        cursor: DailyCursorState = DailyCursorState()
    ): DailyBlock {
        val tasks = rebuildBlock(blockType, lessonLevel, packId, languageId, lessonId, cumulativeTenses, cursor)
        return DailyBlock(type = blockType, tasks = tasks)
    }

    fun rebuildBlock(
        blockType: DailyBlockType,
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList(),
        cursor: DailyCursorState = DailyCursorState()
    ): List<DailyTask> {
        val tenses = if (cumulativeTenses.isNotEmpty()) cumulativeTenses
                     else TENSE_LADDER[lessonLevel] ?: emptyList()
        return when (blockType) {
            DailyBlockType.TRANSLATE -> buildSentenceBlock(lessonLevel, packId, languageId, lessonId, cursor)
            DailyBlockType.VOCAB -> buildVocabBlock(packId, languageId)
            DailyBlockType.VERBS -> buildVerbBlock(packId, languageId, tenses, cursor)
        }
    }

    /**
     * Build a Repeat session using specific card IDs from the first session of the day.
     *
     * Block 1 (Translation): looks up sentence cards by ID from all lessons.
     * Block 2 (Vocab): always follows its own SRS process independently.
     * Block 3 (Verb Drill): looks up verb cards by ID from verb drill files.
     *
     * @param sentenceCardIds card IDs from the first session's block 1.
     * @param verbCardIds card IDs from the first session's block 3.
     */
    suspend fun buildRepeatSession(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList(),
        sentenceCardIds: List<String> = emptyList(),
        verbCardIds: List<String> = emptyList()
    ): List<DailyTask> {
        return coroutineScope {
            val translateBlock = async { buildSentenceBlockFromIds(lessonLevel, packId, languageId, lessonId, sentenceCardIds) }
            val vocabBlock = async { buildVocabBlock(packId, languageId) }
            val verbBlock = async { buildVerbBlockFromIds(packId, languageId, verbCardIds) }

            val tasks = mutableListOf<DailyTask>()
            tasks.addAll(translateBlock.await())
            tasks.addAll(vocabBlock.await())
            tasks.addAll(verbBlock.await())
            tasks
        }
    }

    /**
     * Build a Repeat session as structured blocks.
     * Same logic as [buildRepeatSession] but returns [List<DailyBlock>].
     * Empty blocks are omitted.
     */
    suspend fun buildRepeatBlocks(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cumulativeTenses: List<String> = emptyList(),
        sentenceCardIds: List<String> = emptyList(),
        verbCardIds: List<String> = emptyList()
    ): List<DailyBlock> {
        return coroutineScope {
            val translateTasks = async { buildSentenceBlockFromIds(lessonLevel, packId, languageId, lessonId, sentenceCardIds) }
            val vocabTasks = async { buildVocabBlock(packId, languageId) }
            val verbTasks = async { buildVerbBlockFromIds(packId, languageId, verbCardIds) }

            val blocks = mutableListOf<DailyBlock>()
            val translate = translateTasks.await()
            if (translate.isNotEmpty()) {
                blocks.add(DailyBlock(type = DailyBlockType.TRANSLATE, tasks = translate))
            }
            val vocab = vocabTasks.await()
            if (vocab.isNotEmpty()) {
                blocks.add(DailyBlock(type = DailyBlockType.VOCAB, tasks = vocab))
            }
            val verbs = verbTasks.await()
            if (verbs.isNotEmpty()) {
                blocks.add(DailyBlock(type = DailyBlockType.VERBS, tasks = verbs))
            }
            blocks
        }
    }

    /**
     * Convert a flat task list to structured blocks.
     * Groups tasks by block type in order of appearance.
     */
    fun tasksToBlocks(tasks: List<DailyTask>): List<DailyBlock> {
        if (tasks.isEmpty()) return emptyList()
        val blocks = mutableListOf<DailyBlock>()
        var currentType = tasks.first().blockType
        var currentTasks = mutableListOf<DailyTask>()
        for (task in tasks) {
            if (task.blockType != currentType) {
                blocks.add(DailyBlock(type = currentType, tasks = currentTasks.toList()))
                currentType = task.blockType
                currentTasks = mutableListOf()
            }
            currentTasks.add(task)
        }
        if (currentTasks.isNotEmpty()) {
            blocks.add(DailyBlock(type = currentType, tasks = currentTasks.toList()))
        }
        return blocks
    }

    /**
     * Block 1: Cursor-based sentence selection by lesson index.
     *
     * Uses cursor.currentLessonIndex to pick which lesson to draw from,
     * and cursor.sentenceOffset to resume within that lesson.
     * Cards are taken in order (no shuffle). Returns empty if the lesson
     * is exhausted, signalling the caller to advance the lesson index.
     */
    private fun buildSentenceBlock(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cursor: DailyCursorState
    ): List<DailyTask.TranslateSentence> {
        val lessons = lessonStore.getLessons(languageId)

        // Use cursor index to select lesson; fall back to lessonId match
        val lesson = if (cursor.currentLessonIndex in lessons.indices) {
            lessons[cursor.currentLessonIndex]
        } else {
            lessons.firstOrNull { it.id.value == lessonId }
        } ?: return emptyList()

        val cards = lesson.cards
        if (cards.isEmpty()) return emptyList()

        // Take cards starting at sentenceOffset, in order (no shuffle)
        val remaining = cards.drop(cursor.sentenceOffset)
        if (remaining.isEmpty()) return emptyList()

        val selected = remaining.take(sessionSize)

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

    /**
     * Build sentence block from a specific list of card IDs (for Repeat).
     * Looks up cards across all lessons, preserves the original ID order.
     */
    private fun buildSentenceBlockFromIds(
        lessonLevel: Int,
        packId: String,
        languageId: String,
        lessonId: String,
        cardIds: List<String>
    ): List<DailyTask.TranslateSentence> {
        if (cardIds.isEmpty()) return emptyList()

        val lessons = lessonStore.getLessons(languageId)
        val allCards = lessons.flatMap { it.cards }
        val cardMap = allCards.associateBy { it.id }

        return cardIds.mapNotNull { id ->
            cardMap[id]
        }.take(sessionSize).mapIndexed { index, card ->
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

    /**
     * Block 2: Pure SRS vocab selection from the full pack word list.
     *
     * No rank range tied to lessonLevel. Selection logic:
     * 1. Load all mastery states from WordMasteryStore.
     * 2. Get SRS-due words (nextReviewDateMs <= now or lastReviewDateMs == 0).
     * 3. Sort due words by most overdue first (now - nextReviewDateMs descending).
     * 4. If enough due words (>=10), take 10 by most overdue.
     * 5. If not enough due words, fill with new (never reviewed) words by rank.
     * 6. If still not enough, fallback to least recently reviewed.
     */
    private fun buildVocabBlock(
        packId: String,
        languageId: String
    ): List<DailyTask.VocabFlashcard> {
        val allWords = loadVocabWords(packId, languageId)
        if (allWords.isEmpty()) return emptyList()

        val allMastery = wordMasteryStore.loadAll()
        val now = System.currentTimeMillis()

        // Categorise words by SRS status
        val dueWords = mutableListOf<Pair<VocabWord, Long>>()      // word, overdueMs
        val newWords = mutableListOf<VocabWord>()                    // never reviewed
        val scheduledWords = mutableListOf<Pair<VocabWord, Long>>() // word, lastReviewDateMs (for fallback)

        for (word in allWords) {
            val mastery = allMastery[word.id]
            if (mastery == null) {
                newWords.add(word)
            } else if (mastery.nextReviewDateMs <= now || mastery.lastReviewDateMs == 0L) {
                val overdueMs = now - mastery.nextReviewDateMs
                dueWords.add(word to overdueMs)
            } else {
                scheduledWords.add(word to mastery.lastReviewDateMs)
            }
        }

        // Sort due words by most overdue first
        dueWords.sortByDescending { it.second }

        val selected = mutableListOf<VocabWord>()

        // Take due words, most overdue first
        selected.addAll(dueWords.take(sessionSize).map { it.first })

        // Fill with new words (never reviewed), sorted by rank
        if (selected.size < sessionSize) {
            val remaining = sessionSize - selected.size
            selected.addAll(newWords.sortedBy { it.rank }.take(remaining))
        }

        // Fallback: least recently reviewed
        if (selected.size < sessionSize) {
            val remaining = sessionSize - selected.size
            val alreadySelectedIds = selected.map { it.id }.toSet()
            val fallbackCandidates = scheduledWords
                .filter { it.first.id !in alreadySelectedIds }
                .sortedBy { it.second }
            selected.addAll(fallbackCandidates.take(remaining).map { it.first })
        }

        if (selected.isEmpty()) return emptyList()

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

    /**
     * Block 3: Cursor-based weak-first verb drill selection with collocation grouping.
     *
     * Uses cursor.verbOffset to skip already-practiced cards in the weak-first order.
     * Cards are sorted by weakness first (least-practiced tenses appear earliest),
     * then a sliding window of sessionSize cards is taken starting at the offset.
     *
     * Weakness score calculation: more unshown cards in a tense = weaker tense.
     * A tense with 0 cards shown has weakness 1.0 (weakest); a fully-shown tense has 0.0.
     *
     * Cards within the weakness-sorted list are further ordered by:
     *   1. Weakness descending (weak-first, primary sort)
     *   2. Verb+tense group frequency ascending (most common verb first)
     *   3. Individual card rank ascending (most frequent collocation first)
     *   4. Original index as tiebreaker for stability
     * This groups all collocations of the same verb+tense together, with
     * the most common verbs appearing first within each weakness tier.
     *
     * The cursor.verbOffset advances as cards are practiced, enabling progress
     * through the full deck in weak-first order without repetition.
     * Returns empty only if active tenses have no cards at all.
     */
    private fun buildVerbBlock(
        packId: String,
        languageId: String,
        activeTenses: List<String>,
        cursor: DailyCursorState
    ): List<DailyTask.ConjugateVerb> {
        if (activeTenses.isEmpty()) return emptyList()

        // Validate cache key matches current pack
        val expectedKey = "$packId:$languageId"
        if (cachedVerbDrillCards?.first != expectedKey) {
            Log.d("DailySessionComposer", "Cache key mismatch: expected $expectedKey, got ${cachedVerbDrillCards?.first}. Clearing stale cache.")
            cachedVerbDrillCards = null
        }

        val allCards = loadVerbDrillCards(packId, languageId)

        val filtered = allCards.filter { card ->
            card.tense != null && card.tense in activeTenses
        }

        if (filtered.isEmpty()) return emptyList()

        val progressMap = verbDrillStore.loadProgress()

        // Score ALL filtered cards by weakness: more unshown in that tense = weaker
        val scored = filtered.mapIndexed { idx, card ->
            val shownInCombo = progressMap.values
                .filter { it.tense == card.tense }
                .sumOf { it.everShownCardIds.size }
            val comboTotal = filtered.count { it.tense == card.tense }
            val weakness = if (comboTotal == 0) 0f else 1f - (shownInCombo.toFloat() / comboTotal)
            Triple(card, weakness, idx)  // idx preserves original order for stability
        }

        // Pre-compute a frequency rank per verb+tense group: the minimum rank
        // among all cards sharing the same verb+tense. This orders verb groups
        // so the most common verb (lowest rank) comes first.
        val verbGroupRank = scored.groupBy { "${it.first.verb}_${it.first.tense}" }
            .mapValues { (_, group) ->
                group.mapNotNull { it.first.rank }.minOrNull() ?: Int.MAX_VALUE
            }

        // Sort order:
        //   1. Weakness descending (weak-first, primary sort)
        //   2. Verb+tense group rank ascending (most common verb group first)
        //   3. Individual card rank ascending (most frequent collocation first)
        //   4. Original index as tiebreaker for stability
        val sorted = scored.sortedWith(
            compareByDescending<Triple<VerbDrillCard, Float, Int>> { it.second }
                .thenBy { verbGroupRank["${it.first.verb}_${it.first.tense}"] ?: Int.MAX_VALUE }
                .thenBy { it.first.rank ?: Int.MAX_VALUE }
                .thenBy { it.third }
        )

        // Apply cursor-based windowing: skip already-practiced cards, take sessionSize
        val offsetCards = sorted.drop(cursor.verbOffset)
        if (offsetCards.isEmpty()) return emptyList()

        val selected = offsetCards.take(sessionSize)

        return selected.mapIndexed { index, (card, _, _) ->
            val mode = if (index % 2 == 0) InputMode.KEYBOARD else InputMode.WORD_BANK
            DailyTask.ConjugateVerb(
                id = "verb_${card.id}",
                card = card,
                inputMode = mode
            )
        }
    }

    /**
     * Build verb block from a specific list of card IDs (for Repeat).
     * Looks up cards from verb drill files, preserves the original ID order.
     */
    private fun buildVerbBlockFromIds(
        packId: String,
        languageId: String,
        cardIds: List<String>
    ): List<DailyTask.ConjugateVerb> {
        if (cardIds.isEmpty()) return emptyList()

        val allCards = loadVerbDrillCards(packId, languageId)
        val cardMap = allCards.associateBy { it.id }

        return cardIds.mapNotNull { id ->
            cardMap[id]
        }.take(sessionSize).mapIndexed { index, card ->
            val mode = if (index % 2 == 0) InputMode.KEYBOARD else InputMode.WORD_BANK
            DailyTask.ConjugateVerb(
                id = "verb_${card.id}",
                card = card,
                inputMode = mode
            )
        }
    }

    /**
     * Invalidate cached drill data. Call when the active pack changes.
     * If both params are null, clears all caches.
     */
    fun invalidateCache(packId: String? = null, languageId: String? = null) {
        Log.d("DailySessionComposer", "Invalidating verb cache for pack: $packId, lang: $languageId")

        if (packId != null && languageId != null) {
            val key = "$packId:$languageId"
            // Clear cache if key doesn't match (not just if it matches)
            if (cachedVocabWords?.first != key) {
                cachedVocabWords = null
            }
            if (cachedVerbDrillCards?.first != key) {
                cachedVerbDrillCards = null
            }
        } else {
            cachedVocabWords = null
            cachedVerbDrillCards = null
        }
    }

    /**
     * Test-only: Inject verb drill cards directly into the cache.
     * This bypasses file loading for unit tests.
     */
    fun injectVerbDrillCardsForTest(packId: String, languageId: String, cards: List<VerbDrillCard>) {
        val key = "$packId:$languageId"
        cachedVerbDrillCards = key to cards
    }

    private fun loadVocabWords(packId: String, languageId: String): List<VocabWord> {
        val key = "$packId:$languageId"
        cachedVocabWords?.let { if (it.first == key) return it.second }

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

        // Exclude numbers from daily practice vocab block — they should only
        // appear when the user explicitly selects the Numbers filter in Vocab Drill.
        val result = words.filter { it.pos != "numbers" }.sortedBy { it.rank }
        cachedVocabWords = key to result
        return result
    }

    private fun loadVerbDrillCards(packId: String, languageId: String): List<VerbDrillCard> {
        val key = "$packId:$languageId"

        if (cachedVerbDrillCards?.first == key) {
            Log.d("DailySessionComposer", "Cache HIT for verb cards: $key")
            return cachedVerbDrillCards!!.second
        }

        Log.d("DailySessionComposer", "Cache MISS for verb cards: $key - loading from storage")

        val files = lessonStore.getVerbDrillFiles(packId, languageId)
        val cards = mutableListOf<VerbDrillCard>()

        for (file in files) {
            try {
                val (_, parsed) = file.bufferedReader().use { reader ->
                    VerbDrillCsvParser.parse(reader)
                }
                cards.addAll(parsed)
            } catch (_: Exception) {
                // Skip unreadable files
            }
        }

        cachedVerbDrillCards = key to cards
        return cards
    }
}
