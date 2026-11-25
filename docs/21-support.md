# 21. Support

This page is for the officer at the counter, the person who meets a bad reading or a failed export first. It covers what to do in the room, what to attach when the department needs a report, how that report travels, and what the department commits to answer. It assumes the app is the issued one and the session is still on the phone.

## 1. A bad signing, during the session

The first action is in the room, not the help desk. Do not record the reading as the person's answer. The reading stays logged as the app's reading: docs/04-security.md section 2.10 says readings are marked as readings and printed in their own section, and the "Signing regression" row in section 9 of the same file puts it plainly, that a reading is never an answer.

Offer the question again and let the person answer their own way. The officer taps "Type what was signed" (`reading_correct`) or "Ask them to choose" (`signed_ask`), or asks the person to sign again (`reading_sign_again`), which asks for the camera and good light.

The session carries on. Nothing is deleted and nothing is typed over the reading. The report is written after the session ends, not during it: the room comes first, and a report written mid-session costs the person time they came to spend.

## 2. A failed export

A failed export shows `export_failed`: "Could not create the report: the phone may be full. The session is unchanged." (app/src/main/res/values/strings.xml line 121). The first action is to free some space and choose Export report once more, because the message says the session is unchanged and the export is rebuilt from the stored rows.

If it fails a second time, do three things. Keep the session. Export the record to the case file by the department's own route, which may mean a second phone or the station computer. Then report the failure.

Do not delete the session to "fix" the export. The record is the case evidence, and retention deletes it on its own schedule: 24 hours idle by default (docs/02-architecture.md). A deleted session cannot be exported again.

The PDF waits in cacheDir/exports only briefly, since files older than 15 minutes, and all of them at every unlock, are deleted (docs/02-architecture.md). Save it to the case file promptly, not at the end of the shift.

## 3. What to attach

A short list, sent with the report:

- the version and build date from Settings, where `settings_version` shows "Version %1$s, built %2$s" from the version name and the build day (app/src/main/java/app/signsetu/ui/settings/SettingsScreen.kt line 198)
- the phone model and its Android version
- the exact on-screen message, word for word
- the time
- the session name
- for a bad reading, the word, the profile and whether a second screen was in use
- the exported PDF, or the SS1 seal line when one exists, whose shape docs/04-security.md section 2.10 gives as SS1|session|sealed entries|chain head|Merkle root
- how many other exports worked

Nothing is attached automatically. The officer gathers these items and sends them with the report.

## 4. How it is sent, and who receives it

The report goes through the department's own support channel, which is held by a role and not a name [confirm]. The PDF travels by the department's case-file route and never by a personal messaging app. A report has no password, and the app says so at export: anyone who holds it can read the conversation, so it is shared only with the case file or the CWC (strings.xml line 118).

A report that fails "Check a report" (`check_report`, strings.xml line 732), or that verifies as altered, is not a help-desk matter. It is a security matter for the records custodian, and it follows docs/04-security.md section 9 and section 9.2, and docs/13-dpia.md.

## 5. Response window

These are commitments the department adopts, and support is measured against them.

- Acknowledge within one working day.
- A bad signing in a live session is resolved in the room by the officer, and support answers the same working day.
- A failed export gets a workaround or a fresh export path within one working day, and a cause within five working days.
- Anything that may have exposed personal data starts the six-hour CERT-In clock at once.

The last point is the hard one. docs/04-security.md section 9.2 and docs/13-dpia.md put CERT-In within six hours under the CERT-In Directions of 28 April 2022, with the Data Protection Board and the affected persons notified under the 2025 Rules.

No person, phone number or email is named here. Every real contact is [confirm], as the conventions line in docs/README.md explains.

## 6. What support never asks for

Support never asks for the device PIN, the officer's credential, or a build installed from outside the department release. The app has no password to read a current report, so a request for one is a sign of a wrong channel. If someone asks, stop and report it through the department channel in section 4.
