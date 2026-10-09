package de.langerhans.odintools.models

import androidx.annotation.StringRes
import de.langerhans.odintools.R
import de.langerhans.odintools.tools.ShellExecutor

/**
 * Aspect ratio of what Android draws, and therefore of what a mirrored external display shows.
 *
 * Works like Retroid's "Connected TV" 4:3 / 16:9 toggle, which simply forces Android's display size
 * (the global setting display_size_forced, i.e. "wm size"): 16:9 forces 1080x1920, 4:3 resets to the
 * panel's native size. Sizes are in the panel's portrait orientation, as Android expects.
 */
sealed class AspectRatio(
    val id: String,
    @StringRes val textRes: Int,
) {
    data object Ratio4x3 : AspectRatio("4_3", R.string.aspectRatio4x3)
    data object Ratio16x9 : AspectRatio("16_9", R.string.aspectRatio16x9)
    data object Unknown : AspectRatio("unknown", R.string.unknown)

    fun enable(executor: ShellExecutor) {
        when (this) {
            Ratio16x9 -> setForcedSize(executor, SIZE_16X9)
            Ratio4x3 -> setForcedSize(executor, null)
            Unknown -> Unit
        }
    }

    companion object {
        private const val SIZE_16X9 = "1080x1920"

        /** The currently forced display size (e.g. "1080x1920"), or null if Android uses the panel's native size. */
        fun getForcedSize(executor: ShellExecutor): String? = executor.executeAsRoot("wm size")
            .getOrNull()
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(":")
            ?.trim()

        /** Forces the given display size, or resets to the panel's native size if null. */
        fun setForcedSize(executor: ShellExecutor, size: String?) {
            executor.executeAsRoot(if (size == null) "wm size reset" else "wm size $size")
        }

        fun getById(id: String?) = when (id) {
            Ratio4x3.id -> Ratio4x3
            Ratio16x9.id -> Ratio16x9
            else -> Unknown
        }
    }
}
