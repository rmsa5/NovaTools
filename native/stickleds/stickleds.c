// SPDX-License-Identifier: GPL-3.0-or-later
// stickleds: joystick LEDs follow the sticks on the Retroid Pocket Nova (research tool, run as root).
// Each stick has 4 RGB LEDs on a Heroic HTR3212 (I2C address 0x3c, left on bus 3, right on bus 6).
// LED i lights with  level = m * max(0, cos(stick angle - LED angle))^k,  m = how far the stick is pushed.
// The LED angles are the nominal cross (0 = right, 90 = top) rotated by a per-stick tilt, to calibrate.
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/i2c-dev.h>
#include <linux/input.h>
#include <math.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#define CHIP 0x3c
#define REG_PWM 0x0d
#define REG_UPDATE 0x25
#define MIN_INTERVAL_MS 10 // at most 100 LED updates per second
#define REFRESH_MS 1000    // rewrite everything this often: Retroid's settings app also writes these LEDs

static const int BUS[2] = {3, 6};
static const char SIDE[2] = {'l', 'r'};
static const char *NAME[2] = {"left", "right"};
// Nominal angle of LED groups 0..3 (degrees, 0 = right, anticlockwise), before tilt. Tested 2026-10-11.
static const double NOMINAL[2][4] = {{0, 90, 180, 270}, {270, 180, 90, 0}};

// Measured by Louis on his Nova (2026-10-11): left +10, right -15.
static double tilt[2] = {10, -15};
// Per stick ([0] left, [1] right): options apply to the sticks chosen with -s (both by default)
static double colour[2][3] = {{1, 1, 1}, {1, 1, 1}};          // colour when the stick is pushed a little
static double colourFull[2][3] = {{-1, -1, -1}, {-1, -1, -1}}; // colour at full push (-C); unset = same as colour
static int flat[2] = {0, 0};               // -n: brightness does not follow the push, only the colour does
static double beam[2] = {2, 2}, maxBrightness[2] = {1, 1};
static double deadzone = 0.1, gamma_ = 2.2;
static int verbose = 0;
static int opposite[2] = {0, 0}; // light the LEDs opposite the stick (the pushed stick hides the LEDs it points at)
static volatile sig_atomic_t stop = 0;

static int i2c[2] = {-1, -1};
static unsigned char shown[2][12];

struct axis { int code; double centre, half, value; };
static struct axis axes[2][2]; // [stick][x,y]

static void onSignal(int sig) { (void)sig; stop = 1; }

static long nowMs(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000L + t.tv_nsec / 1000000L;
}

static int writeFile(const char *path, const char *text) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) return -1;
    int ok = write(fd, text, strlen(text)) == (ssize_t)strlen(text);
    close(fd);
    return ok ? 0 : -1;
}

static int writeReg(int stick, int reg, int value) {
    unsigned char buf[2] = {(unsigned char)reg, (unsigned char)value};
    if (write(i2c[stick], buf, 2) != 2) {
        fprintf(stderr, "%s stick: I2C write to 0x%02x failed: %s\n", NAME[stick], reg, strerror(errno));
        return -1;
    }
    return 0;
}

// Writes the channels that changed (all of them if [all]), then latches them.
static void show(int stick, const unsigned char *values, int all) {
    int changed = 0;
    for (int ch = 0; ch < 12; ch++) {
        if (all || values[ch] != shown[stick][ch]) {
            if (writeReg(stick, REG_PWM + ch, values[ch]) == 0) shown[stick][ch] = values[ch];
            changed = 1;
        }
    }
    if (changed) writeReg(stick, REG_UPDATE, 0);
}

static int openChip(int stick) {
    char path[64];
    // Let Retroid's driver power the chip up (shutdown pin, channels on, 22 kHz), then clear it.
    snprintf(path, sizeof path, "/sys/class/sn3112%c/led/enable", SIDE[stick]);
    if (writeFile(path, "1") != 0) fprintf(stderr, "warning: could not write %s\n", path);
    snprintf(path, sizeof path, "/sys/class/sn3112%c/led/brightness", SIDE[stick]);
    writeFile(path, "1-0:0:0");
    usleep(50000);

    snprintf(path, sizeof path, "/dev/i2c-%d", BUS[stick]);
    i2c[stick] = open(path, O_RDWR);
    if (i2c[stick] < 0) { fprintf(stderr, "%s: %s\n", path, strerror(errno)); return -1; }
    // FORCE: the address belongs to Retroid's driver, we write next to it.
    if (ioctl(i2c[stick], I2C_SLAVE_FORCE, CHIP) < 0) {
        fprintf(stderr, "%s: I2C_SLAVE_FORCE: %s\n", path, strerror(errno));
        return -1;
    }
    memset(shown[stick], 0, 12);
    return 0;
}

static int hasBit(const unsigned long *bits, int bit) {
    return (bits[bit / (8 * sizeof(long))] >> (bit % (8 * sizeof(long)))) & 1;
}

// Returns 1 if this device has both sticks; fills in the axis codes.
static int pickAxes(int fd, const char *rightChoice) {
    unsigned long abs[(ABS_MAX + 1) / (8 * sizeof(long)) + 1];
    memset(abs, 0, sizeof abs);
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof abs), abs) < 0) return 0;
    if (!hasBit(abs, ABS_X) || !hasBit(abs, ABS_Y)) return 0;
    int haveRx = hasBit(abs, ABS_RX) && hasBit(abs, ABS_RY);
    int haveZ = hasBit(abs, ABS_Z) && hasBit(abs, ABS_RZ);
    int useZ;
    if (rightChoice && strcmp(rightChoice, "z") == 0) useZ = 1;
    else if (rightChoice && strcmp(rightChoice, "rx") == 0) useZ = 0;
    // Android-style pads put the right stick on Z/RZ and triggers on BRAKE/GAS; Xbox-style on RX/RY.
    else if (haveZ && (hasBit(abs, ABS_BRAKE) || hasBit(abs, ABS_GAS) || !haveRx)) useZ = 1;
    else useZ = 0;
    if (useZ ? !haveZ : !haveRx) return 0;
    axes[0][0].code = ABS_X;
    axes[0][1].code = ABS_Y;
    axes[1][0].code = useZ ? ABS_Z : ABS_RX;
    axes[1][1].code = useZ ? ABS_RZ : ABS_RY;
    for (int s = 0; s < 2; s++)
        for (int a = 0; a < 2; a++) {
            struct input_absinfo info;
            if (ioctl(fd, EVIOCGABS(axes[s][a].code), &info) < 0) return 0;
            axes[s][a].centre = (info.minimum + info.maximum) / 2.0;
            axes[s][a].half = (info.maximum - info.minimum) / 2.0;
            if (axes[s][a].half <= 0) return 0;
            axes[s][a].value = info.value;
        }
    return 1;
}

static int openInput(const char *device, const char *rightChoice) {
    char path[64], name[128];
    for (int n = device ? -1 : 0; n < 64; n++) {
        const char *p = device;
        if (!device) { snprintf(path, sizeof path, "/dev/input/event%d", n); p = path; }
        int fd = open(p, O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            name[0] = 0;
            ioctl(fd, EVIOCGNAME(sizeof name), name);
            if (pickAxes(fd, rightChoice)) {
                printf("input: %s (%s), right stick on %s\n", p, name,
                       axes[1][0].code == ABS_Z ? "Z/RZ" : "RX/RY");
                return fd;
            }
            close(fd);
        }
        if (device) break;
    }
    fprintf(stderr, "no input device with two sticks found%s\n", device ? " at that path" : "");
    return -1;
}

static double norm(const struct axis *a) {
    double v = (a->value - a->centre) / a->half;
    return v < -1 ? -1 : v > 1 ? 1 : v;
}

static unsigned char current[2][12];

static void update(int stick, int all) {
    double x = norm(&axes[stick][0]);
    double y = -norm(&axes[stick][1]); // evdev: down is positive
    double m = sqrt(x * x + y * y);
    if (m > 1) m = 1;
    m = m < deadzone ? 0 : (m - deadzone) / (1 - deadzone);
    double stickAngle = atan2(y, x);
    double angle = opposite[stick] ? stickAngle + M_PI : stickAngle; // direction the light points to
    unsigned char values[12];
    for (int led = 0; led < 4; led++) {
        double ledAngle = (NOMINAL[stick][led] + tilt[stick]) * M_PI / 180;
        double c = cos(angle - ledAngle);
        double level = (flat[stick] ? (m > 0) : m) * (c > 0 ? pow(c, beam[stick]) : 0);
        level = pow(level, gamma_) * maxBrightness[stick];
        for (int k = 0; k < 3; k++) {
            const double *lo = colour[stick], *hi = colourFull[stick];
            double col = hi[0] < 0 ? lo[k] : lo[k] + (hi[k] - lo[k]) * m;
            values[led * 3 + k] = (unsigned char)lround(255 * level * col);
        }
    }
    if (verbose && m > 0)
        printf("%s stick: stick %6.1f, light %6.1f, pushed %3.0f%%\n", NAME[stick], stickAngle * 180 / M_PI,
               fmod(angle * 180 / M_PI + 360, 360) > 180 ? fmod(angle * 180 / M_PI + 360, 360) - 360
                                                         : fmod(angle * 180 / M_PI + 360, 360), m * 100);
    memcpy(current[stick], values, 12);
    show(stick, values, all);
}

static void usage(void) {
    fprintf(stderr,
        "usage: stickleds [options]   (Enter, Ctrl-C or pkill stickleds stops it; the LEDs go off)\n"
        "  -l DEG     left stick LED tilt, degrees anticlockwise (default 10)\n"
        "  -r DEG     right stick LED tilt, degrees anticlockwise (default -15)\n"
        "  -s l|r|b   the following -c -C -n -k -b -o apply to the left stick, right stick or both (default b)\n"
        "  -c RRGGBB  colour (default ffffff)\n"
        "  -C RRGGBB  colour at full push: blends from -c (light push) to -C (full push)\n"
        "  -n         brightness does not follow the push (use with -C: only the colour changes)\n"
        "  -k N       beam tightness: 1 wide, 4+ narrow (default 2)\n"
        "  -z F       dead zone, 0 to 1 (default 0.1)\n"
        "  -g F       gamma, 1 = linear PWM (default 2.2)\n"
        "  -b F       maximum brightness, 0 to 1 (default 1)\n"
        "  -t SEC     stop after SEC seconds (default 0 = never)\n"
        "  -a rx|z    axes of the right stick (default: automatic)\n"
        "  -d PATH    input device (default: first /dev/input/event* with two sticks)\n"
        "  -o         opposite: light the LEDs on the side away from the stick\n"
        "  -v         print stick angle, light angle and push\n");
}

int main(int argc, char **argv) {
    const char *device = NULL, *rightChoice = NULL;
    double seconds = 0;
    int opt, first = 0, last = 1; // sticks the per-stick options apply to (-s)
    while ((opt = getopt(argc, argv, "l:r:s:c:C:k:z:g:b:t:a:d:novh")) != -1) {
        switch (opt) {
        case 's':
            if (strcmp(optarg, "l") == 0) { first = 0; last = 0; }
            else if (strcmp(optarg, "r") == 0) { first = 1; last = 1; }
            else if (strcmp(optarg, "b") == 0) { first = 0; last = 1; }
            else { usage(); return 2; }
            break;
        case 'l': tilt[0] = atof(optarg); break;
        case 'r': tilt[1] = atof(optarg); break;
        case 'c':
        case 'C': {
            unsigned int rgb;
            if (strlen(optarg) != 6 || sscanf(optarg, "%6x", &rgb) != 1) { usage(); return 2; }
            for (int st = first; st <= last; st++) {
                double *target = opt == 'c' ? colour[st] : colourFull[st];
                target[0] = ((rgb >> 16) & 255) / 255.0;
                target[1] = ((rgb >> 8) & 255) / 255.0;
                target[2] = (rgb & 255) / 255.0;
            }
            break;
        }
        case 'n': for (int st = first; st <= last; st++) flat[st] = 1; break;
        case 'k': for (int st = first; st <= last; st++) beam[st] = atof(optarg); break;
        case 'z': deadzone = atof(optarg); break;
        case 'g': gamma_ = atof(optarg); break;
        case 'b': for (int st = first; st <= last; st++) maxBrightness[st] = atof(optarg); break;
        case 't': seconds = atof(optarg); break;
        case 'a': rightChoice = optarg; break;
        case 'd': device = optarg; break;
        case 'o': for (int st = first; st <= last; st++) opposite[st] = 1; break;
        case 'v': verbose = 1; break;
        default: usage(); return 2;
        }
    }
    int invalid = deadzone < 0 || deadzone >= 1 || gamma_ <= 0;
    for (int st = 0; st < 2; st++)
        invalid |= beam[st] <= 0 || maxBrightness[st] < 0 || maxBrightness[st] > 1;
    if (invalid) {
        usage();
        return 2;
    }
    setvbuf(stdout, NULL, _IOLBF, 0);

    int input = openInput(device, rightChoice);
    if (input < 0) return 1;
    for (int s = 0; s < 2; s++)
        if (openChip(s) != 0) return 1;
    for (int st = 0; st < 2; st++)
        printf("%s stick: tilt %.1f degrees, beam %.1f, brightness %.2f%s%s\n", NAME[st], tilt[st], beam[st],
               maxBrightness[st], flat[st] ? ", constant" : "", opposite[st] ? ", opposite" : "");
    printf("%s\n", isatty(0) ? "Press Enter to stop." : "Stop with: pkill stickleds (or -t SEC).");

    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = onSignal;
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    // Started in the background by NovaTools: no terminal, and the starting shell's hang-up must not stop it
    if (isatty(0)) sigaction(SIGHUP, &sa, NULL);
    else signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    int watchStdin = isatty(0);
    long start = nowMs(), lastShow = 0, lastRefresh = nowMs();
    int pending[2] = {1, 1};
    while (!stop) {
        long now = nowMs();
        if (seconds > 0 && now - start >= seconds * 1000) break;
        if (now - lastRefresh >= REFRESH_MS) {
            for (int s = 0; s < 2; s++) {
                if (pending[s]) { update(s, 1); pending[s] = 0; }
                else show(s, current[s], 1);
            }
            lastRefresh = lastShow = now;
            continue;
        }
        int wait = (int)(REFRESH_MS - (now - lastRefresh));
        if (pending[0] || pending[1]) {
            long left = MIN_INTERVAL_MS - (now - lastShow);
            if (left <= 0) {
                for (int s = 0; s < 2; s++)
                    if (pending[s]) { update(s, 0); pending[s] = 0; }
                lastShow = now;
                continue;
            }
            if (left < wait) wait = (int)left;
        }
        if (seconds > 0) {
            long untilEnd = (long)(seconds * 1000) - (now - start);
            if (untilEnd < wait) wait = (int)(untilEnd > 0 ? untilEnd : 0);
        }
        // Ctrl-C does not always get through adb + su, so Enter on the terminal stops too.
        struct pollfd p[2] = {{input, POLLIN, 0}, {watchStdin ? 0 : -1, POLLIN, 0}};
        int r = poll(p, 2, wait);
        if (r < 0) { if (errno == EINTR) continue; break; }
        if (r == 0) continue;
        if (p[1].revents) break; // a key line, or the terminal went away
        if (p[0].revents & (POLLERR | POLLHUP | POLLNVAL)) { fprintf(stderr, "input device gone\n"); break; }
        if (!(p[0].revents & POLLIN)) continue;
        struct input_event ev[64];
        ssize_t got = read(input, ev, sizeof ev);
        if (got < 0) { if (errno == EAGAIN || errno == EINTR) continue; perror("read input"); break; }
        for (size_t i = 0; i < got / sizeof ev[0]; i++) {
            if (ev[i].type != EV_ABS) continue;
            for (int s = 0; s < 2; s++)
                for (int a = 0; a < 2; a++)
                    if (ev[i].code == axes[s][a].code) { axes[s][a].value = ev[i].value; pending[s] = 1; }
        }
    }

    unsigned char off[12] = {0};
    for (int s = 0; s < 2; s++) show(s, off, 1);
    printf("stopped, LEDs off. Toggle the stick lights in Retroid's settings to get your colour back.\n");
    return 0;
}
