package io.github.mateovich.allergotracker.features.compounds.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import kotlin.math.roundToInt

internal const val COMPOUND_TOTAL_BASIS_POINTS: Int = 10_000
internal const val MIN_COMPONENT_SUGGESTION_QUERY_LENGTH: Int = 2
internal const val MAX_VISIBLE_COMPONENT_OPTIONS: Int = 8

enum class CompoundCompositionPreset(
    val wireValue: String,
    val label: String
) {
    UNIFORM(wireValue = "uniform", label = "Uniform"),
    DECREASING(wireValue = "decreasing", label = "Decreasing")
}

data class CompoundComponentCatalogOptionUiModel(
    val id: Long,
    val name: String,
    val kind: String,
    val supportingLabel: String,
    val updatedAtEpochMs: Long
)

data class CompoundComponentRowUiModel(
    val kind: String,
    val reusableEntityId: Long,
    val name: String,
    val proportionBasisPoints: Int? = null,
    val proportionPercent: String = "",
    val proportionError: String? = null,
    val targetError: String? = null,
    val isTouched: Boolean = false
)

data class CompoundProportionAdjustmentResult(
    val values: List<Int>,
    val touchedOrder: List<Int>
)

data class CompoundPresetAllocationResult(
    val values: List<Int>,
    val preset: CompoundCompositionPreset
)

object CompoundProportionAdjustmentEngine {
    fun adjust(
        values: List<Int>,
        touchedOrder: List<Int>,
        index: Int,
        requestedBasisPoints: Int
    ): CompoundProportionAdjustmentResult {
        if (index !in values.indices) {
            return CompoundProportionAdjustmentResult(values = values, touchedOrder = touchedOrder)
        }
        val requestedValue = requestedBasisPoints.coerceIn(0, COMPOUND_TOTAL_BASIS_POINTS)
        val previousValue = values[index]
        val delta = requestedValue - previousValue
        if (delta == 0) {
            return CompoundProportionAdjustmentResult(values = values, touchedOrder = touchedOrder)
        }

        val workingValues = values.toMutableList()
        val normalizedTouchedOrder = touchedOrder.filter { it in values.indices && it != index } + index
        val previouslyTouched = touchedOrder.filter { it in values.indices }.toSet() + index
        var remaining = -delta

        remaining = distributeEqually(
            values = workingValues,
            indices = values.indices.filter { it != index && it !in previouslyTouched },
            remaining = remaining
        )
        remaining = distributeSequentially(
            values = workingValues,
            indices = normalizedTouchedOrder.filter { it != index },
            remaining = remaining
        )

        workingValues[index] = (requestedValue + remaining).coerceIn(0, COMPOUND_TOTAL_BASIS_POINTS)
        normalizeTotal(workingValues)

        return CompoundProportionAdjustmentResult(
            values = workingValues.toList(),
            touchedOrder = normalizedTouchedOrder
        )
    }

    private fun distributeEqually(values: MutableList<Int>, indices: List<Int>, remaining: Int): Int {
        var pending = remaining
        var pool = indices.filter { canAdjust(values[it], pending) }
        while (pool.isNotEmpty() && pending != 0) {
            val baseShare = pending / pool.size
            var remainder = pending % pool.size
            var appliedTotal = 0
            val nextPool = mutableListOf<Int>()

            pool.forEach { rowIndex ->
                var desired = baseShare
                if (remainder != 0) {
                    val step = remainder.sign()
                    desired += step
                    remainder -= step
                }
                val applied = clampDelta(desired, values[rowIndex])
                values[rowIndex] += applied
                appliedTotal += applied
                if (canAdjust(values[rowIndex], pending - appliedTotal)) {
                    nextPool += rowIndex
                }
            }

            if (appliedTotal == 0) {
                break
            }
            pending -= appliedTotal
            pool = nextPool
        }
        return pending
    }

    private fun distributeSequentially(values: MutableList<Int>, indices: List<Int>, remaining: Int): Int {
        var pending = remaining
        indices.forEach { rowIndex ->
            if (pending == 0) {
                return pending
            }
            val applied = clampDelta(pending, values[rowIndex])
            values[rowIndex] += applied
            pending -= applied
        }
        return pending
    }

    private fun clampDelta(delta: Int, currentValue: Int): Int {
        return when {
            delta > 0 -> delta.coerceAtMost(COMPOUND_TOTAL_BASIS_POINTS - currentValue)
            delta < 0 -> delta.coerceAtLeast(-currentValue)
            else -> 0
        }
    }

    private fun canAdjust(currentValue: Int, delta: Int): Boolean {
        return when {
            delta > 0 -> currentValue < COMPOUND_TOTAL_BASIS_POINTS
            delta < 0 -> currentValue > 0
            else -> false
        }
    }

    private fun normalizeTotal(values: MutableList<Int>) {
        if (values.isEmpty()) {
            return
        }
        val difference = COMPOUND_TOTAL_BASIS_POINTS - values.sum()
        if (difference == 0) {
            return
        }
        val targetIndex = values.indices.firstOrNull { index ->
            when {
                difference > 0 -> values[index] < COMPOUND_TOTAL_BASIS_POINTS
                difference < 0 -> values[index] > 0
                else -> false
            }
        } ?: 0
        values[targetIndex] = (values[targetIndex] + difference).coerceIn(0, COMPOUND_TOTAL_BASIS_POINTS)
    }

    private fun Int.sign(): Int {
        return when {
            this > 0 -> 1
            this < 0 -> -1
            else -> 0
        }
    }
}

object CompoundPresetAllocationEngine {
    fun allocate(
        preset: CompoundCompositionPreset,
        componentCount: Int
    ): CompoundPresetAllocationResult? {
        if (componentCount <= 0 || componentCount > COMPOUND_TOTAL_BASIS_POINTS) {
            return null
        }
        val values = when (preset) {
            CompoundCompositionPreset.UNIFORM -> allocateUniform(componentCount)
            CompoundCompositionPreset.DECREASING -> allocateDecreasing(componentCount)
        }
        if (values.size != componentCount || values.any { it !in 1..COMPOUND_TOTAL_BASIS_POINTS } || values.sum() != COMPOUND_TOTAL_BASIS_POINTS) {
            return null
        }
        return CompoundPresetAllocationResult(values = values, preset = preset)
    }

    private fun allocateUniform(componentCount: Int): List<Int> {
        val equalShare = COMPOUND_TOTAL_BASIS_POINTS / componentCount
        val remainder = COMPOUND_TOTAL_BASIS_POINTS % componentCount
        return List(componentCount) { index ->
            equalShare + if (index >= componentCount - remainder) 1 else 0
        }
    }

    private fun allocateDecreasing(componentCount: Int): List<Int> {
        val totalWeight = componentCount * (componentCount + 1) / 2
        val rawValues = (componentCount downTo 1).map { weight ->
            COMPOUND_TOTAL_BASIS_POINTS.toLong() * weight / totalWeight
        }
        val values = rawValues.map { it.toInt().coerceAtLeast(1) }.toMutableList()
        normalizePresetTotal(values)
        return values.toList()
    }

    private fun normalizePresetTotal(values: MutableList<Int>) {
        var difference = COMPOUND_TOTAL_BASIS_POINTS - values.sum()
        var cursor = 0
        while (difference != 0) {
            if (difference > 0) {
                val index = values.indices.firstOrNull { it >= cursor } ?: values.indices.firstOrNull() ?: return
                values[index] += 1
                difference -= 1
                cursor = index + 1
            } else {
                val index = values.indices.firstOrNull { it >= cursor && values[it] > 1 }
                    ?: values.indices.firstOrNull { values[it] > 1 }
                    ?: return
                values[index] -= 1
                difference += 1
                cursor = index + 1
            }
        }
    }
}

internal fun sanitizePercentInput(rawValue: String): String {
    if (rawValue.isBlank()) {
        return ""
    }
    val builder = StringBuilder()
    var decimalFound = false
    var decimals = 0
    rawValue.forEach { character ->
        when {
            character.isDigit() && (!decimalFound || decimals < 2) -> {
                builder.append(character)
                if (decimalFound) {
                    decimals += 1
                }
            }

            character == '.' && !decimalFound -> {
                if (builder.isEmpty()) {
                    builder.append('0')
                }
                builder.append(character)
                decimalFound = true
            }
        }
    }
    return builder.toString()
}

internal fun String.toBasisPointsOrNull(): Int? {
    if (isBlank()) {
        return null
    }
    val percent = toBigDecimalOrNull() ?: return null
    if (percent < BigDecimal.ZERO || percent > BigDecimal("100")) {
        return null
    }
    return try {
        percent.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

internal fun Float.toSliderBasisPointsStep(): Int {
    val basisPoints = (this * 100).roundToInt()
    return (((basisPoints + 5) / 10) * 10).coerceIn(0, COMPOUND_TOTAL_BASIS_POINTS)
}

internal fun Int.toPercentLabel(): String {
    val whole = this / 100
    val fractional = this % 100
    return if (fractional == 0) {
        whole.toString() + "%"
    } else if (fractional % 10 == 0) {
        whole.toString() + "." + (fractional / 10) + "%"
    } else {
        "%d.%02d%%".format(whole, fractional)
    }
}

internal fun Int.toPercentInput(): String {
    return toPercentLabel().removeSuffix("%")
}

internal fun List<CompoundComponentRowUiModel>.hasCompleteValidComposition(): Boolean {
    val totalBasisPoints = sumOf { it.proportionBasisPoints ?: 0 }
    return isNotEmpty() &&
        all { component -> component.proportionBasisPoints in 1..COMPOUND_TOTAL_BASIS_POINTS } &&
        totalBasisPoints == COMPOUND_TOTAL_BASIS_POINTS
}

internal fun List<CompoundComponentRowUiModel>.normalizedValuesForAdjustment(): List<Int> {
    if (isEmpty()) {
        return emptyList()
    }
    val currentValues = map { it.proportionBasisPoints ?: 0 }
    val currentTotal = currentValues.sum()
    if (currentTotal == COMPOUND_TOTAL_BASIS_POINTS) {
        return currentValues
    }
    if (currentTotal == 0) {
        val equalShare = COMPOUND_TOTAL_BASIS_POINTS / size
        val remainder = COMPOUND_TOTAL_BASIS_POINTS - (equalShare * size)
        return mapIndexed { index, _ -> equalShare + if (index == lastIndex) remainder else 0 }
    }
    val result = currentValues.toMutableList()
    val remaining = COMPOUND_TOTAL_BASIS_POINTS - currentTotal
    val zeroValueIndices = indices.filter { currentValues[it] == 0 }
    if (zeroValueIndices.isEmpty()) {
        result[0] += remaining
        return result
    }
    val equalShare = remaining / zeroValueIndices.size
    var applied = 0
    zeroValueIndices.forEachIndexed { order, rowIndex ->
        val share = if (order == zeroValueIndices.lastIndex) {
            remaining - applied
        } else {
            equalShare
        }
        result[rowIndex] += share
        applied += share
    }
    return result
}

internal fun List<CompoundComponentCatalogOptionUiModel>.filterByQuery(query: String): List<CompoundComponentCatalogOptionUiModel> {
    val normalizedQuery = query.toComponentSearchKey()
    if (query.isBlank()) {
        return this
    }
    if (normalizedQuery.length < MIN_COMPONENT_SUGGESTION_QUERY_LENGTH) {
        return emptyList()
    }
    return filter { option ->
        option.name.toComponentSearchKey().contains(normalizedQuery)
    }
}

internal fun List<CompoundComponentCatalogOptionUiModel>.hasExactSavedIngredientMatch(query: String): Boolean {
    val normalizedQuery = query.toComponentSearchKey()
    return normalizedQuery.isNotBlank() && any { option ->
        option.kind == "ingredient" && option.name.toComponentSearchKey() == normalizedQuery
    }
}

internal fun String.toComponentSearchKey(): String {
    val collapsed = trim().replace(Regex("\\s+"), " ").lowercase()
    return Normalizer.normalize(collapsed, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}
