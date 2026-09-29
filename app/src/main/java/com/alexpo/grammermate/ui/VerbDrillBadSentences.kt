package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.VerbDrillCard

/** Owns bad-sentence operations for the currently loaded verb-drill catalog. */
internal class VerbDrillBadSentences(private val store: BadSentenceStore) {
    private var packByCardId: Map<String, String> = emptyMap()
    private var activePackIds: Set<String> = emptySet()

    fun useCatalog(cardToPack: Map<String, String>, packIds: Set<String>) {
        packByCardId = cardToPack
        activePackIds = packIds
    }

    fun count(): Int = activePackIds.sumOf { store.getBadSentenceCount(it) }

    fun flag(card: VerbDrillCard, languageId: String): Boolean {
        val packId = packByCardId[card.id] ?: return false
        store.addBadSentence(
            packId = packId,
            cardId = card.id,
            languageId = languageId,
            sentence = card.promptRu,
            translation = card.answer,
            mode = "verb_drill"
        )
        return true
    }

    fun unflag(card: VerbDrillCard): Boolean {
        val packId = packByCardId[card.id] ?: return false
        store.removeBadSentence(packId, card.id)
        return true
    }

    fun isBad(card: VerbDrillCard): Boolean {
        val packId = packByCardId[card.id] ?: return false
        return store.isBadSentence(packId, card.id)
    }

    fun export(): String? {
        if (activePackIds.isEmpty()) return null
        for (packId in activePackIds) {
            if (store.getBadSentences(packId).isNotEmpty()) {
                return store.exportUnified().absolutePath
            }
        }
        return null
    }
}
