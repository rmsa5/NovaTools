package de.langerhans.odintools.tools

import javax.inject.Inject

/**
 * The Nova's own screen while an external screen is connected.
 *
 * Retroid's firmware turns the Nova's screen off on connect when its setting "close_screen_when_output_video" is on
 * (the default), and applies any change to that setting immediately, even mid-connection. "Off" only cuts the
 * backlight: Android still reports the display as on, so the real state is read from the backlight itself.
 */
class HandheldScreen @Inject constructor(
    private val executor: ShellExecutor,
) {
    /** Retroid's setting: turn the Nova's screen off while an external screen is connected (absent = on). */
    fun getTurnOffWhenConnected(): Boolean =
        executor.executeAsRoot("settings get system $KEY_TURN_OFF").getOrNull()?.trim() != "0"

    /** Changes Retroid's setting. Retroid applies it straight away if a screen is connected. */
    fun setTurnOffWhenConnected(turnOff: Boolean) {
        executor.executeAsRoot("settings put system $KEY_TURN_OFF ${if (turnOff) 1 else 0}")
    }

    /** Whether the Nova's backlight is on, or null if it can't be read. */
    fun isBacklightOn(): Boolean? =
        executor.executeAsRoot("cat $BACKLIGHT_POWER").getOrNull()?.trim()?.toIntOrNull()?.let { it == 0 }

    /** Switches the backlight directly, the way Retroid does (for this connection only: its setting is unchanged). */
    fun setBacklight(on: Boolean) {
        executor.executeAsRoot(
            if (on) {
                "echo 1 > $BACKLIGHT_ENABLE; echo 0 > $BACKLIGHT_POWER"
            } else {
                "echo 1 > $BACKLIGHT_POWER; echo 0 > $BACKLIGHT_ENABLE"
            },
        )
    }

    companion object {
        private const val KEY_TURN_OFF = "close_screen_when_output_video"
        private const val BACKLIGHT_POWER = "/sys/class/backlight/panel0-backlight/bl_power" // 1 = off
        private const val BACKLIGHT_ENABLE = "/sys/class/gpio5_pwm2/en_backlight" // 0 = off
    }
}
