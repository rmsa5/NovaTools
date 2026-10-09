package de.langerhans.odintools.presets

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.overrides.AspectRatioOverride
import de.langerhans.odintools.overrides.ColorModeOverride
import de.langerhans.odintools.overrides.DisplayOverrideManager
import de.langerhans.odintools.overrides.RefreshRateOverride
import de.langerhans.odintools.overrides.SaturationOverride
import de.langerhans.odintools.overrides.TintOverride
import de.langerhans.odintools.tools.DeviceUtils
import de.langerhans.odintools.tools.ScreenIdentityReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreensViewModel @Inject constructor(
    private val screenPresets: ScreenPresetRepository,
    private val displayOverrideManager: DisplayOverrideManager,
    private val screenIdentityReader: ScreenIdentityReader,
    private val prefs: SharedPrefsRepo,
    deviceUtils: DeviceUtils,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScreensUiModel(
            deviceVersion = deviceUtils.getDeviceVersion(),
            novaScreen = NovaScreenValues(saturation = prefs.saturationOverride),
        ),
    )
    val uiState: StateFlow<ScreensUiModel> = _uiState.asStateFlow()

    // Keeps the connected screen and the Nova screen's state up to date while the page is open
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshScreens()
        override fun onDisplayRemoved(displayId: Int) = refreshScreens()
        override fun onDisplayChanged(displayId: Int) = refreshScreens()
    }

    init {
        viewModelScope.launch {
            screenPresets.observeAll().collect { presets -> _uiState.update { it.copy(presets = presets) } }
        }
        viewModelScope.launch {
            displayOverrideManager.active.collect { active ->
                _uiState.update { it.copy(activePreset = active) }
                loadNovaValues()
            }
        }
        screenIdentityReader.displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshScreens()
    }

    override fun onCleared() {
        screenIdentityReader.displayManager.unregisterDisplayListener(displayListener)
    }

    /** Runs again shortly after each change, as a screen's identity can arrive a bit later. */
    private fun refreshScreens() {
        val read = {
            val handheld = screenIdentityReader.displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            _uiState.update {
                it.copy(
                    connectedScreen = screenIdentityReader.readExternal(),
                    handheldScreenOn = handheld?.state == Display.STATE_ON,
                )
            }
        }
        read()
        viewModelScope.launch {
            delay(REFRESH_DELAY)
            read()
        }
    }

    /** Reads the Nova screen's current settings (shell commands, so in the background). */
    private fun loadNovaValues() {
        viewModelScope.launch(Dispatchers.IO) {
            val values = displayOverrideManager.novaValues()
            val nova = NovaScreenValues(
                aspectRatio = if (values[AspectRatioOverride.ID] == AspectRatio.Ratio16x9.forcedSize) {
                    AspectRatio.Ratio16x9
                } else {
                    AspectRatio.Ratio4x3
                },
                refreshRate = values[RefreshRateOverride.ID],
                colorMode = values[ColorModeOverride.ID]?.toIntOrNull(),
                tint = values[TintOverride.ID]?.toIntOrNull(),
                saturation = prefs.saturationOverride,
            )
            _uiState.update { it.copy(novaScreen = nova) }
        }
    }

    fun novaScreenClicked() {
        _uiState.update { it.copy(showNovaDialog = true) }
    }

    fun novaScreenDismissed() {
        _uiState.update { it.copy(showNovaDialog = false) }
    }

    /** Applies the Nova screen's changed settings, in an order where the colour mode comes before the saturation. */
    fun saveNovaScreen(values: NovaScreenValues) {
        val old = _uiState.value.novaScreen
        _uiState.update { it.copy(showNovaDialog = false, novaScreen = values) }
        if (values.aspectRatio != old.aspectRatio) {
            displayOverrideManager.setNovaValue(AspectRatioOverride.ID, values.aspectRatio.forcedSize)
        }
        if (values.refreshRate != old.refreshRate) {
            displayOverrideManager.setNovaValue(RefreshRateOverride.ID, values.refreshRate)
        }
        if (values.colorMode != old.colorMode && values.colorMode != null) {
            displayOverrideManager.setNovaValue(ColorModeOverride.ID, values.colorMode.toString())
        }
        if (values.tint != old.tint && values.tint != null) {
            displayOverrideManager.setNovaValue(TintOverride.ID, values.tint.toString())
        }
        if (values.saturation != old.saturation) {
            prefs.saturationOverride = values.saturation
            displayOverrideManager.setNovaValue(SaturationOverride.ID, values.saturation.toString())
        }
    }

    fun addPresetForConnectedScreen() {
        val screen = _uiState.value.connectedScreen ?: return
        changePresets { screenPresets.addForScreen(screen) }
    }

    fun editClicked(preset: ScreenPresetEntity) {
        _uiState.update { it.copy(editingPreset = preset) }
    }

    fun editDismissed() {
        _uiState.update { it.copy(editingPreset = null) }
    }

    fun saveEdit(preset: ScreenPresetEntity) {
        _uiState.update { it.copy(editingPreset = null) }
        changePresets { screenPresets.save(preset) }
    }

    fun moveUp(preset: ScreenPresetEntity) = moveTo(preset, indexOf(preset) - 1)

    fun moveDown(preset: ScreenPresetEntity) = moveTo(preset, indexOf(preset) + 1)

    fun moveToTop(preset: ScreenPresetEntity) = moveTo(preset, 0)

    private fun indexOf(preset: ScreenPresetEntity) = _uiState.value.orderedPresets.indexOfFirst { it.id == preset.id }

    private fun moveTo(preset: ScreenPresetEntity, index: Int) {
        changePresets { screenPresets.move(preset.id, index) }
    }

    fun deleteClicked(preset: ScreenPresetEntity) {
        _uiState.update { it.copy(presetToDelete = preset) }
    }

    fun deleteDismissed() {
        _uiState.update { it.copy(presetToDelete = null) }
    }

    fun deleteConfirmed() {
        val preset = _uiState.value.presetToDelete ?: return
        _uiState.update { it.copy(presetToDelete = null) }
        changePresets { screenPresets.delete(preset.id) }
    }

    /** Runs a change to the presets, then applies the right preset again if a screen is connected. */
    private fun changePresets(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            displayOverrideManager.reapply()
        }
    }

    companion object {
        private const val REFRESH_DELAY = 1500L
    }
}
