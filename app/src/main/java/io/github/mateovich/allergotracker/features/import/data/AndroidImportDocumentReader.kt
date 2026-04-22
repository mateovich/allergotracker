package io.github.mateovich.allergotracker.features.import.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.import.domain.ImportDocumentContent
import io.github.mateovich.allergotracker.features.import.domain.ImportDocumentReader

class AndroidImportDocumentReader(
    private val contentResolver: ContentResolver
) : ImportDocumentReader {
    override suspend fun readDocument(documentUri: String): AppResult<ImportDocumentContent> {
        return try {
            val uri = Uri.parse(documentUri)
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return AppResult.Error(DomainError.ImportError("Couldn't read the selected import file."))
            AppResult.Success(
                ImportDocumentContent(
                    documentUri = documentUri,
                    fileName = resolveDisplayName(uri),
                    rawJson = content
                )
            )
        } catch (_: Exception) {
            AppResult.Error(DomainError.ImportError("Couldn't read the selected import file."))
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(columnIndex)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "import.json"
    }
}
