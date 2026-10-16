# 14. Presentation Script

A spoken walk-through of Sign Setu as built, about 12 minutes with the live demonstration. Stage directions are in italics. Figures match the [product overview](13-product-overview.md).

## Before the meeting

- Emulator or phone running the debug build; phone PIN known; the screen kept awake.
- First run completed and one test session already started, so the conversation is not empty.
- For the assistant: the model file imported in Settings (about 40 seconds on the emulator).
- [System design](system-design.html) open in a browser for the diagrams.

## 1. The problem (1 minute)

"When police find a lost or rescued child who cannot speak, who is deaf, traumatised, or speaks a language the officer does not know, the first conversation is the hardest. There is rarely an interpreter on the spot, and the child is frightened. Sign Setu is an aid for that first conversation. The officer and the child share the officer's phone. It is not an interpreter and it never records statements: anything that will become evidence still goes through a qualified interpreter."

## 2. Design principles (1 minute)

"Four principles shaped it. First, it works completely offline: the app has no internet permission at all, so it works in districts with no signal, including the Naxal-affected areas of Chhattisgarh. Second, privacy: everything is encrypted on the phone and deleted after 24 hours by default. Third, the officer stays in control: nothing reaches the child until the officer sends it, and nothing the child gestures is recorded until the child confirms it. Fourth, it is built for children: a warm stage, a friendly character, large answer buttons."

## 3. Home and profiles (1 minute)

*Show Home.*

"The officer picks who they are helping. A young child gets the child character and spoken questions. Someone who signs gets the adult character and never any sound. Someone who speaks another language first sees a grid of greetings in 17 languages, including Chhattisgarhi, Gondi and Halbi. The officer also picks the language for captions."

## 4. The officer panel (2 minutes)

*Open the session; point at the conversation.*

"The officer side reads like a messaging app. What was shown to the child is on the right, the child's answers with their pictures on the left."

*Point at the category chips and suggestion cards.*

"Prepared questions sit in groups: safety, health, needs, identity, family, location. A sensitive group stays locked until the officer confirms it is needed. Each card shows whether the character has a gesture for it and whether a recording exists, so nothing is a surprise. When a card is chosen, 'Ready to show' lists exactly what the child will get."

*Tap the plus button.*

"Other ways to ask: the officer can describe a gesture the child made, show picture cards, ask the child to draw, or open the language finder."

## 5. The stage (2 minutes)

*Choose "Are you hurt?" and send.*

"The phone now faces the child. The character asks with its hands and face, the caption is in the child's language, and the picture is held up. The child can always answer Yes, No, 'I don't know' or 'Stop', or choose a picture, draw, or write their answer. Their own hands appear in the camera mirror below; nothing from the camera is recorded."

"The app is pinned to this screen, so the child cannot leave it. The officer taps the small lock to take the phone back."

*Tap the lock; show the answer in the conversation.*

## 6. Typing anything (2 minutes)

*Type "where is ravi mother?" and send.*

"Prepared questions cannot cover everything, so the officer can type anything. The character plays it word by word: the question sign for 'where', then it fingerspells R-A-V-I with the two-handed Indian alphabet, showing each letter beside it, then the sign for 'mother' with her picture. The word being signed is lit in the caption. Hindi typed in Devanagari is spelled in Latin letters, the way Deaf signers spell Hindi words. This is word-by-word signing, not full ISL grammar, so prepared questions remain the first choice."

## 7. The on-device assistant (1 minute, optional)

*Type "are you hungry? do you want water?", tap Assistant.*

"For a child who reads another language, an optional assistant translates the officer's words on the phone, with no internet. It also translates its own output back into English, so the officer can see if the meaning went wrong before sending. It is marked as an unchecked machine translation, and it is the officer's choice to use it."

## 8. Security and privacy (1 minute)

"Sessions live in an encrypted database whose key only works after the phone's own lock. The app locks again after five minutes away. Screenshots are blocked, other apps cannot tap through overlays, the keyboard is told not to learn what is typed, and every content file is checked against a fingerprint before it is used. At the end, the officer exports a PDF encrypted with a one-time password, or simply deletes the session."

## 9. Camera and sign recognition (1 minute)

"The camera mirror is built and ready for recognition. No open model can yet read fast, continuous Indian Sign Language reliably, so the plan is practical. MediaPipe tracks the hands and face on the phone. When a signer answers too fast, the app captures points, never video, and lets the officer replay the answer as a stick figure at quarter speed. The partner team's model will recognise individual signs from a small answer vocabulary, and the child still confirms every meaning with Yes."

## 10. What is done and what remains (1 minute)

"Done: the complete offline application, the officer panel, the stage, typed-text signing, the assistant, encryption, export, and the security controls. It passes 17 unit tests and 20 on-device tests, and the release is 19.1 MB."

"Remaining: review of the signs, alphabet and local-language wording by ISL, child-welfare and language reviewers; the hand-and-face tracking layer and the partner team's sign recogniser; tests on a real Android 10 phone and with TalkBack; the CERT-In security audit; and a pilot in one district."

## Likely questions

| Question | Answer |
| --- | --- |
| Does it need the internet? | No. The app has no internet permission. Sending an exported PDF uses whichever app the officer chooses. |
| Is it an interpreter? | No. It helps the first conversation; statements still need a qualified interpreter. |
| Are the signs correct ISL? | The gestures and signs are drafts awaiting ISL review; typed text is word-by-word, not ISL grammar. |
| Does it record the child? | No video, audio or photographs. Session text and drawings are encrypted and deleted after 24 hours or at the end. |
| Can it read the child's signs? | Not yet. The camera layer and capture are planned; the partner team supplies the sign recogniser. |
| What phones does it need? | Android 10 or later. The assistant needs a 64-bit phone with about 3 GB of memory. |
| What does it cost to run? | Nothing per use: no servers, no paid services. |
