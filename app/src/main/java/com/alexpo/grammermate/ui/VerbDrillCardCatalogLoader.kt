package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.data.VerbDrillStore

/** File-backed catalog snapshot built on the caller's I/O dispatcher. */
internal class VerbDrillCardCatalogLoader(
    private val lessonStore: LessonStore,
    private val progressStore: ProgressStore,
    private val badSentenceStore: BadSentenceStore,
    private val tenseInfoRepository: VerbTenseInfoRepository
) {
    data class Snapshot(
        val cards: List<VerbDrillCard>,
        val cardToPack: Map<String, String>,
        val packIds: Set<String>,
        val tenses: List<String>,
        val groups: List<String>,
        val persons: List<String>,
        val progress: Map<String, VerbDrillComboProgress>,
        val badCount: Int,
        val lang: String,
        val tenseInfo: Map<String, TenseInfo>
    )

    fun load(languageId: String?, packId: String?, store: VerbDrillStore): Snapshot {
        val lang = languageId ?: progressStore.load().languageId.value
        val files = if (packId != null) {
            lessonStore.getVerbDrillFiles(packId, lang)
        } else emptyList()
        val cards = mutableListOf<VerbDrillCard>()
        val cardToPack = mutableMapOf<String, String>()
        val packIds = mutableSetOf<String>()

        for (file in files) {
            val parseResult = file.bufferedReader().use { reader ->
                VerbDrillCsvParser.parse(reader)
            }
            val parsed = parseResult.data ?: continue
            val sourcePack = packId ?: continue
            packIds.add(sourcePack)
            for (card in parsed) cardToPack[card.id] = sourcePack
            cards.addAll(parsed)
        }

        return Snapshot(
            cards = cards,
            cardToPack = cardToPack,
            packIds = packIds,
            tenses = cards.mapNotNull { it.tense }.distinct().sorted(),
            groups = cards.mapNotNull { it.group }.distinct().sorted(),
            persons = cards.mapNotNull { it.person }.distinct()
                .sortedBy { VerbDrillCsvParser.PERSON_ORDER.indexOf(it) },
            progress = store.loadProgress(),
            badCount = packIds.sumOf { badSentenceStore.getBadSentenceCount(it) },
            lang = lang,
            tenseInfo = tenseInfoRepository.load(lang)
        )
    }
}
