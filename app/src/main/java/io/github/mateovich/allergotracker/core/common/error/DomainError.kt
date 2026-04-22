package io.github.mateovich.allergotracker.core.common.error

sealed class DomainError(open val message: String) {
    data class ValidationError(
        override val message: String,
        val fieldErrors: Map<String, String> = emptyMap()
    ) : DomainError(message)
    data class IntegrityError(override val message: String) : DomainError(message)
    data class PersistenceError(override val message: String) : DomainError(message)
    data class ExportError(override val message: String) : DomainError(message)
    data class ImportError(override val message: String) : DomainError(message)
}
