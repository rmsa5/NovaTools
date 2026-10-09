package de.langerhans.odintools.presets

import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.models.ScreenIdentity
import de.langerhans.odintools.overrides.ActivePreset

data class ScreensUiModel(
    val deviceVersion: String = "",
    /** All presets in priority order, the default preset last. */
    val presets: List<ScreenPresetEntity> = emptyList(),
    val connectedScreen: ScreenIdentity? = null,
    val activePreset: ActivePreset? = null,
    val novaScreen: NovaScreenValues = NovaScreenValues(),
    val handheldScreenOn: Boolean = true,
    val showNovaDialog: Boolean = false,
    val editingPreset: ScreenPresetEntity? = null,
    val presetToDelete: ScreenPresetEntity? = null,
) {
    /** The presets the user ordered, without the default preset. */
    val orderedPresets: List<ScreenPresetEntity>
        get() = presets.filterNot { it.isDefault }

    val defaultPreset: ScreenPresetEntity?
        get() = presets.firstOrNull { it.isDefault }

    /** True if a connected screen has no preset of its own yet. */
    val canAddPresetForConnectedScreen: Boolean
        get() = connectedScreen?.let { screen -> presets.none { it.matches(screen) } } ?: false
}
