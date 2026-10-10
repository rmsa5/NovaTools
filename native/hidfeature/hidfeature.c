// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * hidfeature: list HID devices, and read or write HID feature reports through Linux hidraw.
 * Written for NovaTools research: setting an Apple display's brightness from the Retroid Pocket Nova.
 *
 *   hidfeature list
 *       Every /dev/hidraw* with its bus, USB vendor:product and name.
 *   hidfeature get <device> <report id> <length>
 *       Reads a feature report (length includes the report ID byte) and prints it in hex.
 *   hidfeature set <device> <byte> <byte> ...
 *       Writes a feature report. Bytes in hex, the first one is the report ID.
 *   hidfeature input <device> <report id> <length>
 *       Asks the device for its current input report (a measurement), printed in hex.
 *   hidfeature light <device>
 *       Apple displays' ambient light sensors (HID Sensor page): reads input reports 1 and 2 and prints
 *       illuminance (lux), colour temperature (K) and chromaticity x/y.
 *   hidfeature monitor find
 *       Finds displays with a USB brightness control (HID "Monitor Control": VESA Virtual Controls page, Brightness
 *       usage, as a feature report) by parsing each device's report descriptor. Prints one line per control:
 *       <device> <report id> <bit offset> <bit size> <min> <max> <report length>
 *   hidfeature monitor get
 *       Prints "<device> <value> <min> <max>" for the first control found.
 *   hidfeature monitor set <value>
 *       Sets the first control found (clamped to its range), keeping the report's other fields. Prints
 *       "<device> <value>". Exit code 3 when no control is found (e.g. the display's USB side isn't there yet).
 *   hidfeature brightness <device> [value]
 *       Apple displays: without a value, prints the current brightness;
 *       with a value, sets it. Report 1 = 7 bytes: ID, brightness (32 bits, little-endian), 2 zero bytes.
 *       The unit is 1/100 nit (Studio Display: 400 to 60000).
 *
 * Build (static, for the Nova's 64-bit ARM): zig cc -target aarch64-linux-musl -static -O2 -o hidfeature hidfeature.c
 */
#include <errno.h>
#include <fcntl.h>
#include <glob.h>
#include <linux/hidraw.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define MAX_REPORT 256
#define APPLE_BRIGHTNESS_REPORT 1
#define APPLE_BRIGHTNESS_LENGTH 7

static void print_hex(const unsigned char *buf, int len) {
    for (int i = 0; i < len; i++) printf("%02x%s", buf[i], i + 1 < len ? " " : "\n");
}

static int open_device(const char *path, int flags) {
    int fd = open(path, flags);
    if (fd < 0) fprintf(stderr, "open %s: %s\n", path, strerror(errno));
    return fd;
}

static int list_devices(void) {
    glob_t found;
    if (glob("/dev/hidraw*", 0, NULL, &found) != 0) {
        printf("no /dev/hidraw* devices (is CONFIG_HIDRAW enabled?)\n");
        return 1;
    }
    for (size_t i = 0; i < found.gl_pathc; i++) {
        const char *path = found.gl_pathv[i];
        int fd = open(path, O_RDONLY);
        if (fd < 0) {
            printf("%s: %s\n", path, strerror(errno));
            continue;
        }
        struct hidraw_devinfo info;
        char name[256] = "";
        int desc_size = 0;
        ioctl(fd, HIDIOCGRAWNAME(sizeof(name)), name);
        ioctl(fd, HIDIOCGRDESCSIZE, &desc_size);
        if (ioctl(fd, HIDIOCGRAWINFO, &info) == 0) {
            printf("%s: bus %d, %04x:%04x, descriptor %d bytes, \"%s\"\n", path, info.bustype,
                   (unsigned short)info.vendor, (unsigned short)info.product, desc_size, name);
        }
        close(fd);
    }
    globfree(&found);
    return 0;
}

static int get_feature(const char *path, int report_id, int len) {
    if (len < 1 || len > MAX_REPORT) {
        fprintf(stderr, "length must be 1 to %d\n", MAX_REPORT);
        return 1;
    }
    int fd = open_device(path, O_RDWR);
    if (fd < 0) return 1;
    unsigned char buf[MAX_REPORT] = {0};
    buf[0] = (unsigned char)report_id;
    int got = ioctl(fd, HIDIOCGFEATURE(len), buf);
    close(fd);
    if (got < 0) {
        fprintf(stderr, "get feature report %d: %s\n", report_id, strerror(errno));
        return 1;
    }
    print_hex(buf, got);
    return 0;
}

#ifndef HIDIOCGINPUT
#define HIDIOCGINPUT(len) _IOC(_IOC_WRITE | _IOC_READ, 'H', 0x0A, len)
#endif

static int get_input(const char *path, int report_id, unsigned char *buf, int len) {
    int fd = open_device(path, O_RDWR);
    if (fd < 0) return -1;
    memset(buf, 0, len);
    buf[0] = (unsigned char)report_id;
    int got = ioctl(fd, HIDIOCGINPUT(len), buf);
    if (got < 0) fprintf(stderr, "get input report %d: %s\n", report_id, strerror(errno));
    close(fd);
    return got;
}

static uint32_t u32_at(const unsigned char *b) {
    return b[0] | (b[1] << 8) | (b[2] << 16) | ((uint32_t)b[3] << 24);
}

/* Input report: ID, sensor event (1 byte), illuminance (lux x 1000), colour temperature (K), chromaticity x and y
 * (x 1e8), each 32 bits little-endian: 18 bytes. */
static int light(const char *path) {
    for (int id = 1; id <= 2; id++) {
        unsigned char buf[18];
        int got = get_input(path, id, buf, sizeof(buf));
        if (got < 18) {
            if (got >= 0) fprintf(stderr, "sensor %d: short report (%d bytes)\n", id, got);
            continue;
        }
        printf("sensor %d: %.1f lux, %u K, x %.4f y %.4f, raw: ", id, u32_at(buf + 2) / 1000.0, u32_at(buf + 6),
               u32_at(buf + 10) / 1e8, u32_at(buf + 14) / 1e8);
        print_hex(buf, got);
    }
    return 0;
}

static int set_feature(const char *path, const unsigned char *buf, int len) {
    int fd = open_device(path, O_RDWR);
    if (fd < 0) return 1;
    int sent = ioctl(fd, HIDIOCSFEATURE(len), buf);
    close(fd);
    if (sent < 0) {
        fprintf(stderr, "set feature report %d: %s\n", buf[0], strerror(errno));
        return 1;
    }
    printf("sent %d bytes: ", sent);
    print_hex(buf, len);
    return 0;
}

static int brightness(const char *path, const char *value) {
    unsigned char buf[APPLE_BRIGHTNESS_LENGTH] = {APPLE_BRIGHTNESS_REPORT};
    if (value == NULL) {
        int fd = open_device(path, O_RDWR);
        if (fd < 0) return 1;
        int got = ioctl(fd, HIDIOCGFEATURE(APPLE_BRIGHTNESS_LENGTH), buf);
        close(fd);
        if (got < 0) {
            fprintf(stderr, "get brightness: %s\n", strerror(errno));
            return 1;
        }
        uint32_t current = buf[1] | (buf[2] << 8) | (buf[3] << 16) | ((uint32_t)buf[4] << 24);
        printf("brightness %u (%.2f nits), raw: ", current, current / 100.0);
        print_hex(buf, got);
        return 0;
    }
    uint32_t level = (uint32_t)strtoul(value, NULL, 10);
    buf[1] = level & 0xff;
    buf[2] = (level >> 8) & 0xff;
    buf[3] = (level >> 16) & 0xff;
    buf[4] = (level >> 24) & 0xff;
    return set_feature(path, buf, APPLE_BRIGHTNESS_LENGTH);
}

static int usage(void);

/* ---- Monitor Control brightness, found through the report descriptor ---- */

#define USAGE_PAGE_VESA_VIRTUAL_CONTROLS 0x82
#define USAGE_BRIGHTNESS 0x10
#define EXIT_NO_CONTROL 3

struct monitor_control {
    char path[64];
    int report_id;
    int bit_offset; /* within the report data, after the report ID byte */
    int bit_size;
    long min, max;
    int report_length; /* including the report ID byte */
};

static long item_value(const unsigned char *data, int size, int is_signed) {
    uint32_t v = 0;
    for (int i = 0; i < size; i++) v |= (uint32_t)data[i] << (8 * i);
    if (!is_signed || size == 0) return (long)v;
    if (size == 1) return (int8_t)v;
    if (size == 2) return (int16_t)v;
    return (int32_t)v;
}

/* Parses a report descriptor and fills [found] with the brightness feature field, if any. Returns 1 if found. */
static int parse_brightness(const unsigned char *desc, int len, struct monitor_control *found) {
    uint32_t usage_page = 0, usages[64];
    int usage_count = 0, report_id = 0, report_size = 0, report_count = 0;
    long logical_min = 0, logical_max = 0;
    int max_size = 0; /* byte size of the Logical Maximum item, to reinterpret it as unsigned if needed */
    static int feature_bits[256];
    int result = 0;
    memset(feature_bits, 0, sizeof(feature_bits));

    for (int i = 0; i < len;) {
        unsigned char prefix = desc[i];
        if (prefix == 0xfe) { /* long item: skip */
            if (i + 1 >= len) break;
            i += 3 + desc[i + 1];
            continue;
        }
        int size = (int[]){0, 1, 2, 4}[prefix & 3];
        int type = (prefix >> 2) & 3, tag = prefix >> 4;
        if (i + 1 + size > len) break;
        const unsigned char *data = desc + i + 1;
        i += 1 + size;

        if (type == 1) { /* global */
            switch (tag) {
                case 0x0: usage_page = (uint32_t)item_value(data, size, 0); break;
                case 0x1: logical_min = item_value(data, size, 1); break;
                case 0x2: logical_max = item_value(data, size, 1); max_size = size; break;
                case 0x7: report_size = (int)item_value(data, size, 0); break;
                case 0x8: report_id = (int)item_value(data, size, 0) & 0xff; break;
                case 0x9: report_count = (int)item_value(data, size, 0); break;
            }
        } else if (type == 2) { /* local */
            if (tag == 0x0 && usage_count < 64) {
                uint32_t usage = (uint32_t)item_value(data, size, 0);
                usages[usage_count++] = size == 4 ? usage : (usage_page << 16) | usage;
            }
        } else if (type == 0) { /* main */
            if (tag == 0xb) { /* Feature */
                int is_constant = size > 0 && (data[0] & 1);
                long max = logical_max;
                /* Common rule (as in Linux): with a non-negative minimum, the maximum is unsigned */
                if (logical_min >= 0 && max < 0 && max_size > 0 && max_size < 4) max += 1L << (8 * max_size);
                if (logical_min >= 0 && max < 0 && max_size == 4) max += 1L << 32;
                for (int f = 0; f < report_count && !is_constant; f++) {
                    if (usage_count == 0) break;
                    uint32_t usage = usages[f < usage_count ? f : usage_count - 1];
                    if ((usage >> 16) == USAGE_PAGE_VESA_VIRTUAL_CONTROLS && (usage & 0xffff) == USAGE_BRIGHTNESS
                        && !result) {
                        found->report_id = report_id;
                        found->bit_offset = feature_bits[report_id] + f * report_size;
                        found->bit_size = report_size;
                        found->min = logical_min;
                        found->max = max;
                        result = 1;
                    }
                }
                feature_bits[report_id] += report_size * report_count;
            }
            usage_count = 0; /* locals end with each main item */
        }
    }
    if (result) {
        found->report_length = (feature_bits[found->report_id] + 7) / 8 + (found->report_id ? 1 : 0);
    }
    return result;
}

static int find_controls(struct monitor_control *controls, int max_controls) {
    glob_t found;
    int count = 0;
    if (glob("/dev/hidraw*", 0, NULL, &found) != 0) return 0;
    for (size_t i = 0; i < found.gl_pathc && count < max_controls; i++) {
        int fd = open(found.gl_pathv[i], O_RDONLY);
        if (fd < 0) continue;
        struct hidraw_report_descriptor desc;
        int desc_size = 0;
        if (ioctl(fd, HIDIOCGRDESCSIZE, &desc_size) == 0 && desc_size > 0 && desc_size <= HID_MAX_DESCRIPTOR_SIZE) {
            desc.size = desc_size;
            if (ioctl(fd, HIDIOCGRDESC, &desc) == 0 && parse_brightness(desc.value, desc_size, &controls[count])) {
                snprintf(controls[count].path, sizeof(controls[count].path), "%s", found.gl_pathv[i]);
                count++;
            }
        }
        close(fd);
    }
    globfree(&found);
    return count;
}

/* Little-endian bit field access within report data (data[0] is the first byte after the report ID) */
static long read_bits(const unsigned char *data, int offset, int size) {
    uint64_t v = 0;
    for (int b = 0; b < size; b++) {
        int bit = offset + b;
        if (data[bit / 8] & (1 << (bit % 8))) v |= (uint64_t)1 << b;
    }
    return (long)v;
}

static void write_bits(unsigned char *data, int offset, int size, long value) {
    for (int b = 0; b < size; b++) {
        int bit = offset + b;
        if (((uint64_t)value >> b) & 1) data[bit / 8] |= (unsigned char)(1 << (bit % 8));
        else data[bit / 8] &= (unsigned char)~(1 << (bit % 8));
    }
}

static int read_control(const struct monitor_control *c, unsigned char *buf) {
    int fd = open_device(c->path, O_RDWR);
    if (fd < 0) return -1;
    memset(buf, 0, MAX_REPORT);
    buf[0] = (unsigned char)c->report_id;
    int got = ioctl(fd, HIDIOCGFEATURE(c->report_length), buf);
    if (got < 0) fprintf(stderr, "get feature report %d: %s\n", c->report_id, strerror(errno));
    close(fd);
    return got;
}

static int monitor(int argc, char **argv) {
    struct monitor_control controls[8];
    int count = find_controls(controls, 8);
    if (strcmp(argv[2], "find") == 0) {
        for (int i = 0; i < count; i++) {
            printf("%s %d %d %d %ld %ld %d\n", controls[i].path, controls[i].report_id, controls[i].bit_offset,
                   controls[i].bit_size, controls[i].min, controls[i].max, controls[i].report_length);
        }
        return count > 0 ? 0 : EXIT_NO_CONTROL;
    }
    if (count == 0) {
        fprintf(stderr, "no display brightness control found\n");
        return EXIT_NO_CONTROL;
    }
    struct monitor_control *c = &controls[0];
    unsigned char buf[MAX_REPORT];
    int data_start = c->report_id ? 1 : 0;
    if (c->report_length > MAX_REPORT || read_control(c, buf) < 0) return 1;
    if (strcmp(argv[2], "get") == 0 && argc == 3) {
        printf("%s %ld %ld %ld\n", c->path, read_bits(buf + data_start, c->bit_offset, c->bit_size), c->min, c->max);
        return 0;
    }
    if (strcmp(argv[2], "set") == 0 && argc == 4) {
        long value = strtol(argv[3], NULL, 10);
        if (value < c->min) value = c->min;
        if (value > c->max) value = c->max;
        write_bits(buf + data_start, c->bit_offset, c->bit_size, value);
        buf[0] = data_start ? (unsigned char)c->report_id : buf[0];
        int fd = open_device(c->path, O_RDWR);
        if (fd < 0) return 1;
        int sent = ioctl(fd, HIDIOCSFEATURE(c->report_length), buf);
        close(fd);
        if (sent < 0) {
            fprintf(stderr, "set feature report %d: %s\n", c->report_id, strerror(errno));
            return 1;
        }
        printf("%s %ld\n", c->path, value);
        return 0;
    }
    return usage();
}

static int usage(void) {
    fprintf(stderr,
            "usage:\n"
            "  hidfeature list\n"
            "  hidfeature get <device> <report id> <length>\n"
            "  hidfeature set <device> <hex byte> [<hex byte> ...]\n"
            "  hidfeature input <device> <report id> <length>\n"
            "  hidfeature light <device>\n"
            "  hidfeature monitor find|get|set <value>\n"
            "  hidfeature brightness <device> [value in 1/100 nit]\n");
    return 2;
}

int main(int argc, char **argv) {
    if (argc >= 2 && strcmp(argv[1], "list") == 0) return list_devices();
    if (argc == 5 && strcmp(argv[1], "get") == 0) {
        return get_feature(argv[2], (int)strtol(argv[3], NULL, 0), (int)strtol(argv[4], NULL, 0));
    }
    if (argc >= 4 && strcmp(argv[1], "set") == 0) {
        unsigned char buf[MAX_REPORT];
        int len = argc - 3;
        if (len > MAX_REPORT) return usage();
        for (int i = 0; i < len; i++) buf[i] = (unsigned char)strtoul(argv[3 + i], NULL, 16);
        return set_feature(argv[2], buf, len);
    }
    if (argc == 5 && strcmp(argv[1], "input") == 0) {
        unsigned char buf[MAX_REPORT];
        int len = (int)strtol(argv[4], NULL, 0);
        if (len < 1 || len > MAX_REPORT) return usage();
        int got = get_input(argv[2], (int)strtol(argv[3], NULL, 0), buf, len);
        if (got < 0) return 1;
        print_hex(buf, got);
        return 0;
    }
    if (argc == 3 && strcmp(argv[1], "light") == 0) return light(argv[2]);
    if (argc >= 3 && strcmp(argv[1], "monitor") == 0) return monitor(argc, argv);
    if ((argc == 3 || argc == 4) && strcmp(argv[1], "brightness") == 0) {
        return brightness(argv[2], argc == 4 ? argv[3] : NULL);
    }
    return usage();
}
