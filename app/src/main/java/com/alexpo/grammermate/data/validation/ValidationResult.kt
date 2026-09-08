package com.alexpo.grammermate.data.validation

/**
 * Sealed class representing the result of data validation.
 *
 * @param T The type of data being validated
 */
sealed class ValidationResult<out T> {
    /**
     * Data passed validation successfully.
     */
    data class Valid<T>(val data: T) : ValidationResult<T>()

    /**
     * Data failed validation with errors. A safe default is provided.
     */
    data class Invalid<T>(
        val errors: List<ValidationError>,
        val safeDefault: T
    ) : ValidationResult<T>()

    /**
     * Data passed validation but has warnings that should be logged.
     */
    data class Warning<T>(
        val data: T,
        val warnings: List<ValidationWarning>
    ) : ValidationResult<T>()

    /**
     * Check if the validation result is valid (either Valid or Warning).
     */
    val isValid: Boolean
        get() = this is Valid || this is Warning

    /**
     * Get the data if valid, or the safe default if invalid.
     */
    fun getDataOrDefault(): T = when (this) {
        is Valid -> data
        is Invalid -> safeDefault
        is Warning -> data
    }

    /**
     * Get the data if valid, or null if invalid.
     */
    fun getDataOrNull(): T? = when (this) {
        is Valid -> data
        is Invalid -> null
        is Warning -> data
    }

    /**
     * Map the data if valid, or preserve the safe default if invalid.
     */
    fun <R> map(transform: (T) -> R): ValidationResult<R> = when (this) {
        is Valid -> Valid(transform(data))
        is Invalid -> Invalid(errors, transform(safeDefault))
        is Warning -> Warning(transform(data), warnings)
    }

    /**
     * FlatMap the data if valid, or preserve the safe default if invalid.
     */
    fun <R> flatMap(transform: (T) -> ValidationResult<R>): ValidationResult<R> = when (this) {
        is Valid -> transform(data)
        is Invalid -> this as ValidationResult<R> // Unsafe cast but preserved for API
        is Warning -> {
            val result = transform(data)
            when (result) {
                is Valid -> Warning(result.data, warnings)
                is Invalid -> result
                is Warning -> Warning(result.data, warnings + result.warnings)
            }
        }
    }

    companion object {
        /**
         * Create a Valid result.
         */
        fun <T> valid(data: T): ValidationResult<T> = Valid(data)

        /**
         * Create an Invalid result with a single error.
         */
        fun <T> invalid(error: ValidationError, safeDefault: T): ValidationResult<T> =
            Invalid(listOf(error), safeDefault)

        /**
         * Create an Invalid result with multiple errors.
         */
        fun <T> invalid(errors: List<ValidationError>, safeDefault: T): ValidationResult<T> =
            Invalid(errors, safeDefault)

        /**
         * Create a Warning result with a single warning.
         */
        fun <T> warning(data: T, warning: ValidationWarning): ValidationResult<T> =
            Warning(data, listOf(warning))

        /**
         * Create a Warning result with multiple warnings.
         */
        fun <T> warning(data: T, warnings: List<ValidationWarning>): ValidationResult<T> =
            Warning(data, warnings)
    }
}

/**
 * Represents a validation error that makes data invalid.
 */
data class ValidationError(
    val field: String,
    val message: String,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
    val userMessage: String? = null
)

/**
 * Represents a validation warning that doesn't make data invalid but should be logged.
 */
data class ValidationWarning(
    val field: String,
    val message: String
)

/**
 * Severity level for validation errors.
 */
enum class ErrorSeverity {
    /**
     * Critical error that makes data unusable.
     */
    ERROR,

    /**
     * Error that can be recovered with safe defaults.
     */
    RECOVERABLE
}