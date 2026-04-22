package com.example.app.core.common.error

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainErrorTest {

    @Test
    fun domain_error_supports_required_error_types() {
        assertEquals("v", DomainError.ValidationError("v").message)
        assertEquals("i", DomainError.IntegrityError("i").message)
        assertEquals("p", DomainError.PersistenceError("p").message)
        assertEquals("e", DomainError.ExportError("e").message)
        assertEquals("m", DomainError.ImportError("m").message)
        assertTrue(DomainError.ValidationError("v") != DomainError.IntegrityError("v"))
    }
}
