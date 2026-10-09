package de.langerhans.odintools.overrides

import android.util.Log
import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.ScreenIdentity
import de.langerhans.odintools.presets.ScreenPresetRepository
import de.langerhans.odintools.tools.DisplaySettings
import de.langerhans.odintools.tools.ScreenIdentityReader
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the screen preset matching the connected external display, and restores the previous values on
 * disconnect.
 *
 * On connect, the current value of every setting is saved in a snapshot (kept in the app's preferences, so a
 * restore still works if the app is restarted while connected). The preset is then applied, and can be switched
 * live with [reapply]: a setting the new preset doesn't change goes back to its saved value. On disconnect, a
 * setting is restored if it still has the value the preset applied (or already looks restored); if the user
 * changed it to something else while connected, that change is respected.
 *
 * All the work runs in the background, one request at a time and in order, so the several broadcasts of a
 * single connection, or a disconnect arriving during a connect, can't overlap.
 */
@Singleton
class DisplayOverrideManager @Inject constructor(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    settings: SettingsRepo,
    display: DisplaySettings,
    private val presets: ScreenPresetRepository,
    private val screenIdentityReader: ScreenIdentityReader,
) {
    private val overrides: List<DisplayOverride> = listOf(
        ControllerStyleOverride(executor),
        L2R2StyleOverride(executor),
        AspectRatioOverride(executor),
        RefreshRateOverride(display),
        // Before the saturation: a colour mode change resets it
        ColorModeOverride(display, settings),
        TintOverride(display),
        SaturationOverride(settings),
    )

    // Guards the snapshot between the requests and the UI (updateSavedValue)
    private val lock = Any()

    private val _active = MutableStateFlow(loadSnapshot()?.toActivePreset())

    // Requests run one at a time, strictly in the order they were made, each to its end (including any wait for
    // the screen's identity) before the next one starts
    private val requests = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (request in requests) {
                runCatching { request() }.onFailure { Log.e(TAG, "Request failed", it) }
            }
        }
    }

    private fun enqueue(request: suspend () -> Unit) {
        requests.trySend(request)
    }

    /** The preset in effect while connected, or null when nothing is applied. */
    val active: StateFlow<ActivePreset?> = _active.asStateFlow()

    /** True while a preset is applied (between a handled connect and the matching disconnect). */
    val isActive: Boolean
        get() = _active.value != null

    /** Ids of the settings the active preset changed, in their usual order. */
    fun appliedIds(): List<String> = _active.value?.appliedIds.orEmpty()

    /** True if the active preset changed the setting [id]. */
    fun isApplied(id: String): Boolean = id in appliedIds()

    // Counts disconnect requests, so a connect still waiting for the screen's identity knows the screen is gone.
    // The firmware's broadcast says "connected" a moment before sys.dp.isconnect does, so the broadcast is trusted
    @Volatile
    private var disconnectCount = 0

    fun onConnected() {
        val disconnectsSoFar = disconnectCount
        enqueue { connect(disconnectsSoFar) }
    }

    fun onDisconnected() {
        disconnectCount++
        enqueue { disconnect() }
    }

    /** Applies the right preset again, after the presets were edited, added, removed or reordered. */
    fun reapply() {
        enqueue { reapplyNow() }
    }

    /**
     * Brings the presets in line with the actual connection, e.g. after the app was restarted or the
     * feature was turned on while already connected.
     */
    fun syncWithConnectionState() {
        enqueue {
            val connected = isConnected()
            Log.i(TAG, "Sync: connected=$connected, active=$isActive")
            when {
                connected && !isActive -> connect(disconnectCount)
                !connected && isActive -> disconnect()
            }
        }
    }

    /**
     * While connected, replaces the value restored on disconnect for the setting [id]. Returns true if the active
     * preset changes that setting, meaning the new value must wait for the disconnect: applying it now would change
     * what the external display shows.
     */
    fun updateSavedValue(id: String, value: String?): Boolean {
        synchronized(lock) {
            val snapshot = loadSnapshot() ?: return false
            val entry = snapshot.entries[id] ?: return false
            entry.saved = value
            saveSnapshot(snapshot)
            Log.i(TAG, "Updated value to restore for $id: $value")
            return entry.applied != null
        }
    }

    /**
     * The Nova screen's own value for each setting, by override id: the value saved on connect while a preset
     * changes it, otherwise the value in effect. Runs shell commands: call it off the main thread.
     */
    fun novaValues(): Map<String, String?> = synchronized(lock) {
        val snapshot = loadSnapshot()
        overrides.associate { override ->
            val entry = snapshot?.entries?.get(override.id)
            override.id to if (entry?.applied != null) entry.saved else override.read()
        }
    }

    /**
     * Sets the Nova screen's own value for a setting: right away, or on disconnect if the connected screen's
     * preset changes that setting (applying it now would change what the external screen shows).
     */
    fun setNovaValue(id: String, value: String?) {
        enqueue {
            val deferred = updateSavedValue(id, value)
            if (!deferred) overrides.firstOrNull { it.id == id }?.write(value)
            Log.i(TAG, "Nova screen $id = $value" + if (deferred) " (applies on disconnect)" else "")
        }
    }

    private suspend fun connect(disconnectsSoFar: Int) {
        if (isActive) return // Each connection sends several broadcasts

        val screen = waitForScreen { disconnectCount != disconnectsSoFar }
        if (disconnectCount != disconnectsSoFar) {
            Log.i(TAG, "Disconnected before anything was applied")
            return
        }
        Log.i(TAG, "Connected screen: ${screen ?: "not identified, using the default preset"}")
        val preset = presets.presetFor(screen)
        val values = presets.effectiveValues(preset)

        synchronized(lock) {
            val entries = overrides.associate { it.id to Entry(saved = it.read(), applied = null) }
            val snapshot = Snapshot(preset.id, preset.name, preset.isDefault, entries.toMutableMap())
            apply(snapshot, preset, values)
            saveSnapshot(snapshot)
        }
    }

    private suspend fun reapplyNow() {
        if (!isActive) return
        val preset = presets.presetFor(screenIdentityReader.readExternal())
        val values = presets.effectiveValues(preset)
        synchronized(lock) {
            val snapshot = loadSnapshot() ?: return
            apply(snapshot, preset, values)
            saveSnapshot(snapshot)
        }
    }

    private fun disconnect() {
        synchronized(lock) { restoreAll() }
    }

    private fun restoreAll() {
        val snapshot = loadSnapshot() ?: return
        Log.i(TAG, "Disconnected, restoring after preset \"${snapshot.presetName}\"")
        overrides.forEach { override ->
            val entry = snapshot.entries[override.id] ?: return@forEach
            val applied = entry.applied ?: return@forEach // Left unchanged by the preset
            val current = override.read()
            if (current == applied || current == entry.saved) {
                // Also written when it already looks restored: harmless, and a reading taken during the display
                // transition can be momentary
                override.write(entry.saved)
                Log.i(TAG, "Restored ${override.id}: $current -> ${entry.saved}")
            } else {
                Log.i(TAG, "Kept ${override.id} = $current (changed by the user while connected)")
            }
        }
        clearSnapshot()
    }

    /**
     * Puts [preset] into effect on top of the values saved in [snapshot], switching from any previous preset.
     * [values] are the preset's own values completed with the default preset's, for the settings it tracks.
     */
    private fun apply(snapshot: Snapshot, preset: ScreenPresetEntity, values: ScreenPresetEntity) {
        Log.i(TAG, "Using preset ${ScreenPresetRepository.describe(preset)}")
        if (values != preset) Log.i(TAG, "With the default preset's values: ${ScreenPresetRepository.describe(values)}")
        snapshot.presetId = preset.id
        snapshot.presetName = preset.name
        snapshot.presetIsDefault = preset.isDefault
        overrides.forEach { override ->
            val entry = snapshot.entries.getOrPut(override.id) { Entry(saved = override.read(), applied = null) }
            val target = override.target(values)
            val previouslyApplied = entry.applied
            when {
                target != null -> {
                    override.write(target)
                    entry.applied = target
                    Log.i(TAG, "Applied ${override.id} = $target (restores ${entry.saved})")
                }
                previouslyApplied != null -> {
                    // The previous preset changed this setting, the new one doesn't: back to the saved value,
                    // unless the user changed it in the meantime
                    val current = override.read()
                    if (current == previouslyApplied) {
                        override.write(entry.saved)
                        Log.i(TAG, "Reset ${override.id}: $current -> ${entry.saved}")
                    } else {
                        Log.i(TAG, "Kept ${override.id} = $current (changed by the user while connected)")
                    }
                    entry.applied = null
                }
            }
        }
    }

    /**
     * Waits for the screen's identity, which Android only reports a moment after the connect broadcast.
     * Returns null if the screen isn't identified in time, or as soon as [disconnected] says it's gone.
     */
    private suspend fun waitForScreen(disconnected: () -> Boolean): ScreenIdentity? {
        repeat(IDENTITY_ATTEMPTS) {
            screenIdentityReader.readExternal()?.let { return it }
            if (disconnected()) return null
            delay(IDENTITY_POLL_INTERVAL)
        }
        return null
    }

    private fun isConnected() = executor.getStringProperty(KEY_DP_CONNECTED, "0") == "1"

    private class Entry(var saved: String?, var applied: String?)

    private class Snapshot(
        var presetId: Long,
        var presetName: String,
        var presetIsDefault: Boolean,
        val entries: MutableMap<String, Entry>,
    ) {
        fun toActivePreset() = ActivePreset(
            id = presetId,
            name = presetName,
            isDefault = presetIsDefault,
            appliedIds = entries.filterValues { it.applied != null }.keys.toList(),
        )
    }

    private fun loadSnapshot(): Snapshot? {
        val raw = prefs.displayOverrideSnapshot ?: return null
        val json = runCatching { JSONObject(raw) }.getOrElse {
            Log.w(TAG, "Unreadable snapshot, ignored", it)
            return null
        }
        // Snapshots saved before presets existed hold the values directly, without the preset part
        val preset = json.optJSONObject(KEY_PRESET)
        val values = json.optJSONObject(KEY_VALUES) ?: json
        val entries = mutableMapOf<String, Entry>()
        overrides.forEach { override ->
            val entry = values.optJSONObject(override.id) ?: return@forEach
            entries[override.id] = Entry(
                saved = if (entry.isNull(KEY_SAVED)) null else entry.optString(KEY_SAVED),
                applied = if (entry.isNull(KEY_APPLIED)) null else entry.optString(KEY_APPLIED),
            )
        }
        return Snapshot(
            presetId = preset?.optLong(KEY_ID, UNKNOWN_PRESET_ID) ?: UNKNOWN_PRESET_ID,
            presetName = preset?.optString(KEY_NAME) ?: ScreenPresetRepository.DEFAULT_PRESET_NAME,
            presetIsDefault = preset?.optBoolean(KEY_DEFAULT) ?: true,
            entries = entries,
        )
    }

    private fun saveSnapshot(snapshot: Snapshot) {
        val values = JSONObject()
        snapshot.entries.forEach { (id, entry) ->
            values.put(
                id,
                JSONObject()
                    .put(KEY_SAVED, entry.saved ?: JSONObject.NULL)
                    .put(KEY_APPLIED, entry.applied ?: JSONObject.NULL),
            )
        }
        val preset = JSONObject()
            .put(KEY_ID, snapshot.presetId)
            .put(KEY_NAME, snapshot.presetName)
            .put(KEY_DEFAULT, snapshot.presetIsDefault)
        prefs.displayOverrideSnapshot = JSONObject().put(KEY_PRESET, preset).put(KEY_VALUES, values).toString()
        _active.value = snapshot.toActivePreset()
    }

    private fun clearSnapshot() {
        prefs.displayOverrideSnapshot = null
        _active.value = null
    }

    companion object {
        private const val TAG = "DisplayOverrideManager"
        private const val KEY_PRESET = "preset"
        private const val KEY_VALUES = "values"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_DEFAULT = "default"
        private const val KEY_SAVED = "saved"
        private const val KEY_APPLIED = "applied"
        private const val KEY_DP_CONNECTED = "sys.dp.isconnect"
        private const val UNKNOWN_PRESET_ID = -1L

        // Up to 5 seconds for Android to report the screen's identity
        private const val IDENTITY_ATTEMPTS = 20
        private const val IDENTITY_POLL_INTERVAL = 250L
    }
}
