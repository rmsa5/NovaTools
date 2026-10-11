package de.langerhans.odintools.tools

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.StickLightSettings
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Joystick LEDs that follow the sticks. Runs the bundled stickleds tool in the background with root: it reads the
 * sticks and writes the Nova's two LED driver chips directly (Retroid's own driver can only give all 4 LEDs of a
 * stick one colour).
 *
 * While it runs, Retroid's joystick light setting is switched on at 0 % brightness: Retroid then keeps powering
 * the LEDs (and turns them off with the screen) but shows no colour of its own. Its settings are put back when
 * the feature is switched off.
 */
@Singleton
class StickLights @Inject constructor(
    @ApplicationContext context: Context,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
) {
    private val tool = File(context.applicationInfo.nativeLibraryDir, "libstickleds.so").absolutePath

    // One change at a time, off the main thread
    private val worker = Executors.newSingleThreadExecutor()

    /** Starts, restarts or stops the tool to match [settings]. */
    fun applyInBackground(settings: StickLightSettings = prefs.stickLights) {
        worker.execute { apply(settings) }
    }

    /** When NovaTools starts (after a boot or an update): makes sure the tool runs if the feature is on. */
    fun ensureRunning() {
        if (prefs.stickLights.enabled) applyInBackground()
    }

    private fun apply(settings: StickLightSettings) {
        if (settings.enabled) {
            takeOverFromRetroid()
            start(settings)
        } else {
            stop()
            giveBackToRetroid()
        }
    }

    private fun start(settings: StickLightSettings) {
        val command = (listOf(tool) + settings.toolArguments()).joinToString(" ")
        // Already running with these settings (from this version of the app)
        if (executor.executeAsRoot("pgrep -f -x '$command'").getOrNull()?.isNotBlank() == true) return
        stop()
        // setsid: in its own session, so it outlives the shell that starts it
        executor.executeAsRoot("setsid $command </dev/null >/dev/null 2>&1 &")
        Log.i(TAG, "Started: ${settings.toolArguments().joinToString(" ")}")
    }

    private fun stop() {
        // [s]: so the pattern doesn't match the command line of the shell running pkill
        executor.executeAsRoot("pkill -f 'libstickled[s][.]so'")
        // Lets a stopping instance switch its LEDs off before anything else writes them
        Thread.sleep(STOP_DELAY)
    }

    private fun takeOverFromRetroid() {
        if (prefs.retroidStickLightsBackup == null) {
            prefs.retroidStickLightsBackup = JSONObject()
                .put(KEY_ENABLED, getSetting(KEY_ENABLED) ?: JSONObject.NULL)
                .put(KEY_BRIGHTNESS, getSetting(KEY_BRIGHTNESS) ?: JSONObject.NULL)
                .toString()
        }
        // Only when different: each change makes Retroid rewrite the LEDs
        if (getSetting(KEY_ENABLED) != BOTH_ON) putSetting(KEY_ENABLED, BOTH_ON)
        if (getSetting(KEY_BRIGHTNESS)?.toFloatOrNull() != 0f) putSetting(KEY_BRIGHTNESS, "0.0")
    }

    private fun giveBackToRetroid() {
        val backup = prefs.retroidStickLightsBackup?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        for (key in listOf(KEY_ENABLED, KEY_BRIGHTNESS)) {
            if (backup.isNull(key)) {
                executor.executeAsRoot("settings delete system $key")
            } else {
                putSetting(key, backup.getString(key))
            }
        }
        prefs.retroidStickLightsBackup = null
        Log.i(TAG, "Retroid's joystick light settings restored")
    }

    private fun getSetting(key: String) = executor.executeAsRoot("settings get system $key").getOrNull()

    private fun putSetting(key: String, value: String) {
        executor.executeAsRoot("settings put system $key $value")
    }

    companion object {
        private const val TAG = "StickLights"
        private const val STOP_DELAY = 150L

        // Retroid's settings: "left,right" (0 or 1), and a brightness from 0 to 1
        private const val KEY_ENABLED = "joystick_light_enabled"
        private const val KEY_BRIGHTNESS = "led_light_brightness_percent"
        private const val BOTH_ON = "1,1"
    }
}
