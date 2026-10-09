package de.langerhans.odintools.presets

import android.util.Log
import de.langerhans.odintools.data.ScreenPresetDao
import de.langerhans.odintools.data.ScreenPresetEntity
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.data.SharedPrefsRepo.Companion.NO_SATURATION_CHANGE
import de.langerhans.odintools.models.AspectRatio
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.models.ScreenIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Access to the screen presets. Makes sure the default preset exists before anything reads the list. */
@Singleton
class ScreenPresetRepository @Inject constructor(
    private val dao: ScreenPresetDao,
    private val prefs: SharedPrefsRepo,
) {
    private val setupLock = Mutex()

    @Volatile
    private var setupDone = false

    suspend fun getAll(): List<ScreenPresetEntity> {
        ensureDefaultPreset()
        return dao.getAll()
    }

    fun observeAll(): Flow<List<ScreenPresetEntity>> = flow {
        ensureDefaultPreset()
        emitAll(dao.observeAll())
    }

    suspend fun getDefault(): ScreenPresetEntity {
        ensureDefaultPreset()
        return dao.getDefault()
    }

    /** The preset to use for [screen]: the first one from the top that matches it, else the default preset. */
    suspend fun presetFor(screen: ScreenIdentity?): ScreenPresetEntity = getAll().first { preset ->
        preset.isDefault ||
            (
                screen != null &&
                    preset.manufacturerPnpId == screen.manufacturerPnpId &&
                    preset.productId == screen.productId
                )
    }

    suspend fun save(preset: ScreenPresetEntity) {
        dao.update(preset)
        Log.i(TAG, "Saved preset ${describe(preset)}")
    }

    /** Adds a preset for [screen] at the top of the list, so it takes priority. It changes nothing until edited. */
    suspend fun addForScreen(screen: ScreenIdentity): ScreenPresetEntity {
        val others = getAll().filterNot { it.isDefault }
        dao.updateAll(others.mapIndexed { index, preset -> preset.copy(position = index + 1) })
        val preset = ScreenPresetEntity(
            name = screen.displayName,
            position = 0,
            manufacturerPnpId = screen.manufacturerPnpId,
            productId = screen.productId,
        )
        val added = preset.copy(id = dao.insert(preset))
        Log.i(TAG, "Added preset ${describe(added)}")
        return added
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        Log.i(TAG, "Deleted preset #$id")
    }

    /**
     * Creates the default preset on first use. Its settings come from the former "External display override"
     * settings, so updating NovaTools doesn't change what happens when a screen is connected.
     */
    private suspend fun ensureDefaultPreset() {
        if (setupDone) return
        setupLock.withLock {
            if (setupDone) return
            if (dao.countDefault() == 0) {
                val preset = ScreenPresetEntity(
                    name = DEFAULT_PRESET_NAME,
                    position = 0,
                    isDefault = true,
                    controllerStyle = prefs.videoOutputControllerStyle.takeUnless { it == ControllerStyle.Unknown.id },
                    l2R2Style = prefs.videoOutputL2R2Style.takeUnless { it == L2R2Style.Unknown.id },
                    aspectRatio = prefs.videoOutputAspectRatio.takeUnless { it == AspectRatio.Unknown.id },
                    saturation = prefs.videoOutputSaturation.takeUnless { it == NO_SATURATION_CHANGE },
                )
                val id = dao.insert(preset)
                Log.i(TAG, "Created the default preset from the external display override settings: " +
                    describe(preset.copy(id = id)))
            }
            setupDone = true
        }
    }

    companion object {
        private const val TAG = "ScreenPresets"

        /** Stored name of the default preset; the UI shows a translated label instead. */
        const val DEFAULT_PRESET_NAME = "Default (any screen)"

        fun describe(preset: ScreenPresetEntity): String {
            val screen = if (preset.isDefault) "any screen" else "${preset.manufacturerPnpId}:${preset.productId}"
            val settings = listOfNotNull(
                preset.controllerStyle?.let { "controller=$it" },
                preset.l2R2Style?.let { "l2r2=$it" },
                preset.aspectRatio?.let { "aspect=$it" },
                preset.saturation?.let { "saturation=$it" },
            ).ifEmpty { listOf("no changes") }.joinToString(", ")
            return "#${preset.id} \"${preset.name}\" (position ${preset.position}, $screen): $settings"
        }
    }
}
