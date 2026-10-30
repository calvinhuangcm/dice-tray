package com.calvinhuang.dicetray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calvinhuang.dicetray.AppLanguage
import com.calvinhuang.dicetray.DiceColor
import com.calvinhuang.dicetray.DiceRollerViewModel
import com.calvinhuang.dicetray.DiceRules
import com.calvinhuang.dicetray.DiceSettings
import com.calvinhuang.dicetray.DiceType
import com.calvinhuang.dicetray.DiceUiState

private val Background = Color(0xFF0D1016)
private val Panel = Color(0xFF171B23)
private val PanelRaised = Color(0xFF202630)
private val Accent = Color(0xFFF5B942)

@Composable
fun DiceTrayApp(
    viewModel: DiceRollerViewModel,
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        DiceHome(
            state = state,
            onRoll = viewModel::roll,
            onClear = viewModel::clear,
            onOpenSettings = viewModel::showSettings,
            onRendererReady = viewModel::onRendererReady,
            onRendererError = viewModel::onRendererError,
            onRendererRetry = viewModel::retryRenderer,
            onAnimationFinished = viewModel::onAnimationFinished,
        )

        if (state.showSettings) {
            SettingsSheet(
                settings = state.settings,
                onDismiss = viewModel::hideSettings,
                onCountChanged = viewModel::setCount,
                onTypeChanged = viewModel::setType,
                onColorChanged = viewModel::setColor,
                onSoundChanged = viewModel::setSoundEnabled,
                onVibrationChanged = viewModel::setVibrationEnabled,
                onTrailsChanged = viewModel::setMotionTrailsEnabled,
                onTiltChanged = viewModel::setPhoneTiltEnabled,
                onMotionSensitivityChanged = viewModel::setMotionSensitivity,
                onResultChipChanged = viewModel::setResultChipEnabled,
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
            )
        }
    }
}

@Composable
private fun DiceHome(
    state: DiceUiState,
    onRoll: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onRendererReady: () -> Unit,
    onRendererError: () -> Unit,
    onRendererRetry: () -> Unit,
    onAnimationFinished: (Long) -> Unit,
) {
    val settingsDescription = stringResource(id = com.calvinhuang.dicetray.R.string.open_settings)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        val isWideLandscape = maxWidth >= 600.dp && maxWidth > maxHeight * 1.1f

        if (isWideLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DiceScenePanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    state = state,
                    onRoll = onRoll,
                    onRendererReady = onRendererReady,
                    onRendererError = onRendererError,
                    onRendererRetry = onRendererRetry,
                    onAnimationFinished = onAnimationFinished,
                )
                Column(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 360.dp)
                        .fillMaxHeight(),
                ) {
                    ResultCard(
                        modifier = Modifier.fillMaxWidth(),
                        state = state,
                    )
                    SettingsButton(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 10.dp),
                        description = settingsDescription,
                        onClick = onOpenSettings,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    DiceActionRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        state = state,
                        onRoll = onRoll,
                        onClear = onClear,
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ResultCard(
                        modifier = Modifier.weight(1f),
                        state = state,
                    )
                    SettingsButton(
                        description = settingsDescription,
                        onClick = onOpenSettings,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                DiceScenePanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = state,
                    onRoll = onRoll,
                    onRendererReady = onRendererReady,
                    onRendererError = onRendererError,
                    onRendererRetry = onRendererRetry,
                    onAnimationFinished = onAnimationFinished,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DiceActionRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    state = state,
                    onRoll = onRoll,
                    onClear = onClear,
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(
    modifier: Modifier = Modifier,
    description: String,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(PanelRaised)
            .semantics {
                contentDescription = description
            },
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun DiceScenePanel(
    modifier: Modifier,
    state: DiceUiState,
    onRoll: () -> Unit,
    onRendererReady: () -> Unit,
    onRendererError: () -> Unit,
    onRendererRetry: () -> Unit,
    onAnimationFinished: (Long) -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF111820)),
    ) {
        key(state.rendererReloadRequestId) {
            DiceWebScene(
                modifier = Modifier.fillMaxSize(),
                requestId = state.animationRequestId,
                clearRequestId = state.clearRequestId,
                results = state.lastRoll?.results.orEmpty(),
                sides = state.lastRoll?.type?.sides ?: state.settings.type.sides,
                color = state.settings.color,
                trailsEnabled = state.settings.motionTrailsEnabled,
                tiltEnabled = state.settings.phoneTiltEnabled,
                motionSensitivity = state.settings.motionSensitivity,
                listener = object : DiceRendererListener {
                    override fun onReady() = onRendererReady()
                    override fun onRollFinished(requestId: Long) =
                        onAnimationFinished(requestId)

                    override fun onSceneTapped() = onRoll()

                    override fun onError() = onRendererError()
                },
            )
        }
        if (!state.rendererReady && !state.rendererError) {
            Text(
                text = stringResource(id = com.calvinhuang.dicetray.R.string.renderer_loading),
                color = Color(0xFF8E98A8),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (state.rendererError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = com.calvinhuang.dicetray.R.string.renderer_unavailable),
                    color = Color(0xFFFFC7B9),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRendererRetry) {
                    Text(text = stringResource(id = com.calvinhuang.dicetray.R.string.renderer_retry))
                }
            }
        }
    }
}

@Composable
private fun DiceActionRow(
    modifier: Modifier,
    state: DiceUiState,
    onRoll: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onClear,
            enabled = state.lastRoll != null,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(id = com.calvinhuang.dicetray.R.string.clear))
        }
        Button(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            onClick = onRoll,
            enabled = true,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color(0xFF2B210A),
            ),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isRolling) {
                    stringResource(id = com.calvinhuang.dicetray.R.string.rolling)
                } else {
                    stringResource(id = com.calvinhuang.dicetray.R.string.roll)
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ResultCard(
    modifier: Modifier,
    state: DiceUiState,
) {
    val roll = state.lastRoll
    Surface(
        modifier = modifier,
        color = Panel,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (roll == null || !state.settings.resultChipEnabled) {
                Text(
                    text = stringResource(id = com.calvinhuang.dicetray.R.string.result_ready),
                    color = Color(0xFFD6DCE5),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = stringResource(
                        id = com.calvinhuang.dicetray.R.string.result_expression,
                        roll.expression,
                        roll.total,
                    ),
                    color = if (state.isRolling) Color(0xFFD6DCE5) else Accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = pluralStringResource(
                    com.calvinhuang.dicetray.R.plurals.dice_count,
                    state.settings.count,
                    state.settings.count,
                ) + " · " + stringResource(id = state.settings.type.labelRes),
                color = Color(0xFF8E98A8),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SettingsSheet(
    settings: DiceSettings,
    onDismiss: () -> Unit,
    onCountChanged: (Int) -> Unit,
    onTypeChanged: (DiceType) -> Unit,
    onColorChanged: (DiceColor) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onTrailsChanged: (Boolean) -> Unit,
    onTiltChanged: (Boolean) -> Unit,
    onMotionSensitivityChanged: (Float) -> Unit,
    onResultChipChanged: (Boolean) -> Unit,
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f)),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                color = Panel,
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Text(
                            text = stringResource(id = com.calvinhuang.dicetray.R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(id = com.calvinhuang.dicetray.R.string.close))
                        }
                    }
                    Text(
                        text = stringResource(
                            id = com.calvinhuang.dicetray.R.string.language_note,
                            stringResource(id = selectedLanguage.labelRes),
                        ),
                        color = Color(0xFF9EA7B6),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LanguageSelector(
                        selected = selectedLanguage,
                        onSelected = onLanguageSelected,
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    val decrementDescription = stringResource(
                        id = com.calvinhuang.dicetray.R.string.count_decrease,
                    )
                    SettingLabel(text = stringResource(id = com.calvinhuang.dicetray.R.string.how_many))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onCountChanged(settings.count - 1) },
                            enabled = settings.count > DiceRules.MIN_DICE_COUNT,
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                modifier = Modifier.semantics {
                                    contentDescription = decrementDescription
                                },
                            )
                        }
                        Slider(
                            modifier = Modifier.weight(1f),
                            value = settings.count.toFloat(),
                            onValueChange = { onCountChanged(it.toInt()) },
                            valueRange = DiceRules.MIN_DICE_COUNT.toFloat()..DiceRules.MAX_DICE_COUNT.toFloat(),
                            steps = DiceRules.MAX_DICE_COUNT - DiceRules.MIN_DICE_COUNT - 1,
                        )
                        IconButton(
                            onClick = { onCountChanged(settings.count + 1) },
                            enabled = settings.count < DiceRules.MAX_DICE_COUNT,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(id = com.calvinhuang.dicetray.R.string.count_increase))
                        }
                        Text(
                            text = pluralStringResource(
                                com.calvinhuang.dicetray.R.plurals.dice_count,
                                settings.count,
                                settings.count,
                            ),
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingLabel(text = stringResource(id = com.calvinhuang.dicetray.R.string.dice_type))
                    DiceTypeSelector(selected = settings.type, onSelected = onTypeChanged)

                    Spacer(modifier = Modifier.height(18.dp))
                    SettingLabel(text = stringResource(id = com.calvinhuang.dicetray.R.string.dice_color))
                    ColorSelector(selected = settings.color, onSelected = onColorChanged)

                    Spacer(modifier = Modifier.height(18.dp))
                    Divider(color = Color(0xFF303744))
                    SettingSwitch(
                        label = stringResource(id = com.calvinhuang.dicetray.R.string.result_chip),
                        checked = settings.resultChipEnabled,
                        onCheckedChange = onResultChipChanged,
                    )
                    SettingSwitch(
                        label = stringResource(id = com.calvinhuang.dicetray.R.string.motion_trails),
                        checked = settings.motionTrailsEnabled,
                        onCheckedChange = onTrailsChanged,
                    )
                    SettingSwitch(
                        label = stringResource(id = com.calvinhuang.dicetray.R.string.vibration),
                        checked = settings.vibrationEnabled,
                        onCheckedChange = onVibrationChanged,
                        icon = {
                            Text(
                                text = "✦",
                                color = Color(0xFF9EA7B6),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                    )
                    SettingSwitch(
                        label = stringResource(id = com.calvinhuang.dicetray.R.string.phone_tilt),
                        checked = settings.phoneTiltEnabled,
                        onCheckedChange = onTiltChanged,
                    )
                    SettingLabel(
                        text = stringResource(
                            id = com.calvinhuang.dicetray.R.string.motion_sensitivity,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                id = com.calvinhuang.dicetray.R.string.motion_sensitivity_soft,
                            ),
                            color = Color(0xFF9EA7B6),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            modifier = Modifier.weight(1f),
                            value = settings.motionSensitivity,
                            onValueChange = onMotionSensitivityChanged,
                            valueRange = DiceRules.MIN_MOTION_SENSITIVITY..
                                DiceRules.MAX_MOTION_SENSITIVITY,
                            steps = 24,
                            enabled = settings.phoneTiltEnabled,
                        )
                        Text(
                            text = stringResource(
                                id = com.calvinhuang.dicetray.R.string.motion_sensitivity_value,
                                settings.motionSensitivity,
                            ),
                            color = if (settings.phoneTiltEnabled) Accent else Color(0xFF697384),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        text = stringResource(
                            id = com.calvinhuang.dicetray.R.string.motion_sensitivity_hint,
                        ),
                        color = Color(0xFF9EA7B6),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SettingSwitch(
                        label = stringResource(id = com.calvinhuang.dicetray.R.string.sound),
                        checked = settings.soundEnabled,
                        onCheckedChange = onSoundChanged,
                        icon = {
                            Text(
                                text = "♫",
                                color = Color(0xFF9EA7B6),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingLabel(text = stringResource(id = com.calvinhuang.dicetray.R.string.tips))
                    Text(
                        text = "• " + stringResource(id = com.calvinhuang.dicetray.R.string.tip_roll) +
                            "\n• " + stringResource(id = com.calvinhuang.dicetray.R.string.tip_tilt) +
                            "\n• " + stringResource(id = com.calvinhuang.dicetray.R.string.tip_future),
                        color = Color(0xFFB7BFCC),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFFE8ECF2),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun LanguageSelector(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SettingLabel(text = stringResource(id = com.calvinhuang.dicetray.R.string.language))
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            color = PanelRaised,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = selected.labelRes),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = "⌄", color = Color(0xFFB7BFCC))
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppLanguage.entries.forEach { language ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text = stringResource(id = language.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelected(language)
                    },
                )
            }
        }
    }
    Text(
        text = stringResource(id = com.calvinhuang.dicetray.R.string.language_hint),
        color = Color(0xFF9EA7B6),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun DiceTypeSelector(
    selected: DiceType,
    onSelected: (DiceType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            color = PanelRaised,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(id = selected.labelRes),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DiceType.entries.forEach { type ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text = stringResource(id = type.labelRes)) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorSelector(
    selected: DiceColor,
    onSelected: (DiceColor) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (color in DiceColor.entries) {
            val selectedBorder = if (color == selected) Accent else Color.Transparent
            val colorDescription = stringResource(
                id = com.calvinhuang.dicetray.R.string.selected_color,
                stringResource(id = color.labelRes),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(color.cssColor.toColorInt()))
                    .clickable { onSelected(color) }
                    .semantics {
                        contentDescription = colorDescription
                    },
            ) {
                if (color == selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(2.dp, selectedBorder, CircleShape),
                    )
                }
            }
        }
    }
    Text(
        text = stringResource(
            id = com.calvinhuang.dicetray.R.string.selected_color,
            stringResource(id = selected.labelRes),
        ),
        color = Color(0xFF9EA7B6),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            Text(text = label, color = Color(0xFFE8ECF2))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
