package de.langerhans.odintools.overrides

import android.util.Log
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the external display overrides on connect and restores the previous values on disconnect.
 *
 * The previous values are saved as a snapshot in the app's preferences, so a restore still works if the app is
 * restarted while connected. On disconnect, a setting is restored if it still has the value NovaTools applied (or
 * already looks restored); if the user changed it to something else while connected, that change is respected.
 */
@Singleton
class DisplayOverrideManager @Inject constructor(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    settings: SettingsRepo,
) {
    private val overrides: List<DisplayOverride> = listOf(
        ControllerStyleOverride(executor, prefs),
        L2R2StyleOverride(executor, prefs),
        AspectRatioOverride(executor, prefs),
        SaturationOverride(settings, prefs),
    )

    /** True while overrides are applied (between a handled connect and the matching disconnect). */
    val isActive: Boolean
        get() = prefs.displayOverrideSnapshot != null

    /** Ids of the overrides currently applied, in their usual order. */
    fun appliedIds(): List<String> {
        val raw = prefs.displayOverrideSnapshot ?: return emptyList()
        val snapshot = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        return overrides.map { it.id }.filter { snapshot.has(it) }
    }

    /** True if the override [id] is currently applied (connected, with a value set for it). */
    fun isApplied(id: String): Boolean {
        val raw = prefs.displayOverrideSnapshot ?: return false
        return runCatching { JSONObject(raw).has(id) }.getOrDefault(false)
    }

    /** Applies the overrides. Returns false if they were already applied (each connect sends several broadcasts). */
    @Synchronized
    fun onConnected(): Boolean {
        if (isActive) return false

        val snapshot = JSONObject()
        overrides.forEach { override ->
            val target = override.target() ?: return@forEach
            val saved = override.read()
            override.write(target)
            snapshot.put(
                override.id,
                JSONObject().put(KEY_SAVED, saved ?: JSONObject.NULL).put(KEY_APPLIED, target),
            )
            Log.i(TAG, "Applied ${override.id}: $saved -> $target")
        }
        prefs.displayOverrideSnapshot = snapshot.toString()
        return true
    }

    @Synchronized
    fun onDisconnected() {
        val raw = prefs.displayOverrideSnapshot ?: return
        val snapshot = runCatching { JSONObject(raw) }.getOrElse {
            Log.w(TAG, "Unreadable snapshot, nothing restored", it)
            prefs.displayOverrideSnapshot = null
            return
        }

        overrides.forEach { override ->
            val entry = snapshot.optJSONObject(override.id) ?: return@forEach
            val applied = entry.optString(KEY_APPLIED)
            val saved = if (entry.isNull(KEY_SAVED)) null else entry.optString(KEY_SAVED)
            val current = override.read()
            if (current == applied || current == saved) {
                // Also written when it already looks restored: harmless, and a reading taken during the display
                // transition can be momentary
                override.write(saved)
                Log.i(TAG, "Restored ${override.id}: $current -> $saved")
            } else {
                Log.i(TAG, "Kept ${override.id} = $current (changed by the user while connected)")
            }
        }
        prefs.displayOverrideSnapshot = null
    }

    /**
     * If the override [id] is currently applied, replaces the value it will restore on disconnect and returns true.
     * Lets the handheld settings be changed while connected without touching what the external display shows.
     */
    @Synchronized
    fun updateSavedValue(id: String, value: String?): Boolean {
        val raw = prefs.displayOverrideSnapshot ?: return false
        val snapshot = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val entry = snapshot.optJSONObject(id) ?: return false
        entry.put(KEY_SAVED, value ?: JSONObject.NULL)
        prefs.displayOverrideSnapshot = snapshot.toString()
        Log.i(TAG, "Updated value to restore for $id: $value")
        return true
    }

    /**
     * Brings the overrides in line with the actual connection, e.g. after the app was restarted or the
     * External override switch was turned on while already connected.
     */
    fun syncWithConnectionState() {
        val connected = executor.getStringProperty(KEY_DP_CONNECTED, "0") == "1"
        Log.i(TAG, "Sync: connected=$connected, active=$isActive")
        when {
            connected && !isActive -> onConnected()
            !connected && isActive -> onDisconnected()
        }
    }

    companion object {
        private const val TAG = "DisplayOverrideManager"
        private const val KEY_SAVED = "saved"
        private const val KEY_APPLIED = "applied"
        private const val KEY_DP_CONNECTED = "sys.dp.isconnect"
    }
}
