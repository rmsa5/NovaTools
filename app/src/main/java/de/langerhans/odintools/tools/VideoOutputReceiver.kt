package de.langerhans.odintools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.overrides.DisplayOverrideManager
import de.langerhans.odintools.service.ForegroundAppWatcherService.Companion.OVERRIDE_DELAY
import javax.inject.Inject

/** Listens for the firmware's external display broadcasts and hands them to [DisplayOverrideManager]. */
@AndroidEntryPoint
class VideoOutputReceiver : BroadcastReceiver() {

    @Inject
    lateinit var executor: ShellExecutor

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var overrideManager: DisplayOverrideManager

    private fun handleEvent(connected: Boolean) {
        if (connected) overrideManager.onConnected() else overrideManager.onDisconnected()
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
            // Fallback for firmwares that don't send is_connected: the Nova exposes the DisplayPort state as a property
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
