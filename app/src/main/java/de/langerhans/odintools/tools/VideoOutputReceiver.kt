package de.langerhans.odintools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.data.SharedPrefsRepo.Companion.NO_SATURATION_CHANGE
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.service.ForegroundAppWatcherService.Companion.OVERRIDE_DELAY
import javax.inject.Inject

@AndroidEntryPoint
class VideoOutputReceiver : BroadcastReceiver() {

    var overrideEnabled = false

    private var savedControllerStyle: ControllerStyle? = null
    private var savedL2R2Style: L2R2Style? = null
    private var savedForcedDisplaySize: String? = null
    private var aspectRatioOverridden = false
    private var saturationOverridden = false

    @Inject
    lateinit var executor: ShellExecutor

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var settings: SettingsRepo

    private fun handleEvent(connected: Boolean?) {
        if (connected == true && !overrideEnabled) {
            // Save default styles
            savedControllerStyle = ControllerStyle.getStyle(executor)
            savedL2R2Style = L2R2Style.getStyle(executor)

            // Apply style profile
            ControllerStyle.getById(prefs.videoOutputControllerStyle).takeIf {
                it != ControllerStyle.Unknown
            }?.enable(executor)
            L2R2Style.getById(prefs.videoOutputL2R2Style).takeIf {
                it != L2R2Style.Unknown
            }?.enable(executor)

            // Aspect ratio: remember the current forced size (null = native) so undocking restores it exactly
            val aspectRatio = AspectRatio.getById(prefs.videoOutputAspectRatio)
            aspectRatioOverridden = aspectRatio != AspectRatio.Unknown
            if (aspectRatioOverridden) {
                savedForcedDisplaySize = AspectRatio.getForcedSize(executor)
                aspectRatio.enable(executor)
            }

            // Saturation: the handheld screen is off while docked, so this only affects the external display
            val saturation = prefs.videoOutputSaturation
            saturationOverridden = saturation != NO_SATURATION_CHANGE
            if (saturationOverridden) {
                settings.setSfSaturation(saturation)
            }

            overrideEnabled = true
        } else if (connected == false && overrideEnabled) {
            // Reset to defaults
            savedControllerStyle?.enable(executor)
            savedL2R2Style?.enable(executor)
            if (aspectRatioOverridden) {
                AspectRatio.setForcedSize(executor, savedForcedDisplaySize)
            }
            if (saturationOverridden) {
                // Back to the handheld saturation from the main "Display saturation" setting
                settings.setSfSaturation(prefs.saturationOverride)
            }

            overrideEnabled = false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_INTENTS) {
            return
        }

        // Log the extras so we can learn what each firmware sends (the system dump hides them)
        val extras = intent.extras
        val extrasText = extras?.keySet()?.joinToString { key ->
            // Bundle.get() is deprecated in favour of typed getters, but here we deliberately log values of unknown type
            @Suppress("DEPRECATION")
            "$key=${extras.get(key)}"
        }
        Log.i(TAG, "Received ${intent.action}, extras: {$extrasText}")

        val connected = if (extras?.containsKey("is_connected") == true) {
            extras.getBoolean("is_connected")
        } else {
            // Fallback for firmwares that don't send is_connected (the Nova's broadcast extras are unknown so far):
            // the Nova exposes the DisplayPort connection state as a system property
            executor.getStringProperty(KEY_DP_CONNECTED, "0") == "1"
        }
        Log.i(TAG, "External display connected: $connected")

        if (prefs.overrideDelay) {
            Handler(Looper.getMainLooper()).postDelayed({ handleEvent(connected) }, OVERRIDE_DELAY)
        } else {
            handleEvent(connected)
        }
    }

    companion object {
        private const val TAG = "VideoOutputReceiver"
        private const val KEY_DP_CONNECTED = "sys.dp.isconnect"

        // Odin 2 / RP4 firmware
        private const val ACTION_DP_STATUS_CHANGED = "com.retrostation.action.DP_STATUS_CHANGED"
        private const val ACTION_HDMI_STATUS_CHANGED = "com.retrostation.action.HDMI_STATUS_CHANGED"
        private const val ACTION_HDMI_OR_DP_STATUS_CHANGED = "com.retrostation.action.HDMI_OR_DP_STATUS_CHANGED"

        // Retroid Pocket Nova firmware: same broadcasts, different prefix
        private const val ACTION_RO_HDMI_STATUS_CHANGED = "com.ro.action.HDMI_STATUS_CHANGED"
        private const val ACTION_RO_HDMI_OR_DP_STATUS_CHANGED = "com.ro.action.HDMI_OR_DP_STATUS_CHANGED"

        val ALLOWED_INTENTS = listOf(
            ACTION_DP_STATUS_CHANGED,
            ACTION_HDMI_STATUS_CHANGED,
            ACTION_HDMI_OR_DP_STATUS_CHANGED,
            ACTION_RO_HDMI_STATUS_CHANGED,
            ACTION_RO_HDMI_OR_DP_STATUS_CHANGED,
        )
    }
}
