"""Audits app/gradle.lockfile against OSV and fails on a pinned version below its fix.

Reads the shipped release runtime lines, asks api.osv.dev/v1/querybatch in batches
of 100 with per-query pagination, then /v1/vulns/{id} for every hit. A hit fails
the gate only when OSV names a fixed version above the version pinned here; a
vulnerability with no fix is printed but does not fail, because no pin can clear it.

--offline re-checks against the committed snapshot so the same decision can be
reproduced without the network, and fails when the lockfile has a triple the
snapshot never queried, because then the snapshot cannot certify it.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import re
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
SNAPSHOT = ROOT / "tools" / "security" / "data" / "osv-snapshot-2026-11-27.json"
BATCH_ENDPOINT = "https://api.osv.dev/v1/querybatch"
VULN_ENDPOINT = "https://api.osv.dev/v1/vulns/"
ECOSYSTEM = "Maven"
BATCH_SIZE = 100
TIMEOUT = 90


def shipped_lines(lines):
    """Lockfile lines on any release runtime classpath, the set the SBOM and CI scan."""
    return [line for line in lines if "=" in line and "releaseruntimeclasspath" in line.split("=", 1)[1].lower()]


def components(lines):
    """Sorted (group:artifact, version) pairs for every shipped line, duplicates removed."""
    out = set()
    for line in shipped_lines(lines):
        coordinate = line.split("=", 1)[0]
        parts = coordinate.split(":")
        if len(parts) != 3:
            raise ValueError(f"not a group:artifact:version coordinate: {coordinate}")
        out.add((f"{parts[0]}:{parts[1]}", parts[2]))
    return sorted(out)


def version_key(text):
    """Numeric runs as numbers, everything else as text, so 33.3.1-android sorts under 33.7.2."""
    key = []
    for part in re.split(r"[.\-_+]", str(text).lower()):
        if part.isdigit():
            key.append((1, int(part), ""))
        elif part:
            key.append((0, 0, part))
    return key


# A missing trailing token outranks a qualifier, so 4.0.0 is above 4.0.0-rc-1.
RELEASE = (2, 0, "")


def compare_versions(a, b):
    """-1, 0 or 1 for a Maven-ish version pair; enough to order the fixed releases OSV names."""
    ka, kb = version_key(a), version_key(b)
    for index in range(max(len(ka), len(kb))):
        left = ka[index] if index < len(ka) else RELEASE
        right = kb[index] if index < len(kb) else RELEASE
        if left != right:
            return -1 if left < right else 1
    return 0


def fixed_after(vuln, package, pinned):
    """The lowest fixed release OSV names for this package that is above the pin, or None."""
    fixed = set()
    for affected in vuln.get("affected") or []:
        named = affected.get("package") or {}
        if named.get("ecosystem") != ECOSYSTEM or named.get("name") != package:
            continue
        for version_range in affected.get("ranges") or []:
            for event in version_range.get("events") or []:
                if event.get("fixed"):
                    fixed.add(event["fixed"])
    above = [f for f in fixed if compare_versions(f, pinned) > 0]
    return min(above, key=version_key) if above else None


def aliases(vuln):
    return [a for a in vuln.get("aliases") or [] if a.startswith("CVE-")]


def severity(vuln):
    """GitHub's label when OSV carries one, then the CVSS vector type, then unknown."""
    label = (vuln.get("database_specific") or {}).get("severity")
    if label:
        return str(label).upper()
    vectors = vuln.get("severity") or []
    if vectors:
        return str(vectors[0].get("type") or vectors[0].get("score") or "unknown")
    return "UNKNOWN"


def _fetch(request, attempts=3):
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError):
            if attempt == attempts - 1:
                raise


def _post(body):
    return _fetch(urllib.request.Request(
        BATCH_ENDPOINT,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    ))


def _get(vuln_id):
    return _fetch(urllib.request.Request(VULN_ENDPOINT + vuln_id))


def query_ids(pairs, post=_post):
    """{package, version, vuln_ids} for each (package, version), paging per query as OSV asks."""
    found = [{"package": package, "version": version, "vuln_ids": []} for package, version in pairs]
    for start in range(0, len(found), BATCH_SIZE):
        chunk = found[start:start + BATCH_SIZE]
        pending = list(range(len(chunk)))
        first = True
        while pending:
            queries = []
            for index in pending:
                row = chunk[index]
                query = {"package": {"ecosystem": ECOSYSTEM, "name": row["package"]}, "version": row["version"]}
                if not first:
                    query["page_token"] = row.pop("_token")
                queries.append(query)
            response = post({"queries": queries})
            results = response.get("results") or []
            if len(results) != len(queries):
                raise RuntimeError(f"{len(results)} results for {len(queries)} queries")
            following = []
            for index, result in zip(pending, results):
                row = chunk[index]
                row["vuln_ids"].extend(v["id"] for v in result.get("vulns") or [])
                token = result.get("next_page_token")
                if token:
                    row["_token"] = token
                    following.append(index)
            pending = following
            first = False
    for row in found:
        row.pop("_token", None)
        row["vuln_ids"].sort()
    return found


def collect(pairs, post=_post, get=_get, generated=None, lock=LOCK):
    """The snapshot document: every triple queried, its vuln ids, and each vuln record in full."""
    results = query_ids(pairs, post=post)
    ids = sorted({vid for row in results for vid in row["vuln_ids"]})
    return {
        "generated": generated or datetime.date.today().isoformat(),
        "ecosystem": ECOSYSTEM,
        "source": "https://api.osv.dev/v1",
        "lockfile": {
            "path": lock.relative_to(ROOT).as_posix() if lock.is_relative_to(ROOT) else str(lock),
            "sha256": hashlib.sha256(lock.read_bytes()).hexdigest(),
        },
        "components": [{"package": package, "version": version} for package, version in pairs],
        "results": results,
        "vulns": {vid: get(vid) for vid in ids},
    }


def hits(snapshot):
    """One record per (component, vulnerability), fix None when OSV names no fixed release."""
    vulns = snapshot["vulns"]
    out = []
    for row in snapshot["results"]:
        for vid in row["vuln_ids"]:
            vuln = vulns[vid]
            out.append({
                "id": vid,
                "aliases": aliases(vuln),
                "severity": severity(vuln),
                "package": row["package"],
                "version": row["version"],
                "fixed": fixed_after(vuln, row["package"], row["version"]),
                "summary": vuln.get("summary") or "",
            })
    return sorted(out, key=lambda h: (h["package"], h["version"], h["id"]))


def uncovered(snapshot, pairs=None):
    """Lockfile triples the snapshot never queried, so an offline run cannot vouch for them."""
    queried = {(row["package"], row["version"]) for row in snapshot["results"]}
    return [f"{package}:{version}" for package, version in (pairs if pairs is not None else components(
        LOCK.read_text(encoding="utf-8").splitlines())) if (package, version) not in queried]


def line(hit):
    names = f" ({', '.join(hit['aliases'])})" if hit["aliases"] else ""
    fix = hit["fixed"] or "no fixed release"
    return (f"{hit['id']}{names} {hit['severity']} {hit['package']}:{hit['version']} "
            f"fixed-in {fix}: {hit['summary']}")


def main(argv=None, lock=LOCK, snapshot=SNAPSHOT) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--lock", type=pathlib.Path, default=lock)
    parser.add_argument("--snapshot", type=pathlib.Path, default=snapshot)
    parser.add_argument("--offline", action="store_true", help="re-check the committed snapshot, no network")
    parser.add_argument("--write-snapshot", action="store_true", help="write the live result to --snapshot")
    args = parser.parse_args(argv)

    pairs = components(args.lock.read_text(encoding="utf-8").splitlines())
    if args.offline:
        if args.write_snapshot:
            print("--write-snapshot needs a live query; drop --offline", file=sys.stderr)
            return 2
        data = json.loads(args.snapshot.read_text(encoding="utf-8"))
        missing = uncovered(data, pairs)
        if missing:
            for triple in missing:
                print(f"snapshot does not cover {triple}", file=sys.stderr)
            print(f"{len(missing)} lockfile triples missing from {args.snapshot}", file=sys.stderr)
            return 1
    else:
        data = collect(pairs, lock=args.lock)
        if args.write_snapshot:
            args.snapshot.parent.mkdir(parents=True, exist_ok=True)
            args.snapshot.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")

    found = hits(data)
    failing = [hit for hit in found if hit["fixed"]]
    print(f"{len(pairs)} components, {len(found)} vulnerabilities, {len(failing)} below a fixed release")
    for hit in found:
        print(line(hit))
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main())
