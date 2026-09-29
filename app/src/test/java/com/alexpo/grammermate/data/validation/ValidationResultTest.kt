package com.alexpo.grammermate.data.validation

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

class ValidationResultTest {
    // ── ValidationResult Tests ─────────────────────────────────────────────────────

    @Test
    fun `Valid result should be valid`() {
        val result = ValidationResult.Valid("test data")
        assertTrue("Valid result should be valid", result.isValid)
        assertEquals("test data", result.getDataOrDefault())
        assertEquals("test data", result.getDataOrNull())
    }

    @Test
    fun `Invalid result should be invalid`() {
        val error = ValidationError("field", "error message")
        val result = ValidationResult.Invalid(listOf(error), "default")
        assertFalse("Invalid result should not be valid", result.isValid)
        assertEquals("default", result.getDataOrDefault())
        assertNull("Invalid result should return null data", result.getDataOrNull())
    }

    @Test
    fun `Warning result should be valid`() {
        val warning = ValidationWarning("field", "warning message")
        val result = ValidationResult.Warning("test data", listOf(warning))
        assertTrue("Warning result should be valid", result.isValid)
        assertEquals("test data", result.getDataOrDefault())
    }

    @Test
    fun `Valid result should map correctly`() {
        val result = ValidationResult.Valid(5)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isValid)
        assertEquals(10, mapped.getDataOrDefault())
    }

    @Test
    fun `Invalid result should preserve safe default on map`() {
        val error = ValidationError("field", "error")
        val result = ValidationResult.Invalid(listOf(error), 10)
        val mapped = result.map { it * 2 }
        assertFalse(mapped.isValid)
        assertEquals(20, mapped.getDataOrDefault())
    }
}
