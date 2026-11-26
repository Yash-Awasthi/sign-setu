# Build baseline: the tree at 402339f, measured 2026-11-27

Twelve agents made changes in the same working copy at once, so the working copy no longer
describes what any one of them measured. This page is the other end of that: the numbers taken
from the commit itself, so a later change can be compared with a tree that nobody had touched.

The tree measured is a clean export of HEAD `402339f` (`a11y: give the person's answers a
keyboard and single-switch path`) on branch `fleet/recal-2026-11-26`, made with
`git archive HEAD` into an empty directory outside the repository. Nothing in that directory was
edited after the export. `local.properties` is not in the commit, so the same file the working
copy uses (SDK path and gateway URL) was copied in; no other file was added.

## 1. Environment

| Item | Value |
| --- | --- |
| Commit | `402339f` |
| JDK | Temurin 21.0.12.1+1 LTS |
| Gradle | 9.8.2 (wrapper, `--no-daemon`) |
| Android SDK | as named by `local.properties`; platform 37 by the build's own configuration |
| Gradle cache | enabled; some tasks restored `FROM-CACHE` as noted per command |
| Configuration cache | stored anew for each of the three commands |

## 2. Commands

Each command ran on its own, with `--console=plain`, and exited 0.

| Command | Gradle's time | Wall clock | Tasks |
| --- | --- | --- | --- |
| `:app:testStandardDebugUnitTest` | 2m 29s | 45m 20s | 29: 16 executed, 13 from cache |
| `:app:assembleStandardDebug` | 1m 56s | 11m 07s | 44: 18 executed, 5 from cache, 21 up-to-date |
| `:app:compileStandardDebugAndroidTestKotlin` | 33s | 3m 12s | 31: 4 executed, 6 from cache, 21 up-to-date |

Gradle's own time is the `BUILD SUCCESSFUL in ...` line. The wall clock is the whole process,
and it is larger on every row because the Gradle calls from all the agents share one lock
(`C:\GITHUB\.fleet\gradle.lock`) and wait for each other; the two figures are both reported so
neither is mistaken for the other. A wall clock taken on a quiet machine has not been measured
here.

## 3. Unit tests

| Figure | Value |
| --- | --- |
| Tests | 440 |
| Test classes | 64 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 2 |
| Sum of per-class times | 18.9 s |

Counted from the 64 XML files under `app/build/test-results/testStandardDebugUnitTest`. The
two skipped cases are `JoinScan :: scan` and `PlanDump :: dump`, both guarded so they only run
when a person asks for the scan or the dump.

The slowest classes, by their own reported time, are `OnlineAssistantTest` 4.592 s,
`SentenceEvalTest` 2.023 s, `AutoPilotTest` 1.776 s, `ConversationE2ETest` 1.669 s and
`StorageBudgetTest` 1.557 s.

## 4. APK

| Figure | Value |
| --- | --- |
| Path | `app-standard-debug.apk` under the debug output of the `standard` flavour |
| Bytes | 327927941 |
| MiB | 312.7 |
| MB | 327.9 |

This is the debug build, unsigned by a release key and carrying debug symbols; it is not the
size of a release APK and is not comparable with the release sizes quoted elsewhere.

## 5. Warnings that repeat

Two compiler warnings repeat more than five times. Both are deprecations of Compose APIs whose
replacements exist in the version this build uses; neither breaks the build.

| Command | Warning | Times |
| --- | --- | --- |
| Unit tests | `ListItem` overload with `headlineContent` deprecated; the trailing `content` lambda replaces it | 17 |
| Instrumented compile | `createAndroidComposeRule` deprecated in favour of the `junit4.v2` rule | 26 |
| Instrumented compile | `createComposeRule` deprecated for the same reason | 1 |

The whole unit test run printed 34 warnings, the whole instrumented compile 29. The rest are
one-off deprecations (`BasicTextField` with `String`, `VisualTransformation`, `OffsetMapping`,
`rememberModalBottomSheetState`, `Slider`, `PasswordVisualTransformation`, `TextMeasurer`) plus
one redundant conversion and one unnecessary safe call.

Two other lines are worth knowing and are not warning counts:

- Each of the three runs ends with `Deprecated Gradle features were used in this build, making it
  incompatible with Gradle 10.` Gradle does not name them without `--warning-mode all`, which was
  not passed, so this page does not claim where they come from.
- The `assembleStandardDebug` run configures CMake twice per ABI and each time prints
  `fatal: not a git repository (or any of the parent directories): .git`. That is the exported
  tree having no `.git`, not a defect in the build; a checkout does not print it. The same run
  reports `Unable to strip the following libraries, packaging them as they are: libjnidispatch.so.`
  once.

## 6. What these numbers do not say

They do not say the build is fast, and they are not a threshold: a single run on one machine,
with a warm Gradle cache, is not a performance measurement. They say what the tree at `402339f`
does when built, so that a later failure, a newly repeating warning, or a test count that moved
can be recognised as a change rather than a guess. The instrumented tests were compiled, not run:
`compileStandardDebugAndroidTestKotlin` proves the test sources build, and nothing here speaks for
what they do on a device.
