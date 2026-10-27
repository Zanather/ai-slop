#!/usr/bin/env python3
"""Fails if R8 stripped a constructor that Android or a library creates by reflection.

Unit tests run unminified code, so they can't see this. It shipped once: R8 dropped WorkManager's database
constructor and every launch crashed in androidx.startup before the first screen. This reads the release APK's
dex and manifest and checks, for every class something instantiates reflectively, that a constructor survived:

- every manifest component (application, activities, services, receivers, providers),
- every androidx.startup initializer listed under InitializationProvider,
- every concrete subclass of a type that libraries instantiate by class name (Room databases, WorkManager
  input mergers and workers, Glance action callbacks).

The class hierarchy comes from the unminified debug APK, because R8 rewrites it in the release build (it dropped
WorkDatabase's RoomDatabase superclass and PressKeyAction's ActionCallback interface once it had decided nothing
used them).

Usage: verify-release-dex.py path/to/app-release.apk path/to/app-debug.apk
"""

import os
import re
import subprocess
import sys
import tempfile
import zipfile

# Libraries create concrete subclasses of these by reflection.
REFLECTIVE_BASES = [
    "Landroidx/room/RoomDatabase;",
    "Landroidx/work/InputMerger;",
    "Landroidx/work/ListenableWorker;",
    "Landroidx/glance/appwidget/action/ActionCallback;",
]


def build_tool(name):
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not home:
        sys.exit("ANDROID_HOME is not set")
    root = os.path.join(home, "build-tools")
    for version in sorted(os.listdir(root), key=lambda v: [int(p) if p.isdigit() else p for p in re.split(r"[.-]", v)], reverse=True):
        path = os.path.join(root, version, name)
        if os.path.exists(path):
            return path
    sys.exit(f"{name} not found under {root}")


def read_classes(apk):
    """Maps class descriptor -> (superclass, interfaces, is_abstract, method names)."""
    classes = {}
    dexdump = build_tool("dexdump")
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk) as zf:
        for entry in zf.namelist():
            if not re.fullmatch(r"classes\d*\.dex", entry):
                continue
            path = zf.extract(entry, tmp)
            out = subprocess.run([dexdump, path], capture_output=True, encoding="utf-8", errors="replace", check=True).stdout
            current = None
            section = None
            for line in out.splitlines():
                m = re.match(r"\s*Class descriptor\s*: '(.+)'", line)
                if m:
                    current = {"super": None, "interfaces": [], "abstract": False, "methods": set()}
                    classes[m.group(1)] = current
                    section = None
                    continue
                if current is None:
                    continue
                if re.match(r"\s*Access flags\s*:", line) and section is None:
                    current["abstract"] = "ABSTRACT" in line or "INTERFACE" in line
                elif m := re.match(r"\s*Superclass\s*: '(.+)'", line):
                    current["super"] = m.group(1)
                elif re.match(r"\s*Interfaces\s*-", line):
                    section = "interfaces"
                elif re.match(r"\s*(Direct|Virtual) methods\s*-", line):
                    section = "methods"
                elif re.match(r"\s*(Static|Instance) fields\s*-", line):
                    section = "fields"
                elif section == "interfaces" and (m := re.match(r"\s*#\d+\s*: '(.+)'", line)):
                    current["interfaces"].append(m.group(1))
                elif section == "methods" and (m := re.match(r"\s*name\s*: '(.+)'", line)):
                    current["methods"].add(m.group(1))
    return classes


def manifest_classes(apk):
    """Component and startup-initializer class names from the binary manifest."""
    dump = subprocess.run(
        [build_tool("aapt2"), "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
        capture_output=True, text=True, check=True,
    ).stdout
    names = []
    element = None
    meta_name = None
    for line in dump.splitlines():
        if m := re.match(r"\s*E: (\S+)", line):
            element = m.group(1)
            meta_name = None
            continue
        if m := re.search(r':name\(0x[0-9a-f]+\)="([^"]+)"', line):
            if element in ("application", "activity", "service", "receiver", "provider"):
                names.append(m.group(1))
            elif element == "meta-data":
                meta_name = m.group(1)
        elif element == "meta-data" and meta_name and re.search(r':value\(0x[0-9a-f]+\)="androidx.startup"', line):
            names.append(meta_name)
    return {"L" + n.replace(".", "/") + ";" for n in names}


def ancestry(classes, name):
    seen = []
    while name in classes:
        info = classes[name]
        seen.append(name)
        seen.extend(info["interfaces"])
        name = info["super"]
    if name:
        seen.append(name)
    return seen


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    release_apk, debug_apk = sys.argv[1], sys.argv[2]
    release = read_classes(release_apk)
    hierarchy = read_classes(debug_apk)

    # Manifest classes must exist; reflectively created library subclasses only need a constructor if R8 kept them.
    must_exist = {name: "declared in the manifest" for name in manifest_classes(release_apk)}
    if_kept = {}
    for name, info in hierarchy.items():
        if info["abstract"]:
            continue
        bases = set(ancestry(hierarchy, name))
        for base in REFLECTIVE_BASES:
            if base in bases and name != base:
                if_kept[name] = f"concrete {base.strip('L;').replace('/', '.')}"

    problems = []
    for name, why in sorted(must_exist.items()):
        if name not in release:
            problems.append(f"{name} missing entirely ({why})")
        elif "<init>" not in release[name]["methods"]:
            problems.append(f"{name} has no constructor ({why})")
    for name, why in sorted(if_kept.items()):
        if name in release and name not in must_exist and "<init>" not in release[name]["methods"]:
            problems.append(f"{name} has no constructor ({why})")
    required = {**must_exist, **if_kept}

    if problems:
        print("R8 stripped classes or constructors that are created by reflection:")
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print(f"OK: {len(required)} reflectively created classes keep their constructors.")


if __name__ == "__main__":
    main()
