package de.langerhans.odintools.ui.composables

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/*
 * Temporary, until the UI gets proper controller support: NovaTools ignores the sticks, the D-pad and the
 * controller buttons, so testing the stick lights (or bumping a stick) can't move sliders or switch settings.
 * B still goes back. Touch works as before.
 */

/** Passes everything to [base] except controller input. */
private class GamepadBlockingCallback(private val base: Window.Callback) : Window.Callback by base {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (isControllerKey(event.keyCode)) true else base.dispatchKeyEvent(event)

    // Also stops Android from turning stick movements into D-pad presses
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) true else base.dispatchGenericMotionEvent(event)

    private fun isControllerKey(keyCode: Int): Boolean = when (keyCode) {
        // B: Android turns it into Back
        KeyEvent.KEYCODE_BUTTON_B -> false
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP_LEFT,
        KeyEvent.KEYCODE_DPAD_UP_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        -> true
        else -> KeyEvent.isGamepadButton(keyCode)
    }
}

/** Makes this window ignore controller input (see above). */
fun Window.blockGamepadInput() {
    val current = callback ?: return
    if (current !is GamepadBlockingCallback) callback = GamepadBlockingCallback(current)
}

/** Put inside a dialog's content: dialogs are windows of their own. */
@Composable
fun BlockGamepadInDialog() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.blockGamepadInput()
        onDispose {}
    }
}
