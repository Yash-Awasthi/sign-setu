"""Resolve the licence of every locked component, from the local Gradle cache where it is stated.

`gradle.lockfile` records coordinates only. A component's POM states its licences, so that is the
first source: the POM in `~/.gradle/caches/modules-2/files-2.1`, then its `<parent>` chain, because
several Google POMs declare nothing themselves. Gradle does not keep a POM for every coordinate a
retired configuration once resolved, and a few POMs name no licence at all; those are listed in
`OVERRIDES` with the evidence, or recorded as unresolved so no licence is invented for them.

The resolved result is checked in as `data/licences.json`, because the release SBOM must be
reproducible byte for byte on a machine with an empty cache; `--write` refreshes it and
`check_notices.py` refuses a stale map. `Elections` records the dual-grant choices the notices
state: JNA under Apache-2.0 and checker-compat-qual under MIT.
"""
import argparse
import json
import os
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
DATA = ROOT / "tools" / "security" / "data" / "licences.json"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

APACHE = "Apache-2.0"
MIT = "MIT"
BSD = "BSD-3-Clause"
ANDROID = "Android SDK License"
BOUNCY = "Bouncy Castle Licence"
SPDX = {APACHE, MIT, BSD, "LGPL-2.1-or-later", "GPL-2.0-with-classpath-exception"}

# POM names in the wild -> the identifier written into the SBOM and the notices.
NAMES = {
    "the apache software license, version 2.0": APACHE,
    "the apache license, version 2.0": APACHE,
    "apache license, version 2.0": APACHE,
    "apache license 2.0": APACHE,
    "apache 2.0": APACHE,
    "apache-2.0": APACHE,
    "mit license": MIT,
    "the mit license": MIT,
    "mit": MIT,
    "bsd-3-clause": BSD,
    "android software development kit license": ANDROID,
    "bouncy castle licence": BOUNCY,
    "lgpl-2.1-or-later": "LGPL-2.1-or-later",
    "gnu general public license, version 2 (gpl2), with the classpath exception":
        "GPL-2.0-with-classpath-exception",
}

# A dual grant is a choice the distributor makes; the elected grant is the one relied on.
ELECTIONS = {
    "net.java.dev.jna:jna": ("Apache-2.0", "LGPL-2.1-or-later",
                             "upstream LICENSE: Apache-2.0 OR LGPL-2.1-or-later, freely chosen"),
    "org.checkerframework:checker-compat-qual": ("MIT", "GPL-2.0-with-classpath-exception",
                                                 "POM declares GPL-2.0 with classpath exception or MIT"),
}

# Coordinates whose POM is absent from the cache or states no licence. The evidence is the POM or
# parent POM named, as recorded in .fleet/qa/licences.md.
OVERRIDES = {
    "androidx.appcompat:appcompat:1.2.0": (APACHE, "Google Maven POM"),
    "androidx.appcompat:appcompat-resources:1.2.0": (APACHE, "Google Maven POM"),
    "androidx.biometric:biometric:1.1.0": (APACHE, "Google Maven POM"),
    "androidx.compose.ui:ui-tooling-preview:1.12.1": (APACHE, "Google Maven POM"),
    "androidx.compose.ui:ui-tooling-preview-android:1.12.1": (APACHE, "Google Maven POM"),
    "androidx.fragment:fragment:1.9.1": (APACHE, "Google Maven POM"),
    "androidx.fragment:fragment-ktx:1.9.1": (APACHE, "Google Maven POM"),
    "androidx.lifecycle:lifecycle-service:2.11.0": (APACHE, "Google Maven POM"),
    "androidx.work:work-runtime:2.12.0": (APACHE, "Google Maven POM"),
    "androidx.work:work-runtime-ktx:2.12.0": (APACHE, "Google Maven POM"),
    "com.google.errorprone:error_prone_annotations:2.28.0": (APACHE, "Google Maven POM"),
    "com.google.mediapipe:tasks-core:1.0.0": (APACHE, "Google Maven POM"),
    "com.google.mediapipe:tasks-vision:1.0.0": (APACHE, "Google Maven POM"),
    "org.jetbrains.kotlinx:kotlinx-serialization-bom:1.11.0": (APACHE, "Maven Central POM"),
    "org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0": (APACHE, "Maven Central POM"),
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0": (APACHE, "Maven Central POM"),
    "org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0": (APACHE, "Maven Central POM"),
    "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0": (APACHE, "Maven Central POM"),
    "com.google.auto.value:auto-value-annotations:1.6.3": (APACHE, "parent POM com.google.auto:auto-parent:7"),
    "com.google.guava:failureaccess:1.0.2": (APACHE, "parent POM com.google.guava:guava-parent:26.0-android"),
    "com.google.guava:failureaccess:1.0.3": (APACHE, "jar META-INF/LICENSE"),
    "com.google.guava:guava:33.3.1-android": (APACHE, "parent POM com.google.guava:guava-parent:33.3.1-android"),
    "com.google.guava:guava:33.7.2-android": (APACHE, "jar META-INF/LICENSE"),
    "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava":
        (APACHE, "parent POM com.google.guava:guava-parent:26.0-android; empty placeholder jar"),
    "com.google.protobuf:protobuf-javalite:4.26.1": (BSD, "parent POM com.google.protobuf:protobuf-parent:4.26.1"),
    "com.google.protobuf:protobuf-javalite:4.36.2": (BSD, "parent POM com.google.protobuf:protobuf-parent:4.36.2"),
    "com.google.zxing:core:3.5.3": (APACHE, "parent POM com.google.zxing:zxing-parent:3.5.3"),
    "javax.inject:javax.inject:1": (APACHE, "Gradle cache POM"),
    "net.zetetic:sqlcipher-android:4.19.1": (BSD, "POM has an empty <name>; upstream sqlcipher/sqlcipher LICENSE.md"),
    "net.zetetic:sqlcipher-android:4.20.0": (BSD, "POM has an empty <name>; upstream sqlcipher/sqlcipher LICENSE.md"),
    "org.bouncycastle:bcpkix-jdk15to18:1.86": (BOUNCY, "Gradle cache POM"),
    "org.bouncycastle:bcprov-jdk15to18:1.86": (BOUNCY, "Gradle cache POM"),
    "org.bouncycastle:bcutil-jdk15to18:1.86": (BOUNCY, "Gradle cache POM"),
}


def cache_dir(env=None) -> pathlib.Path:
    """Gradle's module cache: $GRADLE_CACHE, then $GRADLE_USER_HOME, then ~/.gradle."""
    env = os.environ if env is None else env
    if env.get("GRADLE_CACHE"):
        return pathlib.Path(env["GRADLE_CACHE"])
    base = pathlib.Path(env.get("GRADLE_USER_HOME") or pathlib.Path.home() / ".gradle")
    return base / "caches" / "modules-2" / "files-2.1"


def coordinate(line: str) -> str:
    """`group:artifact:version` from a lockfile line."""
    return line.split("=", 1)[0]


def split_coordinate(coord: str):
    parts = coord.split(":")
    return (parts + ["", "", ""])[:3]


def pom_path(group, artifact, version, cache) -> pathlib.Path | None:
    """The cached POM for the coordinate, wherever Gradle's content-addressed folder put it."""
    folder = cache / group / artifact / version
    if not folder.is_dir():
        return None
    found = sorted(folder.glob(f"*/{artifact}-{version}.pom"))
    return found[0] if found else None


def pom_licences(path, cache, depth=0) -> list[str]:
    """Licence names from a POM, or from its parent chain when the POM names none."""
    root = ET.parse(path).getroot()
    declared = [entry.findtext("m:name", default="", namespaces=NS).strip()
                for entry in root.findall("m:licenses/m:license", NS)]
    names = [name for name in declared if name]
    if names:
        return names
    if depth >= 3:
        return []
    parent = root.find("m:parent", NS)
    if parent is None:
        return []
    parent_pom = pom_path(parent.findtext("m:groupId", default="", namespaces=NS),
                          parent.findtext("m:artifactId", default="", namespaces=NS),
                          parent.findtext("m:version", default="", namespaces=NS), cache)
    return pom_licences(parent_pom, cache, depth + 1) if parent_pom else []


def pom_holder(path) -> str:
    """Who publishes the component, from the POM's organisation or first developer; '' when silent."""
    root = ET.parse(path).getroot()
    for where in ("m:organization/m:name", "m:developers/m:developer/m:name"):
        found = root.findtext(where, default="", namespaces=NS).strip()
        if found:
            return found
    return ""


def normalise(names) -> list[str]:
    """Known POM licence names as identifiers, de-duplicated in first-seen order."""
    out = []
    for name in names:
        found = NAMES.get(name.strip().lower())
        if found and found not in out:
            out.append(found)
    return out


def resolve(lines, cache=None) -> dict:
    """{coordinate: {licences, source, attribution?}} for every shipped line."""
    cache = cache or cache_dir()
    components = {}
    for line in lines:
        coord = coordinate(line)
        if not coord.count(":"):
            continue
        group, artifact, version = split_coordinate(coord)
        key = f"{group}:{artifact}"
        path = pom_path(group, artifact, version, cache)
        entry = None
        if path:
            ids = normalise(pom_licences(path, cache))
            if ids:
                entry = {"licences": ids, "source": "gradle-cache-pom"}
                holder = pom_holder(path)
                if holder:
                    entry["attribution"] = holder
        if entry is None and coord in OVERRIDES:
            licence, evidence = OVERRIDES[coord]
            entry = {"licences": [licence], "source": f"override: {evidence}"}
        if entry is None:
            entry = {"licences": [], "source": "unresolved"}
        if key in ELECTIONS:
            elected, alternative, why = ELECTIONS[key]
            if elected in entry["licences"]:
                entry["election"] = {"elected": elected, "alternative": alternative, "basis": why}
        components[coord] = entry
    return components


def load(path=DATA) -> dict:
    """The checked-in map; an absent file reads as an empty map."""
    try:
        return json.loads(path.read_text(encoding="utf-8"))["components"]
    except (OSError, ValueError, KeyError) as error:
        raise SystemExit(f"{path}: {error}") from error


def save(components, path=DATA) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    doc = {
        "format": "sign-setu-licences/1",
        "resolver": "tools/security/licences.py",
        "components": dict(sorted(components.items())),
    }
    path.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    return 0


def ids(coord, components) -> list[str]:
    """Licence identifiers for a coordinate in the map; [] when unresolved."""
    return list((components.get(coord) or {}).get("licences") or [])


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--lock", type=pathlib.Path, default=LOCK)
    parser.add_argument("--out", type=pathlib.Path, default=DATA)
    parser.add_argument("--write", action="store_true", help="rewrite the map from the local cache")
    args = parser.parse_args(argv)
    from sbom import shipped_lines
    lines = shipped_lines(args.lock.read_text(encoding="utf-8").splitlines())
    if not args.write:
        current = load(args.out)
        stale = [coordinate(line) for line in lines if coordinate(line) not in current]
        print(f"{len(lines)} shipped components, {len(stale)} not in {args.out.name}")
        return 1 if stale else 0
    components = resolve(lines)
    save(components, args.out)
    unresolved = [coord for coord, entry in components.items() if not entry["licences"]]
    print(f"{len(components)} components -> {args.out}; unresolved {len(unresolved)}")
    for coord in unresolved:
        print(f"  no licence: {coord}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
