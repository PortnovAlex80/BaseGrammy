package com.alexpo.grammermate.data

/**
 * One lead-in drill pair: mass-drill [verb] in [tense], which prepares the
 * student for [lessonId].
 */
data class AuxDrillPair(
    val category: String,
    val verb: String,
    val tense: String,
    val lessonId: String,
    val lessonTopic: String
)

/**
 * Static catalog of aux (lead-in) drill pairs. Source of truth for the menu
 * screen. MVP scope: only avere/essere/stare in tenses fully covered by the
 * pool (it_verb_groups_all.csv). Each pair yields ~35 pool cards
 * (7 persons × 5 collocations).
 */
object AuxDrillCatalog {

    val ALL: List<AuxDrillPair> = listOf(
        // Настоящее
        AuxDrillPair("Настоящее", "avere", "Presente", "B03", "Passato Prossimo con Avere"),
        AuxDrillPair("Настоящее", "essere", "Presente", "B04", "Passato Prossimo con Essere"),
        AuxDrillPair("Настоящее", "stare", "Presente", "B17", "Stare + gerundio"),
        // Прошедшее
        AuxDrillPair("Прошедшее", "avere", "Imperfetto", "B06", "Trapassato Prossimo"),
        AuxDrillPair("Прошедшее", "essere", "Imperfetto", "B06", "Trapassato Prossimo"),
        AuxDrillPair("Прошедшее", "stare", "Imperfetto", "C12", "Stare + gerundio nel passato"),
        // Будущее
        AuxDrillPair("Будущее", "avere", "Futuro Semplice", "B08", "Futuro Anteriore"),
        AuxDrillPair("Будущее", "essere", "Futuro Semplice", "B08", "Futuro Anteriore"),
        // Условное
        AuxDrillPair("Условное", "avere", "Condizionale Presente", "B10", "Condizionale Composto"),
        AuxDrillPair("Условное", "essere", "Condizionale Presente", "B10", "Condizionale Composto"),
        // Сослагательное
        AuxDrillPair("Сослагательное", "avere", "Congiuntivo Presente", "B25", "Congiuntivo Presente"),
        AuxDrillPair("Сослагательное", "essere", "Congiuntivo Presente", "B26", "Congiuntivo Presente: irregolari")
    )

    /** Pairs grouped by category, preserving declaration order. */
    val GROUPED: Map<String, List<AuxDrillPair>> = ALL.groupBy { it.category }

    /** Aux verbs that the pool must contain for these pairs to work. */
    val AUX_VERBS: Set<String> = ALL.map { it.verb }.toSet()
}
