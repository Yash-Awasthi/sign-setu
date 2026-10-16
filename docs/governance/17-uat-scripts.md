# 17. User Acceptance Test Scripts

Run by department officers on department phones with the release candidate. One tester acts as officer, one as subject. Record Pass / Fail / Notes per step. A script passes when every step passes. The [user guide](18-user-guide.md) describes each screen.

| Script | Steps | Pass when |
| --- | --- | --- |
| UAT-01 First run | Install; open; read the three screens; pick Hindi for the app; set a screen lock if asked | The notice says "aid, not interpreter"; the app refuses to start sessions until a screen lock exists |
| UAT-02 Lost child | Airplane mode on. Choose Young child and Hindi; start; show "I am a police officer", "Are you hurt?", "Do you want water?"; subject answers with Yes, No and a picture | Each question appears at once with the character, caption, picture and speech; officer sees each answer with its time; nothing needs a network |
| UAT-03 Sensitive gate | Try to open the Sensitive group | Needs a deliberate confirmation; no sensitive question on the first screen |
| UAT-04 Deaf subject | Choose Signs; show "Are you hurt?"; subject answers No; officer uses They gestured for a gesture the subject makes and shows it back | Adult character, no sound; show-back asks "Is this it?" and only Yes records the meaning |
| UAT-05 Own words | In the type box, type "Where is Ravi's home?"; leave the default toggles; tap Send | The stage shows the caption with each word lit in turn: the asking sign for "Where", "Ravi" spelled letter by letter with the letter shown, the home sign with the home picture; nothing reaches the subject before Send |
| UAT-06 Phrase | Phrases, "Do you want ...?", water; show it | The full sentence appears with the picture; the character signs it in parts |
| UAT-07 Stop and don't know | Subject taps I don't know, then Stop | Both logged; Stop shows the officer a banner with guidance until acknowledged |
| UAT-08 Drawing | Subject draws a house and finishes | Drawing appears in the session log |
| UAT-09 Language finder | Choose Other language; start; subject taps their language | Their greeting plays; back on the officer panel the session is in that language |
| UAT-10 Back to officer | On the stage, try Home, Back and recents; then tap the lock | The subject cannot leave the stage by Home, Back or recents; the lock returns to the panel; with Screen lock to leave the stage on, the fingerprint or PIN is asked as well |
| UAT-11 Interruptions | Take a phone call mid-session; leave the app for six minutes; reopen | Session intact; app asks for unlock |
| UAT-12 Interpreter | Call an interpreter from the panel | Messaging app opens at the department contact; no session details are sent |
| UAT-13 Export and end | End session; export; share the report; give the password separately; finish | Report opens only with the password; session gone from the phone |
| UAT-14 Privacy | Try a screenshot on the stage and the panel; open recents | Screenshot blocked; recents thumbnail blank |
| UAT-15 Auto-delete | Leave an unended session past the auto-delete period; reopen | Session deleted |
| UAT-16 Old and small phones | Repeat UAT-02 and UAT-04 on the oldest supported phone and on a 5-inch phone | Same results; answer cards whole; character smooth |
| UAT-17 Large text | Set the phone's font size to largest; repeat UAT-02 | Every screen readable; no text cut off |

**Sign-off:** department nominee signs the completed sheet; failures become defects with severity.
