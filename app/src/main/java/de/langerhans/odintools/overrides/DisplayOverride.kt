package de.langerhans.odintools.overrides

/**
 * One setting that NovaTools changes while an external display is connected, and restores afterwards.
 *
 * Values are plain strings so that [DisplayOverrideManager] can save any setting's previous value in one generic
 * snapshot. Each implementation decides how its value is written as text.
 */
interface DisplayOverride {
    /** Stable key used in the saved snapshot. Never change it once released. */
    val id: String

    /** The value to apply while connected, or null when the user chose "no change". */
    fun target(): String?

    /** The value currently in effect. */
    fun read(): String?

    /** Puts the given value into effect. */
    fun write(value: String?)
}
