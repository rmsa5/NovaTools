package de.langerhans.odintools.presets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.langerhans.odintools.R
import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.tools.DisplaySettings
import de.langerhans.odintools.tools.MonitorBrightness
import de.langerhans.odintools.ui.composables.SpinnerDialogPreference
import kotlin.math.roundToInt

/** The Nova screen's own values, as currently in effect for it. */
data class NovaScreenValues(
    val aspectRatio: AspectRatio = AspectRatio.Ratio4x3,
    val refreshRate: String? = null,
    val colorMode: Int? = null,
    val tint: Int? = null,
    val saturation: Float = 1.0f,
)

// Key used in the choice lists for "track" (same as the default preset / the Nova screen)
private const val TRACK = "track"

/** Edits a screen preset, or the default preset: every setting is either tracked or given its own value. */
@Composable
fun PresetEditorDialog(
    title: String,
    trackLabel: String,
    showReset: Boolean,
    initial: ScreenPresetEntity,
    onCancel: () -> Unit,
    onSave: (ScreenPresetEntity) -> Unit,
) {
    var controllerStyle by remember { mutableStateOf(initial.controllerStyle ?: ControllerStyle.Unknown.id) }
    var l2R2Style by remember { mutableStateOf(initial.l2R2Style ?: L2R2Style.Unknown.id) }
    var aspectRatio by remember { mutableStateOf(initial.aspectRatio ?: AspectRatio.Unknown.id) }
    var refreshRate by remember { mutableStateOf(initial.refreshRate ?: TRACK) }
    var colorMode by remember { mutableStateOf(initial.colorMode?.toString() ?: TRACK) }
    var tint by remember { mutableStateOf(TintChoice.from(initial.tint)) }
    var trackSaturation by remember { mutableStateOf(initial.saturation == null) }
    var saturation by remember { mutableFloatStateOf(initial.saturation ?: 1.0f) }
    var trackBrightness by remember { mutableStateOf(initial.brightness == null) }
    var brightness by remember { mutableFloatStateOf((initial.brightness ?: DEFAULT_BRIGHTNESS).toFloat()) }

    SettingsDialogFrame(
        title = title,
        onCancel = onCancel,
        onReset = if (showReset) {
            {
                controllerStyle = ControllerStyle.Unknown.id
                l2R2Style = L2R2Style.Unknown.id
                aspectRatio = AspectRatio.Unknown.id
                refreshRate = TRACK
                colorMode = TRACK
                tint = TintChoice.from(null)
                trackSaturation = true
                trackBrightness = true
            }
        } else {
            null
        },
        onSave = {
            onSave(
                initial.copy(
                    controllerStyle = controllerStyle.takeUnless { it == ControllerStyle.Unknown.id },
                    l2R2Style = l2R2Style.takeUnless { it == L2R2Style.Unknown.id },
                    aspectRatio = aspectRatio.takeUnless { it == AspectRatio.Unknown.id },
                    refreshRate = refreshRate.takeUnless { it == TRACK },
                    colorMode = colorMode.takeUnless { it == TRACK }?.toIntOrNull(),
                    tint = tint.value,
                    saturation = saturation.takeUnless { trackSaturation },
                    brightness = brightness.roundToInt().takeUnless { trackBrightness },
                ),
            )
        },
    ) {
        SpinnerDialogPreference(
            label = R.string.controllerStyle,
            items = listOf(ControllerStyle.Unknown.id to trackLabel) +
                listOf(ControllerStyle.Odin, ControllerStyle.Xbox, ControllerStyle.Disconnect)
                    .map { it.id to stringResource(id = it.textRes) },
            selectedKey = controllerStyle,
        ) { controllerStyle = it }
        SpinnerDialogPreference(
            label = R.string.l2r2mode,
            items = listOf(L2R2Style.Unknown.id to trackLabel) +
                listOf(L2R2Style.Analog, L2R2Style.Digital, L2R2Style.Both)
                    .map { it.id to stringResource(id = it.textRes) },
            selectedKey = l2R2Style,
        ) { l2R2Style = it }
        SpinnerDialogPreference(
            label = R.string.aspectRatio,
            items = listOf(AspectRatio.Unknown.id to trackLabel) + aspectRatioChoices(),
            selectedKey = aspectRatio,
        ) { aspectRatio = it }
        SpinnerDialogPreference(
            label = R.string.refreshRate,
            items = listOf(TRACK to trackLabel) + refreshRateChoices(),
            selectedKey = refreshRate,
        ) { refreshRate = it }
        SpinnerDialogPreference(
            label = R.string.colorMode,
            items = listOf(TRACK to trackLabel) + colorModeChoices(),
            selectedKey = colorMode,
        ) { colorMode = it }
        TintChooser(choice = tint, trackLabel = trackLabel) { tint = it }
        Text(text = stringResource(id = R.string.saturation), modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = trackLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = trackSaturation, onCheckedChange = { trackSaturation = it })
        }
        SaturationSlider(value = saturation, enabled = !trackSaturation) { saturation = it }
        // The display's own brightness, for displays that take it over USB. For the default preset, "track" means
        // leaving the display's brightness as it is
        Text(text = stringResource(id = R.string.externalBrightness), modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (initial.isDefault) stringResource(id = R.string.brightnessUnchanged) else trackLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = trackBrightness, onCheckedChange = { trackBrightness = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Slider(
                value = brightness,
                valueRange = MonitorBrightness.MIN_NITS.toFloat()..MonitorBrightness.MAX_NITS.toFloat(),
                enabled = !trackBrightness,
                // Steps of 5 nits, but the minimum (4) stays reachable
                onValueChange = { brightness = maxOf(MonitorBrightness.MIN_NITS.toFloat(), (it / 5f).roundToInt() * 5f) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
            Text(
                text = stringResource(id = R.string.nitsValue, brightness.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = stringResource(id = R.string.externalBrightnessNote),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private const val DEFAULT_BRIGHTNESS = 200

/**
 * Edits the Nova screen's own settings. They apply right away, except the ones a connected screen's preset
 * changes: those apply on disconnect, which [deferredNote] explains when there are any.
 */
@Composable
fun NovaScreenDialog(
    initial: NovaScreenValues,
    deferredNote: String?,
    onCancel: () -> Unit,
    onSave: (NovaScreenValues) -> Unit,
) {
    var aspectRatio by remember { mutableStateOf(initial.aspectRatio.id) }
    var refreshRate by remember { mutableStateOf(initial.refreshRate ?: DisplaySettings.REFRESH_120) }
    var colorMode by remember { mutableStateOf((initial.colorMode ?: DisplaySettings.COLOR_MODE_STANDARD).toString()) }
    var tint by remember { mutableStateOf(TintChoice.from(initial.tint ?: DisplaySettings.TINT_NATURE)) }
    var saturation by remember { mutableFloatStateOf(initial.saturation) }

    SettingsDialogFrame(
        title = stringResource(id = R.string.novaScreen),
        onCancel = onCancel,
        onReset = null,
        onSave = {
            onSave(
                NovaScreenValues(
                    aspectRatio = AspectRatio.getById(aspectRatio),
                    refreshRate = refreshRate,
                    colorMode = colorMode.toIntOrNull(),
                    tint = tint.value,
                    saturation = saturation,
                ),
            )
        },
    ) {
        SpinnerDialogPreference(
            label = R.string.aspectRatio,
            items = aspectRatioChoices(),
            selectedKey = aspectRatio,
        ) { aspectRatio = it }
        SpinnerDialogPreference(
            label = R.string.refreshRate,
            items = refreshRateChoices(),
            selectedKey = refreshRate,
        ) { refreshRate = it }
        SpinnerDialogPreference(
            label = R.string.colorMode,
            items = colorModeChoices(),
            selectedKey = colorMode,
        ) {
            colorMode = it
            // Screen enhancement is Android's standard colours at saturation 1.1: show that on the slider
            if (it == DisplaySettings.COLOR_MODE_ENHANCED.toString()) saturation = ENHANCED_SATURATION
        }
        TintChooser(choice = tint, trackLabel = null) { tint = it }
        Text(text = stringResource(id = R.string.saturation), modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        SaturationSlider(value = saturation, enabled = true) { saturation = it }
        if (deferredNote != null) {
            Text(
                text = deferredNote,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }
    }
}

private const val ENHANCED_SATURATION = 1.1f

@Composable
private fun aspectRatioChoices() = listOf(AspectRatio.Ratio4x3, AspectRatio.Ratio16x9)
    .map { it.id to stringResource(id = it.textRes) }

@Composable
private fun refreshRateChoices() = listOf(
    DisplaySettings.REFRESH_60 to stringResource(id = R.string.refresh60),
    DisplaySettings.REFRESH_120 to stringResource(id = R.string.refresh120),
)

@Composable
private fun colorModeChoices() = listOf(
    DisplaySettings.COLOR_MODE_STANDARD.toString() to stringResource(id = R.string.colorModeStandard),
    DisplaySettings.COLOR_MODE_ENHANCED.toString() to stringResource(id = R.string.colorModeEnhanced),
    DisplaySettings.COLOR_MODE_INTELLIGENT.toString() to stringResource(id = R.string.colorModeIntelligent),
)

/** A tint choice: tracked (null value), Nature, Warm, Cool, or a custom colour from a hue and a strength. */
data class TintChoice(val key: String, val hue: Float = 30f, val strength: Float = 0.5f) {
    val value: Int?
        get() = when (key) {
            NATURE -> DisplaySettings.TINT_NATURE
            WARM -> DisplaySettings.TINT_WARM
            COOL -> DisplaySettings.TINT_COOL
            CUSTOM -> DisplaySettings.customTint(hue, strength)
            else -> null
        }

    companion object {
        const val NATURE = "nature"
        const val WARM = "warm"
        const val COOL = "cool"
        const val CUSTOM = "custom"

        fun from(value: Int?): TintChoice = when (value) {
            null -> TintChoice(TRACK)
            DisplaySettings.TINT_NATURE -> TintChoice(NATURE)
            DisplaySettings.TINT_WARM -> TintChoice(WARM)
            DisplaySettings.TINT_COOL -> TintChoice(COOL)
            else -> DisplaySettings.hueAndStrength(value).let { (hue, strength) -> TintChoice(CUSTOM, hue, strength) }
        }
    }
}

@Composable
private fun TintChooser(choice: TintChoice, trackLabel: String?, onChange: (TintChoice) -> Unit) {
    val items = listOfNotNull(
        trackLabel?.let { TRACK to it },
        TintChoice.NATURE to stringResource(id = R.string.tintNature),
        TintChoice.WARM to stringResource(id = R.string.tintWarm),
        TintChoice.COOL to stringResource(id = R.string.tintCool),
        TintChoice.CUSTOM to stringResource(id = R.string.tintCustom),
    )
    SpinnerDialogPreference(label = R.string.tint, items = items, selectedKey = choice.key) {
        onChange(choice.copy(key = it))
    }
    if (choice.key == TintChoice.CUSTOM) {
        val tintColor = Color(DisplaySettings.customTint(choice.hue, choice.strength) or 0xFF000000.toInt())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                // The hue strip, at the disc's strongest (pastel) saturation
                val hues = remember {
                    (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, DisplaySettings.MAX_TINT_SATURATION, 1f) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(hues)),
                )
                LabeledSlider(R.string.tintHue, choice.hue, 0f..360f, "%.0f°") { onChange(choice.copy(hue = it)) }
                LabeledSlider(R.string.tintStrength, choice.strength * 100f, 0f..100f, "%.0f %%") {
                    onChange(choice.copy(strength = it / 100f))
                }
            }
            // What white becomes
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tintColor),
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(id = label),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 8.dp),
        )
        Slider(value = value, valueRange = range, onValueChange = onChange, modifier = Modifier.weight(1f))
        Text(
            text = String.format(LocalConfiguration.current.locales[0], format, value),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SaturationSlider(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Slider(
            value = value,
            valueRange = 0f..2f,
            steps = 19,
            enabled = enabled,
            onValueChange = { onChange((it * 10).roundToInt() / 10f) },
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
        )
        Text(
            text = String.format(LocalConfiguration.current.locales[0], "%.1f", value),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** A scrollable dialog with a title, the given settings, and Reset (optional) / Cancel / Save buttons. */
@Composable
private fun SettingsDialogFrame(
    title: String,
    onCancel: () -> Unit,
    onReset: (() -> Unit)?,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    // Tall: scrolls on the Nova's landscape screen
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                content()
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onReset != null) {
                        TextButton(onClick = onReset) { Text(text = stringResource(id = R.string.reset)) }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text(text = stringResource(id = R.string.cancel)) }
                    TextButton(onClick = onSave) { Text(text = stringResource(id = R.string.save)) }
                }
            }
        }
    }
}
