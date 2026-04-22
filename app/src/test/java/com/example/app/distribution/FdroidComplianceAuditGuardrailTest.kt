package com.example.app.distribution

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FdroidComplianceAuditGuardrailTest {

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
        assertTrue(source.contains("Pass/Fail"))
        assertTrue(source.contains("Owner-ready remediation"))
        assertTrue(source.contains("Story 7.5"))
        assertTrue(source.contains("AllowedAPKSigningKeys"))
        assertTrue(source.contains("Package identity"))
        assertTrue(source.contains("Publication metadata"))
        assertTrue(source.contains("Signing expectations"))
    }

    @Test
    fun build_gradle_keeps_namespace_and_application_id_in_sync_with_audit() {
        assertTrue("Expected app/build.gradle.kts to exist", buildGradle.exists())

        val complianceSource = File(repoRoot, "docs/f-droid_compliance.md").readText()
        val applicationId = currentApplicationId()
        val namespace = currentNamespace()

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
