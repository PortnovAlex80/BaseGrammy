package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.feature.progress.PackProgressCalculator

/** Display data for an installed lesson pack. */
data class PackTileUi(
    val packId: String,
    val displayName: String,
    val languageId: String,
    val flowerEmoji: String,
    val flowerState: FlowerState,
    val depth: Float,
    val healthPercent: Float,
    val scaleMultiplier: Float,
    val completedLessons: Int,
    val totalLessons: Int,
    val totalCards: Int,
    val isActive: Boolean
)

/** Reads pack progress and builds the Home tile projection; the ViewModel publishes its snapshot. */
internal class PackTileSource(
    private val lessonStore: LessonStore,
    private val masteryStore: MasteryStore
) {
    fun getTiles(nav: NavigationState): List<PackTileUi> {
        val selectedLangId = nav.selectedLanguageId?.value
        val packs = if (selectedLangId != null) {
            nav.installedPacks.filter { it.languageId.value == selectedLangId }
        } else {
            nav.installedPacks
        }
        val activePackId = nav.activePackId?.value

        return packs.map { pack ->
            val packIdStr = pack.packId.value
            val langIdStr = pack.languageId.value
            val lessons = lessonStore.getLessons(packIdStr, langIdStr)
            val lessonIds = lessons.map { it.id.value }

            // One snapshot per pack instead of a lock-taking getForPack call per lesson.
            val packMastery = masteryStore.loadAll()["pack:$packIdStr"]
            val masteryMap = mutableMapOf<String, LessonMasteryState>()
            var totalCards = 0
            for (lesson in lessons) {
                val lessonIdStr = lesson.id.value
                val mastery = packMastery?.get(lessonIdStr)
                if (mastery != null) {
                    masteryMap[lessonIdStr] = mastery
                    totalCards += mastery.uniqueCardShows
                }
            }

            val packFlower = PackProgressCalculator.calculatePackFlower(lessonIds, masteryMap)
            val hasFlower = totalCards > 0
            PackTileUi(
                packId = packIdStr,
                displayName = pack.displayName ?: packIdStr,
                languageId = langIdStr,
                flowerEmoji = if (hasFlower) FlowerCalculator.getEmoji(packFlower.flowerState) else "",
                flowerState = packFlower.flowerState,
                depth = packFlower.depth,
                healthPercent = packFlower.healthPercent,
                scaleMultiplier = packFlower.scaleMultiplier,
                completedLessons = packFlower.completedLessons,
                totalLessons = packFlower.totalLessons,
                totalCards = totalCards,
                isActive = packIdStr == activePackId
            )
        }.sortedWith(compareBy({ it.languageId }, { it.displayName }))
    }
}
