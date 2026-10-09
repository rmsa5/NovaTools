package de.langerhans.odintools.overrides

/** The screen preset currently in effect, and which of its settings it changed. */
data class ActivePreset(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val appliedIds: List<String>,
)
