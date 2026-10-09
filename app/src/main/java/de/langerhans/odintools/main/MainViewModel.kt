package de.langerhans.odintools.main

import android.view.KeyEvent
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.ControllerStyle.Disconnect
import de.langerhans.odintools.models.ControllerStyle.Odin
import de.langerhans.odintools.models.ControllerStyle.Xbox
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.models.L2R2Style.Analog
import de.langerhans.odintools.models.L2R2Style.Both
import de.langerhans.odintools.models.L2R2Style.Digital
import de.langerhans.odintools.overrides.DisplayOverrideManager
import de.langerhans.odintools.overrides.SaturationOverride
import de.langerhans.odintools.tools.DeviceType.NOVA
import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.DeviceUtils
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val deviceUtils: DeviceUtils,
    private val executor: ShellExecutor,
    private val settings: SettingsRepo,
    private val prefs: SharedPrefsRepo,
    private val displayOverrideManager: DisplayOverrideManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiModel())

    // Latest saturation to apply while dragging. A StateFlow keeps only the newest value, so a slow shell call
    // never queues up a backlog of intermediate values
    private val saturationPreview = MutableStateFlow<Float?>(null)
    val uiState: StateFlow<MainUiModel> = _uiState.asStateFlow()

    private var _controllerStyleOptions = getCurrentControllerStyles().toMutableStateList()
    val controllerStyleOptions: List<CheckboxPreferenceUiModel>
        get() = _controllerStyleOptions

    private var _l2r2StyleOptions = getCurrentL2r2Styles().toMutableStateList()
    val l2r2StyleOptions: List<CheckboxPreferenceUiModel>
        get() = _l2r2StyleOptions

    init {
        settings.applyRequiredSettings()

        val deviceType = deviceUtils.getDeviceType()

        viewModelScope.launch(Dispatchers.IO) {
            saturationPreview.filterNotNull().collect { settings.setSfSaturation(it) }
        }

        _uiState.update { _ ->
            MainUiModel(
                currentSaturation = prefs.saturationOverride,
                saturationDeferred = displayOverrideManager.isApplied(SaturationOverride.ID),
                deviceType = deviceType,
                deviceVersion = deviceUtils.getDeviceVersion(),
                showIncompatibleDeviceDialog = deviceType != ODIN2 && deviceType != NOVA,
                singlePressHomeEnabled = !settings.preventPressHome,
                showPServerNotAvailableDialog = !executor.pServerAvailable,
                overrideDelayEnabled = prefs.overrideDelay,
                vibrationEnabled = settings.vibrationEnabled,
                chargeLimitEnabled = prefs.chargeLimitEnabled,
                videoOutputOverrideEnabled = prefs.videoOutputOverrideEnabled,
            )
        }
    }

    fun incompatibleDeviceDialogDismissed() {
        _uiState.update { current ->
            current.copy(showIncompatibleDeviceDialog = false)
        }
    }

    fun updateSinglePressHomePreference(newValue: Boolean) {
        // Invert here as prevent == double press
        settings.preventPressHome = !newValue

        _uiState.update { current ->
            current.copy(singlePressHomeEnabled = newValue)
        }
    }

    fun showControllerStylePreference() {
        _controllerStyleOptions = getCurrentControllerStyles().toMutableStateList()
        _uiState.update { current ->
            current.copy(showControllerStyleDialog = true)
        }
    }

    fun hideControllerStylePreference() {
        _uiState.update { current ->
            current.copy(showControllerStyleDialog = false)
        }
    }

    private fun getCurrentControllerStyles(): List<CheckboxPreferenceUiModel> {
        val disabled = prefs.disabledControllerStyle
        return listOf(
            CheckboxPreferenceUiModel(Xbox.id, R.string.xbox, disabled != Xbox.id),
            CheckboxPreferenceUiModel(Odin.id, R.string.odin, disabled != Odin.id),
            CheckboxPreferenceUiModel(Disconnect.id, R.string.disconnect, disabled != Disconnect.id),
        )
    }

    fun updateControllerStyles(models: List<CheckboxPreferenceUiModel>) {
        prefs.disabledControllerStyle = models.find { it.checked.not() }?.key
    }

    fun showL2r2StylePreference() {
        _l2r2StyleOptions = getCurrentL2r2Styles().toMutableStateList()
        _uiState.update {
            it.copy(showL2r2StyleDialog = true)
        }
    }

    fun hideL2r2StylePreference() {
        _uiState.update {
            it.copy(showL2r2StyleDialog = false)
        }
    }

    private fun getCurrentL2r2Styles(): List<CheckboxPreferenceUiModel> {
        val disabled = prefs.disabledL2r2Style
        return listOf(
            CheckboxPreferenceUiModel(Analog.id, R.string.analog, disabled != Analog.id),
            CheckboxPreferenceUiModel(Digital.id, R.string.digital, disabled != Digital.id),
            CheckboxPreferenceUiModel(Both.id, R.string.both, disabled != Both.id),
        )
    }

    fun updateL2r2Styles(models: List<CheckboxPreferenceUiModel>) {
        prefs.disabledL2r2Style = models.find { it.checked.not() }?.key
    }

    /** Live update while dragging: applied right away unless an external display's own saturation is in effect. */
    fun previewSaturation(newValue: Float) {
        val value = roundSaturation(newValue)
        _uiState.update { it.copy(currentSaturation = value) }
        if (!displayOverrideManager.isApplied(SaturationOverride.ID)) {
            saturationPreview.value = value
        }
    }

    /** Saves the value when the slider is released. */
    fun commitSaturation() {
        val value = _uiState.value.currentSaturation
        prefs.saturationOverride = value
        // While docked with a docked saturation, the handheld value is only restored on disconnect:
        // SurfaceFlinger's saturation is global, so applying it now would change the external display
        val deferredUntilDisconnect = displayOverrideManager.updateSavedValue(SaturationOverride.ID, value.toString())
        if (!deferredUntilDisconnect) {
            saturationPreview.value = value
        }
        _uiState.update { it.copy(saturationDeferred = deferredUntilDisconnect) }
    }

    fun resetSaturation() {
        previewSaturation(1.0f)
        commitSaturation()
    }

    // One decimal, matching the slider steps, so stored values compare cleanly (0.7, not 0.70000005)
    private fun roundSaturation(value: Float) = (value * 10).roundToInt() / 10f

    fun updateVibrationPreference(newValue: Boolean) {
        settings.vibrationEnabled = newValue
        _uiState.update {
            it.copy(vibrationEnabled = newValue)
        }
    }

    fun vibrationClicked() {
        _uiState.update {
            it.copy(showVibrationDialog = true, currentVibration = settings.vibrationStrength)
        }
    }

    fun vibrationDialogDismissed() {
        _uiState.update {
            it.copy(showVibrationDialog = false)
        }
    }

    fun saveVibration(newValue: Int) {
        prefs.vibrationStrength = newValue
        settings.vibrationStrength = newValue
        _uiState.update {
            it.copy(showVibrationDialog = false, currentVibration = newValue)
        }
    }

    fun remapButtonClicked(setting: String) {
        _uiState.update {
            it.copy(
                showRemapButtonDialog = true,
                currentButtonSetting = setting,
                currentButtonKeyCode = executor.getIntSystemSetting(setting, 0),
            )
        }
    }

    fun remapButtonDialogDismissed() {
        _uiState.update {
            it.copy(showRemapButtonDialog = false)
        }
    }

    private fun getDefaultKeyCode(setting: String): Int {
        if (setting == SettingsRepo.KEY_CUSTOM_M1_VALUE) {
            return KeyEvent.KEYCODE_BUTTON_C
        }
        if (setting == SettingsRepo.KEY_CUSTOM_M2_VALUE) {
            return KeyEvent.KEYCODE_BUTTON_Z
        }
        return KeyEvent.KEYCODE_UNKNOWN
    }

    fun resetButtonKeyCode(setting: String) {
        val newValue: Int = getDefaultKeyCode(setting)
        executor.setIntSystemSetting(setting, newValue)
        _uiState.update {
            it.copy(showRemapButtonDialog = false)
        }
    }

    fun saveButtonKeyCode(setting: String, newValue: Int) {
        executor.setIntSystemSetting(setting, newValue)
        _uiState.update {
            it.copy(showRemapButtonDialog = false)
        }
    }

    fun updateVideoOutputOverridePreference(newValue: Boolean) {
        prefs.videoOutputOverrideEnabled = newValue
        _uiState.update {
            it.copy(videoOutputOverrideEnabled = newValue)
        }
    }

    fun videoOutputOverrideClicked() {
        _uiState.update {
            it.copy(
                showVideoOutputOverrideDialog = true,
                videoOutputControllerStyle = ControllerStyle.getById(prefs.videoOutputControllerStyle),
                videoOutputL2R2Style = L2R2Style.getById(prefs.videoOutputL2R2Style),
                videoOutputAspectRatio = AspectRatio.getById(prefs.videoOutputAspectRatio),
                videoOutputSaturation = prefs.videoOutputSaturation,
            )
        }
    }

    fun videoOutputOverrideDialogDismissed() {
        _uiState.update {
            it.copy(showVideoOutputOverrideDialog = false)
        }
    }

    fun saveVideoOutputOverride(
        newControllerStyle: ControllerStyle,
        newL2R2Style: L2R2Style,
        newAspectRatio: AspectRatio,
        newSaturation: Float,
    ) {
        prefs.videoOutputControllerStyle = newControllerStyle.id
        prefs.videoOutputL2R2Style = newL2R2Style.id
        prefs.videoOutputAspectRatio = newAspectRatio.id
        prefs.videoOutputSaturation = newSaturation
        _uiState.update {
            it.copy(
                showVideoOutputOverrideDialog = false,
                videoOutputControllerStyle = newControllerStyle,
                videoOutputL2R2Style = newL2R2Style,
                videoOutputAspectRatio = newAspectRatio,
                videoOutputSaturation = newSaturation,
            )
        }
    }

    fun appOverridesEnabled(newValue: Boolean) {
        prefs.appOverridesEnabled = newValue
        _uiState.update {
            it.copy(appOverridesEnabled = newValue)
        }
    }

    fun overrideDelayEnabled(newValue: Boolean) {
        prefs.overrideDelay = newValue
        _uiState.update {
            it.copy(overrideDelayEnabled = newValue)
        }
    }

    fun updateChargeLimitPreference(newValue: Boolean) {
        prefs.chargeLimitEnabled = newValue
        _uiState.update {
            it.copy(chargeLimitEnabled = newValue)
        }
    }

    fun chargeLimitClicked() {
        _uiState.update {
            it.copy(showChargeLimitDialog = true, currentChargeLimit = prefs.minBatteryLevel..prefs.maxBatteryLevel)
        }
    }

    fun chargeLimitDialogDismissed() {
        _uiState.update {
            it.copy(showChargeLimitDialog = false)
        }
    }

    fun saveChargeLimit(newValue: ClosedRange<Int>) {
        prefs.minBatteryLevel = newValue.start
        prefs.maxBatteryLevel = newValue.endInclusive

        _uiState.update {
            it.copy(showChargeLimitDialog = false, currentChargeLimit = newValue)
        }
    }

    fun dumpLogToFile() {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val timeStamp = dateFormat.format(currentDate)
        val directory = "/storage/emulated/0"
        val fileName = "$directory/OdinTools_$timeStamp.log"
        executor
            .executeAsRoot("logcat -d -v threadtime > $fileName")
    }
}
