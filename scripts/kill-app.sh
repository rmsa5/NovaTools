#!/bin/sh
# Kills NovaTools on the connected device using root.
#
# Why: Retroid's firmware refuses to force-stop any app with an enabled
# accessibility service ("forceStopPackage skipped (accessibility enabled)"),
# which also breaks Android Studio's Stop button and can block reinstalls.
# Used as a "Before launch" step in Android Studio's run configuration.

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
# pidof matches the process name only (pkill -f would also match, and kill, this very shell)
"$ADB" shell "su -c 'for p in \$(pidof io.github.rmsa5.novatools); do kill -9 \$p; done; true'"
