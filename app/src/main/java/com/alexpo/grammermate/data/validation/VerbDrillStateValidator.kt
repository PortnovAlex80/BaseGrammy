package com.alexpo.grammermate.data.validation

import android.util.Log
import com.alexpo.grammermate.data.*
import java.time.LocalDate

/** Validates persisted verbdrillstate data at its domain boundary. */
object VerbDrillStateValidator {
    private const val TAG = "DataValidator"

    /**
     * Validate VerbDrillComboProgress data.
     */
    fun validateVerbDrillComboProgress(
        key: String,
        data: Map<String, Any>?
    ): ValidationResult<VerbDrillComboProgress> {
        if (data == null) {
            Log.w(TAG, "Missing verb drill combo progress for key=$key - using default")
            val defaultProgress = createDefaultVerbDrillComboProgress()
            return ValidationResult.warning(
                defaultProgress,
                ValidationWarning(
                    field = "verbDrillComboProgress",
                    message = "Verb drill combo progress missing for key=$key. Using default progress."
                )
            )
        }

        val errors = mutableListOf<ValidationError>()

        // Empty strings represent an unfiltered selection in persisted combo
        // progress. A missing field or whitespace-only value is corrupt.
        val group = data["group"] as? String
        if (group == null || (group.isNotEmpty() && group.isBlank())) {
            errors.add(ValidationError(
                field = "group",
                message = "Missing or invalid group for key=$key"
            ))
        }

        val tense = data["tense"] as? String
        if (tense == null || (tense.isNotEmpty() && tense.isBlank())) {
            errors.add(ValidationError(
                field = "tense",
                message = "Missing or invalid tense for key=$key"
            ))
        }

        // Validate totalCards
        val totalCards = (data["totalCards"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate everShownCardIds
        val everShownCardIds = (data["everShownCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?: emptySet()
        if (everShownCardIds.size > totalCards && totalCards > 0) {
            Log.w(TAG, "everShownCardIds (${everShownCardIds.size}) > totalCards ($totalCards) for key=$key")
        }

        // Validate lastDate
        val lastDate = validateIsoDate(data["lastDate"] as? String)

        // Validate todayShownCardIds
        val today = LocalDate.now().toString()
        val todayShownCardIds = if (lastDate != today) {
            emptySet()
        } else {
            (data["todayShownCardIds"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.toSet()
                ?: emptySet()
        }

        return if (errors.isNotEmpty() || group == null || tense == null) {
            Log.w(TAG, "Verb drill combo progress validation failed for key=$key: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultVerbDrillComboProgress())
        } else {
            ValidationResult.valid(
                VerbDrillComboProgress(
                    group = group,
                    tense = tense,
                    totalCards = totalCards,
                    everShownCardIds = everShownCardIds,
                    todayShownCardIds = todayShownCardIds,
                    lastDate = if (lastDate != today) today else lastDate
                )
            )
        }
    }

    private fun createDefaultVerbDrillComboProgress() = VerbDrillComboProgress(
        group = "unknown",
        tense = "unknown",
        totalCards = 0
    )

    /**
     * Validate VerbDrillLastSessionState data.
     */
    fun validateVerbDrillLastSession(data: Map<String, Any>?): ValidationResult<VerbDrillLastSessionState?> {
        if (data == null) {
            Log.d(TAG, "No last session data found")
            return ValidationResult.valid(null)
        }

        val warnings = mutableListOf<ValidationWarning>()

        // Validate selectedTense (optional)
        val selectedTense = (data["selectedTense"] as? String)?.takeIf { it.isNotBlank() }

        // Validate selectedGroup (optional)
        val selectedGroup = (data["selectedGroup"] as? String)?.takeIf { it.isNotBlank() }

        // Validate selectedPerson (optional)
        val selectedPerson = (data["selectedPerson"] as? String)?.takeIf { it.isNotBlank() }

        // Validate sortByFrequency
        val sortByFrequency = data["sortByFrequency"] as? Boolean ?: false

        // Validate todayShownCardIds
        val todayShownCardIds = (data["todayShownCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?: emptySet()

        // Validate sessionCardIds
        val sessionCardIds = (data["sessionCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
        if (sessionCardIds.isNotEmpty() && sessionCardIds.size != sessionCardIds.toSet().size) {
            warnings.add(ValidationWarning(
                field = "sessionCardIds",
                message = "Duplicate card IDs in session list"
            ))
        }

        // Validate currentIndex
        val currentIndex = (data["currentIndex"] as? Number)?.toInt()?.coerceIn(0, sessionCardIds.size) ?: 0

        // Validate packId (optional)
        val packId = (data["packId"] as? String)?.takeIf { it.isNotBlank() }

        return if (warnings.isNotEmpty()) {
            warnings.forEach { Log.w(TAG, "Last session warning: ${it.message}") }
            ValidationResult.warning(
                VerbDrillLastSessionState(
                    selectedTense = selectedTense,
                    selectedGroup = selectedGroup,
                    selectedPerson = selectedPerson,
                    sortByFrequency = sortByFrequency,
                    todayShownCardIds = todayShownCardIds,
                    sessionCardIds = sessionCardIds.distinct(),
                    currentIndex = currentIndex,
                    packId = packId
                ),
                warnings
            )
        } else {
            ValidationResult.valid(
                VerbDrillLastSessionState(
                    selectedTense = selectedTense,
                    selectedGroup = selectedGroup,
                    selectedPerson = selectedPerson,
                    sortByFrequency = sortByFrequency,
                    todayShownCardIds = todayShownCardIds,
                    sessionCardIds = sessionCardIds,
                    currentIndex = currentIndex,
                    packId = packId
                )
            )
        }
    }
}
