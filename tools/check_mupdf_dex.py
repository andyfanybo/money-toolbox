"""Fail release builds if R8 strips Java symbols used by MuPDF's native JNI code."""

import re
import struct
import sys
import zipfile


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def dex_symbols(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Invalid DEX file")

    strings_count, strings_offset = u32(data, 56), u32(data, 60)
    types_count, types_offset = u32(data, 64), u32(data, 68)
    fields_count, fields_offset = u32(data, 80), u32(data, 84)
    classes_count, classes_offset = u32(data, 96), u32(data, 100)

    strings = []
    for index in range(strings_count):
        cursor = u32(data, strings_offset + index * 4)
        while data[cursor] & 0x80:  # ULEB128 UTF-16 length
            cursor += 1
        cursor += 1
        end = data.index(b"\0", cursor)
        strings.append(data[cursor:end].decode("utf-8", errors="replace"))

    types = [strings[u32(data, types_offset + index * 4)] for index in range(types_count)]
    classes = {types[u32(data, classes_offset + index * 32)] for index in range(classes_count)}
    fields = set()
    for index in range(fields_count):
        class_index, type_index, name_index = struct.unpack_from("<HHI", data, fields_offset + index * 8)
        fields.add((types[class_index], strings[name_index], types[type_index]))
    return classes, fields


def main(apk_path):
    classes, fields = set(), set()
    with zipfile.ZipFile(apk_path) as apk:
        dex_files = [name for name in apk.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
        if not dex_files:
            raise SystemExit("No DEX files in APK")
        for name in dex_files:
            found_classes, found_fields = dex_symbols(apk.read(name))
            classes.update(found_classes)
            fields.update(found_fields)

    required_classes = {
        "Lcom/artifex/mupdf/fitz/Context;",
        "Lcom/artifex/mupdf/fitz/Context$Log;",
        "Lcom/artifex/mupdf/fitz/Document;",
        "Lcom/artifex/mupdf/fitz/Page;",
        "Lcom/artifex/mupdf/fitz/Pixmap;",
    }
    missing = required_classes - classes
    log_field = (
        "Lcom/artifex/mupdf/fitz/Context;",
        "log",
        "Lcom/artifex/mupdf/fitz/Context$Log;",
    )
    if missing or log_field not in fields:
        raise SystemExit(f"MuPDF JNI symbols missing after R8: classes={sorted(missing)}, Context.log={log_field in fields}")
    print(f"MuPDF JNI symbols verified in {len(dex_files)} DEX file(s)")


if __name__ == "__main__":
    main(sys.argv[1])
