package de.langerhans.odintools.overrides

import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.tools.DisplaySettings
import de.langerhans.odintools.tools.MonitorBrightness
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor

class ControllerStyleOverride(
    private val executor: ShellExecutor,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = preset.controllerStyle?.takeIf { it != ControllerStyle.Unknown.id }

    override fun read() = ControllerStyle.getStyle(executor).id

    override fun write(value: String?) = ControllerStyle.getById(value).enable(executor)

    companion object {
        const val ID = "controller_style"
    }
}

class L2R2StyleOverride(
    private val executor: ShellExecutor,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = preset.l2R2Style?.takeIf { it != L2R2Style.Unknown.id }

    override fun read() = L2R2Style.getStyle(executor).id

    override fun write(value: String?) = L2R2Style.getById(value).enable(executor)

    companion object {
        const val ID = "l2r2_style"
    }
}

class AspectRatioOverride(
    private val executor: ShellExecutor,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = AspectRatio.getById(preset.aspectRatio).forcedSize

    override fun read() = AspectRatio.getForcedSize(executor) ?: AspectRatio.NATIVE_SIZE

    override fun write(value: String?) = AspectRatio.setForcedSize(executor, value?.takeIf { it != AspectRatio.NATIVE_SIZE })

    companion object {
        const val ID = "aspect_ratio"
    }
}

class SaturationOverride(
    private val settings: SettingsRepo,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = preset.saturation?.toString()

    // SurfaceFlinger can't be asked for its saturation, so NovaTools remembers the last value it applied
    override fun read() = settings.currentSaturation.toString()

    override fun write(value: String?) {
        value?.toFloatOrNull()?.let { settings.setSfSaturation(it) }
    }

    companion object {
        const val ID = "saturation"
    }
}

class RefreshRateOverride(
    private val display: DisplaySettings,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = preset.refreshRate

    override fun read() = display.getRefreshRate()

    override fun write(value: String?) = display.setRefreshRate(value)

    companion object {
        const val ID = "refresh_rate"
    }
}

/** Android resets the saturation when the colour mode changes, so NovaTools' saturation is put back right after. */
class ColorModeOverride(
    private val display: DisplaySettings,
    private val settings: SettingsRepo,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = preset.colorMode?.toString()

    override fun read() = display.getColorMode()?.toString()

    override fun write(value: String?) {
        val mode = value?.toIntOrNull() ?: return
        display.setColorMode(mode)
        // Android resets the saturation a moment later, from its own thread: wait for it before putting ours back
        Thread.sleep(SATURATION_RESET_DELAY)
        settings.setSfSaturation(settings.currentSaturation)
    }

    companion object {
        const val ID = "color_mode"
        private const val SATURATION_RESET_DELAY = 500L
    }
}

class TintOverride(
    private val display: DisplaySettings,
) : DisplayOverride {
    override val id = ID

    override fun target(preset: ScreenPresetEntity) = preset.tint?.toString()

    override fun read() = display.getTint()?.toString()

    override fun write(value: String?) {
        value?.toIntOrNull()?.let { display.setTint(it) }
    }

    companion object {
        const val ID = "tint"
    }
}

/**
 * Brightness of the external display itself, for displays that take it over USB. The display keeps the value
 * (even across power cuts), and other computers set their own on connect, so nothing is restored on disconnect.
 */
class BrightnessOverride(
    private val brightness: MonitorBrightness,
    /**
     * Gets the display's value from just before NovaTools changes it. On connect the display's USB side usually
     * isn't there yet when the other settings are saved, so this fills in the value to go back to.
     */
    private val onPreviousValue: (String) -> Unit,
) : DisplayOverride {
    override val id = ID

    override val restoreOnDisconnect = false

    override fun target(preset: ScreenPresetEntity) = preset.brightness?.let { it * MonitorBrightness.UNITS_PER_NIT }?.toString()

    override fun read() = brightness.get()?.toString()

    override fun write(value: String?) {
        value?.toIntOrNull()?.let { brightness.setInBackground(it) { previous -> onPreviousValue(previous.toString()) } }
    }

    companion object {
        const val ID = "brightness"
    }
}
