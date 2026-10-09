package de.langerhans.odintools.tools

import de.langerhans.odintools.tools.DeviceType.NOVA
import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.DeviceType.OTHER
import de.langerhans.odintools.tools.DeviceType.RP4
import javax.inject.Inject

class DeviceUtils @Inject constructor(
    private val executor: ShellExecutor,
) {

    fun getDeviceVersion() = when (getDeviceType()) {
        // The Nova firmware doesn't set the Odin 2 OTA property; its firmware version lives in ro.fota.version
        NOVA -> executor.getStringProperty(SettingsRepo.KEY_FOTA_VERSION, "")
        else -> executor.getStringProperty(SettingsRepo.KEY_BUILD_VERSION, "")
    }

    fun getDeviceCodename() = executor.getStringProperty(SettingsRepo.KEY_VENDOR_NAME, "")

    fun getDeviceType(): DeviceType {
        // The Nova doesn't set ro.vendor.retro.name, only ro.vendor.retro.name_sub ("RPN")
        if (executor.getStringProperty(SettingsRepo.KEY_VENDOR_NAME_SUB, "") == "RPN") {
            return NOVA
        }
        return when (getDeviceCodename()) {
            "Q9" -> ODIN2
            "4.0", "4.0P" -> RP4
            else -> OTHER
        }
    }
}

enum class DeviceType {
    ODIN2,
    RP4,
    NOVA,
    OTHER,
}
