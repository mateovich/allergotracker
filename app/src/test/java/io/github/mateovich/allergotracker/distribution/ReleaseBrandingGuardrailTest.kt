package io.github.mateovich.allergotracker.distribution

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseBrandingGuardrailTest {

    private val repoRoot = resolveRepoRoot()

    @Test
    fun launcher_label_and_public_metadata_use_final_product_name() {
        val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText()
        val strings = File(repoRoot, "app/src/main/res/values/strings.xml").readText()
        val fdroidTitle = File(repoRoot, "metadata/io.github.mateovich.allergotracker/en-US/title.txt").readText().trim()
        val fdroidMetadata = File(repoRoot, "metadata/io.github.mateovich.allergotracker.yml").readText()
        val fdroidFullDescription = File(repoRoot, "metadata/io.github.mateovich.allergotracker/en-US/full_description.txt").readText()

        assertTrue(manifest.contains("android:label=\"@string/app_name\""))
        assertTrue(strings.contains("<string name=\"app_name\">Allergo Tracker</string>"))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertTrue(fdroidTitle == "Allergo Tracker")
        assertTrue(fdroidMetadata.contains("Allergo Tracker is an Android-native"))
        assertTrue(fdroidFullDescription.contains("Allergo Tracker helps"))
    }

    @Test
    fun launcher_icon_resources_are_present_for_android_launcher_surfaces() {
        val expectedLauncherIconSizes = mapOf(
            "mipmap-ldpi" to 36,
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192
        )

        expectedLauncherIconSizes.forEach { (densityDirectory, expectedSize) ->
            listOf("ic_launcher.png", "ic_launcher_round.png").forEach { iconName ->
                val relativePath = "app/src/main/res/$densityDirectory/$iconName"
                val file = File(repoRoot, relativePath)
                assertTrue("Expected launcher icon resource $relativePath", file.exists() && file.length() > 0L)

                val (width, height) = readPngDimensions(file)
                assertEquals("Expected launcher icon width for $relativePath", expectedSize, width)
                assertEquals("Expected launcher icon height for $relativePath", expectedSize, height)
            }
        }

        val expectedForegroundIconSizes = mapOf(
            "mipmap-mdpi" to 108,
            "mipmap-hdpi" to 162,
            "mipmap-xhdpi" to 216,
            "mipmap-xxhdpi" to 324,
            "mipmap-xxxhdpi" to 432
        )

        expectedForegroundIconSizes.forEach { (densityDirectory, expectedSize) ->
            val relativePath = "app/src/main/res/$densityDirectory/ic_launcher_foreground.png"
            val file = File(repoRoot, relativePath)
            assertTrue("Expected adaptive foreground resource $relativePath", file.exists() && file.length() > 0L)

            val (width, height) = readPngDimensions(file)
            assertEquals("Expected foreground icon width for $relativePath", expectedSize, width)
            assertEquals("Expected foreground icon height for $relativePath", expectedSize, height)
        }

        val adaptiveIcon = File(repoRoot, "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val adaptiveRoundIcon = File(repoRoot, "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val adaptiveBackground = File(repoRoot, "app/src/main/res/values/ic_launcher_background.xml").readText()

        listOf(adaptiveIcon, adaptiveRoundIcon).forEach { iconXml ->
            assertTrue(iconXml.contains("@color/ic_launcher_background"))
            assertTrue(iconXml.contains("@mipmap/ic_launcher_foreground"))
        }
        assertTrue(adaptiveBackground.contains("<color name=\"ic_launcher_background\">"))
    }

    private fun readPngDimensions(file: File): Pair<Int, Int> {
        val bytes = file.readBytes()
        val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        assertTrue("Expected valid PNG signature for ${file.path}", bytes.take(8).toByteArray().contentEquals(pngSignature))

        return readBigEndianInt(bytes, 16) to readBigEndianInt(bytes, 20)
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun resolveRepoRoot(): File {
        val startDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(startDirectory) { current -> current.parentFile }
            .firstOrNull { candidate -> File(candidate, "settings.gradle.kts").exists() }
            ?: error("Could not locate repo root from ${startDirectory.path}")
    }
}
