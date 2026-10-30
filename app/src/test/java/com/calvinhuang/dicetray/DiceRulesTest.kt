package com.calvinhuang.dicetray

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRulesTest {
    @Test
    fun rollReturnsRequestedCountAndValidFaces() {
        val results = DiceRules.roll(DiceType.D6, count = 6, random = Random(7))

        assertEquals(6, results.size)
        assertTrue(results.all { it in 1..6 })
    }

    @Test
    fun countIsClampedToMvpLimit() {
        assertEquals(DiceRules.MIN_DICE_COUNT, DiceRules.sanitizeCount(-2))
        assertEquals(DiceRules.MAX_DICE_COUNT, DiceRules.sanitizeCount(99))
    }

    @Test
    fun motionSensitivityUsesFiniteBoundedRange() {
        assertEquals(
            DiceRules.MIN_MOTION_SENSITIVITY,
            DiceRules.sanitizeMotionSensitivity(0.1f),
            0f,
        )
        assertEquals(
            DiceRules.MAX_MOTION_SENSITIVITY,
            DiceRules.sanitizeMotionSensitivity(9f),
            0f,
        )
        assertEquals(
            DiceRules.DEFAULT_MOTION_SENSITIVITY,
            DiceRules.sanitizeMotionSensitivity(Float.NaN),
            0f,
        )
        assertEquals(
            DiceRules.DEFAULT_MOTION_SENSITIVITY,
            DiceRules.sanitizeMotionSensitivity(Float.POSITIVE_INFINITY),
            0f,
        )
        assertEquals(
            DiceRules.DEFAULT_MOTION_SENSITIVITY,
            DiceRules.sanitizeMotionSensitivity(Float.NEGATIVE_INFINITY),
            0f,
        )
    }

    @Test
    fun settingsSanitizationKeepsRendererInputsWithinBounds() {
        val sanitized = DiceSettings(
            count = 99,
            motionSensitivity = -1f,
        ).sanitized()

        assertEquals(DiceRules.MAX_DICE_COUNT, sanitized.count)
        assertEquals(DiceRules.MIN_MOTION_SENSITIVITY, sanitized.motionSensitivity, 0f)
    }

    @Test
    fun rollUsesSelectedDiceSides() {
        val results = DiceRules.roll(DiceType.D20, count = 8, random = Random(9))

        assertEquals(8, results.size)
        assertTrue(results.all { it in 1..20 })
    }

    @Test
    fun everySupportedDiceTypeProducesOnlyValidFaces() {
        DiceType.entries.forEach { type ->
            val results = DiceRules.roll(type, count = DiceRules.MAX_DICE_COUNT, random = Random(type.sides))

            assertEquals(DiceRules.MAX_DICE_COUNT, results.size)
            assertTrue(results.all { it in 1..type.sides })
        }
    }
}
