package com.calvinhuang.dicetray

import androidx.annotation.StringRes

enum class DiceType(
    @StringRes val labelRes: Int,
    val sides: Int,
) {
    D4(R.string.dice_d4, 4),
    D6(R.string.dice_d6, 6),
    D8(R.string.dice_d8, 8),
    D10(R.string.dice_d10, 10),
    D12(R.string.dice_d12, 12),
    D20(R.string.dice_d20, 20),
}

enum class DiceColor(
    @StringRes val labelRes: Int,
    val cssColor: String,
) {
    IVORY(R.string.color_ivory, "#F5F0E4"),
    GREEN(R.string.color_green, "#208C59"),
    RED(R.string.color_red, "#C92E3B"),
    BLUE(R.string.color_blue, "#2D63B9"),
    PURPLE(R.string.color_purple, "#7443C4"),
    BLACK(R.string.color_black, "#191B21"),
}

data class DiceSettings(
    val count: Int = DiceRules.DEFAULT_DICE_COUNT,
    val type: DiceType = DiceType.D6,
    val color: DiceColor = DiceColor.IVORY,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val motionTrailsEnabled: Boolean = true,
    val phoneTiltEnabled: Boolean = false,
    val motionSensitivity: Float = DiceRules.DEFAULT_MOTION_SENSITIVITY,
    val resultChipEnabled: Boolean = true,
)

data class DiceRoll(
    val type: DiceType,
    val results: List<Int>,
) {
    val total: Int
        get() = results.sum()

    val expression: String
        get() = results.joinToString(" + ")
}

data class DiceUiState(
    val settings: DiceSettings = DiceSettings(),
    val lastRoll: DiceRoll? = null,
    val isRolling: Boolean = false,
    val animationRequestId: Long = 0L,
    val clearRequestId: Long = 1L,
    val rendererReady: Boolean = false,
    val rendererError: Boolean = false,
    val rendererReloadRequestId: Long = 0L,
    val showSettings: Boolean = false,
)
