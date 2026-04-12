package com.example.app.features.export.data

import com.example.app.core.common.result.AppResult
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalJsonExportArtifactWriterTest {

    @Test
    fun move_failure_returns_export_error_and_leaves_no_temp_files() {
        val exportDirectory = Files.createTempFile("export-writer", ".tmp").toFile()
        val fileName = "app-export-v1_0_0-20231114T221320Z.json"
        val writer = LocalJsonExportArtifactWriter(exportDirectory)

        val result = kotlinx.coroutines.runBlocking {
            writer.writeJson(fileName, "{}")
        }

        require(result is AppResult.Error)
        assertTrue(result.error.message == "Could not generate the local JSON export. Try again.")
        assertTrue(exportDirectory.isFile)
        assertFalse(exportDirectory.parentFile?.listFiles().orEmpty().any { it.name.endsWith(".json.tmp") })
    }

    @Test
    fun successful_write_creates_local_json_artifact() {
        val exportDirectory = Files.createTempDirectory("export-writer-success").toFile()
        val writer = LocalJsonExportArtifactWriter(exportDirectory)

        val result = kotlinx.coroutines.runBlocking {
            writer.writeJson("export.json", "{\"ok\":true}")
        }

        require(result is AppResult.Success)
        assertTrue(File(result.value.absolutePath).exists())
    }
}
