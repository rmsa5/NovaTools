package de.langerhans.odintools.tools

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.models.ScreenIdentity
import javax.inject.Inject

/** Reads the identity of the connected external screen through Android's public display APIs. */
class ScreenIdentityReader @Inject constructor(
    @ApplicationContext context: Context,
) {
    val displayManager: DisplayManager = context.getSystemService(DisplayManager::class.java)

    /**
     * The external screen, if one is connected. Physical screens carry identity data (DeviceProductInfo);
     * virtual displays (screen recording, casting) don't, which keeps them out.
     */
    fun externalDisplay(): Display? = displayManager.displays.firstOrNull {
        it.displayId != Display.DEFAULT_DISPLAY && it.deviceProductInfo != null
    }

    fun readExternal(): ScreenIdentity? = externalDisplay()?.let(::read)

    fun read(display: Display): ScreenIdentity {
        val info = display.deviceProductInfo
        val mode = display.mode
        // Only the model year is public API (the manufacture week/year is hidden); -1 means the screen didn't say
        val date = info?.modelYear?.takeIf { it > 0 }?.toString()
        return ScreenIdentity(
            name = info?.name?.takeIf { it.isNotBlank() } ?: display.name,
            manufacturerPnpId = info?.manufacturerPnpId,
            productId = info?.productId,
            manufactureDate = date,
            uniqueId = uniqueIdOf(display),
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            refreshRate = mode.refreshRate,
        )
    }

    // Display.getUniqueId() is hidden API; it's only extra information, so failing to read it is fine
    private fun uniqueIdOf(display: Display): String? = runCatching {
        Display::class.java.getMethod("getUniqueId").invoke(display) as? String
    }.getOrNull()
}
