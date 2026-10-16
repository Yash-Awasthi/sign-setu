# 14. Presentation Script

A spoken walk-through of Sign Setu as built, about 15 minutes with the live demonstration. Stage directions are in italics. Figures match the [product overview](13-product-overview.md).

## Before the meeting

- Emulator or phone running the debug build; phone PIN known; the screen kept awake.
- First run completed. In Settings, "Allow automatic conversation" turned on for the Auto demonstration.
- For sign reading on the emulator: the debug stand-in hand (`adb shell run-as in.signsetu touch files/demo_hands`); on a 64-bit phone, real hands.
- For the assistant: the model file imported in Settings.
- For the second screen: a second phone or tablet already paired in Bluetooth settings (the emulator has no Bluetooth).
- [System design](system-design.html) open in a browser for the diagrams.

## 1. The problem (1 minute)

"When police meet a person who cannot speak, who is deaf, traumatised, or speaks a language the officer does not know, the first conversation is the hardest. The person may be a lost child, a victim, a witness or someone just arrested. There is rarely an interpreter on the spot. Sign Setu is a bridge for that first contact. It is never the statement: anything that will become evidence still needs a qualified interpreter and the statutory video recording, and every page the app exports says so."

## 2. Design principles (1 minute)

"Five principles shaped it. It works completely offline: the app has no internet permission at all. Privacy: everything is encrypted on the phone and deleted after 24 hours by default, and the camera is analysed only when the person is asked to sign. The officer stays in control: nothing reaches the person until the officer sends it. The person owns the meaning: a reading by the app or by the officer stays a reading until the person picks it themselves. And it is built for frightened people: a warm stage, a calm pause, large answers."

## 3. Home, role and the opening (1 minute)

*Show Home; choose Young child, leave the role as Not known yet, start.*

"The officer picks who they are helping, their language, and, once known, whether this is a victim or witness or an accused person. Until then the app offers only the shared questions."

*Let the opening run.*

"The stage opens with the officer's introduction, four ground rules in pictures and signs: I was not there, you can say I don't know, tell me if I'm wrong, you can take a break; then a practice round. This is how good child interviews start."

## 4. The officer page (2 minutes)

*Take the phone back; point at the conversation.*

"The officer side reads like a messaging app. Each answer carries a grade, Said, Confirmed, Officer's reading, App's reading, Said no or Unclear, and a trail: how long it took and whether the person changed it. A slow or changed answer is outlined, which means look again, never that someone is lying."

*Type "wat" and point at the completions.*

"Completions are tinted by what the character can do with the word: a sign, a gesture, a picture, or spelling. One tap prepares a reviewed phrase."

*Long-press two quick questions; show the run.*

"A question run puts up to five safe questions on one handover. Sensitive questions are never queued."

## 5. The stage (2 minutes)

*Send "Do you want water?"*

"The character asks with its hands and face, the caption is in the person's language, the picture is held up. Yes and No are always there, and under them I don't know, I don't understand and That's wrong. Break is a tea cup; Stop is a stop sign, never an open palm. Picture choices are shuffled every time, with Something else, which opens the drawing pad."

*Tap Stop.*

"Stop covers the character with a calm pause: a breathing circle and bubbles, no score, no sound. Only the person's I'm ready ends it. The officer feels three short pulses and sees the answer upside down on the strip facing them."

*Take the phone back with the small lock.*

## 6. Reading signs (2 minutes)

*Tap Read signs; sign, or let the stand-in hand move; tap Done.*

"A crying person signs fast. The camera is analysed only inside this window: twenty seconds for an answer, or three minutes for an account with Tell me. MediaPipe tracks the hands on the phone, and the app splits the signing at pauses and reads each sign with a model trained on the INCLUDE dataset of Indian signs."

*Open the reading sheet; replay at quarter speed.*

"The officer sees the app's reading, marked not checked, and can replay the signing at a quarter speed. An interpreter can do the same later in the session. Then Ask them to choose: the person sees two or three pictures for each sign and None of these. Only their tap makes it their answer. Landmarks stay in memory and are wiped when the session ends or the app locks."

## 7. Typing anything and the assistant (1 minute)

*Type "where is ravi mother?" and send.*

"The character signs typed words in draft ISL order, question word last, spells names with the two-handed alphabet, and lights each word in the caption. For a person who reads another language, an optional assistant translates on the phone and back into English, so the officer can see a wrong meaning before sending."

## 8. Roles and rights (1 minute)

*Set the role to Accused; open the Rights group; show one item.*

"For an accused, the rights under the BNSS appear in pictures and signs: grounds of arrest, a lawyer, a doctor, a Magistrate within 24 hours, no forced self-incrimination. Each says the rights must also be explained through a qualified interpreter, and the record says shown, not proof of understanding. A child in conflict with the law gets the Juvenile Justice protections instead."

## 9. Auto and the second screen (1 minute)

*Switch to Auto; approve one suggestion.*

"Auto suggests the next welfare question from the reviewed bank, in protocol order, and the officer approves each one. It never suggests sensitive or rights questions, and a second Stop ends it."

*Open Second screen.*

"A department tablet, paired once in Bluetooth settings, can show the stage while the officer keeps the phone. The officer types the code from the tablet and both screens show the same check number. Everything is still stored only on the officer's phone, and the tablet keeps nothing."

## 10. Security and privacy (1 minute)

"Sessions live in an encrypted database whose key only works after the phone's own lock. The app locks again after five minutes away, and after a crash on the stage it always asks for the full fingerprint or PIN. Screenshots are blocked, other apps cannot tap through overlays, and every content file and model is checked against a fingerprint before it is used. A library's usage statistics are blocked at build time. At the end, the officer exports a PDF encrypted with a one-time password, or simply deletes the session."

## 11. What is done and what remains (1 minute)

"Done: the offline application, the stage, sign reading on the phone, roles and rights, Auto, the second screen, the graded record, and the security controls. It passes 120 unit tests, and the release is 47 MB."

"Remaining: review of the signs, ISL order, rights wording and local-language text by ISL, legal, child-welfare and language reviewers, through the reviewer portal; sign reading measured with real phones and children; the two-phone test; TalkBack on a real phone; the CERT-In security audit; and a pilot in one district."

## Likely questions

| Question | Answer |
| --- | --- |
| Does it need the internet? | No. The app has no internet permission. Sending an exported PDF uses whichever app the officer chooses. |
| Is it an interpreter? | No. It bridges first contact; statements still need a qualified interpreter and the statutory video recording. |
| Are the signs correct ISL? | The gestures, signs and ISL word order are drafts awaiting Deaf review. |
| Does it record the person? | No video, audio or photographs. Hand and face points are analysed only in a sign window, kept in memory for the session and never stored. |
| Can it read the person's signs? | Isolated signs from a 262-word vocabulary, as proposals. The person confirms every meaning by picking it. Yes, no and pain are not yet in the vocabulary; picture answers cover them. |
| Can the officer use it on an accused? | Only to communicate and to show rights, each marked shown, not understood. It never asks an accused for an account. |
| What phones does it need? | Android 10 or later. Sign reading needs a 64-bit phone; the assistant needs about 3 GB of memory. The second screen needs Bluetooth. |
| What does it cost to run? | Nothing per use: no servers, no paid services. |
