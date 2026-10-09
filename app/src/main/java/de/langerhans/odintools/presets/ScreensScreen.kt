package de.langerhans.odintools.presets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.overrides.AspectRatioOverride
import de.langerhans.odintools.overrides.ColorModeOverride
import de.langerhans.odintools.overrides.RefreshRateOverride
import de.langerhans.odintools.overrides.SaturationOverride
import de.langerhans.odintools.overrides.TintOverride
import de.langerhans.odintools.tools.DisplaySettings
import de.langerhans.odintools.ui.composables.OdinTopAppBar
import de.langerhans.odintools.ui.composables.SettingsHeader

/**
 * The Nova's own screen, then the external screen presets in priority order, the default preset last.
 * Everything works with the Nova's controls: the move buttons and the menu replace drag-and-drop.
 */
@Composable
fun ScreensScreen(viewModel: ScreensViewModel = hiltViewModel()) {
    val uiState: ScreensUiModel by viewModel.uiState.collectAsState()

    uiState.editingPreset?.let { preset ->
        PresetEditorDialog(
            title = presetName(preset),
            trackLabel = stringResource(id = if (preset.isDefault) R.string.trackNovaScreen else R.string.trackDefaultPreset),
            showReset = preset.isDefault,
            initial = preset,
            onCancel = { viewModel.editDismissed() },
            onSave = { viewModel.saveEdit(it) },
        )
    }

    if (uiState.showNovaDialog) {
        // Settings the connected screen's preset changes only apply to the Nova screen on disconnect
        val active = uiState.activePreset
        val deferred = active?.appliedIds.orEmpty().filter { it in NOVA_SETTING_IDS }
        val deferredNote = if (active != null && deferred.isNotEmpty()) {
            val activePreset = uiState.presets.firstOrNull { it.id == active.id }
            stringResource(
                id = R.string.novaDeferredNote,
                activePreset?.let { presetName(it) } ?: active.name,
                deferred.map { settingLabel(it) }.joinToString(", "),
            )
        } else {
            null
        }
        NovaScreenDialog(
            initial = uiState.novaScreen,
            deferredNote = deferredNote,
            onCancel = { viewModel.novaScreenDismissed() },
            onSave = { viewModel.saveNovaScreen(it) },
        )
    }

    uiState.presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { viewModel.deleteDismissed() },
            text = { Text(text = stringResource(id = R.string.deletePresetConfirm, presetName(preset))) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConfirmed() }) {
                    Text(text = stringResource(id = R.string.delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteDismissed() }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
    }

    Scaffold(topBar = { OdinTopAppBar(deviceVersion = uiState.deviceVersion) }) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp), // Extra padding cause of GameAssist bar overlay
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { SettingsHeader(R.string.novaScreen) }
            item { NovaScreenItem(uiState) { viewModel.novaScreenClicked() } }

            item { SettingsHeader(R.string.externalScreens) }
            val screen = uiState.connectedScreen
            if (screen != null && uiState.canAddPresetForConnectedScreen) {
                item {
                    ScreenRow(
                        icon = R.drawable.ic_add,
                        tint = true,
                        onClick = { viewModel.addPresetForConnectedScreen() },
                    ) {
                        Text(text = stringResource(id = R.string.addPresetFor, screen.displayName))
                        Text(
                            text = stringResource(id = R.string.addPresetForDescription),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            val ordered = uiState.orderedPresets
            itemsIndexed(items = ordered, key = { _, preset -> preset.id }) { index, preset ->
                PresetItem(
                    preset = preset,
                    active = uiState.activePreset?.id == preset.id,
                    connected = screen != null && preset.matches(screen),
                    onClick = { viewModel.editClicked(preset) },
                    actions = PresetActions(
                        canMoveUp = index > 0,
                        canMoveDown = index < ordered.lastIndex,
                        onMoveUp = { viewModel.moveUp(preset) },
                        onMoveDown = { viewModel.moveDown(preset) },
                        onMoveToTop = { viewModel.moveToTop(preset) },
                        onDelete = { viewModel.deleteClicked(preset) },
                    ),
                )
            }
            uiState.defaultPreset?.let { preset ->
                item(key = preset.id) {
                    PresetItem(
                        preset = preset,
                        active = uiState.activePreset?.id == preset.id,
                        connected = false,
                        onClick = { viewModel.editClicked(preset) },
                        actions = null,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(id = R.string.screensHint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** The Nova's built-in screen: the baseline that external screen presets are applied on top of. */
@Composable
private fun NovaScreenItem(uiState: ScreensUiModel, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    // A preset applied to a connected screen also changes the Nova's screen if both are on (the setting is global)
    val replacing = uiState.activePreset
        ?.takeIf { SaturationOverride.ID in it.appliedIds }
        ?.let { active -> uiState.presets.firstOrNull { it.id == active.id } }
    val default = uiState.defaultPreset
    val replacingSaturation = replacing?.let { if (default != null) it.withDefaults(default) else it }?.saturation
    val nova = uiState.novaScreen
    val saturation = String.format(locale, "%.1f", nova.saturation)
    ScreenRow(icon = R.drawable.ic_gamepad, onClick = onClick) {
        TitleWithBadge(
            title = stringResource(id = R.string.novaScreen),
            badge = if (uiState.handheldScreenOn) BadgeStyle.Active else null,
        )
        Text(
            text = stringResource(id = R.string.novaScreenDescription),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = listOfNotNull(
                stringResource(id = nova.aspectRatio.textRes),
                refreshRateLabel(nova.refreshRate),
                nova.colorMode?.let { colorModeLabel(it) },
                nova.tint?.let { "${stringResource(id = R.string.tint)}: ${tintLabel(it)}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (replacing != null && replacingSaturation != null) {
                stringResource(
                    id = R.string.novaSaturationReplaced,
                    saturation,
                    presetName(replacing),
                    String.format(locale, "%.1f", replacingSaturation),
                )
            } else {
                stringResource(id = R.string.novaSaturation, saturation)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private class PresetActions(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onMoveToTop: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun PresetItem(
    preset: ScreenPresetEntity,
    active: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
    actions: PresetActions?,
) {
    ScreenRow(
        icon = R.drawable.ic_gamepad_docked,
        onClick = onClick,
        trailing = if (actions != null) {
            { PresetActionButtons(actions) }
        } else {
            null
        },
    ) {
        TitleWithBadge(
            title = presetName(preset),
            badge = when {
                active -> BadgeStyle.Active
                connected -> BadgeStyle.Connected
                else -> null
            },
        )
        Text(
            text = preset.vendorProductId() ?: stringResource(id = R.string.anyScreenDescription),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(text = presetChanges(preset), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PresetActionButtons(actions: PresetActions) {
    IconButton(onClick = actions.onMoveUp, enabled = actions.canMoveUp) {
        Icon(
            painter = painterResource(id = R.drawable.ic_keyboard_arrow_up),
            contentDescription = stringResource(id = R.string.moveUp),
        )
    }
    IconButton(onClick = actions.onMoveDown, enabled = actions.canMoveDown) {
        Icon(
            painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
            contentDescription = stringResource(id = R.string.moveDown),
        )
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_vert),
                contentDescription = stringResource(id = R.string.moreActions),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.moveToTop)) },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_vertical_align_top), contentDescription = null) },
                enabled = actions.canMoveUp,
                onClick = {
                    menuOpen = false
                    actions.onMoveToTop()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.delete)) },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    actions.onDelete()
                },
            )
        }
    }
}

/** One row of the page: an icon, a text column, and optional buttons at the end. */
@Composable
private fun ScreenRow(
    @DrawableRes icon: Int,
    onClick: (() -> Unit)?,
    tint: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            colorFilter = if (tint) ColorFilter.tint(LocalContentColor.current) else null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            content()
        }
        trailing?.invoke()
    }
}

private enum class BadgeStyle { Active, Connected }

@Composable
private fun TitleWithBadge(title: String, badge: BadgeStyle?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text = title, modifier = Modifier.weight(1f, fill = false))
        if (badge != null) {
            val filled = badge == BadgeStyle.Active
            Surface(
                shape = RoundedCornerShape(50),
                color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = stringResource(id = if (filled) R.string.badgeActive else R.string.badgeConnected),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun presetName(preset: ScreenPresetEntity) =
    if (preset.isDefault) stringResource(id = R.string.defaultPresetName) else preset.name

/**
 * The preset's own values, e.g. "Aspect ratio: 16:9 · Display saturation: 1.5". Settings it tracks aren't listed.
 */
@Composable
private fun presetChanges(preset: ScreenPresetEntity): String {
    val controllerStyle = ControllerStyle.getById(preset.controllerStyle).takeUnless { it == ControllerStyle.Unknown }
    val l2R2Style = L2R2Style.getById(preset.l2R2Style).takeUnless { it == L2R2Style.Unknown }
    val aspectRatio = AspectRatio.getById(preset.aspectRatio).takeUnless { it == AspectRatio.Unknown }
    val changes = listOfNotNull(
        controllerStyle?.let { "${stringResource(id = R.string.controllerStyle)}: ${stringResource(id = it.textRes)}" },
        l2R2Style?.let { "${stringResource(id = R.string.l2r2mode)}: ${stringResource(id = it.textRes)}" },
        aspectRatio?.let { "${stringResource(id = R.string.aspectRatio)}: ${stringResource(id = it.textRes)}" },
        preset.saturation?.let {
            "${stringResource(id = R.string.saturation)}: " +
                String.format(LocalConfiguration.current.locales[0], "%.1f", it)
        },
        preset.refreshRate?.let { refreshRateLabel(it) },
        preset.colorMode?.let { "${stringResource(id = R.string.colorMode)}: ${colorModeLabel(it)}" },
        preset.tint?.let { "${stringResource(id = R.string.tint)}: ${tintLabel(it)}" },
    )
    return when {
        changes.isEmpty() && preset.isDefault -> stringResource(id = R.string.tracksNovaScreen)
        changes.isEmpty() -> stringResource(id = R.string.tracksDefaultPreset)
        preset.isDefault -> changes.joinToString(" · ")
        else -> stringResource(id = R.string.ownValuesThenDefault, changes.joinToString(" · "))
    }
}

/** Override ids of the settings the Nova screen has (controller settings aren't screen settings). */
private val NOVA_SETTING_IDS = setOf(
    AspectRatioOverride.ID,
    RefreshRateOverride.ID,
    ColorModeOverride.ID,
    TintOverride.ID,
    SaturationOverride.ID,
)

@Composable
private fun settingLabel(id: String) = stringResource(
    id = when (id) {
        AspectRatioOverride.ID -> R.string.aspectRatio
        RefreshRateOverride.ID -> R.string.refreshRate
        ColorModeOverride.ID -> R.string.colorMode
        TintOverride.ID -> R.string.tint
        else -> R.string.saturation
    },
)

@Composable
private fun refreshRateLabel(value: String?): String? = when (value) {
    DisplaySettings.REFRESH_60 -> stringResource(id = R.string.refresh60)
    DisplaySettings.REFRESH_120 -> stringResource(id = R.string.refresh120)
    else -> null
}

@Composable
private fun colorModeLabel(mode: Int) = stringResource(
    id = when (mode) {
        DisplaySettings.COLOR_MODE_ENHANCED -> R.string.colorModeEnhanced
        DisplaySettings.COLOR_MODE_INTELLIGENT -> R.string.colorModeIntelligent
        else -> R.string.colorModeStandard
    },
)

@Composable
private fun tintLabel(value: Int) = when (value) {
    DisplaySettings.TINT_NATURE -> stringResource(id = R.string.tintNature)
    DisplaySettings.TINT_WARM -> stringResource(id = R.string.tintWarm)
    DisplaySettings.TINT_COOL -> stringResource(id = R.string.tintCool)
    else -> stringResource(id = R.string.tintCustom)
}
