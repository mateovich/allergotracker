package com.example.app.features.export.data

import com.example.app.core.common.result.AppResult
import com.example.app.features.export.domain.ExportArtifact
import com.example.app.features.export.domain.ExportArtifactWriter
import com.example.app.features.export.domain.toExportError
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LocalJsonExportArtifactWriter(
    private val exportDirectory: File
) : ExportArtifactWriter {
    override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> {
        var temporaryFile: File? = null
        return try {
            if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                throw IllegalStateException("Could not prepare local export storage.")
            }
            temporaryFile = File.createTempFile("export-", ".json.tmp", exportDirectory)
            temporaryFile.writeText(content)
            val finalFile = buildAvailableTargetFile(fileName)
            Files.move(
                temporaryFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
            AppResult.Success(
                ExportArtifact(
                    fileName = finalFile.name,
                    absolutePath = finalFile.absolutePath
                )
            )
        } catch (error: Exception) {
            temporaryFile?.delete()
            AppResult.Error(error.toExportError("Could not generate the local JSON export. Try again."))
        }
    }

    private fun buildAvailableTargetFile(fileName: String): File {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val baseName = if (extension.isEmpty()) fileName else fileName.removeSuffix(".$extension")
        var candidate = File(exportDirectory, fileName)
        var suffix = 2
        while (candidate.exists()) {
            val resolvedName = if (extension.isEmpty()) {
                "$baseName-$suffix"
            } else {
                "$baseName-$suffix.$extension"
            }
            candidate = File(exportDirectory, resolvedName)
            suffix += 1
        }
        return candidate
    }
}
