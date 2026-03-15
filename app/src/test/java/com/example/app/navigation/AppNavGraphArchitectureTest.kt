package com.example.app.navigation

import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavGraphArchitectureTest {

    @Test
    fun nav_graph_exposes_records_factory_as_its_only_runtime_dependency() {
        val method = Class.forName("com.example.app.navigation.AppNavGraphKt")
            .declaredMethods
            .first { declaredMethod ->
                declaredMethod.name == "AppNavGraph" &&
                    declaredMethod.parameterTypes.firstOrNull() == ViewModelProvider.Factory::class.java
            }

        assertEquals(ViewModelProvider.Factory::class.java, method.parameterTypes.first())
        assertTrue(method.parameterTypes.none { parameterType ->
            parameterType.name.contains("RepositoryImpl") ||
                parameterType.name.contains("UseCase") ||
                parameterType.name.contains("Dao")
        })
    }
}
