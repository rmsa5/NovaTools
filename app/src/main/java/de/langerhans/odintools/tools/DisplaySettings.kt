package de.langerhans.odintools.tools

import android.graphics.Color
import java.util.Locale
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Nova's refresh rate, colour mode and tint, set exactly the way Android's own Display pages do, so those
 * pages always show what's in effect.
 *
 * - Refresh rate: the two settings behind Settings > Display > Smooth display.
 * - Colour mode and tint: Android's colour service ("color_display"), like Settings > Display > Colors. The tint is
 *   a vendor addition to that service. Both are saved by the service itself.
 */
class DisplaySettings @Inject constructor(
    private val executor: ShellExecutor,
) {
    /** The refresh rate cap as Android stores it ("120.00001" or "60.0"), or null if never set. */
    fun getRefreshRate(): String? = executor.executeAsRoot("settings get system $KEY_PEAK_REFRESH_RATE").getOrNull()

    /** Writes both limits, like the Smooth display switch. Null removes them (Android's default). */
    fun setRefreshRate(value: String?) {
        if (value == null) {
            executor.executeAsRoot("settings delete system $KEY_MIN_REFRESH_RATE; settings delete system $KEY_PEAK_REFRESH_RATE")
        } else {
            executor.executeAsRoot("settings put system $KEY_MIN_REFRESH_RATE $value; settings put system $KEY_PEAK_REFRESH_RATE $value")
        }
    }

    fun getColorMode(): Int? = parcelInt(executor.executeAsRoot("service call color_display $CALL_GET_COLOR_MODE").getOrNull())

    fun setColorMode(mode: Int) {
        executor.executeAsRoot("service call color_display $CALL_SET_COLOR_MODE i32 $mode")
    }

    fun getTint(): Int? = parcelInt(executor.executeAsRoot("service call color_display $CALL_GET_TINT").getOrNull())

    fun setTint(value: Int) {
        executor.executeAsRoot("service call color_display $CALL_SET_TINT i32 $value")
        // Retroid's Colors page draws its disc marker from a saved position, only for a custom colour
        markerPosition(value)?.let { (x, y) ->
            val text = String.format(Locale.US, "%f,%f", x, y)
            executor.executeAsRoot("settings put system $KEY_TINT_MARKER $text")
        }
    }

    // "Result: Parcel(00000000 ff000000   '........')": the first group is the status, the second the value
    private fun parcelInt(output: String?): Int? = output
        ?.let { PARCEL_INT.find(it) }
        ?.takeIf { it.groupValues[1] == "00000000" }
        ?.groupValues?.get(2)?.toLong(16)?.toInt()

    companion object {
        private const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"
        private const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"
        private const val KEY_TINT_MARKER = "sprd_display_color_temperature_point_coordinate"

        // IColorDisplayManager call numbers on the Nova's firmware (from its framework.jar)
        private const val CALL_SET_TINT = 17
        private const val CALL_GET_TINT = 18
        private const val CALL_GET_COLOR_MODE = 19
        private const val CALL_SET_COLOR_MODE = 20

        private val PARCEL_INT = Regex("""Parcel\(([0-9a-f]{8}) ([0-9a-f]{8})""")

        /** The values Android stores for Smooth display off and on. */
        const val REFRESH_60 = "60.0"
        const val REFRESH_120 = "120.00001"

        const val COLOR_MODE_STANDARD = 0
        const val COLOR_MODE_ENHANCED = 1
        const val COLOR_MODE_INTELLIGENT = 3

        /** Tint values as Retroid's Colors page stores them. A custom tint is a plain RGB colour (no alpha). */
        const val TINT_NATURE = -16777216 // 0xFF000000: no tint
        const val TINT_WARM = -16777215 // A code, not a colour
        const val TINT_COOL = -4398593 // 0xFFBCE1FF

        /** The disc only goes up to half-saturated colours: the strongest tint is a pastel. */
        const val MAX_TINT_SATURATION = 0.498f

        fun isCustomTint(value: Int) = value != TINT_NATURE && value != TINT_WARM && value != TINT_COOL

        /** A custom tint from a hue (0-360) and a strength (0-1, 1 = the disc's edge). */
        fun customTint(hue: Float, strength: Float): Int =
            Color.HSVToColor(floatArrayOf(hue, strength.coerceIn(0f, 1f) * MAX_TINT_SATURATION, 1f)) and 0xFFFFFF

        /** Hue (0-360) and strength (0-1) of a custom tint. */
        fun hueAndStrength(value: Int): Pair<Float, Float> {
            val hsv = FloatArray(3)
            Color.colorToHSV(value or 0xFF000000.toInt(), hsv)
            return hsv[0] to (hsv[1] / MAX_TINT_SATURATION).coerceIn(0f, 1f)
        }

        /**
         * Where Retroid's disc shows a custom tint, as fractions of the picker's width and height (it assumes a
         * square picker). Inverse of the disc's own colour-at-point: the hue is the angle, the strength the distance
         * from the centre. Null for Nature, Warm and Cool, which the page places itself.
         */
        fun markerPosition(value: Int): Pair<Float, Float>? {
            if (!isCustomTint(value)) return null
            val (hue, strength) = hueAndStrength(value)
            // The disc's circle: radius 15/16 of (half the picker minus its stroke)
            val radius = 0.5f * (1f - 1f / 75f) * 15f / 16f
            val angle = Math.toRadians((hue - 180f).toDouble())
            val distance = strength * radius
            return (0.5f + distance * sin(angle).toFloat()) to (0.5f + distance * cos(angle).toFloat())
        }
    }
}
