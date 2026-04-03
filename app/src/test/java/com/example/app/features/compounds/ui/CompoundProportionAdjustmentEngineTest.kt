package com.example.app.features.compounds.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompoundProportionAdjustmentEngineTest {

    @Test
    fun untouched_components_absorb_adjustment_before_touched_fifo_rows() {
        val result = CompoundProportionAdjustmentEngine.adjust(
            values = listOf(4_000, 3_000, 3_000),
            touchedOrder = listOf(1),
            index = 0,
            requestedBasisPoints = 5_000
        )

        assertEquals(listOf(5_000, 3_000, 2_000), result.values)
        assertEquals(listOf(1, 0), result.touchedOrder)
    }

    @Test
    fun touched_rows_fall_back_in_fifo_order_when_untouched_capacity_is_exhausted() {
        val result = CompoundProportionAdjustmentEngine.adjust(
            values = listOf(2_000, 3_000, 5_000),
            touchedOrder = listOf(1, 2),
            index = 0,
            requestedBasisPoints = 7_000
        )

        assertEquals(listOf(7_000, 0, 3_000), result.values)
        assertEquals(listOf(1, 2, 0), result.touchedOrder)
    }

    @Test
    fun impossible_requests_are_clamped_without_leaving_total_outside_100_percent() {
        val result = CompoundProportionAdjustmentEngine.adjust(
            values = listOf(9_500, 500),
            touchedOrder = emptyList(),
            index = 1,
            requestedBasisPoints = 2_000
        )

        assertEquals(listOf(8_000, 2_000), result.values)
        assertEquals(10_000, result.values.sum())
    }

    @Test
    fun decimal_input_rounds_and_normalizes_back_to_exact_100_percent() {
        val result = CompoundProportionAdjustmentEngine.adjust(
            values = listOf(3_333, 3_333, 3_334),
            touchedOrder = emptyList(),
            index = 0,
            requestedBasisPoints = 3_334
        )

        assertEquals(10_000, result.values.sum())
        assertEquals(listOf(3_334, 3_332, 3_334), result.values)
    }

    @Test
    fun repeated_edits_keep_deterministic_fifo_redistribution() {
        val first = CompoundProportionAdjustmentEngine.adjust(
            values = listOf(6_000, 2_500, 1_500),
            touchedOrder = listOf(1, 2),
            index = 0,
            requestedBasisPoints = 5_500
        )

        val second = CompoundProportionAdjustmentEngine.adjust(
            values = first.values,
            touchedOrder = first.touchedOrder,
            index = 0,
            requestedBasisPoints = 5_000
        )

        assertEquals(listOf(5_500, 3_000, 1_500), first.values)
        assertEquals(listOf(1, 2, 0), first.touchedOrder)
        assertEquals(listOf(5_000, 3_500, 1_500), second.values)
        assertEquals(listOf(1, 2, 0), second.touchedOrder)
        assertEquals(10_000, second.values.sum())
    }
}
