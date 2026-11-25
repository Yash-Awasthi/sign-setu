"""The NDK and CMake pin is one version across the build script, the CMake minimum, the SDK and the docs."""

import pathlib
import shutil
import tempfile
import unittest

import check_toolchain

BUILD = """android {
    defaultConfig {
        externalNativeBuild { cmake { abiFilters += listOf("arm64-v8a", "x86_64") } }
    }

    ndkVersion = "30.0.16248370"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    buildTypes {
        release {
            version = "9.9.9"
        }
    }
}
"""

CMAKE_LISTS = "cmake_minimum_required(VERSION 3.22.1)\nproject(assistant LANGUAGES C CXX)\n"

README = "The build needs JDK 21, Android SDK 37, NDK `30.0.16248370` and CMake 4.1.2.\n"


class CheckToolchainTest(unittest.TestCase):
    def setUp(self):
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, root, ignore_errors=True)
        self.build = root / "build.gradle.kts"
        self.build.write_text(BUILD, encoding="utf-8")
        self.cmake_lists = root / "CMakeLists.txt"
        self.cmake_lists.write_text(CMAKE_LISTS, encoding="utf-8")
        self.readme = root / "README.md"
        self.readme.write_text(README, encoding="utf-8")
        self.docs = root / "docs-README.md"
        self.docs.write_text(README, encoding="utf-8")
        self.sdk = root / "sdk"
        (self.sdk / "ndk" / check_toolchain.EXPECTED_NDK).mkdir(parents=True)
        (self.sdk / "cmake" / check_toolchain.EXPECTED_CMAKE).mkdir(parents=True)

    def check(self):
        return check_toolchain.check(self.build, self.cmake_lists, self.readme, self.docs, self.sdk)

    def test_the_matching_fixture_passes(self):
        self.assertEqual([], self.check())

    def test_a_wrong_ndk_version_names_the_expected_value(self):
        self.build.write_text(BUILD.replace('ndkVersion = "30.0.16248370"', 'ndkVersion = "29.0.1"'), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("30.0.16248370" in e for e in errors), errors)

    def test_a_wrong_cmake_version_names_the_expected_value(self):
        self.build.write_text(BUILD.replace('version = "4.1.2"', 'version = "3.1.0"'), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("4.1.2" in e for e in errors), errors)

    def test_a_cmake_minimum_above_the_expected_version_fails(self):
        self.cmake_lists.write_text(CMAKE_LISTS.replace("3.22.1", "9.0"), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("4.1.2" in e for e in errors), errors)

    def test_a_readme_that_drops_a_version_fails(self):
        self.readme.write_text(README.replace(" and CMake 4.1.2", ""), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("4.1.2" in e for e in errors), errors)

    def test_an_sdk_without_the_expected_components_fails(self):
        empty = self.sdk.parent / "empty-sdk"
        empty.mkdir()
        errors = check_toolchain.check(self.build, self.cmake_lists, self.readme, self.docs, empty)
        self.assertTrue(any("30.0.16248370" in e for e in errors), errors)
        self.assertTrue(any("4.1.2" in e for e in errors), errors)

    def test_the_repository_files_match(self):
        self.assertEqual([], check_toolchain.check(sdk=None))


if __name__ == "__main__":
    unittest.main()
