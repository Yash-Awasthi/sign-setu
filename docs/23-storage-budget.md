# Storage budget

What one conversation costs on the officer's phone, and the ceiling the retention
window has to stay inside. Measured 2026-11-26 from the formats the app itself
writes; `StorageBudgetTest` reproduces every number below and fails if one drifts.

## 1. What is stored

A session lives in one encrypted SQLCipher database, `session.db`, and nowhere
else (`docs/04-security.md` section 2.1). Its rows are:

| Part | Format | Where |
| --- | --- | --- |
| Records | one row per event: type, source, language, text, ids, hash | `event` table |
| Voice | WAV, 16 kHz mono 16-bit PCM, one take at most 60 s | `audio` table |
| Frames | the landmarks of one sign window, gzipped JSON, capped at 1.8 MB a window | `landmarks` table |
| Drawings | PNG at the drawing sheet's size | `drawing` table |

Nothing else is kept: no camera picture, no face mesh, no officer audio.

## 2. Measured sizes

| Component | Measured | Note |
| --- | --- | --- |
| Voice, 60 s | 1 920 044 B | 32 000 B a second plus the 44-byte WAV header; the recorder stops at 60 s (`VoiceCapture.MAX_SECONDS`) |
| Frames, 20 s answer | 103 114 B | 600 frames, both hands and pose, gzipped |
| Frames, 3 min narrative | 923 701 B | 5 400 frames, gzipped; the serializer halves the kept frames until a window fits 1.8 MB |
| Drawing | 70 403 B | 1080 x 1440 sheet, 12 strokes, PNG |
| Records | 512 B a row | planning figure for a row with text, ids and a 64-character hash |

A documented worst-case session - one three-minute narrative, one 60-second
recording, one drawing and 400 rows - measures **3 118 948 B (3.1 MB)**. A
typical session - a five-second answer window, an eight-second recording, three
drawings and 40 rows - measures **514 043 B (0.5 MB)**.

The per-session ceiling the app plans for is **8 MB**, which covers the
worst-case composition above twice over.

## 3. Budget and the retention window

The budget is **1 GB** of the app's own data. The retention job deletes a
session once it is older than the officer's window (`Retention`, `RetentionJob`,
`SessionRepository.purgeExpired`); it is time-based, not size-based, so the
budget holds when the arrival rate is the one planned for: **one conversation
every half hour, around the clock** (2 an hour).

| Window | Sessions kept | Worst case | Typical |
| --- | --- | --- | --- |
| 4 h | 8 | 25 MB | 4 MB |
| 8 h | 16 | 50 MB | 8 MB |
| 12 h | 24 | 75 MB | 12 MB |
| 24 h (default) | 48 | 150 MB | 25 MB |
| 48 h | 96 | 299 MB | 49 MB |
| 72 h (maximum) | 144 | 449 MB | 74 MB |

Every window on offer stays inside 1 GB at that rate, with the 72-hour maximum
using 449 MB. Zero means keep until the officer deletes it; that is a department
choice, and the table does not cover it.

## 4. What the assertion covers, and what it does not

The job keeps the device inside the budget in this sense: at the planned rate,
everything inside the window fits. It does not cap a single session: every row
may carry its own 60-second recording and its own landmark window, so a session
with many long takes exceeds the 8 MB ceiling, and a station that runs more than
two conversations an hour past 48 hours can exceed the budget. The control for
both is the retention setting the department picks; the app never deletes a
session early for space.

## 5. Re-measuring

    ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.StorageBudgetTest

The test prints each measured size and the per-window totals, and fails if a
size or a window leaves the documented limits.
