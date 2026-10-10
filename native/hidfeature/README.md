# hidfeature

A small command-line tool NovaTools runs (with root) to talk to USB HID devices through Linux hidraw.
Its main use: setting the brightness of displays that take it over USB (HID "Monitor Control", e.g. Apple's
Studio Display, Studio Display XDR and Pro Display XDR), found by parsing each device's report descriptor.

NovaTools ships it as `app/src/main/jniLibs/arm64-v8a/libhidfeature.so`: a static executable with a library-style
name, so Android installs it as a file in the app's native library folder.

Build (static, 64-bit ARM), e.g. with Zig's C compiler:

    zig cc -target aarch64-linux-musl -static -O2 -s -o ../../app/src/main/jniLibs/arm64-v8a/libhidfeature.so hidfeature.c

Usage: `hidfeature monitor get`, `hidfeature monitor set <value>`, `hidfeature monitor find`, plus lower-level
commands (`list`, `get`, `set`, `input`, `light`); see the comment at the top of hidfeature.c.
