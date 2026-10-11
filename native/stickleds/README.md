# stickleds

A small command-line tool NovaTools runs in the background (with root) to make the Retroid Pocket Nova's
joystick LEDs follow the sticks: the LEDs on the side the stick points to light up, brighter (and optionally in
another colour) the further it's pushed.

Each stick has 4 RGB LEDs on a Heroic HTR3212 LED driver (I2C address 0x3c; left stick on bus 3, right stick on
bus 6). Retroid's own driver can only give all 4 LEDs one colour, so the tool writes the chip's brightness
registers directly through `/dev/i2c-N`, next to that driver. It reads the sticks from `/dev/input/event*`
without taking them over, so games keep working.

NovaTools ships it as `app/src/main/jniLibs/arm64-v8a/libstickleds.so`: a static executable with a library-style
name, so Android installs it as a file in the app's native library folder.

Build (static, 64-bit ARM), e.g. with Zig's C compiler:

    zig cc -target aarch64-linux-musl -static -O2 -s -o ../../app/src/main/jniLibs/arm64-v8a/libstickleds.so stickleds.c

Run it without options, or with `-h`, for the list of options (tilt of each stick's LEDs, colours, beam width,
brightness...). From a terminal it stops on Enter or Ctrl-C; in the background on SIGTERM. It switches the LEDs
off when it stops.
