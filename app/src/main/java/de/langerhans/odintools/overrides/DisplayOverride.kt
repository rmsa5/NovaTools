package de.langerhans.odintools.overrides

import de.langerhans.odintools.data.ScreenPresetEntity

/**
 * One setting that a screen preset can change while an external display is connected, and that is restored
 * afterwards.
 *
 * Values are plain strings so that [DisplayOverrideManager] can save any setting's previous value in one generic
 * snapshot. Each implementation decides how its value is written as text.
 */
interface DisplayOverride {
    /** Stable key used in the saved snapshot. Never change it once released. */
    val id: String

    /** The value the preset applies, or null when it leaves this setting unchanged. */
    fun target(preset: ScreenPresetEntity): String?

    /** The value currently in effect. */
    fun read(): String?

    /** Puts the given value into effect. */
    fun write(value: String?)
}
