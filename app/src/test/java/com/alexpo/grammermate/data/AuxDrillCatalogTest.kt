package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxDrillCatalogTest {

    @Test
    fun allPairs_useOnlyCoreAuxVerbs() {
        val verbs = AuxDrillCatalog.ALL.map { it.verb }.toSet()
        assertEquals(setOf("avere", "essere", "stare"), verbs)
    }

    @Test
    fun allPairs_targetValidPoolTenses() {
        val tenses = AuxDrillCatalog.ALL.map { it.tense }.toSet()
        val expected = setOf(
            "Presente", "Imperfetto", "Futuro Semplice",
            "Condizionale Presente", "Congiuntivo Presente"
        )
        assertEquals(expected, tenses)
    }

    @Test
    fun allPairs_haveLessonTarget() {
        AuxDrillCatalog.ALL.forEach { pair ->
            assertTrue("pair $pair has blank lesson", pair.lessonId.isNotBlank())
            assertTrue("pair $pair has blank topic", pair.lessonTopic.isNotBlank())
        }
    }

    @Test
    fun catalog_hasTwelvePairs() {
        assertEquals(12, AuxDrillCatalog.ALL.size)
    }

    @Test
    fun pairsGroupByCategory() {
        val categories = AuxDrillCatalog.ALL.map { it.category }.toSet()
        assertTrue("Настоящее" in categories)
        assertTrue("Прошедшее" in categories)
    }

    @Test
    fun averePresente_leadsToB03() {
        val pair = AuxDrillCatalog.ALL.first {
            it.verb == "avere" && it.tense == "Presente"
        }
        assertEquals("B03", pair.lessonId)
    }
}
