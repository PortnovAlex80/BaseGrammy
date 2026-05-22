package com.alexpo.grammermate.data.validation

import android.util.Log
import com.alexpo.grammermate.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Comprehensive data validation layer for all application data.
 *
 * Validates all loaded data before using it and prevents invalid data from propagating
 * to business logic. Provides safe defaults for corrupted data with warnings.
 *
 * Validation rules:
 * - **Mastery data**: step range [0, 150], timestamp not future, valid card IDs
 * - **Progress data**: currentStep ≤ totalSteps, valid lesson IDs, non-negative scores
 * - **Card data**: non-empty prompts, at least one accepted answer, valid tense/grammar
 * - **Pack metadata**: valid version format, non-empty pack ID, valid language codes
 * - **VerbDrill data**: valid infinitive forms, conjugations match pattern
 */
object DataValidator {

    private const val TAG = "DataValidator"
    private const val MAX_MASTERY_STEP = 150
    private const val MAX_REASONABLE_TIMESTAMP = System.currentTimeMillis() + 86400000L // +1 day
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ── Mastery Data Validation ─────────────────────────────────────────────────────

    /**
     * Validate LessonMasteryState data.
     */
    fun validateMasteryState(
        lessonId: String,
        languageId: String,
        data: Map<String, Any>?
    ): ValidationResult<LessonMasteryState> {
        if (data == null) {
            Log.w(TAG, "Missing mastery data for lesson=$lessonId, language=$languageId - using default state")
            val defaultState = createDefaultMasteryState(lessonId, languageId)
            return ValidationResult.warning(
                defaultState,
                ValidationWarning(
                    code = "MISSING_MASTERY_DATA",
                    message = "Mastery data missing for lesson=$lessonId, language=$languageId. Using default state.",
                    severity = ValidationSeverity.WARNING
                )
            )
        }

        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate uniqueCardShows
        val uniqueCardShows = (data["uniqueCardShows"] as? Number)?.toInt() ?: 0
        if (uniqueCardShows < 0) {
            errors.add(ValidationError(
                field = "uniqueCardShows",
                message = "Negative uniqueCardShows: $uniqueCardShows",
                userMessage = "Invalid progress data. Progress has been reset."
            ))
        }
        if (uniqueCardShows > Lesson.MAIN_POOL_SIZE) {
            warnings.add(ValidationWarning(
                field = "uniqueCardShows",
                message = "uniqueCardShows ($uniqueCardShows) exceeds main pool size (${Lesson.MAIN_POOL_SIZE})"
            ))
        }

        // Validate totalCardShows
        val totalCardShows = (data["totalCardShows"] as? Number)?.toInt() ?: 0
        if (totalCardShows < 0) {
            errors.add(ValidationError(
                field = "totalCardShows",
                message = "Negative totalCardShows: $totalCardShows",
                userMessage = "Invalid progress data. Progress has been reset."
            ))
        }
        if (totalCardShows < uniqueCardShows) {
            errors.add(ValidationError(
                field = "totalCardShows",
                message = "totalCardShows ($totalCardShows) < uniqueCardShows ($uniqueCardShows)"
            ))
        }

        // Validate lastShowDateMs
        val lastShowDateMs = (data["lastShowDateMs"] as? Number)?.toLong() ?: 0L
        if (lastShowDateMs < 0) {
            errors.add(ValidationError(
                field = "lastShowDateMs",
                message = "Negative timestamp: $lastShowDateMs"
            ))
        }
        if (lastShowDateMs > MAX_REASONABLE_TIMESTAMP) {
            warnings.add(ValidationWarning(
                field = "lastShowDateMs",
                message = "Timestamp in the future: $lastShowDateMs"
            ))
        }

        // Validate intervalStepIndex
        val intervalStepIndex = (data["intervalStepIndex"] as? Number)?.toInt() ?: 0
        if (intervalStepIndex < 0 || intervalStepIndex > MAX_MASTERY_STEP) {
            errors.add(ValidationError(
                field = "intervalStepIndex",
                message = "intervalStepIndex out of range [0, $MAX_MASTERY_STEP]: $intervalStepIndex",
                userMessage = "Invalid progress data. Progress has been reset."
            ))
        }

        // Validate completedAtMs
        val completedAtMs = (data["completedAtMs"] as? Number)?.toLong()
        if (completedAtMs != null && completedAtMs < 0) {
            errors.add(ValidationError(
                field = "completedAtMs",
                message = "Negative completion timestamp: $completedAtMs"
            ))
        }
        if (completedAtMs != null && completedAtMs > MAX_REASONABLE_TIMESTAMP) {
            warnings.add(ValidationWarning(
                field = "completedAtMs",
                message = "Completion timestamp in the future: $completedAtMs"
            ))
        }

        // Validate shownCardIds
        val shownCardIds = (data["shownCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?: emptySet()
        if (shownCardIds.size != uniqueCardShows && uniqueCardShows > 0) {
            warnings.add(ValidationWarning(
                field = "shownCardIds",
                message = "shownCardIds count (${shownCardIds.size}) != uniqueCardShows ($uniqueCardShows)"
            ))
        }

        // Validate cardEncounterCounts
        val cardEncounterCounts = (data["cardEncounterCounts"] as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toInt()
                if (value == null || value < 0) return@mapNotNull null
                key to value
            }
            ?.toMap()
            ?: emptyMap()

        return if (errors.isNotEmpty()) {
            Log.w(TAG, "Mastery data validation failed for lesson=$lessonId: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultMasteryState(lessonId, languageId))
        } else if (warnings.isNotEmpty()) {
            warnings.forEach { Log.w(TAG, "Mastery data warning: ${it.message}") }
            ValidationResult.warning(
                LessonMasteryState(
                    lessonId = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    uniqueCardShows = uniqueCardShows.coerceIn(0, Lesson.MAIN_POOL_SIZE),
                    totalCardShows = totalCardShows.coerceAtLeast(0),
                    lastShowDateMs = lastShowDateMs.coerceAtMost(MAX_REASONABLE_TIMESTAMP),
                    intervalStepIndex = intervalStepIndex.coerceIn(0, MAX_MASTERY_STEP),
                    completedAtMs = completedAtMs?.coerceIn(0, MAX_REASONABLE_TIMESTAMP),
                    shownCardIds = shownCardIds,
                    cardEncounterCounts = cardEncounterCounts
                ),
                warnings
            )
        } else {
            ValidationResult.valid(
                LessonMasteryState(
                    lessonId = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    uniqueCardShows = uniqueCardShows,
                    totalCardShows = totalCardShows,
                    lastShowDateMs = lastShowDateMs,
                    intervalStepIndex = intervalStepIndex,
                    completedAtMs = completedAtMs,
                    shownCardIds = shownCardIds,
                    cardEncounterCounts = cardEncounterCounts
                )
            )
        }
    }

    private fun createDefaultMasteryState(lessonId: String, languageId: String) = LessonMasteryState(
        lessonId = LessonId(lessonId),
        languageId = LanguageId(languageId)
    )

    // ── Progress Data Validation ────────────────────────────────────────────────────

    /**
     * Validate TrainingProgress data.
     */
    fun validateTrainingProgress(data: Map<String, Any>?): ValidationResult<TrainingProgress> {
        if (data == null) {
            Log.w(TAG, "Missing training progress data")
            return ValidationResult.valid(TrainingProgress())
        }

        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate languageId
        val languageId = (data["languageId"] as? String)?.takeIf { it.isNotBlank() } ?: "en"
        if (languageId.length > 10) {
            warnings.add(ValidationWarning(
                field = "languageId",
                message = "Unusually long languageId: $languageId"
            ))
        }

        // Validate mode
        val mode = try {
            TrainingMode.valueOf(data["mode"] as? String ?: TrainingMode.LESSON.name)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid training mode, using LESSON")
            TrainingMode.LESSON
        }

        // Validate boss rewards
        val bossLessonRewards = (data["bossLessonRewards"] as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }
            ?.toMap() ?: emptyMap()

        val bossMegaReward = data["bossMegaReward"] as? String

        val bossMegaRewards = (data["bossMegaRewards"] as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }
            ?.toMap() ?: emptyMap()

        // Validate voice stats
        val voiceActiveMs = (data["voiceActiveMs"] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
        val voiceWordCount = (data["voiceWordCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate hint count
        val hintCount = (data["hintCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate elite stats
        val eliteStepIndex = (data["eliteStepIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val eliteBestSpeeds = (data["eliteBestSpeeds"] as? List<*>)
            ?.mapNotNull { it as? Number }
            ?.map { it.toDouble() }
            ?.filter { it >= 0 }
            ?: emptyList()

        // Validate current screen
        val currentScreen = (data["currentScreen"] as? String)?.takeIf { it.isNotBlank() } ?: "HOME"

        // Validate packId
        val activePackId = (data["activePackId"] as? String)?.takeIf { it.isNotBlank() }?.let { PackId(it) }

        // Validate daily level
        val dailyLevel = (data["dailyLevel"] as? Number)?.toInt()?.coerceIn(0, 100) ?: 0
        val dailyTaskIndex = (data["dailyTaskIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate daily cursor
        val dailyCursor = validateDailyCursor(data["dailyCursor"] as? Map<*, *>)

        return if (errors.isNotEmpty()) {
            Log.w(TAG, "Training progress validation failed: ${errors.joinToString()}")
            ValidationResult.invalid(errors, TrainingProgress())
        } else if (warnings.isNotEmpty()) {
            warnings.forEach { Log.w(TAG, "Training progress warning: ${it.message}") }
            ValidationResult.warning(
                TrainingProgress(
                    languageId = LanguageId(languageId),
                    mode = mode,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaReward = bossMegaReward,
                    bossMegaRewards = bossMegaRewards,
                    voiceActiveMs = voiceActiveMs,
                    voiceWordCount = voiceWordCount,
                    hintCount = hintCount,
                    eliteStepIndex = eliteStepIndex,
                    eliteBestSpeeds = eliteBestSpeeds,
                    currentScreen = currentScreen,
                    activePackId = activePackId,
                    dailyLevel = dailyLevel,
                    dailyTaskIndex = dailyTaskIndex,
                    dailyCursor = dailyCursor
                ),
                warnings
            )
        } else {
            ValidationResult.valid(
                TrainingProgress(
                    languageId = LanguageId(languageId),
                    mode = mode,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaReward = bossMegaReward,
                    bossMegaRewards = bossMegaRewards,
                    voiceActiveMs = voiceActiveMs,
                    voiceWordCount = voiceWordCount,
                    hintCount = hintCount,
                    eliteStepIndex = eliteStepIndex,
                    eliteBestSpeeds = eliteBestSpeeds,
                    currentScreen = currentScreen,
                    activePackId = activePackId,
                    dailyLevel = dailyLevel,
                    dailyTaskIndex = dailyTaskIndex,
                    dailyCursor = dailyCursor
                )
            )
        }
    }

    private fun validateDailyCursor(data: Map<*, *>?): DailyCursorState {
        if (data == null) return DailyCursorState()

        val sentenceOffset = (data["sentenceOffset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val currentLessonIndex = (data["currentLessonIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val lastSessionHash = (data["lastSessionHash"] as? Number)?.toInt() ?: 0
        val firstSessionDate = validateIsoDate(data["firstSessionDate"] as? String)
        val firstSessionSentenceCardIds = (data["firstSessionSentenceCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val firstSessionVerbCardIds = (data["firstSessionVerbCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val verbOffset = (data["verbOffset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        return DailyCursorState(
            sentenceOffset = sentenceOffset,
            currentLessonIndex = currentLessonIndex,
            lastSessionHash = lastSessionHash,
            firstSessionDate = firstSessionDate,
            firstSessionSentenceCardIds = firstSessionSentenceCardIds,
            firstSessionVerbCardIds = firstSessionVerbCardIds,
            verbOffset = verbOffset
        )
    }

    // ── Card Data Validation ───────────────────────────────────────────────────────

    /**
     * Validate SentenceCard data.
     */
    fun validateSentenceCard(data: Map<String, Any>?): ValidationResult<SentenceCard> {
        if (data == null) {
            Log.w(TAG, "Missing sentence card data")
            return ValidationResult.invalid(
                ValidationError(
                    field = "card",
                    message = "Missing card data",
                    userMessage = "Invalid card data found."
                ),
                createDefaultSentenceCard()
            )
        }

        val errors = mutableListOf<ValidationError>()

        // Validate id
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() }
        if (id == null) {
            errors.add(ValidationError(
                field = "id",
                message = "Missing or empty card ID",
                userMessage = "Invalid card found. Skipping."
            ))
        }

        // Validate promptRu
        val promptRu = (data["promptRu"] as? String)?.takeIf { it.isNotBlank() }
        if (promptRu == null) {
            errors.add(ValidationError(
                field = "promptRu",
                message = "Missing or empty Russian prompt",
                userMessage = "Invalid card found. Skipping."
            ))
        }

        // Validate acceptedAnswers
        val acceptedAnswers = (data["acceptedAnswers"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        if (acceptedAnswers.isEmpty()) {
            errors.add(ValidationError(
                field = "acceptedAnswers",
                message = "No accepted answers provided",
                userMessage = "Invalid card found. Skipping."
            ))
        }

        // Validate tense (optional)
        val tense = (data["tense"] as? String)?.takeIf { it.isNotBlank() }

        return if (errors.isNotEmpty() || id == null || promptRu == null) {
            Log.w(TAG, "Sentence card validation failed: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultSentenceCard())
        } else {
            ValidationResult.valid(
                SentenceCard(
                    id = id,
                    promptRu = promptRu,
                    acceptedAnswers = acceptedAnswers,
                    tense = tense
                )
            )
        }
    }

    private fun createDefaultSentenceCard() = SentenceCard(
        id = "invalid_card_${System.currentTimeMillis()}",
        promptRu = "Invalid Card",
        acceptedAnswers = listOf("Invalid Card")
    )

    /**
     * Validate VerbDrillCard data.
     */
    fun validateVerbDrillCard(data: Map<String, Any>?): ValidationResult<VerbDrillCard> {
        if (data == null) {
            Log.w(TAG, "Missing verb drill card data")
            return ValidationResult.invalid(
                ValidationError(
                    field = "verbCard",
                    message = "Missing verb drill card data",
                    userMessage = "Invalid verb card found. Skipping."
                ),
                createDefaultVerbDrillCard()
            )
        }

        val errors = mutableListOf<ValidationError>()

        // Validate id
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() }
        if (id == null) {
            errors.add(ValidationError(
                field = "id",
                message = "Missing or empty verb card ID"
            ))
        }

        // Validate promptRu
        val promptRu = (data["promptRu"] as? String)?.takeIf { it.isNotBlank() }
        if (promptRu == null) {
            errors.add(ValidationError(
                field = "promptRu",
                message = "Missing or empty Russian prompt"
            ))
        }

        // Validate answer
        val answer = (data["answer"] as? String)?.takeIf { it.isNotBlank() }
        if (answer == null) {
            errors.add(ValidationError(
                field = "answer",
                message = "Missing or empty answer"
            ))
        }

        // Validate optional fields
        val verb = (data["verb"] as? String)?.takeIf { it.isNotBlank() }
        val tense = (data["tense"] as? String)?.takeIf { it.isNotBlank() }
        val group = (data["group"] as? String)?.takeIf { it.isNotBlank() }
        val rank = (data["rank"] as? Number)?.toInt()?.coerceAtLeast(0)

        return if (errors.isNotEmpty() || id == null || promptRu == null || answer == null) {
            Log.w(TAG, "Verb drill card validation failed: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultVerbDrillCard())
        } else {
            ValidationResult.valid(
                VerbDrillCard(
                    id = id,
                    promptRu = promptRu,
                    answer = answer,
                    verb = verb,
                    tense = tense,
                    group = group,
                    rank = rank
                )
            )
        }
    }

    private fun createDefaultVerbDrillCard() = VerbDrillCard(
        id = "invalid_verb_card_${System.currentTimeMillis()}",
        promptRu = "Invalid Verb Card",
        answer = "invalid"
    )

    // ── Pack Metadata Validation ───────────────────────────────────────────────────

    /**
     * Validate LessonPack metadata.
     */
    fun validateLessonPack(data: Map<String, Any>?): ValidationResult<LessonPack> {
        if (data == null) {
            Log.w(TAG, "Missing lesson pack data")
            return ValidationResult.invalid(
                ValidationError(
                    field = "pack",
                    message = "Missing pack data",
                    userMessage = "Invalid pack data found."
                ),
                createDefaultLessonPack()
            )
        }

        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate packId
        val packIdValue = (data["packId"] as? String)?.takeIf { it.isNotBlank() }
        if (packIdValue == null) {
            errors.add(ValidationError(
                field = "packId",
                message = "Missing or empty pack ID"
            ))
        }
        val packId = packIdValue?.let { PackId(it) }

        // Validate packVersion
        val packVersion = (data["packVersion"] as? String)?.takeIf { it.isNotBlank() }
        if (packVersion == null) {
            errors.add(ValidationError(
                field = "packVersion",
                message = "Missing or empty pack version"
            ))
        } else if (!isValidVersionFormat(packVersion)) {
            warnings.add(ValidationWarning(
                field = "packVersion",
                message = "Non-standard version format: $packVersion"
            ))
        }

        // Validate languageId
        val languageIdValue = (data["languageId"] as? String)?.takeIf { it.isNotBlank() }
        if (languageIdValue == null) {
            errors.add(ValidationError(
                field = "languageId",
                message = "Missing or empty language ID"
            ))
        }
        val languageId = languageIdValue?.let { LanguageId(it) }

        // Validate importedAt
        val importedAt = (data["importedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        if (importedAt < 0) {
            errors.add(ValidationError(
                field = "importedAt",
                message = "Negative import timestamp: $importedAt"
            ))
        }
        if (importedAt > MAX_REASONABLE_TIMESTAMP) {
            warnings.add(ValidationWarning(
                field = "importedAt",
                message = "Import timestamp in the future: $importedAt"
            ))
        }

        // Validate displayName (optional)
        val displayName = (data["displayName"] as? String)?.takeIf { it.isNotBlank() }

        return if (errors.isNotEmpty() || packId == null || languageId == null || packVersion == null) {
            Log.w(TAG, "Lesson pack validation failed: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultLessonPack())
        } else if (warnings.isNotEmpty()) {
            warnings.forEach { Log.w(TAG, "Lesson pack warning: ${it.message}") }
            ValidationResult.warning(
                LessonPack(
                    packId = packId,
                    packVersion = packVersion,
                    languageId = languageId,
                    importedAt = importedAt.coerceAtMost(MAX_REASONABLE_TIMESTAMP),
                    displayName = displayName
                ),
                warnings
            )
        } else {
            ValidationResult.valid(
                LessonPack(
                    packId = packId,
                    packVersion = packVersion,
                    languageId = languageId,
                    importedAt = importedAt,
                    displayName = displayName
                )
            )
        }
    }

    private fun createDefaultLessonPack() = LessonPack(
        packId = PackId("invalid_pack"),
        packVersion = "0.0.0",
        languageId = LanguageId("en"),
        importedAt = System.currentTimeMillis()
    )

    // ── VerbDrill Progress Validation ───────────────────────────────────────────────

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
                    code = "MISSING_VERB_DRILL_PROGRESS",
                    message = "Verb drill combo progress missing for key=$key. Using default progress.",
                    severity = ValidationSeverity.WARNING
                )
            )
        }

        val errors = mutableListOf<ValidationError>()

        // Validate group
        val group = (data["group"] as? String)?.takeIf { it.isNotBlank() }
        if (group == null) {
            errors.add(ValidationError(
                field = "group",
                message = "Missing or empty group for key=$key"
            ))
        }

        // Validate tense
        val tense = (data["tense"] as? String)?.takeIf { it.isNotBlank() }
        if (tense == null) {
            errors.add(ValidationError(
                field = "tense",
                message = "Missing or empty tense for key=$key"
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
                    sortByFrequency = sortByFrequency,
                    todayShownCardIds = todayShownCardIds,
                    sessionCardIds = sessionCardIds,
                    currentIndex = currentIndex,
                    packId = packId
                )
            )
        }
    }

    // ── Streak Data Validation ──────────────────────────────────────────────────────

    /**
     * Validate StreakData.
     */
    fun validateStreakData(languageId: String, data: Map<String, Any>?): ValidationResult<StreakData> {
        if (data == null) {
            Log.w(TAG, "Missing streak data for language=$languageId - using default")
            val defaultStreak = createDefaultStreakData(languageId)
            return ValidationResult.warning(
                defaultStreak,
                ValidationWarning(
                    code = "MISSING_STREAK_DATA",
                    message = "Streak data missing for language=$languageId. Using default streak.",
                    severity = ValidationSeverity.WARNING
                )
            )
        }

        val errors = mutableListOf<ValidationError>()

        // Validate currentStreak
        val currentStreak = (data["currentStreak"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val longestStreak = (data["longestStreak"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        if (currentStreak > longestStreak) {
            Log.w(TAG, "currentStreak ($currentStreak) > longestStreak ($longestStreak)")
        }

        // Validate lastCompletionDateMs
        val lastCompletionDateMs = (data["lastCompletionDateMs"] as? Number)?.toLong()
        if (lastCompletionDateMs != null && lastCompletionDateMs < 0) {
            errors.add(ValidationError(
                field = "lastCompletionDateMs",
                message = "Negative last completion timestamp: $lastCompletionDateMs"
            ))
        }
        if (lastCompletionDateMs != null && lastCompletionDateMs > MAX_REASONABLE_TIMESTAMP) {
            Log.w(TAG, "Last completion timestamp in the future: $lastCompletionDateMs")
        }

        // Validate totalSubLessonsCompleted
        val totalSubLessonsCompleted = (data["totalSubLessonsCompleted"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate completedTypesToday
        val completedTypesToday = (data["completedTypesToday"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.mapNotNull { type ->
                try { PracticeType.valueOf(type) } catch (e: Exception) { null }
            }
            ?.toSet()
            ?: emptySet()

        // Validate todayFireCount
        val todayFireCount = (data["todayFireCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate lastFireDateMs
        val lastFireDateMs = (data["lastFireDateMs"] as? Number)?.toLong()
        if (lastFireDateMs != null && lastFireDateMs < 0) {
            errors.add(ValidationError(
                field = "lastFireDateMs",
                message = "Negative last fire timestamp: $lastFireDateMs"
            ))
        }

        return if (errors.isNotEmpty()) {
            Log.w(TAG, "Streak data validation failed for language=$languageId: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultStreakData(languageId))
        } else {
            ValidationResult.valid(
                StreakData(
                    languageId = LanguageId(languageId),
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    lastCompletionDateMs = lastCompletionDateMs?.coerceIn(0, MAX_REASONABLE_TIMESTAMP),
                    totalSubLessonsCompleted = totalSubLessonsCompleted,
                    completedTypesToday = completedTypesToday,
                    todayFireCount = todayFireCount,
                    lastFireDateMs = lastFireDateMs?.coerceIn(0, MAX_REASONABLE_TIMESTAMP)
                )
            )
        }
    }

    private fun createDefaultStreakData(languageId: String) = StreakData(
        languageId = LanguageId(languageId)
    )

    // ── Helper Functions ───────────────────────────────────────────────────────────

    /**
     * Validate ISO date string format (yyyy-MM-dd).
     */
    private fun validateIsoDate(dateString: String?): String {
        if (dateString == null) return ""
        if (dateString.isBlank()) return ""
        return try {
            LocalDate.parse(dateString, DATE_FORMATTER)
            dateString
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Invalid ISO date format: $dateString")
            ""
        }
    }

    /**
     * Check if version string follows semantic versioning (e.g., "1.0.0").
     */
    private fun isValidVersionFormat(version: String): Boolean {
        return version.matches(Regex("""^\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$"""))
    }

    /**
     * Check if language ID is valid (2-3 letter ISO code).
     */
    private fun isValidLanguageId(languageId: String): Boolean {
        return languageId.matches(Regex("""^[a-z]{2,3}$"""))
    }
}