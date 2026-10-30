package com.calvinhuang.dicetray

import kotlin.random.Random

object DiceRules {
    const val MIN_DICE_COUNT = 1
    const val MAX_DICE_COUNT = 10
    const val DEFAULT_DICE_COUNT = 6
    const val DEFAULT_MOTION_SENSITIVITY = 2.2f
    const val MIN_MOTION_SENSITIVITY = 0.5f
    const val MAX_MOTION_SENSITIVITY = 3.0f

    fun sanitizeCount(count: Int): Int = count.coerceIn(MIN_DICE_COUNT, MAX_DICE_COUNT)

    fun sanitizeMotionSensitivity(value: Float): Float =
        if (value.isFinite()) {
            value.coerceIn(MIN_MOTION_SENSITIVITY, MAX_MOTION_SENSITIVITY)
        } else {
            DEFAULT_MOTION_SENSITIVITY
        }

    fun roll(type: DiceType, count: Int, random: Random = Random.Default): List<Int> {
        val safeCount = sanitizeCount(count)
        return List(safeCount) { random.nextInt(from = 1, until = type.sides + 1) }
    }
}

/**
 * Applies every persisted/input boundary in one place before state reaches
 * the renderer or DataStore.
 */
fun DiceSettings.sanitized(): DiceSettings = copy(
    count = DiceRules.sanitizeCount(count),
    motionSensitivity = DiceRules.sanitizeMotionSensitivity(motionSensitivity),
)
