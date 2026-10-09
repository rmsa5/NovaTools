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

    /** The forced size this ratio applies ([NATIVE_SIZE] = the panel's own size), or null for no change. */
    val forcedSize: String?
        get() = when (this) {
            Ratio16x9 -> SIZE_16X9
            Ratio4x3 -> NATIVE_SIZE
            Unknown -> null
        }

    companion object {
        private const val SIZE_16X9 = "1080x1920"

        /** Stands for "no forced size": Android uses the panel's native size (4:3 on the Nova). */
        const val NATIVE_SIZE = "native"

        /**
         * The forced display size (e.g. "1080x1920"), or null if Android uses the panel's native size.
         *
         * Reads the value Android stores (global setting display_size_forced, "1080,1920" or empty) rather than
         * asking "wm size": while an external display is being removed, "wm size" can briefly report the native
         * size before Android settles back on the stored one.
         */
        fun getForcedSize(executor: ShellExecutor): String? = executor.executeAsRoot("settings get global display_size_forced")
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "null" }
            ?.replace(',', 'x')

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
