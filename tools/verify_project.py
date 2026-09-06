#!/usr/bin/env python3
"""Validate vendored sources, modern module metadata and optional APK class definitions."""
import argparse
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
META = ROOT / "app/src/main/resources/META-INF/xposed"
ENTRY = "io.github.baiducarsilent.SilentModule"


def require(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)


def dex_classes(data):
    require(data[:4] == b"dex\n", "invalid DEX magic")
    u32 = lambda off: struct.unpack_from("<I", data, off)[0]
    strings_offset = u32(60)
    types_offset = u32(68)
    classes_count, classes_offset = u32(96), u32(100)
    for i in range(classes_count):
        type_id = u32(classes_offset + i * 32)
        string_id = u32(types_offset + type_id * 4)
        off = u32(strings_offset + string_id * 4)
        while data[off] & 0x80:
            off += 1
        off += 1
        end = data.index(b"\0", off)
        yield data[off:end].decode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    source_root = ROOT / "libxposed-api100"
    origin = json.loads((source_root / "UPSTREAM.json").read_text())
    require(origin["commit"] == "e2588ee24bbef12c7c2e07666efa60e58e1ee6b3", "wrong API commit")
    for item in origin["files"]:
        path = source_root / item["path"].removeprefix("api/")
        data = path.read_bytes()
        blob = b"blob " + str(len(data)).encode() + b"\0" + data
        require(hashlib.sha1(blob).hexdigest() == item["sha"], "modified upstream: " + str(path))
    interface = source_root / "src/main/java/io/github/libxposed/api/XposedInterface.java"
    require(re.search(r"int\s+API\s*=\s*100\s*;", interface.read_text()), "API constant is not 100")
    require((META / "java_init.list").read_text().strip() == ENTRY, "entry mismatch")
    require((META / "scope.list").read_text().strip() == "com.baidu.BaiduMap", "scope mismatch")
    props = dict(line.split("=", 1) for line in (META / "module.prop").read_text().splitlines() if line)
    require(props == {"minApiVersion": "100", "targetApiVersion": "100", "staticScope": "true"}, "API properties mismatch")
    for p in ROOT.rglob("*.xml"):
        if "build" not in p.parts:
            ET.parse(p)
    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.jar").read_bytes()
    require(hashlib.sha256(wrapper).hexdigest() == "498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17", "wrapper checksum mismatch")
    require('compileOnly(project(":libxposed-api100"))' in (ROOT / "app/build.gradle.kts").read_text(), "API must be compile-only")
    print("PASS: official API 100 sources, wrapper checksum, XML, entry, scope and dependencies")
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            require(apk.testzip() is None, "corrupt APK")
            for name in ("module.prop", "scope.list", "java_init.list"):
                require(apk.read("META-INF/xposed/" + name) == (META / name).read_bytes(), "APK metadata mismatch: " + name)
            classes = set()
            for name in apk.namelist():
                if re.fullmatch(r"classes\d*\.dex", name):
                    classes.update(dex_classes(apk.read(name)))
            require("L" + ENTRY.replace(".", "/") + ";" in classes, "module class absent")
            require(not any(c.startswith("Lio/github/libxposed/") for c in classes), "framework API packaged in APK")
            require(not any(c.startswith("Lcom/baidu/") for c in classes), "Baidu classes packaged in APK")
        print("PASS: APK metadata, module entry and absence of packaged framework/Baidu classes")


if __name__ == "__main__":
    main()
