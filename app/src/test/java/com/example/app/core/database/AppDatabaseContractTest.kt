package com.example.app.core.database

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseContractTest {

    @Test
    fun app_build_gradle_configures_room_compiler_and_schema_export() {
        val gradleFile = File(System.getProperty("user.dir"), "build.gradle.kts")

        assertTrue(gradleFile.exists())
        val source = gradleFile.readText()

        assertTrue(source.contains("kotlin(\"kapt\")"))
        assertTrue(source.contains("kapt(\"androidx.room:room-compiler:2.8.4\")"))
        assertTrue(source.contains("arg(\"room.schemaLocation\""))
    }
}
