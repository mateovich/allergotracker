package com.example.app.features.ingestion.domain

fun interface CurrentTimeProvider {
    fun nowEpochMillis(): Long
}
