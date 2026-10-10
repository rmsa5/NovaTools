package de.langerhans.odintools.ui.composables

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.langerhans.odintools.R
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.main.CheckboxPreferenceUiModel
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.models.ScreenIdentity
import de.langerhans.odintools.overrides.ActivePreset
import de.langerhans.odintools.overrides.AspectRatioOverride
import de.langerhans.odintools.overrides.BrightnessOverride
import de.langerhans.odintools.overrides.ColorModeOverride
import de.langerhans.odintools.overrides.ControllerStyleOverride
import de.langerhans.odintools.overrides.RefreshRateOverride
import de.langerhans.odintools.overrides.TintOverride
import de.langerhans.odintools.overrides.L2R2StyleOverride
import de.langerhans.odintools.overrides.SaturationOverride
import de.langerhans.odintools.ui.theme.Typography
import kotlin.math.roundToInt

@Composable
fun SettingsHeader(@StringRes name: Int) {
    Text(
        text = stringResource(id = name),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp, top = 8.dp),
    )
}

@Composable
fun PreferenceDescription(@DrawableRes icon: Int, @StringRes title: Int, @StringRes description: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Image(painter = painterResource(id = icon), contentDescription = null)
        Column(
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(
                text = stringResource(id = title),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(id = description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun SwitchPreference(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    state: Boolean,
    onChange: (newValue: Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onChange(state.not())
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreferenceDescription(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(
            checked = state,
            onCheckedChange = {
                onChange(it)
            },
        )
    }
}

@Composable
fun SwitchableTriggerPreference(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    state: Boolean,
    onClick: () -> Unit,
    onChange: (newValue: Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreferenceDescription(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Row(Modifier.height(IntrinsicSize.Min)) {
            VerticalDivider(
                modifier = Modifier
                    .width(17.dp)
                    .padding(end = 16.dp)
                    .fillMaxHeight(),
            )
            Switch(
                checked = state,
                onCheckedChange = {
                    onChange(it)
                },
            )
        }
    }
}

@Composable
fun TriggerPreference(@DrawableRes icon: Int, @StringRes title: Int, @StringRes description: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreferenceDescription(icon = icon, title = title, description = description)
    }
}

@Composable
fun CheckBoxDialogPreference(
    items: List<CheckboxPreferenceUiModel>,
    @StringRes title: Int,
    minSelected: Int = 2,
    onCancel: () -> Unit,
    onSave: (items: List<CheckboxPreferenceUiModel>) -> Unit,
) {
    AlertDialog(onDismissRequest = {}, confirmButton = {
        DialogButton(text = stringResource(id = R.string.save)) {
            onSave(items)
        }
    }, dismissButton = {
        DialogButton(text = stringResource(id = R.string.cancel), onCancel)
    }, title = {
        Text(text = stringResource(id = title))
    }, text = {
        LazyColumn {
            items(items = items, key = { item -> item.key }) { item ->
                fun canChangeCheckbox(): Boolean {
                    return item.checked.not() || items.count { it.checked } == minSelected + 1
                }
                CheckboxDialogRow(
                    text = stringResource(id = item.text),
                    checked = item.checked,
                    enabled = canChangeCheckbox(),
                ) {
                    item.checked = it
                }
            }
        }
    })
}

@Composable
fun CheckboxDialogRow(text: String, enabled: Boolean, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (enabled) onCheckedChange.invoke(!checked)
        },
    ) {
        Text(text = text)
        Spacer(modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** What external screen is connected (from its identity data), its current mode, and the active overrides. */
@Composable
fun ConnectedScreenInfo(
    screen: ScreenIdentity?,
    activePreset: ActivePreset?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Image(painter = painterResource(id = R.drawable.ic_gamepad_docked), contentDescription = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            if (screen == null) {
                Text(text = stringResource(id = R.string.noConnectedScreen))
                return@Column
            }
            Text(text = screen.displayName, modifier = Modifier.padding(bottom = 4.dp))
            val details = listOfNotNull(
                screen.vendorProductId,
                screen.manufactureDate?.let { stringResource(id = R.string.screenMade, it) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = String.format(
                    LocalConfiguration.current.locales[0],
                    "%d × %d @ %.0f Hz",
                    screen.width,
                    screen.height,
                    screen.refreshRate,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (activePreset != null) {
                val presetName = if (activePreset.isDefault) stringResource(id = R.string.defaultPresetName) else activePreset.name
                Text(
                    text = stringResource(id = R.string.presetInEffect, presetName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val labels = activePreset.appliedIds.map { overrideLabel(it) }
                Text(
                    text = if (labels.isEmpty()) {
                        stringResource(id = R.string.noOverridesActive)
                    } else {
                        stringResource(id = R.string.overridesActive, labels.joinToString(", "))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun overrideLabel(id: String) = stringResource(
    id = when (id) {
        ControllerStyleOverride.ID -> R.string.controllerStyle
        L2R2StyleOverride.ID -> R.string.l2r2mode
        AspectRatioOverride.ID -> R.string.aspectRatio
        SaturationOverride.ID -> R.string.saturation
        RefreshRateOverride.ID -> R.string.refreshRate
        ColorModeOverride.ID -> R.string.colorMode
        TintOverride.ID -> R.string.tint
        BrightnessOverride.ID -> R.string.externalBrightness
        else -> R.string.unknown
    },
)

/**
 * Inline saturation setting with a colour reference. Changes apply live while dragging (the reference shows the
 * effect, since SurfaceFlinger's saturation affects everything on screen) and are saved when the slider is released.
 */
@Composable
fun SaturationPreference(
    value: Float,
    deferred: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PreferenceDescription(
                icon = R.drawable.ic_palette,
                title = R.string.saturation,
                description = R.string.saturationDescription,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            )
            TextButton(onClick = onReset) {
                Text(text = stringResource(id = R.string.reset))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                valueRange = 0f..2f,
                steps = 19,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Text(
                text = String.format(LocalConfiguration.current.locales[0], "%.1f", value),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SaturationReference(modifier = Modifier.fillMaxWidth())
        if (deferred) {
            Text(
                text = stringResource(id = R.string.saturationDeferred),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A hue strip and a row of reference swatches (primaries, secondaries, skin tones, neutral grey). */
@Composable
fun SaturationReference(modifier: Modifier = Modifier) {
    val hueStrip = remember { (0..360 step 30).map { Color.hsv((it % 360).toFloat(), 1f, 1f) } }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(hueStrip)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            referenceSwatches.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
        }
    }
}

private val referenceSwatches = listOf(
    Color(0xFFE53935), // red
    Color(0xFF43A047), // green
    Color(0xFF1E88E5), // blue
    Color(0xFFFDD835), // yellow
    Color(0xFF00ACC1), // cyan
    Color(0xFF8E24AA), // magenta
    Color(0xFFF1C27D), // light skin
    Color(0xFFC68642), // medium skin
    Color(0xFF8D5524), // dark skin
    Color(0xFF808080), // neutral grey: shouldn't change with saturation
)

@Composable
fun VibrationPreferenceDialog(initialValue: Int, onCancel: () -> Unit, onSave: (newValue: Int) -> Unit) {
    var userValue: Int by remember {
        mutableIntStateOf(initialValue)
    }

    AlertDialog(onDismissRequest = {}, confirmButton = {
        DialogButton(text = stringResource(id = R.string.save)) {
            onSave(userValue)
        }
    }, dismissButton = {
        DialogButton(text = stringResource(id = R.string.cancel), onCancel)
    }, title = {
        Text(text = stringResource(id = R.string.vibrationStrength))
    }, text = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = userValue.toFloat(),
                valueRange = 1000f..5800f,
                steps = 23,
                onValueChange = {
                    userValue = it.toInt()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
            Text(
                text = "$userValue",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    })
}

@Composable
fun RemapButtonDialog(initialValue: Int, onCancel: () -> Unit, onReset: () -> Unit, onSave: (newValue: Int) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var userValue: Int by remember {
        mutableIntStateOf(initialValue)
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        userValue = it.nativeKeyEvent.keyCode
                    }
                    return@onKeyEvent true
                },
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title
                Text(
                    text = stringResource(id = R.string.remapButton),
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                // Body
                Text(
                    text = stringResource(id = R.string.pressAnyButton),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = KeyEvent.keyCodeToString(userValue),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.padding(12.dp))
                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onReset) {
                        Text(text = stringResource(id = R.string.setDefault))
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                        TextButton(onClick = { onSave(userValue) }) {
                            Text(text = stringResource(id = R.string.save))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun ChargeLimitPreferenceDialog(initialValue: ClosedRange<Int>, onCancel: () -> Unit, onSave: (newValue: ClosedRange<Int>) -> Unit) {
    var userValue by remember {
        mutableStateOf(initialValue.start.toFloat()..initialValue.endInclusive.toFloat())
    }
    val start = userValue.start.roundToInt()
    val end = userValue.endInclusive.roundToInt()

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            DialogButton(text = stringResource(id = R.string.save)) {
                onSave(start..end)
            }
        },
        dismissButton = {
            DialogButton(text = stringResource(id = R.string.cancel), onCancel)
        },
        title = {
            Text(text = stringResource(id = R.string.chargeLimit))
        },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RangeSlider(
                        value = userValue,
                        valueRange = 0f..100f,
                        steps = 9,
                        onValueChange = {
                            userValue = it
                        },
                        modifier = Modifier
                            .weight(1f),
                    )
                }
                Row {
                    Text(text = stringResource(id = R.string.chargeLimitPreferenceDialogOffAt, start))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(id = R.string.chargeLimitPreferenceDialogOnAt, end))
                }
                Row {
                    Text(
                        style = Typography.labelSmall,
                        text = stringResource(id = R.string.chargeLimitPreferenceDialogDescription),
                    )
                }
            }
        },
    )
}

@Composable
fun SpinnerDialogPreference(
    @StringRes label: Int,
    enabled: Boolean = true,
    items: List<Pair<String, String>>,
    selectedKey: String,
    onItemSelected: (selectedItem: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIndex = items.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    val selectedItem = items.getOrNull(selectedIndex)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .padding(8.dp),
    ) {
        OutlinedTextField(
            label = { Text(text = stringResource(id = label)) },
            value = selectedItem?.second ?: "",
            enabled = enabled,
            readOnly = true,
            onValueChange = { },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
            interactionSource = interactionSource,
        )

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect {
                expanded = it is PressInteraction.Release
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach {
                DropdownMenuItem(
                    text = {
                        Text(text = it.second)
                    },
                    onClick = {
                        expanded = false
                        onItemSelected(it.first)
                    },
                )
            }
        }
    }
}
