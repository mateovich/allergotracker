package com.example.app.distribution

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeDistributionGuardrailTest {
    private val moduleDirectory = resolveModuleDirectory()

    private val forbiddenDependencyPatterns = listOf(
        Regex("com\\.google\\.android\\.gms"),
        Regex("play-services"),
        Regex("firebase", RegexOption.IGNORE_CASE),
        Regex("crashlytics", RegexOption.IGNORE_CASE),
        Regex("billingclient", RegexOption.IGNORE_CASE),
        Regex("com\\.android\\.billingclient")
    )

    @Test
    fun app_build_gradle_defines_reproducible_alternative_distribution_path() {
        val gradleFile = File(moduleDirectory, "build.gradle.kts")

        assertTrue(gradleFile.exists())
        val source = gradleFile.readText()

        assertTrue(source.contains("buildConfigField(\"String\", \"DISTRIBUTION_CHANNEL\""))
        assertTrue(source.contains("buildConfigField(\"boolean\", \"SUPPORTS_STORE_SERVICES\", \"false\")"))
        assertTrue(source.contains("create(\"alternativeRelease\")"))
        assertTrue(source.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(source.contains("register<Copy>(\"prepareAlternativeRelease\")"))
        assertTrue(source.contains("assembleAlternativeRelease"))
        assertTrue(source.contains("outputs/apk/alternativeRelease/app-alternativeRelease.apk"))
        assertTrue(source.contains("outputs/alternative/release"))
        assertTrue(source.contains("app-alternative-release.apk"))
    }

    @Test
    fun app_sources_do_not_introduce_mandatory_store_or_proprietary_mobile_sdks() {
        val scannedFiles = buildList {
            add(File(moduleDirectory, "build.gradle.kts"))
            add(moduleDirectory.resolve("src/main/AndroidManifest.xml"))
            addAll(
                File(moduleDirectory, "src/main")
                    .walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.name == "AndroidManifest.xml") }
                    .toList()
            )
        }.distinct()

        assertTrue("Expected app build and production source files to exist", scannedFiles.isNotEmpty())

        val violations = scannedFiles.flatMap { file ->
            val source = file.readText()
            forbiddenDependencyPatterns
                .filter { pattern -> pattern.containsMatchIn(source) }
                .map { pattern -> "${file.relativeTo(moduleDirectory).path} -> ${pattern.pattern}" }
        }

        assertTrue(
            "Forbidden store/proprietary SDK references found:\n${violations.joinToString(separator = "\n")}",
            violations.isEmpty()
        )
    }

    private fun resolveModuleDirectory(): File {
        val startDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = sequence {
            var current: File? = startDirectory
            while (current != null) {
                yield(current)
                current = current.parentFile
            }
        }

        return candidates
            .map { candidate -> candidate.resolve("app") }
            .firstOrNull { appDirectory ->
                appDirectory.resolve("build.gradle.kts").exists() &&
                    appDirectory.resolve("src/main/AndroidManifest.xml").exists()
            }
            ?: throw AssertionError("Unable to locate app module from ${startDirectory.path}")
    }
}
