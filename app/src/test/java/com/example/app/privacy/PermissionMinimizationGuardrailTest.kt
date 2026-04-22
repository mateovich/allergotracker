package com.example.app.privacy

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionMinimizationGuardrailTest {

    private val forbiddenManifestPermissions = setOf(
        "READ_EXTERNAL_STORAGE",
        "WRITE_EXTERNAL_STORAGE",
        "MANAGE_EXTERNAL_STORAGE"
    )

    private val forbiddenManifestPermissionPattern = Regex("READ_MEDIA_[A-Z_]+")

    private val forbiddenRuntimePermissionPatterns = listOf(
        "ActivityResultContracts.RequestPermission(",
        "ActivityResultContracts.RequestMultiplePermissions(",
        "RequestPermission(",
        "RequestMultiplePermissions(",
        "requestPermissions(",
        "Manifest.permission.READ_EXTERNAL_STORAGE",
        "Manifest.permission.WRITE_EXTERNAL_STORAGE",
        "Manifest.permission.MANAGE_EXTERNAL_STORAGE"
    )

    private val forbiddenRuntimePermissionRegexes = listOf(
        Regex("Manifest\\.permission\\.READ_MEDIA_[A-Z_]+")
    )

    private val forbiddenBroadStorageApis = listOf(
        "Environment.getExternalStorageDirectory(",
        "ACTION_OPEN_DOCUMENT_TREE"
    )

    @Test
    fun app_manifests_do_not_declare_broad_storage_or_media_permissions() {
        val manifestFiles = appModuleDirectory()
            .resolve("src")
            .walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .toList()

        assertTrue("Expected at least one AndroidManifest.xml under app/src", manifestFiles.isNotEmpty())

        val violations = manifestFiles.flatMap { manifestFile ->
            val source = manifestFile.readText()
            val relativePath = manifestFile.relativeTo(appModuleDirectory()).path
            buildList {
                forbiddenManifestPermissions
                    .filter { permission -> source.contains(permission) }
                    .forEach { permission -> add("$relativePath -> $permission") }

                forbiddenManifestPermissionPattern
                    .findAll(source)
                    .map { match -> match.value }
                    .distinct()
                    .forEach { permission -> add("$relativePath -> $permission") }
            }
        }

        assertTrue(
            "Forbidden storage/media permissions found:\n${violations.joinToString(separator = "\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun app_source_does_not_introduce_broad_storage_or_runtime_permission_requests() {
        val sourceFiles = appModuleDirectory()
            .resolve("src")
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filterNot { sourceFile ->
                val path = sourceFile.invariantSeparatorsPath
                path.contains("/src/test/") || path.contains("/src/androidTest/")
            }
            .toList()

        assertTrue("Expected production source files under app/src", sourceFiles.isNotEmpty())

        val runtimePermissionViolations = sourceFiles.flatMap { sourceFile ->
            val source = sourceFile.readText()
            val relativePath = sourceFile.relativeTo(appModuleDirectory()).path
            buildList {
                forbiddenRuntimePermissionPatterns
                    .filter { pattern -> source.contains(pattern) }
                    .forEach { pattern -> add("$relativePath -> $pattern") }

                forbiddenRuntimePermissionRegexes
                    .flatMap { regex -> regex.findAll(source).map { match -> match.value }.toList() }
                    .distinct()
                    .forEach { pattern -> add("$relativePath -> $pattern") }
            }
        }

        val broadStorageViolations = sourceFiles.flatMap { sourceFile ->
            val source = sourceFile.readText()
            val relativePath = sourceFile.relativeTo(appModuleDirectory()).path
            forbiddenBroadStorageApis
                .filter { api -> source.contains(api) }
                .map { api -> "$relativePath -> $api" }
        }

        assertTrue(
            "Forbidden runtime permission requests found:\n${runtimePermissionViolations.joinToString(separator = "\n")}",
            runtimePermissionViolations.isEmpty()
        )
        assertTrue(
            "Forbidden broad storage APIs found:\n${broadStorageViolations.joinToString(separator = "\n")}",
            broadStorageViolations.isEmpty()
        )
    }

    private fun appModuleDirectory(): File {
        val startDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(startDirectory) { current -> current.parentFile }
            .forEach { candidate ->
                if (File(candidate, "src/main/AndroidManifest.xml").exists()) {
                    return candidate
                }
                if (File(candidate, "app/src/main/AndroidManifest.xml").exists()) {
                    return File(candidate, "app")
                }
            }

        error("Could not locate the app module directory from ${startDirectory.path}")
    }
}
