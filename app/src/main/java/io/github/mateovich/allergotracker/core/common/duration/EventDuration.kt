package io.github.mateovich.allergotracker.core.common.duration

const val DURATION_VALIDATION_MESSAGE = "Enter a positive duration in minutes."

fun parseOptionalDurationMinutes(input: String): Int? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val minutes = trimmed.toIntOrNull()
    require(minutes != null && minutes > 0) { DURATION_VALIDATION_MESSAGE }
    return minutes
}

fun formatDurationMinutes(durationMinutes: Int?): String {
    return when (durationMinutes) {
        null -> "None"
        1 -> "1 minute"
        else -> "$durationMinutes minutes"
    }
}
