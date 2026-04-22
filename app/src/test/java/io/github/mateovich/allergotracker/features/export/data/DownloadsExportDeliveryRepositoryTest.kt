package io.github.mateovich.allergotracker.features.export.data

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.EXPORT_SCHEMA_VERSION
import io.github.mateovich.allergotracker.features.export.domain.ExportConfiguration
import io.github.mateovich.allergotracker.features.export.domain.ExportDeliveryResult
import io.github.mateovich.allergotracker.features.export.domain.ExportDestination
import io.github.mateovich.allergotracker.features.export.domain.ExportFormat
import io.github.mateovich.allergotracker.features.export.domain.ExportScope
import io.github.mateovich.allergotracker.features.export.domain.ExportArtifact
import io.github.mateovich.allergotracker.features.export.domain.GeneratedJsonExport
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsExportDeliveryRepositoryTest {

    @Test
    fun deliver_returns_success_metadata_for_existing_staged_artifact() = runBlocking {
        val stagedFile = Files.createTempFile("staged-export", ".json").toFile().apply {
            writeText("{}")
        }
        val repository = DownloadsExportDeliveryRepository(
            writer = object : DownloadsDestinationWriter {
                override suspend fun writeToDownloads(
                    sourceFile: File,
                    fileName: String,
                    mimeType: String
                ): AppResult<ExportDeliveryResult> {
                    assertEquals(stagedFile.absolutePath, sourceFile.absolutePath)
                    assertEquals("application/json", mimeType)
                    return AppResult.Success(
                        ExportDeliveryResult(
                            destination = ExportDestination.DeviceDownloads,
                            fileName = fileName,
                            locationLabel = "Downloads/app",
                            locationUri = "content://downloads/$fileName"
                        )
                    )
                }
            }
        )

        val result = repository.deliver(sampleGeneratedExport(stagedFile.absolutePath), ExportDestination.DeviceDownloads)

        require(result is AppResult.Success)
        assertEquals("Downloads/app", result.value.locationLabel)
        assertTrue(result.value.locationUri.contains("content://downloads/"))
    }

    @Test
    fun deliver_returns_actionable_error_when_staged_artifact_is_missing() = runBlocking {
        val missingPath = Files.createTempFile("missing-staged-export", ".json").toFile().absolutePath
        File(missingPath).delete()
        val repository = DownloadsExportDeliveryRepository(
            writer = object : DownloadsDestinationWriter {
                override suspend fun writeToDownloads(
                    sourceFile: File,
                    fileName: String,
                    mimeType: String
                ): AppResult<ExportDeliveryResult> = error("should not be called")
            }
        )

        val result = repository.deliver(sampleGeneratedExport(missingPath), ExportDestination.DeviceDownloads)

        require(result is AppResult.Error)
        assertTrue(result.error is DomainError.ExportError)
        assertEquals(
            "The staged export is no longer available. Generate it again before saving.",
            result.error.message
        )
    }

    @Test
    fun normalize_relative_path_label_reports_downloads_prefix_consistently() {
        assertEquals("Downloads/app", normalizeRelativePathLabel("Download/app/"))
    }

    private fun sampleGeneratedExport(path: String): GeneratedJsonExport {
        return GeneratedJsonExport(
            configuration = ExportConfiguration(
                format = ExportFormat.JSON,
                scope = ExportScope.AllRecords
            ),
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = "2023-11-14T22:13:20Z",
            artifact = ExportArtifact(
                fileName = "app-export-v${EXPORT_SCHEMA_VERSION.replace('.', '_')}-20231114T221320Z.json",
                absolutePath = path
            )
        )
    }
}
