package io.github.mateovich.allergotracker.features.ingestion.domain

fun interface CurrentTimeProvider {
    fun nowEpochMillis(): Long
}
