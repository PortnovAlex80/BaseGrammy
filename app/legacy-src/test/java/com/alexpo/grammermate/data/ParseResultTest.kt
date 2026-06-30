package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ParseResult and ParseError classes.
 * Tests success/failure/partial factory methods, error handling, and user message generation.
 */
class ParseResultTest {

    // ========== ParseResult Factory Methods Tests ==========

    @Test
    fun `success factory creates result with data and no errors`() {
        val data = "test data"
        val result = ParseResult.success<String, ParseError>(data)

        assertEquals("Data should match", data, result.data)
        assertTrue("Errors should be empty", result.errors.isEmpty())
        assertTrue("Result should be success", result.isSuccess)
        assertFalse("Result should not be partial", result.isPartial)
    }

    @Test
    fun `failure factory creates result with errors and no data`() {
        val errors = listOf(
            ParseError.MalformedLine(lineNumber = 1, expected = "2 columns", actual = "3 columns"),
            ParseError.InvalidFormat(lineNumber = 2, reason = "Invalid date format")
        )
        val result = ParseResult.failure<String, ParseError>(errors)

        assertNull("Data should be null", result.data)
        assertEquals("Errors should match", errors, result.errors)
        assertFalse("Result should not be success", result.isSuccess)
        assertFalse("Result should not be partial", result.isPartial)
    }

    @Test
    fun `partial factory creates result with both data and errors`() {
        val data = "partial data"
        val errors = listOf(
            ParseError.MalformedLine(lineNumber = 5, expected = "valid format", actual = "invalid")
        )
        val result = ParseResult.partial(data, errors)

        assertEquals("Data should match", data, result.data)
        assertEquals("Errors should match", errors, result.errors)
        assertFalse("Result should not be success", result.isSuccess)
        assertTrue("Result should be partial", result.isPartial)
    }

    // ========== ParseResult State Properties Tests ==========

    @Test
    fun `isSuccess is true only when data exists and errors are empty`() {
        val success = ParseResult.success<String, ParseError>("data")
        assertTrue(success.isSuccess)

        val failure = ParseResult.failure<String, ParseError>(emptyList())
        assertFalse(failure.isSuccess)

        val partial = ParseResult.partial("data", listOf(ParseError.EmptyFile()))
        assertFalse(partial.isSuccess)
    }

    @Test
    fun `isPartial is true only when data exists and errors are non-empty`() {
        val partial = ParseResult.partial("data", listOf(ParseError.EmptyFile()))
        assertTrue(partial.isPartial)

        val success = ParseResult.success<String, ParseError>("data")
        assertFalse(success.isPartial)

        val failure = ParseResult.failure<String, ParseError>(listOf(ParseError.EmptyFile()))
        assertFalse(failure.isPartial)
    }

    // ========== ParseResult getUserMessage Tests ==========

    @Test
    fun `getUserMessage returns success message for successful parse`() {
        val result = ParseResult.success<String, ParseError>("data")
        val message = result.getUserMessage { it.toUserMessage() }

        assertEquals("Parsing completed successfully.", message)
    }

    @Test
    fun `getUserMessage returns partial message with error count`() {
        val errors = listOf(
            ParseError.MalformedLine(1, "expected", "actual"),
            ParseError.InvalidFormat(2, "bad format")
        )
        val result = ParseResult.partial("data", errors)
        val message = result.getUserMessage { it.toUserMessage() }

        assertTrue("Should mention partial status", message.startsWith("Parsed with 2 error(s):"))
        assertTrue("Should include first error", message.contains("Line 1"))
        assertTrue("Should include second error", message.contains("Line 2"))
    }

    @Test
    fun `getUserMessage returns failure message with error count`() {
        val errors = listOf(
            ParseError.EmptyFile(),
            ParseError.InvalidFormat(1, "corrupt")
        )
        val result = ParseResult.failure<String, ParseError>(errors)
        val message = result.getUserMessage { it.toUserMessage() }

        assertTrue("Should mention failure", message.startsWith("Parsing failed with 2 error(s):"))
        assertTrue("Should include errors", message.contains("Line 0"))
        assertTrue("Should include errors", message.contains("Line 1"))
    }

    // ========== ParseError Subtype Tests ==========

    @Test
    fun `MalformedLine error contains correct properties`() {
        val error = ParseError.MalformedLine(
            lineNumber = 10,
            expected = "2 columns separated by semicolon",
            actual = "single column"
        )

        assertEquals("Line number should match", 10, error.lineNumber)
        assertEquals("Severity should be ERROR", ErrorSeverity.ERROR, error.severity)
        assertTrue("Message should contain expected format", error.message.contains("2 columns separated by semicolon"))
        assertTrue("Message should contain actual content", error.message.contains("single column"))
    }

    @Test
    fun `EmptyFile error contains correct properties`() {
        val error = ParseError.EmptyFile(lineNumber = 0)

        assertEquals("Line number should be 0", 0, error.lineNumber)
        assertEquals("Severity should be CRITICAL", ErrorSeverity.CRITICAL, error.severity)
        assertTrue("Message should describe empty file", error.message.contains("empty"))
    }

    @Test
    fun `InvalidFormat error contains correct properties`() {
        val error = ParseError.InvalidFormat(
            lineNumber = 5,
            reason = "Date must be in ISO format (YYYY-MM-DD)"
        )

        assertEquals("Line number should match", 5, error.lineNumber)
        assertEquals("Severity should be ERROR", ErrorSeverity.ERROR, error.severity)
        assertEquals("Reason should match", "Date must be in ISO format (YYYY-MM-DD)", error.message)
    }

    // ========== ParseError toUserMessage Tests ==========

    @Test
    fun `toUserMessage formats MalformedLine correctly`() {
        val error = ParseError.MalformedLine(3, "expected format", "actual content")
        val message = error.toUserMessage()

        assertEquals("Line 3 [ERROR]: Expected format: expected format, but got: actual content", message)
    }

    @Test
    fun `toUserMessage formats EmptyFile correctly`() {
        val error = ParseError.EmptyFile()
        val message = error.toUserMessage()

        assertTrue(message.startsWith("Line 0 [CRITICAL]:"))
        assertTrue(message.contains("empty"))
    }

    @Test
    fun `toUserMessage formats InvalidFormat correctly`() {
        val error = ParseError.InvalidFormat(7, "Invalid character encoding")
        val message = error.toUserMessage()

        assertEquals("Line 7 [ERROR]: Invalid character encoding", message)
    }

    // ========== Edge Cases Tests ==========

    @Test
    fun `ParseResult handles empty errors list correctly`() {
        val result = ParseResult.failure<String, ParseError>(emptyList())

        assertNull("Data should be null", result.data)
        assertTrue("Errors should be empty", result.errors.isEmpty())
        assertFalse("Should not be success without data", result.isSuccess)
        assertFalse("Should not be partial without data", result.isPartial)
    }

    @Test
    fun `ParseResult handles null data correctly`() {
        val result = ParseResult.success<String?, ParseError>(null)

        assertNull("Data should be null", result.data)
        assertTrue("Errors should be empty", result.errors.isEmpty())
        assertFalse("Should not be success with null data", result.isSuccess)
        assertFalse("Should not be partial with null data", result.isPartial)
    }

    @Test
    fun `ParseResult handles mixed severity errors in user message`() {
        val errors = listOf(
            ParseError.MalformedLine(1, "expected", "actual"), // ERROR
            ParseError.EmptyFile(), // CRITICAL
            ParseError.InvalidFormat(3, "warning") // ERROR
        )
        val result = ParseResult.failure<String, ParseError>(errors)
        val message = result.getUserMessage { it.toUserMessage() }

        assertTrue("Should mention failure", message.startsWith("Parsing failed with 3 error(s):"))
        assertTrue("Should include ERROR severity", message.contains("[ERROR]"))
        assertTrue("Should include CRITICAL severity", message.contains("[CRITICAL]"))
    }

    @Test
    fun `ParseResult success with complex data type`() {
        data class ComplexData(val id: String, val value: Int)
        val data = ComplexData("test", 42)
        val result = ParseResult.success<ComplexData, ParseError>(data)

        assertTrue("Should be success", result.isSuccess)
        assertEquals("Data should match", data, result.data)
        assertEquals("ID should match", "test", result.data?.id)
        assertEquals("Value should match", 42, result.data?.value)
    }

    @Test
    fun `ParseResult with list of errors maintains order`() {
        val errors = listOf(
            ParseError.MalformedLine(1, "e1", "a1"),
            ParseError.MalformedLine(2, "e2", "a2"),
            ParseError.MalformedLine(3, "e3", "a3")
        )
        val result = ParseResult.failure<String, ParseError>(errors)

        assertEquals("Should have 3 errors", 3, result.errors.size)
        assertEquals("First error line should be 1", 1, (result.errors[0] as ParseError.MalformedLine).lineNumber)
        assertEquals("Second error line should be 2", 2, (result.errors[1] as ParseError.MalformedLine).lineNumber)
        assertEquals("Third error line should be 3", 3, (result.errors[2] as ParseError.MalformedLine).lineNumber)
    }

    @Test
    fun `ParseResult partial result distinguishes from failure`() {
        val data = "some data"
        val errors = listOf(ParseError.EmptyFile())

        val partial = ParseResult.partial(data, errors)
        val failure = ParseResult.failure<String, ParseError>(errors)

        assertTrue("Partial should have data", partial.data != null)
        assertTrue("Partial should be isPartial", partial.isPartial)
        assertFalse("Partial should not be isSuccess", partial.isSuccess)

        assertNull("Failure should not have data", failure.data)
        assertFalse("Failure should not be isPartial", failure.isPartial)
        assertFalse("Failure should not be isSuccess", failure.isSuccess)
    }

    // ========== ErrorSeverity Enum Tests ==========

    @Test
    fun `ErrorSeverity enum has correct values`() {
        val severities = ErrorSeverity.entries

        assertTrue("Should contain WARNING", severities.contains(ErrorSeverity.WARNING))
        assertTrue("Should contain ERROR", severities.contains(ErrorSeverity.ERROR))
        assertTrue("Should contain CRITICAL", severities.contains(ErrorSeverity.CRITICAL))
        assertEquals("Should have exactly 3 severities", 3, severities.size)
    }
}
