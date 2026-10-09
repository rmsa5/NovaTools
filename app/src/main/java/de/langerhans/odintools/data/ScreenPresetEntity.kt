package de.langerhans.odintools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.langerhans.odintools.models.DisplayVendors
import de.langerhans.odintools.models.ScreenIdentity

/**
 * Settings to apply while an external screen is connected. Every setting is optional, and null means "track":
 * on a screen preset, the setting follows the default preset; on the default preset, it follows the Nova screen
 * (the value it had before connecting stays in effect).
 */
@Entity(tableName = "screen_preset")
data class ScreenPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Priority in the list, top first (lowest number). The default preset always comes last. */
    val position: Int,
    /** The fallback preset used for any screen no other preset matches. There is exactly one. */
    val isDefault: Boolean = false,
    /** EDID manufacturer code of the matched screen ("APP"), null on the default preset. */
    val manufacturerPnpId: String? = null,
    /** EDID product code as Android reports it (decimal, "44610"), null on the default preset. */
    val productId: String? = null,
    val controllerStyle: String? = null,
    val l2R2Style: String? = null,
    val aspectRatio: String? = null,
    val saturation: Float? = null,
    /** DisplaySettings.REFRESH_60 or REFRESH_120. */
    val refreshRate: String? = null,
    /** DisplaySettings.COLOR_MODE_*. */
    val colorMode: Int? = null,
    /** DisplaySettings.TINT_* or a custom RGB colour. */
    val tint: Int? = null,
) {
    /** True if this preset is for [screen]'s model. The default preset matches no screen in particular. */
    fun matches(screen: ScreenIdentity): Boolean =
        !isDefault && manufacturerPnpId == screen.manufacturerPnpId && productId == screen.productId

    /** This preset's own values, with the [default] preset's values for the settings it tracks. */
    fun withDefaults(default: ScreenPresetEntity): ScreenPresetEntity = if (isDefault) {
        this
    } else {
        copy(
            controllerStyle = controllerStyle ?: default.controllerStyle,
            l2R2Style = l2R2Style ?: default.l2R2Style,
            aspectRatio = aspectRatio ?: default.aspectRatio,
            saturation = saturation ?: default.saturation,
            refreshRate = refreshRate ?: default.refreshRate,
            colorMode = colorMode ?: default.colorMode,
            tint = tint ?: default.tint,
        )
    }

    // A function, not a property: Room must not mistake it for a column
    /** The matched screen in vendor:product notation ("APP:AE42"), null on the default preset. */
    fun vendorProductId(): String? = DisplayVendors.vendorProductId(manufacturerPnpId, productId)
}
