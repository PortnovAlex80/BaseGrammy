package com.alexpo.grammermate.data

/**
 * Generic result type for parsing operations that can succeed, fail, or partially succeed.
 *
 * @param T The type of data returned on success
 * @param E The type of error collected during parsing
 */
data class ParseResult<T, E>(
    val data: T? = null,
    val errors: List<E> = emptyList(),
) {
    /**
     * True if parsing completed without any errors.
     */
    val isSuccess: Boolean
        get() = errors.isEmpty() && data != null

    /**
     * True if parsing produced data but also collected non-critical errors.
     */
    val isPartial: Boolean
        get() = data != null && errors.isNotEmpty()

    companion object {
        /**
         * Creates a successful parse result with data and no errors.
         */
        fun <T, E> success(data: T): ParseResult<T, E> =
            ParseResult(data = data, errors = emptyList())

        /**
         * Creates a failed parse result with errors but no data.
         */
        fun <T, E> failure(errors: List<E>): ParseResult<T, E> =
            ParseResult(data = null, errors = errors)

        /**
         * Creates a partial parse result with both data and errors.
         * Use when parsing recovered from errors but still produced usable output.
         */
        fun <T, E> partial(data: T, errors: List<E>): ParseResult<T, E> =
            ParseResult(data = data, errors = errors)
    }

    /**
     * Generates a user-friendly message summarizing the parse result.
     * Requires error type E to have a getUserMessage() method.
     */
    fun getUserMessage(errorFormatter: (E) -> String): String =
        when {
            isSuccess -> "Parsing completed successfully."
            isPartial -> {
                val errorSummary = errors.joinToString("\n") { errorFormatter(it) }
                "Parsed with ${errors.size} error(s):\n$errorSummary"
            }
            else -> {
                val errorSummary = errors.joinToString("\n") { errorFormatter(it) }
                "Parsing failed with ${errors.size} error(s):\n$errorSummary"
            }
        }
}

/**
 * Sealed class representing parsing errors with context and severity.
 */
sealed class ParseError {
    /**
     * The line number where the error occurred (1-based).
     */
    abstract val lineNumber: Int

    /**
     * The severity level of this error.
     */
    abstract val severity: ErrorSeverity

    /**
     * Human-readable description of the error.
     */
    abstract val message: String

    /**
     * Formats this error for display to the user.
     */
    fun toUserMessage(): String = "Line $lineNumber [$severity]: $message"

    /**
     * Error occurred when a line doesn't match expected format.
     */
    data class MalformedLine(
        override val lineNumber: Int,
        val expected: String,
        val actual: String,
    ) : ParseError() {
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override val message: String =
            "Expected format: $expected, but got: $actual"
    }

    /**
     * Error occurred when attempting to parse an empty file.
     */
    data class EmptyFile(
        override val lineNumber: Int = 0,
    ) : ParseError() {
        override val severity: ErrorSeverity = ErrorSeverity.CRITICAL
        override val message: String = "File is empty or contains no parseable content"
    }

    /**
     * Error occurred when data format is invalid.
     */
    data class InvalidFormat(
        override val lineNumber: Int,
        val reason: String,
    ) : ParseError() {
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override val message: String = reason
    }

    /**
     * Error with file context added.
     */
    data class WithFileContext(
        val fileName: String,
        val wrappedError: ParseError,
    ) : ParseError() {
        override val lineNumber: Int
            get() = wrappedError.lineNumber
        override val severity: ErrorSeverity
            get() = wrappedError.severity
        override val message: String
            get() = "$fileName: ${wrappedError.message}"
    }
}

/**
 * Severity levels for parsing errors.
 */
enum class ErrorSeverity {
    /**
     * Minor issue that doesn't prevent parsing from continuing.
     */
    WARNING,

    /**
     * Error that prevents a specific item from being parsed,
     * but doesn't invalidate the entire file.
     */
    ERROR,

    /**
     * Critical error that makes parsing impossible.
     */
    CRITICAL,
}
