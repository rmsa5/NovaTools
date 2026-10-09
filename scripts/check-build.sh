#!/bin/sh
# Compiles the app from the terminal and saves the full output to build/check-build.log,
# so the result can be read without copying errors out of Android Studio.
# Uses Android Studio's bundled Java to start Gradle (the build itself uses the project's toolchain).

cd "$(dirname "$0")/.." || exit 1
mkdir -p build

if [ -z "$JAVA_HOME" ]; then
    for jbr in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        [ -d "$jbr" ] && export JAVA_HOME="$jbr" && break
    done
fi

./gradlew :app:compileDebugKotlin --console=plain > build/check-build.log 2>&1
status=$?

grep -E "^e: |BUILD (SUCCESSFUL|FAILED)" build/check-build.log
echo "Full log: build/check-build.log (exit code $status)"
exit $status
