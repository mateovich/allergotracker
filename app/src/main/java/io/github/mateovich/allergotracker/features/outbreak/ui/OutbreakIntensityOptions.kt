package io.github.mateovich.allergotracker.features.outbreak.ui

internal fun outbreakIntensityOptions(): List<OutbreakIntensityOptionUiModel> {
    return (1..5).map { level ->
        OutbreakIntensityOptionUiModel(level = level, label = "Intensity $level")
    }
}
