package com.example.app.features.export.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.export.domain.ExportDeliveryRepository
import com.example.app.features.export.domain.ExportDeliveryResult
import com.example.app.features.export.domain.ExportDestination
import com.example.app.features.export.domain.GeneratedJsonExport
import com.example.app.features.export.domain.toExportError
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DownloadsExportDeliveryRepository(
    private val writer: DownloadsDestinationWriter
) : ExportDeliveryRepository {

    constructor(appContext: Context) : this(
        writer = AndroidDownloadsDestinationWriter(
            appContext = appContext,
            contentResolver = appContext.contentResolver
        )
    )

    override suspend fun deliver(
        stagedExport: GeneratedJsonExport,
        destination: ExportDestination
    ): AppResult<ExportDeliveryResult> {
        if (destination != ExportDestination.DeviceDownloads) {
            return AppResult.Error(DomainError.ExportError("Choose a supported export destination."))
        }
        val sourceFile = File(stagedExport.artifact.absolutePath)
        if (!sourceFile.exists()) {
            return AppResult.Error(DomainError.ExportError("The staged export is no longer available. Generate it again before saving."))
        }
        return writer.writeToDownloads(
            sourceFile = sourceFile,
            fileName = stagedExport.artifact.fileName,
            mimeType = stagedExport.artifact.mimeType
        )
    }
}

interface DownloadsDestinationWriter {
    suspend fun writeToDownloads(
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): AppResult<ExportDeliveryResult>
}

private class AndroidDownloadsDestinationWriter(
    private val appContext: Context,
    private val contentResolver: ContentResolver
) : DownloadsDestinationWriter {

    override suspend fun writeToDownloads(
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): AppResult<ExportDeliveryResult> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(sourceFile = sourceFile, fileName = fileName, mimeType = mimeType)
        } else {
            writeToAppScopedDownloads(sourceFile = sourceFile, fileName = fileName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): AppResult<ExportDeliveryResult> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val createdUri = contentResolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/app")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: return AppResult.Error(DomainError.ExportError("Could not prepare the selected Downloads destination."))

        return try {
            contentResolver.openOutputStream(createdUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Could not open the Downloads destination for writing.")

            contentResolver.update(
                createdUri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null
            ).takeIf { it == 1 } ?: throw IOException("Could not finalize the Downloads export.")

            val savedMetadata = querySavedMetadata(createdUri)

            AppResult.Success(
                ExportDeliveryResult(
                    destination = ExportDestination.DeviceDownloads,
                    fileName = savedMetadata.fileName,
                    locationLabel = savedMetadata.locationLabel,
                    locationUri = createdUri.toString()
                )
            )
        } catch (error: Exception) {
            contentResolver.delete(createdUri, null, null)
            AppResult.Error(error.toExportError("Could not save the export to Downloads. Try again."))
        }
    }

    private fun writeToAppScopedDownloads(sourceFile: File, fileName: String): AppResult<ExportDeliveryResult> {
        return try {
            val downloadsDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.resolve("exports")
                ?: return AppResult.Error(DomainError.ExportError("Downloads is unavailable on this device."))
            if (!downloadsDirectory.exists() && !downloadsDirectory.mkdirs()) {
                throw IOException("Could not prepare the Downloads destination.")
            }
            val targetFile = buildAvailableTargetFile(downloadsDirectory, fileName)
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            AppResult.Success(
                ExportDeliveryResult(
                    destination = ExportDestination.DeviceDownloads,
                    fileName = targetFile.name,
                    locationLabel = "App-specific Downloads",
                    locationUri = targetFile.absolutePath
                )
            )
        } catch (error: Exception) {
            AppResult.Error(error.toExportError("Could not save the export to Downloads. Try again."))
        }
    }

    private fun buildAvailableTargetFile(directory: File, fileName: String): File {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val baseName = if (extension.isEmpty()) fileName else fileName.removeSuffix(".$extension")
        var candidate = File(directory, fileName)
        var suffix = 2
        while (candidate.exists()) {
            val resolvedName = if (extension.isEmpty()) {
                "$baseName-$suffix"
            } else {
                "$baseName-$suffix.$extension"
            }
            candidate = File(directory, resolvedName)
            suffix += 1
        }
        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun querySavedMetadata(uri: android.net.Uri): SavedDownloadMetadata {
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            ),
            null,
            null,
            null
        ) ?: throw IOException("Could not read saved export metadata.")
        cursor.use {
            if (!it.moveToFirst()) {
                throw IOException("Could not read saved export metadata.")
            }
            val fileName = it.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
                ?: throw IOException("Could not read saved export file name.")
            val relativePath = it.getStringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
                ?: throw IOException("Could not read saved export location.")
            return SavedDownloadMetadata(
                fileName = fileName,
                locationLabel = normalizeRelativePathLabel(relativePath)
            )
        }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index == -1 || isNull(index)) return null
        return getString(index)
    }

}

internal data class SavedDownloadMetadata(
    val fileName: String,
    val locationLabel: String
)

internal fun normalizeRelativePathLabel(relativePath: String): String {
    val downloadsDirectoryName = Environment.DIRECTORY_DOWNLOADS ?: "Download"
    val trimmed = relativePath.trimEnd('/')
    return if (trimmed.startsWith(downloadsDirectoryName)) {
        trimmed.replaceFirst(downloadsDirectoryName, "Downloads")
    } else {
        trimmed
    }
}
