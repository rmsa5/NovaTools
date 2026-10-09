package de.langerhans.odintools.overrides

import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.data.SharedPrefsRepo.Companion.NO_SATURATION_CHANGE
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor

class ControllerStyleOverride(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
) : DisplayOverride {
    override val id = ID

    override fun target() = prefs.videoOutputControllerStyle?.takeIf { it != ControllerStyle.Unknown.id }

    override fun read() = ControllerStyle.getStyle(executor).id

    override fun write(value: String?) = ControllerStyle.getById(value).enable(executor)

    companion object {
        const val ID = "controller_style"
    }
}

class L2R2StyleOverride(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
) : DisplayOverride {
    override val id = ID

    override fun target() = prefs.videoOutputL2R2Style?.takeIf { it != L2R2Style.Unknown.id }

    override fun read() = L2R2Style.getStyle(executor).id

    override fun write(value: String?) = L2R2Style.getById(value).enable(executor)

    companion object {
        const val ID = "l2r2_style"
    }
}

class AspectRatioOverride(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
) : DisplayOverride {
    override val id = ID

    override fun target() = AspectRatio.getById(prefs.videoOutputAspectRatio).forcedSize

    override fun read() = AspectRatio.getForcedSize(executor) ?: AspectRatio.NATIVE_SIZE

    override fun write(value: String?) = AspectRatio.setForcedSize(executor, value?.takeIf { it != AspectRatio.NATIVE_SIZE })

    companion object {
        const val ID = "aspect_ratio"
    }
}

class SaturationOverride(
    private val settings: SettingsRepo,
    private val prefs: SharedPrefsRepo,
) : DisplayOverride {
    override val id = ID

    override fun target() = prefs.videoOutputSaturation.takeIf { it != NO_SATURATION_CHANGE }?.toString()

    // SurfaceFlinger can't be asked for its saturation, so NovaTools remembers the last value it applied
    override fun read() = settings.currentSaturation.toString()

    override fun write(value: String?) {
        value?.toFloatOrNull()?.let { settings.setSfSaturation(it) }
    }

    companion object {
        const val ID = "saturation"
    }
}
