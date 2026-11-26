import json
import pathlib
import tempfile
import unittest

import licences

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version>
  {body}
</project>
"""


def write_pom(cache: pathlib.Path, group, artifact, version, body) -> pathlib.Path:
    folder = cache / group / artifact / version / "0123456789abcdef"
    folder.mkdir(parents=True)
    path = folder / f"{artifact}-{version}.pom"
    path.write_text(POM.format(group=group, artifact=artifact, version=version, body=body),
                    encoding="utf-8")
    return path


class ResolveTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cache = pathlib.Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_a_pom_that_states_a_licence_is_the_source(self):
        write_pom(self.cache, "a.b", "c", "1.0",
                  "<licenses><license><name>The Apache Software License, Version 2.0</name>"
                  "</license></licenses><organization><name>A. B. Ltd</name></organization>")
        entry = licences.resolve(["a.b:c:1.0=standardReleaseRuntimeClasspath"], self.cache)["a.b:c:1.0"]
        self.assertEqual(entry["licences"], ["Apache-2.0"])
        self.assertEqual(entry["source"], "gradle-cache-pom")
        self.assertEqual(entry["attribution"], "A. B. Ltd")

    def test_the_parent_pom_is_read_when_the_component_names_none(self):
        write_pom(self.cache, "p", "parent", "9",
                  "<licenses><license><name>BSD-3-Clause</name></license></licenses>")
        write_pom(self.cache, "a.b", "c", "1.0",
                  "<parent><groupId>p</groupId><artifactId>parent</artifactId>"
                  "<version>9</version></parent>")
        entry = licences.resolve(["a.b:c:1.0=releaseRuntimeClasspath"], self.cache)["a.b:c:1.0"]
        self.assertEqual(entry["licences"], ["BSD-3-Clause"])

    def test_a_coordinate_with_no_pom_and_no_override_is_unresolved_not_guessed(self):
        entry = licences.resolve(["nobody:nowhere:1.0=releaseRuntimeClasspath"], self.cache)
        self.assertEqual(entry["nobody:nowhere:1.0"],
                         {"licences": [], "source": "unresolved"})

    def test_the_jna_election_is_recorded_only_when_the_alternative_is_declared(self):
        write_pom(self.cache, "net.java.dev.jna", "jna", "5.19.1",
                  "<licenses><license><name>Apache-2.0</name></license>"
                  "<license><name>LGPL-2.1-or-later</name></license></licenses>")
        entry = licences.resolve(["net.java.dev.jna:jna:5.19.1=releaseRuntimeClasspath"],
                                 self.cache)["net.java.dev.jna:jna:5.19.1"]
        self.assertEqual(entry["election"]["elected"], "Apache-2.0")
        self.assertEqual(entry["election"]["alternative"], "LGPL-2.1-or-later")

    def test_an_unknown_licence_name_is_left_out_rather_than_mapped_to_a_guess(self):
        write_pom(self.cache, "a.b", "c", "1.0",
                  "<licenses><license><name>Some Homegrown Terms</name></license></licenses>")
        entry = licences.resolve(["a.b:c:1.0=releaseRuntimeClasspath"], self.cache)["a.b:c:1.0"]
        self.assertEqual(entry["licences"], [])

    def test_the_saved_map_round_trips_and_sorts(self):
        components = licences.resolve(["z.z:z:1=releaseRuntimeClasspath",
                                       "a.a:a:1=releaseRuntimeClasspath"], self.cache)
        path = self.cache / "licences.json"
        licences.save(components, path)
        text = path.read_text(encoding="utf-8")
        self.assertLess(text.index('"a.a:a:1"'), text.index('"z.z:z:1"'))
        self.assertEqual(licences.load(path), components)
        self.assertEqual(json.loads(text)["format"], "sign-setu-licences/1")


class CheckedInMapTest(unittest.TestCase):
    def test_the_map_covers_every_shipped_coordinate(self):
        import sbom
        lock = licences.ROOT / "app" / "gradle.lockfile"
        components = licences.load()
        lines = sbom.shipped_lines(lock.read_text(encoding="utf-8").splitlines())
        missing = [licences.coordinate(line) for line in lines
                   if licences.coordinate(line) not in components]
        self.assertEqual(missing, [], "run python tools/security/licences.py --write")

    def test_the_map_resolves_every_component_to_at_least_one_licence(self):
        unresolved = [coord for coord, entry in licences.load().items() if not entry["licences"]]
        self.assertEqual(unresolved, [])

    def test_both_elections_are_in_the_checked_in_map(self):
        components = licences.load()
        self.assertEqual((components["net.java.dev.jna:jna:5.19.1"]["election"]["elected"],
                          components["net.java.dev.jna:jna:5.19.1"]["election"]["alternative"]),
                         ("Apache-2.0", "LGPL-2.1-or-later"))
        self.assertEqual(
            components["org.checkerframework:checker-compat-qual:2.5.3"]["election"]["elected"],
            "MIT")


if __name__ == "__main__":
    unittest.main()
