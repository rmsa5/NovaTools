package de.langerhans.odintools.sticklights

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.models.StickLightSettings
import de.langerhans.odintools.ui.composables.OdinTopAppBar
import de.langerhans.odintools.ui.composables.SettingsHeader
import de.langerhans.odintools.ui.composables.SwitchPreference
import kotlin.math.roundToInt

/** Joystick LEDs that follow the sticks. Every change applies live. */
@Composable
fun StickLightsScreen(viewModel: StickLightsViewModel = hiltViewModel()) {
    val uiState: StickLightsUiModel by viewModel.uiState.collectAsState()
    val s = uiState.settings
    val style = uiState.editedStyle

    Scaffold(topBar = { OdinTopAppBar(deviceVersion = uiState.deviceVersion) }) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .padding(end = 8.dp) // Extra padding cause of GameAssist bar overlay
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchPreference(
                icon = R.drawable.ic_gamepad,
                title = R.string.stickLightsFollow,
                description = R.string.stickLightsFollowDescription,
                state = s.enabled,
            ) { on -> viewModel.update { it.copy(enabled = on) } }

            SwitchRow(R.string.stickLightsLinked, s.linked) { viewModel.setLinked(it) }
            if (!s.linked) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(
                        selected = !uiState.editingRight,
                        onClick = { viewModel.editStick(right = false) },
                        label = { Text(text = stringResource(id = R.string.stickLightsLeftStick)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = uiState.editingRight,
                        onClick = { viewModel.editStick(right = true) },
                        label = { Text(text = stringResource(id = R.string.stickLightsRightStick)) },
                    )
                }
            }

            SettingsHeader(R.string.stickLightsColour)
            ColourSliders(hue = style.hue, saturation = style.saturation) { hue, saturation ->
                viewModel.updateStyle { it.copy(hue = hue, saturation = saturation) }
            }
            SwitchRow(R.string.stickLightsColourFollowsPush, style.colourFollowsPush) { on ->
                viewModel.updateStyle { it.copy(colourFollowsPush = on) }
            }
            if (style.colourFollowsPush) {
                Text(
                    text = stringResource(id = R.string.stickLightsFullPushColour),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
                ColourSliders(hue = style.fullHue, saturation = style.fullSaturation) { hue, saturation ->
                    viewModel.updateStyle { it.copy(fullHue = hue, fullSaturation = saturation) }
                }
            }

            SettingsHeader(R.string.stickLightsBrightness)
            LabeledSlider(R.string.stickLightsMaxBrightness, style.brightness * 100f, 5f..100f, "%.0f %%") { value ->
                viewModel.updateStyle { it.copy(brightness = value.roundToInt() / 100f) }
            }
            SwitchRow(R.string.stickLightsBrightnessFollowsPush, style.brightnessFollowsPush) { on ->
                viewModel.updateStyle { it.copy(brightnessFollowsPush = on) }
            }

            SettingsHeader(R.string.stickLightsBeam)
            LabeledSlider(R.string.stickLightsBeamWidth, style.beam, 1f..8f, "%.1f") { value ->
                viewModel.updateStyle { it.copy(beam = (value * 2).roundToInt() / 2f) }
            }
            SwitchRow(R.string.stickLightsOpposite, style.opposite) { on -> viewModel.updateStyle { it.copy(opposite = on) } }

            SettingsHeader(R.string.stickLightsAngles)
            Text(
                text = stringResource(id = R.string.stickLightsAnglesDescription),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LabeledSlider(R.string.stickLightsLeftStick, s.tiltLeft, -45f..45f, "%+.0f°") { value ->
                viewModel.update { it.copy(tiltLeft = value.roundToInt().toFloat()) }
            }
            LabeledSlider(R.string.stickLightsRightStick, s.tiltRight, -45f..45f, "%+.0f°") { value ->
                viewModel.update { it.copy(tiltRight = value.roundToInt().toFloat()) }
            }
            TextButton(onClick = { viewModel.resetAngles() }, modifier = Modifier.padding(start = 8.dp)) {
                Text(text = stringResource(id = R.string.stickLightsResetAngles))
            }

            // Navigation bar padding
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}

/** Hue and saturation sliders, with a strip of the hues and a swatch of the colour. */
@Composable
private fun ColourSliders(hue: Float, saturation: Float, onChange: (hue: Float, saturation: Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            val hues = remember { (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(hues)),
            )
            LabeledSlider(R.string.stickLightsHue, hue, 0f..360f, "%.0f°") { onChange(it.roundToInt().toFloat(), saturation) }
            LabeledSlider(R.string.stickLightsSaturation, saturation * 100f, 0f..100f, "%.0f %%") {
                onChange(hue, it.roundToInt() / 100f)
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(StickLightSettings.colourOf(hue, saturation))),
        )
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Slider(value = value, valueRange = range, onValueChange = onChange, modifier = Modifier.weight(1f))
        Text(
            text = String.format(LocalConfiguration.current.locales[0], format, value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(text = stringResource(id = label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
