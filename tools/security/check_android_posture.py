"""Check the shipped APK's security posture from the tree, without Gradle or the Android SDK.

Five failures stop the check:
  1. a dangerous permission the app declares but no code calls (a permission it does not need);
  2. an exported activity, service, receiver or provider with no `android:permission` and no
     documented reason in EXPORT_REASONS (the launcher activity is the only one);
  3. cleartext HTTP allowed by `android:usesCleartextTraffic` or by a network security config;
  4. `android:allowBackup` left true (or unset, which defaults to true) with no backup rules that
     exclude data, or rules named but absent;
  5. a hardcoded key or token found by pattern or by entropy in `app/src` or `brain/`.

It also reads the merged manifests under `app/build/` when they exist, and reports what they add,
but does not gate on build output that a clean checkout does not have. A debuggable application
element in a source manifest is a sixth failure: only the debug build type may set it.

Warnings (logged, exit stays 0) cover risky WebView settings, PendingIntents without
FLAG_IMMUTABLE, exported dynamic receivers and clipboard writes, so the review in
`docs/29-android-posture.md` starts from the whole list.
"""
import argparse
import math
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
NEARBY = ROOT / "app" / "src" / "nearby" / "AndroidManifest.xml"
SOURCE = ROOT / "app" / "src" / "main"
SECRET_ROOTS = (ROOT / "app" / "src", ROOT / "brain")
DOCS = ROOT / "docs" / "29-android-posture.md"
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
TAGS = ("activity", "service", "receiver", "provider")

# Android's dangerous permissions, the ones a person is asked to grant at runtime. A declared one
# with no call site is a permission the app asked for and never used.
DANGEROUS = {
    "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
    "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES",
    "ACCESS_LOCAL_NETWORK",
}

# What a call site looks like in the app's own Kotlin. The first match is evidence enough.
CALL_SITES = {
    "CAMERA": r"Manifest\.permission\.CAMERA|CameraManager|CameraSelector|ImageAnalysis",
    "RECORD_AUDIO": r"Manifest\.permission\.RECORD_AUDIO|AudioRecord\(",
    "VIBRATE": r"Vibrator|vibrate\(",
    "USE_BIOMETRIC": r"BiometricPrompt|USE_BIOMETRIC",
    "RECEIVE_BOOT_COMPLETED": r"setPersisted\(|BOOT_COMPLETED",
    "INTERNET": r"HttpURLConnection|java\.net\.|OkHttp|ktor|iroh",
    "ACCESS_NETWORK_STATE": r"ConnectivityManager",
    "ACCESS_LOCAL_NETWORK": r"ACCESS_LOCAL_NETWORK",
    "BLUETOOTH": r"BluetoothAdapter|BluetoothDevice|BluetoothSocket|createRfcommSocket",
    "BLUETOOTH_CONNECT": r"BLUETOOTH_CONNECT|BluetoothDevice|BluetoothAdapter",
    "BLUETOOTH_SCAN": r"BLUETOOTH_SCAN|startDiscovery",
    "BLUETOOTH_ADMIN": r"startDiscovery",
}

# A library asks for these; the app never reads them. The reason is repeated in the posture doc.
LIBRARY_ONLY = {
    "ACCESS_FINE_LOCATION": "Nearby Connections needs it on the nearby flavour; the app never reads a location",
    "ACCESS_COARSE_LOCATION": "Nearby Connections needs it on the nearby flavour; the app never reads a location",
    "BLUETOOTH_ADVERTISE": "Nearby Connections advertises on the nearby flavour; the app never calls the advertiser",
    "NEARBY_WIFI_DEVICES": "Nearby Connections connects on the nearby flavour; the app never opens the Wi-Fi API",
}

# An exported component may justify itself here instead of carrying a permission. The table in
# docs/29-android-posture.md repeats each reason next to the manifest line.
EXPORT_REASONS = {
    "MainActivity": "the launcher entry: HOME starts it, and its only filter is MAIN/LAUNCHER",
}

# Known key shapes, checked outside string literals too.
SECRET_PATTERNS = (
    (re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"), "private key"),
    (re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "AWS access key"),
    (re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b"), "Google API key"),
    (re.compile(r"\bsk-[A-Za-z0-9]{20,}\b"), "service API key"),
    (re.compile(r"\bghp_[A-Za-z0-9]{36}\b"), "GitHub token"),
    (re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"), "Slack token"),
    (re.compile(r"\beyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\b"), "JWT"),
)
SECRET_ASSIGN = re.compile(
    r"(?i)\b(api[_-]?key|secret|token|password|passwd|bearer|private[_-]?key)\b\s*[:=]\s*[\"']([^\"'\s]{8,})[\"']"
)
QUOTED = re.compile(r"[\"']([A-Za-z0-9+/=_.\-]{32,120})[\"']")

TEXT_SUFFIXES = {".kt", ".java", ".py", ".json", ".xml", ".kts", ".properties", ".sh", ".ps1",
                 ".toml", ".yaml", ".yml", ".cfg", ".ini", ".txt", ".js", ".ts"}
SKIP_PARTS = {"third_party", "__pycache__", "build", ".gradle", "assets"}


def parsed(path: pathlib.Path):
    """The manifest's root element; a manifest that will not parse is an error, not a crash."""
    return ElementTree.parse(path).getroot()


def declared(root) -> dict[str, object]:
    """Every permission the manifest keeps, by short name. `tools:node="remove"` drops one."""
    out = {}
    for node in root.iter("uses-permission"):
        if node.get(f"{TOOLS}node") == "remove":
            continue
        name = (node.get(f"{ANDROID}name") or "").removeprefix("android.permission.")
        if name:
            out[name] = node
    return out


def component_name(node) -> str:
    return (node.get(f"{ANDROID}name") or "").rsplit(".", 1)[-1]


def filters(node) -> list[dict]:
    """Every intent filter's actions, categories and data entries."""
    out = []
    for node_filter in node.findall("intent-filter"):
        out.append({
            "actions": [n.get(f"{ANDROID}name") or "" for n in node_filter.findall("action")],
            "categories": [n.get(f"{ANDROID}name") or "" for n in node_filter.findall("category")],
            "data": [dict(n.attrib) for n in node_filter.findall("data")],
        })
    return out


def components(root) -> list[tuple[str, object]]:
    """Every activity, service, receiver and provider, in manifest order; a `tools:node="remove"`
    entry is one the app refuses, so it is not a component."""
    application = root.find("application")
    if application is None:
        return []
    return [(tag, node) for tag in TAGS for node in application.iter(tag) if node.get(f"{TOOLS}node") != "remove"]


def res_xml(value: str, manifest: pathlib.Path) -> pathlib.Path | None:
    """`@xml/name` beside the manifest, or the main resources for a flavour manifest."""
    name = value.removeprefix("@xml/")
    for base in (manifest.parent, ROOT / "app" / "src" / "main"):
        candidate = base / "res" / "xml" / f"{name}.xml"
        if candidate.is_file():
            return candidate
    return None


def cleartext_errors(application, manifest: pathlib.Path) -> list[str]:
    """Cleartext on the application element or anywhere in its network security config."""
    errors = []
    flag = (application.get(f"{ANDROID}usesCleartextTraffic") or "").lower()
    if flag == "true":
        errors.append(f"{manifest}: android:usesCleartextTraffic=\"true\"")
    config = application.get(f"{ANDROID}networkSecurityConfig")
    if config:
        path = res_xml(config, manifest)
        if path is None:
            errors.append(f"{manifest}: networkSecurityConfig names a missing file: {config}")
            return errors
        network = parsed(path)
        base = network.find("base-config")
        if base is not None and (base.get("cleartextTrafficPermitted") or "").lower() == "true":
            errors.append(f"{path}: base-config permits cleartext traffic")
        for domain_config in network.iter("domain-config"):
            if (domain_config.get("cleartextTrafficPermitted") or "").lower() != "true":
                continue
            domains = [d.text or "" for d in domain_config.iter("domain")]
            errors.append(f"{path}: cleartext permitted for {', '.join(domains) or 'a domain'}")
    return errors

def backup_errors(application, manifest: pathlib.Path) -> list[str]:
    """A backup that leaves the phone must have rules, and the named rule files must exist."""
    if (application.get(f"{ANDROID}allowBackup") or "true").lower() == "false":
        return []
    rules = [application.get(f"{ANDROID}fullBackupContent"), application.get(f"{ANDROID}dataExtractionRules")]
    present = [value for value in rules if value]
    if not present:
        return [f"{manifest}: allowBackup is true (or unset) with no fullBackupContent or dataExtractionRules"]
    errors = []
    for value in present:
        path = res_xml(value, manifest)
        if path is None:
            errors.append(f"{manifest}: backup rules name a missing file: {value}")
        elif not list(parsed(path).iter("exclude")):
            errors.append(f"{path}: backup rules exclude nothing, so the record is uploaded")
    return errors


def exported_errors(root, manifest: pathlib.Path, reasons: dict[str, str]) -> list[str]:
    """Every exported component must carry a permission or a documented reason; any component with
    an intent filter must say `android:exported`."""
    errors = []
    for tag, node in components(root):
        name = component_name(node)
        exported = node.get(f"{ANDROID}exported")
        if filters(node) and exported is None:
            errors.append(f"{manifest}: <{tag} android:name=\"{name}\"> has an intent filter but no android:exported")
            continue
        if (exported or "false").lower() != "true":
            continue
        permission = node.get(f"{ANDROID}permission")
        if permission:
            continue
        if name in reasons:
            continue
        errors.append(f"{manifest}: exported <{tag}> {name} has no android:permission and no reason in EXPORT_REASONS")
    return errors


def debug_errors(application, manifest: pathlib.Path) -> list[str]:
    if (application.get(f"{ANDROID}debuggable") or "false").lower() == "true":
        return [f"{manifest}: android:debuggable=\"true\""]
    return []


def source_text(source: pathlib.Path) -> str:
    """Every Kotlin and Java code line under the source root, with its path and line number.
    Comments are blanked, so a permission named in prose is not evidence of a call site."""
    parts = []
    for path in sorted(source.rglob("*")):
        if path.suffix not in (".kt", ".java") or not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        text = re.sub(r"/\*.*?\*/", lambda found: "\n" * found.group(0).count("\n"), text, flags=re.DOTALL)
        text = re.sub(r"//[^\n]*", "", text)
        for number, line in enumerate(text.splitlines(), 1):
            parts.append(f"{path.relative_to(source)}:{number}:{line}")
    return "\n".join(parts)


def call_site(pattern: str, text: str) -> str | None:
    found = re.search(pattern, text)
    if found is None:
        return None
    line = text.count("\n", 0, found.start()) + 1
    return text.splitlines()[line - 1].split(":", 2)[-1].strip()


def permission_errors(root, source: pathlib.Path) -> tuple[list[str], list[tuple[str, str]]]:
    """A dangerous permission with no call site fails; every declared permission is reported with
    the first line that uses it, or with the library that asks for it."""
    errors, seen = [], []
    text = source_text(source)
    for name in declared(root):
        pattern = CALL_SITES.get(name)
        site = call_site(pattern, text) if pattern else None
        if site:
            seen.append((name, site))
        elif name in DANGEROUS and name not in LIBRARY_ONLY:
            errors.append(f"dangerous permission {name} is declared but no code calls it in {source}")
        elif name in LIBRARY_ONLY:
            seen.append((name, LIBRARY_ONLY[name]))
        else:
            seen.append((name, "no call site (not a dangerous permission)"))
    return errors, seen


def files(roots) -> list[pathlib.Path]:
    out = []
    for root in roots:
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            if SKIP_PARTS & set(path.parts):
                continue
            out.append(path)
    return out


def entropy(value: str) -> float:
    counts = Counter(value)
    return -sum((n / len(value)) * math.log2(n / len(value)) for n in counts.values())


def secret_errors(roots) -> list[str]:
    """Known key shapes, assignments to key-ish names, and high-entropy literals in code/config.
    Content bundles, models and vendored C++ are data, not source, and are skipped."""
    errors = []
    for path in files(roots):
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for number, line in enumerate(text.splitlines(), 1):
            for pattern, kind in SECRET_PATTERNS:
                if pattern.search(line):
                    errors.append(f"{path}:{number}: hardcoded {kind}")
            assignment = SECRET_ASSIGN.search(line)
            if assignment:
                errors.append(f"{path}:{number}: hardcoded {assignment.group(1)}")
        for match in QUOTED.finditer(text):
            value = match.group(1)
            if "://" in value or value.count(".") >= 2:
                continue
            if entropy(value) >= 4.3:
                number = text.count("\n", 0, match.start()) + 1
                errors.append(f"{path}:{number}: high-entropy literal, possible key: {value[:12]}...")
    return errors


def warnings(source: pathlib.Path) -> list[str]:
    """Review leads, never failures: the doc records why each is safe or who decides."""
    out = []
    text = source_text(source)
    if "addJavascriptInterface" in text:
        out.append("WebView bridge: addJavascriptInterface is present; confirm file access stays off and only assets are served")
    if re.search(r"setAllowFileAccess\(true\)|setAllowUniversalAccessFromFileURLs\(true\)", text):
        out.append("WebView: file access from file URLs is enabled")
    for line in text.splitlines():
        if "PendingIntent." in line and "FLAG_IMMUTABLE" not in line and "FLAG_MUTABLE" not in line:
            out.append(f"PendingIntent without an explicit mutability flag: {line.strip()}")
        if "RECEIVER_EXPORTED" in line:
            out.append(f"exported dynamic receiver: confirm a protected system broadcast: {line.strip()}")
        if "setPrimaryClip" in line or "setClipEntry" in line:
            out.append(f"clipboard write: confirm no session content: {line.strip()}")
    return out


def merged_manifests() -> list[pathlib.Path]:
    """Release build output, when it exists: what the libraries add to the shipped manifest."""
    intermediates = ROOT / "app" / "build" / "intermediates" / "merged_manifest"
    return sorted(intermediates.glob("*Release*/*/AndroidManifest.xml")) if intermediates.is_dir() else []


def source_manifests() -> list[pathlib.Path]:
    """Both flavours' source manifests; the standard one is the main manifest."""
    return [path for path in (MAIN, NEARBY) if path.is_file()]


def effective_application(application, base) -> object:
    """A flavour's `<application>` merged over the main one, as the manifest merger does: the
    flavour wins on an attribute it sets, and inherits every attribute it leaves alone."""
    if base is None:
        return application
    merged = ElementTree.Element("application")
    merged.attrib.update(base.attrib)
    merged.attrib.update(application.attrib)
    return merged


def check(manifest: pathlib.Path = MAIN, source: pathlib.Path = SOURCE,
          secret_roots=SECRET_ROOTS, reasons: dict[str, str] | None = None,
          base_application=None) -> tuple[list[str], list[str]]:
    """The failures and the review warnings for one source manifest."""
    reasons = EXPORT_REASONS if reasons is None else reasons
    root = parsed(manifest)
    application = root.find("application")
    errors = []
    if application is None:
        return [f"{manifest}: no <application>"], []
    application = effective_application(application, base_application)
    errors += cleartext_errors(application, manifest)
    errors += backup_errors(application, manifest)
    errors += exported_errors(root, manifest, reasons)
    errors += debug_errors(application, manifest)
    permission_problems, _ = permission_errors(root, source)
    errors += permission_problems
    errors += secret_errors(secret_roots)
    return errors, warnings(source)


def check_all(manifests: list[pathlib.Path] | None = None) -> tuple[list[str], list[str]]:
    """Every source manifest, with the secret scan and the review warnings counted once. A flavour
    manifest is read as an overlay on the main one, so an inherited flag is not a missing flag."""
    chosen = manifests if manifests is not None else source_manifests()
    main_root = parsed(MAIN)
    base = main_root.find("application") if MAIN in chosen else None
    errors, review = [], []
    for manifest in chosen:
        manifest_errors, manifest_review = check(manifest, base_application=base)
        errors += manifest_errors
        review += manifest_review
    return list(dict.fromkeys(errors)), list(dict.fromkeys(review))


def report(manifest: pathlib.Path, source: pathlib.Path, reasons: dict[str, str] | None = None) -> list[str]:
    """The audit lines, for the operator: every permission and component with its evidence."""
    root = parsed(manifest)
    _, seen = permission_errors(root, source)
    lines = [f"{manifest}"]
    lines += [f"  permission {name}: {site}" for name, site in seen]
    for tag, node in components(root):
        exported = node.get(f"{ANDROID}exported") or "unset"
        permission = node.get(f"{ANDROID}permission") or "none"
        lines.append(f"  {tag} {component_name(node)}: exported={exported} permission={permission}")
    return lines


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--manifest", type=pathlib.Path, help="one manifest; defaults to both flavours")
    parser.add_argument("--source", type=pathlib.Path, default=SOURCE)
    parser.add_argument("--quiet", action="store_true", help="print only failures and warnings")
    args = parser.parse_args(argv)
    manifests = [args.manifest] if args.manifest else source_manifests()
    if not manifests or not all(path.is_file() for path in manifests):
        print(f"no manifest at {args.manifest or MAIN}", file=sys.stderr)
        return 1
    if not args.quiet:
        for manifest in manifests:
            print("\n".join(report(manifest, args.source)))
    errors, review = check_all(manifests)
    for path in merged_manifests():
        print(f"merged manifest present: {path.relative_to(ROOT)}")
    for warning in review:
        print(f"warning: {warning}", file=sys.stderr)
    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    print(f"{len(errors)} error(s), {len(review)} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
