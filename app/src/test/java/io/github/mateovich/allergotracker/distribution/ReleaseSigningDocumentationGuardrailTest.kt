package io.github.mateovich.allergotracker.distribution

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningDocumentationGuardrailTest {

    private val repoRoot = resolveRepoRoot()

    @Test
    fun signing_docs_define_local_only_external_signing_without_committed_secrets() {
        val signingDoc = File(repoRoot, "docs/f-droid_signing.md").readText()

        assertTrue(signingDoc.contains("Local-only production signing path"))
        assertTrue(signingDoc.contains("APKSIGNER_KEYSTORE"))
        assertTrue(signingDoc.contains("APKSIGNER_KEY_ALIAS"))
        assertTrue(signingDoc.contains("APKSIGNER_KEYSTORE_PASS"))
        assertTrue(signingDoc.contains("APKSIGNER_KEY_PASS"))
        assertTrue(signingDoc.contains("apksigner sign"))
        assertTrue(signingDoc.contains("apksigner verify --print-certs"))
        assertTrue(signingDoc.contains("not generated in this environment"))
        assertTrue(signingDoc.contains("set -euo pipefail"))
        assertTrue(signingDoc.contains("--ks-pass env:APKSIGNER_KEYSTORE_PASS"))
        assertTrue(signingDoc.contains("--key-pass env:APKSIGNER_KEY_PASS"))
        assertFalse(signingDoc.contains("--ks-pass \"pass:$"))
        assertFalse(signingDoc.contains("--key-pass \"pass:$"))
        assertFalse(signingDoc.contains("storePassword ="))
        assertFalse(signingDoc.contains("keyPassword ="))
    }

    @Test
    fun release_docs_preserve_unsigned_publication_and_debug_signed_validation_boundary() {
        val signingDoc = File(repoRoot, "docs/f-droid_signing.md").readText()
        val alternativeDoc = File(repoRoot, "docs/alternative-distribution.md").readText()
        val checklist = File(repoRoot, "docs/f-droid_release_checklist.md").readText()

        listOf(signingDoc, alternativeDoc, checklist).forEach { source ->
            assertTrue(source.contains("app/build/outputs/apk/release/app-release-unsigned.apk"))
            assertTrue(source.contains("app/build/outputs/alternative/release/app-alternative-release.apk"))
        }
        assertTrue(alternativeDoc.contains("not release-key signed"))
        assertTrue(checklist.contains("Local-only signing path"))
    }

    private fun resolveRepoRoot(): File {
        val startDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(startDirectory) { current -> current.parentFile }
            .firstOrNull { candidate -> File(candidate, "settings.gradle.kts").exists() }
            ?: error("Could not locate repo root from ${startDirectory.path}")
    }
}
