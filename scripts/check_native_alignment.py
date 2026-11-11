"""Google Play requires native libraries of apps targeting Android 15+ to work with 16 KB memory pages.

Checks every 64-bit .so in an APK or AAB: ELF LOAD segments aligned to at least 16 KB and, when stored uncompressed
in an APK, placed at a 16 KB offset. 32-bit libraries are skipped: 16 KB pages exist only on 64-bit devices. Usage: python3 scripts/check_native_alignment.py <file.apk|file.aab> [...]
"""
import struct
import sys
import zipfile

PAGE = 16 * 1024


def load_alignments(data):
    """Alignment of each LOAD segment of a 64-bit library; None for a 32-bit one."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2:
        return None
    offset = struct.unpack_from("<Q", data, 0x20)[0]
    size, count = struct.unpack_from("<HH", data, 0x36)
    result = []
    for index in range(count):
        entry = offset + index * size
        if struct.unpack_from("<I", data, entry)[0] == 1:  # PT_LOAD
            result.append(struct.unpack_from("<Q", data, entry + 0x30)[0])
    return result


def check(path):
    problems = []
    checked = 0
    with zipfile.ZipFile(path) as archive, open(path, "rb") as raw:
        for info in archive.infolist():
            if not info.filename.endswith(".so"):
                continue
            alignments = load_alignments(archive.read(info))
            if alignments is None:
                continue
            checked += 1
            if not alignments or min(alignments) < PAGE:
                problems.append(f"{info.filename}: LOAD alignment {sorted(set(alignments))}")
            if path.endswith(".apk") and info.compress_type == zipfile.ZIP_STORED:
                raw.seek(info.header_offset + 26)
                name_length, extra_length = struct.unpack("<HH", raw.read(4))
                if (info.header_offset + 30 + name_length + extra_length) % PAGE:
                    problems.append(f"{info.filename}: not stored at a 16 KB offset")
    return checked, problems


def main(paths):
    failed = False
    for path in paths:
        checked, problems = check(path)
        print(f"{path}: {checked} 64-bit native libraries checked, {len(problems)} problem(s)")
        for problem in problems:
            print("  " + problem)
        failed = failed or bool(problems) or checked == 0
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
