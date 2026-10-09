package de.langerhans.odintools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Settings to apply while a given external screen is connected. Every setting is optional: null means
 * "no change", so the value the Nova had before connecting stays in effect.
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
)
