package io.github.mateovich.allergotracker.distribution

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FdroidComplianceAuditGuardrailTest {

    private val expectedReleaseApplicationId = "io.github.mateovich.allergotracker"

    private val repoRoot = resolveRepoRoot()
    private val buildGradle = File(repoRoot, "app/build.gradle.kts")

    @Test
    fun compliance_doc_links_stable_dependency_inventory_evidence_for_release_paths() {
        val complianceDoc = File(repoRoot, "docs/f-droid_compliance.md")
        val dependencyInventory = File(repoRoot, "docs/f-droid_dependency_inventory.md")

        assertTrue("Expected docs/f-droid_compliance.md to exist", complianceDoc.exists())
        assertTrue("Expected docs/f-droid_dependency_inventory.md to exist", dependencyInventory.exists())

        val complianceSource = complianceDoc.readText()
        val inventorySource = dependencyInventory.readText()

        assertTrue(complianceSource.contains("docs/f-droid_dependency_inventory.md"))
        assertTrue(inventorySource.contains("# F-Droid dependency inventory evidence"))
        assertTrue(inventorySource.contains("releaseRuntimeClasspath"))
        assertTrue(inventorySource.contains("alternativeReleaseRuntimeClasspath"))
        assertTrue(inventorySource.contains("androidx.appcompat:appcompat:1.6.1"))
        assertTrue(inventorySource.contains("androidx.core:core:1.16.0"))
        assertTrue(inventorySource.contains("org.jetbrains.kotlin:kotlin-stdlib:2.3.10"))
    }

    @Test
    fun compliance_doc_links_artifact_permission_evidence_and_current_distribution_blockers() {
        val complianceDoc = File(repoRoot, "docs/f-droid_compliance.md")
        val permissionEvidence = File(repoRoot, "docs/f-droid_artifact_permissions.md")

        assertTrue("Expected docs/f-droid_compliance.md to exist", complianceDoc.exists())
        assertTrue("Expected docs/f-droid_artifact_permissions.md to exist", permissionEvidence.exists())

        val complianceSource = complianceDoc.readText()
        val permissionSource = permissionEvidence.readText()
        val applicationId = currentApplicationId()

        assertTrue(complianceSource.contains("DownloadsExportDeliveryRepository"))
        assertTrue(complianceSource.contains("AndroidImportDocumentReader"))
        assertTrue(complianceSource.contains("AppDistribution"))
        assertTrue(complianceSource.contains("docs/f-droid_artifact_permissions.md"))
        assertTrue(complianceSource.contains(applicationId))
        assertTrue(permissionSource.contains("app/build/outputs/apk/release/app-release-unsigned.apk"))
        assertTrue(permissionSource.contains("app/build/outputs/alternative/release/app-alternative-release.apk"))
        assertTrue(permissionSource.contains("${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
    }

    @Test
    fun release_checklist_exists_with_story_7_5_ready_follow_ups() {
        val checklist = File(repoRoot, "docs/f-droid_release_checklist.md")

        assertTrue("Expected docs/f-droid_release_checklist.md to exist", checklist.exists())

        val source = checklist.readText()

        assertTrue(source.contains("# F-Droid release checklist"))
        assertTrue(source.contains("| Area | Status | Evidence to collect | Owner-ready remediation |"))
        assertTrue(source.contains("Owner-ready remediation"))
        assertTrue(source.contains("Story 7.5"))
        assertTrue(source.contains("AllowedAPKSigningKeys"))
        assertTrue(source.contains("Package identity"))
        assertTrue(source.contains("Publication metadata"))
        assertTrue(source.contains("Signing expectations"))
        assertTrue(source.contains("Accepted exception"))
    }

    @Test
    fun fdroid_metadata_exists_for_release_identity_with_repo_and_license_context() {
        val metadataFile = File(repoRoot, "metadata/$expectedReleaseApplicationId.yml")

        assertTrue("Expected metadata file for release application ID", metadataFile.exists())

        val source = metadataFile.readText()

        assertTrue(source.contains("RepoType: git"))
        assertTrue(source.contains("Repo: https://github.com/mateovich/allergotracker.git"))
        assertTrue(source.contains("License: "))
        assertTrue(source.contains("Summary: "))
        assertTrue(source.contains("Description: |-"))
    }

    @Test
    fun release_docs_define_signing_expectations_and_allowed_apk_signing_keys_plan() {
        val signingDoc = File(repoRoot, "docs/f-droid_signing.md")
        val alternativeDistributionDoc = File(repoRoot, "docs/alternative-distribution.md")

        assertTrue("Expected docs/f-droid_signing.md to exist", signingDoc.exists())
        assertTrue("Expected docs/alternative-distribution.md to exist", alternativeDistributionDoc.exists())

        val signingSource = signingDoc.readText()
        val alternativeSource = alternativeDistributionDoc.readText()

        assertTrue(signingSource.contains("AllowedAPKSigningKeys"))
        assertTrue(signingSource.contains("SHA-256"))
        assertTrue(signingSource.contains("kept out of the repo"))
        assertTrue(signingSource.contains("mateovich"))
        assertTrue(alternativeSource.contains("assembleRelease"))
        assertTrue(alternativeSource.contains("unsigned release artifact"))
        assertTrue(alternativeSource.contains("debug-signed validation seam"))
    }

    @Test
    fun build_gradle_keeps_namespace_and_application_id_in_sync_with_release_identity() {
        assertTrue("Expected app/build.gradle.kts to exist", buildGradle.exists())

        val complianceSource = File(repoRoot, "docs/f-droid_compliance.md").readText()
        val applicationId = currentApplicationId()
        val namespace = currentNamespace()

        assertEquals(expectedReleaseApplicationId, applicationId)
        assertEquals(namespace, applicationId)
        assertTrue(complianceSource.contains(applicationId))
    }

    private fun resolveRepoRoot(): File {
        val startDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(startDirectory) { current -> current.parentFile }
            .firstOrNull { candidate -> File(candidate, "settings.gradle.kts").exists() }
            ?: error("Could not locate repo root from ${startDirectory.path}")
    }

    private fun currentApplicationId(): String {
        val match = Regex("applicationId\\s*=\\s*\"([^\"]+)\"")
            .find(buildGradle.readText())
            ?: error("Expected applicationId in ${buildGradle.path}")
        return match.groupValues[1]
    }

    private fun currentNamespace(): String {
        val match = Regex("namespace\\s*=\\s*\"([^\"]+)\"")
            .find(buildGradle.readText())
            ?: error("Expected namespace in ${buildGradle.path}")
        return match.groupValues[1]
    }
}
