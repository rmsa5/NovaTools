package de.langerhans.odintools.models

import android.graphics.Color
import org.json.JSONObject
import java.util.Locale

/**
 * How one stick's LEDs light up. Colours are hue (degrees) and saturation (0 to 1), always at full value: the
 * brightness is separate.
 */
data class StickStyle(
    val hue: Float = 177f,
    val saturation: Float = 0.8f,
    val colourFollowsPush: Boolean = false,
    val fullHue: Float = 0f,
    val fullSaturation: Float = 1f,
    val brightness: Float = 0.6f,
    val brightnessFollowsPush: Boolean = true,
    val beam: Float = 2f,
    val opposite: Boolean = false,
) {
    val colour: Int get() = StickLightSettings.colourOf(hue, saturation)
    val fullColour: Int get() = StickLightSettings.colourOf(fullHue, fullSaturation)

    /** The stickleds tool's per-stick options. */
    fun toolArguments(): List<String> = buildList {
        add("-c"); add(hex(colour))
        if (colourFollowsPush) { add("-C"); add(hex(fullColour)) }
        add("-k"); add(String.format(Locale.US, "%.1f", beam))
        add("-b"); add(String.format(Locale.US, "%.2f", brightness))
        if (!brightnessFollowsPush) add("-n")
        if (opposite) add("-o")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("hue", hue.toDouble())
        .put("saturation", saturation.toDouble())
        .put("colourFollowsPush", colourFollowsPush)
        .put("fullHue", fullHue.toDouble())
        .put("fullSaturation", fullSaturation.toDouble())
        .put("brightness", brightness.toDouble())
        .put("brightnessFollowsPush", brightnessFollowsPush)
        .put("beam", beam.toDouble())
        .put("opposite", opposite)

    companion object {
        private fun hex(colour: Int) = String.format(Locale.US, "%06x", colour and 0xFFFFFF)

        fun fromJson(o: JSONObject?): StickStyle {
            val defaults = StickStyle()
            if (o == null) return defaults
            fun float(key: String, default: Float) = o.optDouble(key, default.toDouble()).toFloat()
            return StickStyle(
                hue = float("hue", defaults.hue),
                saturation = float("saturation", defaults.saturation),
                colourFollowsPush = o.optBoolean("colourFollowsPush", defaults.colourFollowsPush),
                fullHue = float("fullHue", defaults.fullHue),
                fullSaturation = float("fullSaturation", defaults.fullSaturation),
                brightness = float("brightness", defaults.brightness),
                brightnessFollowsPush = o.optBoolean("brightnessFollowsPush", defaults.brightnessFollowsPush),
                beam = float("beam", defaults.beam),
                opposite = o.optBoolean("opposite", defaults.opposite),
            )
        }
    }
}

/**
 * Joystick LEDs following the sticks. With [linked], [left] applies to both sticks; otherwise each stick has its
 * own style. The tilts turn each stick's cross of 4 LEDs (degrees, anticlockwise), as it's not quite aligned with
 * the stick's axes.
 */
data class StickLightSettings(
    val enabled: Boolean = false,
    val linked: Boolean = true,
    val left: StickStyle = StickStyle(),
    val right: StickStyle = StickStyle(),
    val tiltLeft: Float = DEFAULT_TILT_LEFT,
    val tiltRight: Float = DEFAULT_TILT_RIGHT,
) {
    /** The style the right stick actually uses. */
    val effectiveRight: StickStyle get() = if (linked) left else right

    /** The stickleds tool's options for these settings. */
    fun toolArguments(): List<String> = buildList {
        add("-l"); add(String.format(Locale.US, "%.1f", tiltLeft))
        add("-r"); add(String.format(Locale.US, "%.1f", tiltRight))
        if (linked) {
            addAll(left.toolArguments())
        } else {
            add("-s"); add("l"); addAll(left.toolArguments())
            add("-s"); add("r"); addAll(right.toolArguments())
        }
    }

    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("linked", linked)
        .put("left", left.toJson())
        .put("right", right.toJson())
        .put("tiltLeft", tiltLeft.toDouble())
        .put("tiltRight", tiltRight.toDouble())
        .toString()

    companion object {
        /** Measured on Louis's Nova; other units may differ a little, hence the settings. */
        const val DEFAULT_TILT_LEFT = 10f
        const val DEFAULT_TILT_RIGHT = -15f

        fun colourOf(hue: Float, saturation: Float): Int = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))

        fun fromJson(json: String?): StickLightSettings {
            val defaults = StickLightSettings()
            val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return defaults
            // First version: one style at the top level, for both sticks
            val left = StickStyle.fromJson(o.optJSONObject("left") ?: o)
            val right = o.optJSONObject("right")?.let { StickStyle.fromJson(it) } ?: left
            return StickLightSettings(
                enabled = o.optBoolean("enabled", defaults.enabled),
                linked = o.optBoolean("linked", defaults.linked),
                left = left,
                right = right,
                tiltLeft = o.optDouble("tiltLeft", defaults.tiltLeft.toDouble()).toFloat(),
                tiltRight = o.optDouble("tiltRight", defaults.tiltRight.toDouble()).toFloat(),
            )
        }
    }
}
