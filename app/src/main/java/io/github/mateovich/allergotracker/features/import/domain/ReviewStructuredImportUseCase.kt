package io.github.mateovich.allergotracker.features.import.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class ReviewStructuredImportUseCase(
    private val documentReader: ImportDocumentReader,
    private val parser: VersionedJsonImportParser,
    private val repository: StructuredImportRepository
) {
    suspend operator fun invoke(documentUri: String): AppResult<StructuredImportReview> {
        val document = when (val result = documentReader.readDocument(documentUri)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val envelope = when (val result = parser.parse(document.rawJson)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        return repository.review(document, envelope)
    }
}
