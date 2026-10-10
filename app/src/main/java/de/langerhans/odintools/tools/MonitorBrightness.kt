package de.langerhans.odintools.tools

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brightness of external displays that take it over USB (HID "Monitor Control": Apple Studio Display, Studio
 * Display XDR, Pro Display XDR...). Runs the bundled hidfeature tool with root; it finds the display's brightness
 * control by parsing each USB HID device's description, so no model is hard-coded.
 *
 * Values are the display's own units (Apple: about 1/100 nit, 400 to 60000).
 */
@Singleton
class MonitorBrightness @Inject constructor(
    @ApplicationContext context: Context,
    private val executor: ShellExecutor,
) {
    private val tool = File(context.applicationInfo.nativeLibraryDir, "libhidfeature.so").absolutePath

    // Brightness changes run one at a time in the background: waiting for the display's USB side mustn't hold up
    // the other settings
    private val worker = Executors.newSingleThreadExecutor()

    /**
     * Sets the brightness in the background (see [set]). [onPrevious] receives the display's value from just before
     * the change, as soon as its brightness control is found.
     */
    fun setInBackground(value: Int, onPrevious: (Int) -> Unit = {}) {
        worker.execute { set(value, onPrevious) }
    }

    /** The current brightness, or null if no display with a brightness control is connected. */
    fun get(): Int? {
        // Output: "<device> <value> <min> <max>"
        val output = executor.executeAsRoot("$tool monitor get").getOrNull()
        return output?.takeIf { it.startsWith("/dev/") }?.split(" ")?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Sets the brightness. The display's USB side can appear a few seconds after its picture, so this retries
     * for a while. Runs on the caller's thread: call it off the main thread. Returns true once it's set.
     */
    fun set(value: Int, onPrevious: (Int) -> Unit = {}): Boolean {
        repeat(ATTEMPTS) { attempt ->
            // Null until the display's brightness control is there
            val previous = get()
            if (previous != null) {
                onPrevious(previous)
                // Output: "<device> <value>", or an error
                val output = executor.executeAsRoot("$tool monitor set $value").getOrNull()
                if (output?.startsWith("/dev/") == true) {
                    Log.i(TAG, "Brightness set: $output (was $previous)")
                    return true
                }
                Log.w(TAG, "Setting the brightness failed: $output")
            } else if (attempt == 0) {
                Log.i(TAG, "No brightness control yet, retrying")
            }
            Thread.sleep(RETRY_DELAY)
        }
        Log.w(TAG, "No display brightness control found, brightness $value not set")
        return false
    }

    companion object {
        private const val TAG = "MonitorBrightness"
        private const val ATTEMPTS = 16
        private const val RETRY_DELAY = 500L

        /** Apple's units are about 1/100 nit: what the UI shows and presets store. */
        const val UNITS_PER_NIT = 100
        const val MIN_NITS = 4
        const val MAX_NITS = 600
    }
}
