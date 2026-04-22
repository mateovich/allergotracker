package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError

data class ExportPreflightResult(
    val issues: List<ExportPreflightIssue> = emptyList()
) {
    val blockingIssues: List<ExportPreflightIssue.Blocking>
        get() = issues.filterIsInstance<ExportPreflightIssue.Blocking>()

    val warningIssues: List<ExportPreflightIssue.Warning>
        get() = issues.filterIsInstance<ExportPreflightIssue.Warning>()

    fun hasBlockingIssues(): Boolean = blockingIssues.isNotEmpty()

    fun requiresExplicitWarningAcceptance(): Boolean = !hasBlockingIssues() && warningIssues.isNotEmpty()
}

data class ExportPreflightReview(
    val result: ExportPreflightResult,
    val preparedExport: PreparedExport? = null
)

data class PreparedExport(
    val configuration: ExportConfiguration,
    val snapshot: ExportSnapshot,
    val normalizedBaseIngredients: List<NormalizedBaseIngredientsRecord>
)

sealed interface ExportPreflightIssue {
    val message: String

    data class Blocking(val error: DomainError) : ExportPreflightIssue {
        override val message: String = error.message
    }

    data class Warning(override val message: String) : ExportPreflightIssue
}

enum class ExportWarningDecision {
    RequireCleanPass,
    AcceptWarnings
}
