package de.langerhans.odintools.sticklights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.StickLightSettings
import de.langerhans.odintools.models.StickStyle
import de.langerhans.odintools.tools.DeviceUtils
import de.langerhans.odintools.tools.StickLights
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StickLightsUiModel(
    val deviceVersion: String,
    val settings: StickLightSettings,
    /** Which stick the style controls edit while the sticks aren't linked. */
    val editingRight: Boolean = false,
) {
    val editedStyle: StickStyle
        get() = if (!settings.linked && editingRight) settings.right else settings.left
}

@HiltViewModel
class StickLightsViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val stickLights: StickLights,
    deviceUtils: DeviceUtils,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StickLightsUiModel(deviceVersion = deviceUtils.getDeviceVersion(), settings = prefs.stickLights),
    )
    val uiState: StateFlow<StickLightsUiModel> = _uiState.asStateFlow()

    private var applyJob: Job? = null

    /**
     * Changes take effect live: saved and applied shortly after the last change (sliders send many), which
     * restarts the stick lights tool with the new settings.
     */
    fun update(change: (StickLightSettings) -> StickLightSettings) {
        val before = _uiState.value.settings
        val after = change(before)
        if (after == before) return
        _uiState.update { it.copy(settings = after) }
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            delay(APPLY_DELAY)
            prefs.stickLights = after
            // Nothing to do while the feature is off and stays off
            if (after.enabled || before.enabled) stickLights.applyInBackground(after)
        }
    }

    /** Changes the style the page currently edits: both sticks when linked, else the chosen one. */
    fun updateStyle(change: (StickStyle) -> StickStyle) {
        val editingRight = _uiState.value.editingRight
        update { if (!it.linked && editingRight) it.copy(right = change(it.right)) else it.copy(left = change(it.left)) }
    }

    /** Unlinking starts the right stick from the shared style. */
    fun setLinked(linked: Boolean) {
        _uiState.update { it.copy(editingRight = false) }
        update { if (linked) it.copy(linked = true) else it.copy(linked = false, right = it.left) }
    }

    fun editStick(right: Boolean) = _uiState.update { it.copy(editingRight = right) }

    fun resetAngles() = update {
        it.copy(tiltLeft = StickLightSettings.DEFAULT_TILT_LEFT, tiltRight = StickLightSettings.DEFAULT_TILT_RIGHT)
    }

    companion object {
        private const val APPLY_DELAY = 300L
    }
}
