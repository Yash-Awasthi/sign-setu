# 11. Product Overview

A single reference for explaining Sign Setu: what it does and how it behaves for the officer and the subject. How it is built is in [architecture](02-architecture.md); the camera and sign readers are in [hand model integration](09-hand-model-integration.md).

## 1. Purpose

Sign Setu is an offline Android application that helps a police officer communicate with a person who cannot communicate in words: deaf, non-verbal, traumatised, or speaking another language; a victim, a witness or an accused person; a child or an adult. Officer and subject share the officer's phone, or the subject uses a paired second screen. The application is a bridge for first contact, never the statement: it is a communication aid, not an interpreter, and never records statements (BNSS s.173 and s.183(6), BSA s.125, POCSO s.26). Every export says "communication note, not a statement", and the app reminds the officer that a qualified interpreter and the statutory video recording are required for statements.

| Property | Value |
| --- | --- |
| Platform | Android 10 (API 29) and later; sign reading on 64-bit phones |
| Network | Optional: the department gateway, or the officer's own custom API endpoint chosen in the same dialog, only when the officer switches the online assistant on and the phone is online; everything works without it |
| Languages for the subject | 17 in the language finder, including Chhattisgarhi, Gondi and Halbi; every stage word, question, phrase and picture label in all 17 (drafts beyond English and Hindi); typed English captioned in Hindi on the phone; sign languages ISL and ASL |
| Officer interface languages | English, Hindi |
| Release APK | arm64-v8a phone APK and universal APK (arm64-v8a and x86_64); nothing compressed for size |

## 2. How a session works

1. The officer opens the app and confirms with the phone's own screen lock or a strong biometric; one confirmation covers the next minute of gated steps. A relock, a crash or a restart always asks again.
2. On Home the officer chooses who they are helping (young child, signs, other language, not sure), the person's role (left as "Not known yet" until known) and the subject's language, then starts.
3. The stage opens with the officer's introduction, four ground rules and a practice round, or with the language finder for someone who speaks another language.
4. The officer prepares something on the officer page (a prepared question, a phrase, typed text, pictures, a drawing request, a sign window) and shows it with Send.
5. The subject sees the stage and answers by tapping, drawing, writing or signing.
6. The officer holds the small handle to take the phone back and reads the answer, its grade and its trail in the conversation.
7. At the end the officer saves the session under a name, exports an encrypted PDF communication note, or deletes it. Sessions idle past the retention period (24 hours by default) are deleted.

## 3. Features

### 3.1 Officer side

The officer page has two tabs, built from one card and tile style. **Transcribe** holds the conversation, quick questions and the composer; **More** searches every question group and phrase and also holds They gestured, the picture board, Draw, the language finder, Tell me, the ISL dictionary, the session log with notes, and profile, role or language. With a blank search it shows the applicable question groups as category chips. Picking something on More returns to Transcribe.

| Feature | Description |
| --- | --- |
| Profiles | Young child (automatic speech), signs (no sound), other language (language finder first), not sure |
| Role | Not known yet (default), victim or witness, accused or in custody; sets which question groups appear (§4.3). An accused may be marked "may be under 18" |
| Conversation | A messaging-style view: items shown to the subject on the right, the subject's answers with pictures on the left, notes and steps in between |
| Prepared questions | 80 questions in 15 groups: Safety, Health, Needs, Personal, Family, Address, School, Everyday, Talking (ground rules, breaks, closing), Their account, Sensitive (victim only, locked until the officer confirms), Rights, Arrest rights (adult), Child's rights, Disability rights. A young child is not asked for a mobile number; an accused gets the factual Personal, Family, Address and School questions but not the welfare-only ones (introduce yourself, favourite subject, what they like at school) |
| Lesson-sheet questions | An ISL lesson sheet in full: name, age, mobile, introduction; father's and mother's names, family size, brothers and sisters, who they live with; where they live, village or city, district, state, nearby place; school, class, teacher, favourite subject, what they like at school; and eight everyday phrases. Each has an answer type that opens its own input on the stage (§3.2), and the character signs it through the typed-text path in draft ISL order |
| Quick questions | Hurt, water, hungry, toilet, doctor, mother nearby, age and where you live, each disappearing once asked |
| Question run | Long-press questions to queue them, or tap "Safety basics": up to 5 on one handover, each 1.4 s after the previous answer. Sensitive questions are never queued. Stop, "Something else" or taking the phone back ends the run, logged "Run ended after k of n" |
| Phrase bank | 10 reviewed sentences built from two patterns, "Do you want [water]?" and "Are you [cold]?" |
| Type anything | Typed text is shown at once; the character plays it word by word under four toggles: Signs, Gestures, Spell, Pictures. With Signs on, the order is draft ISL order (§4.1) |
| Word completions | Above the keyboard; each typed word is tinted by what the character will do with it: green for a sign, teal for a gesture, amber for a picture, no tint when it will be spelled. Reviewed phrase templates about the word prepare a phrase in one tap |
| Vocabulary | 228 entries covering 1,338 written forms in English, Hindi, Hindi in Latin letters and local words |
| Answer trail | Under each answer: time taken, every answer in the order given if the person changed it, and the length of a pause after Stop. A changed answer, or one slower than 20 s, gets an amber outline, which means "look again", never deceit |
| Evidence grades | Said (the person tapped, wrote or drew it), Confirmed (the person picked this meaning from two or three choices shown back), Officer's reading, App's reading, Said no, Unclear |
| Say it back | Shows the officer's reading, edited if needed, to the person among the item's other pictures; only their pick makes it Confirmed; No marks the line "Said no" |
| Read signs and Tell me | Open a sign window: a short answer of up to 20 s, or an account of up to 3 minutes (§6); on the stage, raised hands also open an answer window |
| Signed answers | The app's reading per sign; the reader's top four guesses per sign as chips, logged once as the officer's reading when every sign has a pick; slow replay at ¼× or ½× with sign markers and a typed word per sign; and **Ask them to choose** |
| Manual and Auto | Auto puts the next welfare item from the reviewed bank on the stage by itself; the officer can pause, skip or take over at any time. It is not an interview and records no statement. The report shows the mode, who chose each shown item, and suggestions not acted on |
| Media marks | Each question card shows whether a gesture and a recording exist and whether the caption is missing in the subject's language; "Ready to show" lists what the subject will get |
| Instant Hindi caption | When the subject reads Hindi, English the officer types gets a Hindi caption as they type, from the reviewed bank, sentence patterns or word by word, marked as unchecked; a tap dismisses it, and nothing is shown if any word is unknown |
| Assistant (optional) | Translates typed text into the subject's language and back into English, on the phone or through the gateway; the officer sees both before sending ([assistant](10-on-device-assistant.md)) |
| They gestured | The officer marks where the subject's hand went and what it did; matching entries from the 33-entry gesture lexicon are shown back to the subject, who confirms the meaning |
| Picture board | 117 picture cards in 12 categories (including clothes and colours for describing someone), with search and favourites |
| ISL dictionary | Every recorded and draft sign by category (517 signs in 17 categories: 262 retargeted from INCLUDE, 208 from the ISLRTC dictionary and 47 from the Kaggle ISL videos, plus the authored word signs); an ASL dictionary does the same for ASL, searched in English, Hindi or any written form; a tap plays the sign for the officer, Show prepares it for the stage |
| Draw, language finder | A drawing pad for the subject; a grid of greetings in every language |
| Interpreter call | Opens the department's Sandes or WhatsApp contact; no session data is passed |
| Second screen | Pairs a department phone or tablet as the subject's screen (§3.3) |
| Notes and log | Officer notes and the full session record |
| Export | A PDF communication note encrypted with AES-256 under a one-time 16-character password shown once |
| Settings | Interpreter app and contact; Pin the app, Block screenshots and Ask for fingerprint (each turned off only with the officer's credential); camera mirror, signing speed, ISL word order; the two sign languages with what each reads and signs and how accurately, the sign reader engine; online assistant and its enrolment, assistant model; retention period (4 to 72 hours or keep), delete all data; appearance and colour theme, app language, licences |
| Practice | Type to sign: any English or Hindi sentence played by the character outside a session, with nothing recorded |

### 3.2 Subject side (the stage)

| Feature | Description |
| --- | --- |
| Character | A 3D character framed to the hips, with jointed fingers and facial expressions; motions cover 517 ISL signs retargeted from recorded signers and the ISLRTC dictionary, 112 recorded ISL sentences, 42 everyday gestures, the 26-letter two-handed ISL alphabet, 10 digits and the authored word signs |
| Opening | The officer's introduction (s1), then four pictured and signed ground rules ("you can stop or take a break whenever you want", "it is okay to say I don't know", "tell me if I get something wrong", "I did not see what happened, tell me in your own way") and a neutral practice round, "Show me something you did this morning", answered by drawing, writing or signing. The subject moves on with Next; each step is logged as shown. If the officer takes the phone back before the practice round, the log records "Officer skipped the rest of the ground rules" |
| Caption | The question in the subject's language in large type; during typed text the current word is lit, a spelled word shows its current letter, and a word's picture is held up. Tapping a word replays it alone at half speed; tapping the question replays it |
| Speech | One female voice per language: the greeting plus about 90 clips in each of 15 languages, from ElevenLabs (English, Hindi), Cartesia (Chhattisgarhi, Bengali, Tamil, Telugu, Kannada, Malayalam, Marathi, Gujarati, Punjabi) and Indic Parler-TTS (Assamese, Nepali, Odia, Urdu); typed text is spoken through the gateway when online help is on; the rest show captions and pictures; Gondi and Halbi have no voice; plays once by itself for every subject, then on request |
| Layout | One video call for every item: the character full height, the subject's own camera in a movable window, the caption, and an answer bar (Water and Food as requests, Yes, No, Help for the full picture board) under Stop, Next on the opening, "I don't know", "I don't understand" and "That's wrong". The opening shows no Yes or No; a rights item shows only "I don't understand" and Stop. Pictures, drawing, writing, lesson inputs, the language finder and choices open in the lower part of the call with Stop beneath. Stop is an octagonal sign, never an open palm, and stays on every screen. Every tap is logged right after the prompt that was on screen |
| Gesture proposals | A nod, head shake, thumb, raised palm, shrug or a finger held on a body part lights the matching answer; namaste, a wave, a hand on the chest or a bow offers a courtesy word. Only the subject's tap answers |
| Picture choices | Shuffled every time and capped at four pictures, plus "Something else" (the pictures not yet seen, four at a time, then the drawing pad) and "I don't understand". Yes/no, number and single-picture questions are not shuffled |
| Answer inputs | For a lesson-sheet question the panel under the character becomes its input: a number pad with large digits (age 0 to 120, counts, brothers then sisters), a 10-digit mobile pad grouped 5 and 5 with "No phone", a written name or place, a class grid (1 to 12, college, not in school), a state list (Chhattisgarh first, all states and union territories) then its district or a written one, and several pictures with Done. A written or typed answer is read back large and becomes the subject's own only on their Yes; one left unconfirmed is logged as unconfirmed. "I don't know", "I don't understand", "That's wrong" and Stop stay under every input |
| Phrase board | A button by the character opens the eight everyday phrases as pictures with words; a tap logs the phrase as the subject's own |
| Draw and Write | A drawing pad with five ink colours and Undo; a drawing that answers a question returns to it, a requested drawing ends on a thank-you and hand-back screen; Write opens the keyboard for every profile except a young child; written answers are capped at 2,000 characters |
| Sign an answer | Raised hands or the camera button open a 20-second sign window; quiet hands close it; the stage shows one dot per sign seen. The reading goes to the officer only (§6) |
| What to do now | One plain line over the camera window: answer by tapping or signing, or tap Next; statements show only Next and Stop |
| Choosing a meaning | Two or three picture cards per sign in the subject's language, and "None of these", when the officer taps Ask them to choose |
| Camera mirror | The subject sees the whole camera frame, uncropped at 9:16, with hands, arms, brows and lips traced, never the face oval or eyes; nothing is captured or stored |
| Replay and speed | The subject sets the signing speed (½×, ¾×, 1×), kept for the session in memory only; a young child starts at ¾× at most |
| Requests | Water and Food sit on the call's answer bar whenever no panel is open; a tap is logged as the subject's request ("Asked for"), not as an answer to what is on screen, and does not move a question run on |
| Pause | After Stop a calm night-sky layer with a breathing circle and bubbles to pop covers the whole stage, with no score, sound or timer. Only the subject's "I'm ready" ends it; the officer's lock stays reachable. The log records "Pause ended after N s". Stop is also the way to ask for a breather |
| Vibration codes | Felt by whoever holds the phone: Yes one long buzz; No two buzzes; Stop, Break, "I don't know", "I don't understand" or "That's wrong" three short pulses; a picture a tick. Never for the young-child profile |
| Rotate | The stage turns 180 degrees for a subject sitting opposite |
| Staying on the stage | The app is pinned for the session (lifted only for the interpreter call or a share, and set again on return); Back does nothing; the small handle held for 1/3 s returns to the officer |

Subject-screen words, questions, phrases and card labels exist in English, Hindi, Chhattisgarhi, Gondi, Halbi and the other twelve languages of the language finder; everything beyond English and Hindi is marked draft.

### 3.3 Paired stage

A second phone or tablet, paired once in system Bluetooth settings, shows the subject's stage while the officer keeps their phone. The officer phone stays the only store; the stage device keeps nothing about the session, has no local exit while live, and at the end stays pinned on a hand-back screen until its own credential is given. Stop and "I don't know" are always available on it; Stop opens a break that blocks Show until the officer taps "Break given". Paired mode does not change the app's non-evidentiary status.

With the online assistant on, a paired stage can host the **AI counsellor** for a person who is afraid of the police: the counsellor writes one short, non-leading line per turn from the conversation so far, the officer sees each line for two seconds (Send now, Another line, Take over) before it is signed and captioned on the stage, and Stop, a break or a flagged risk hands the conversation back. It is never offered for an accused person, and every line is marked "AI counsellor" in the record.

### 3.4 Safety and privacy

| Control | Description |
| --- | --- |
| Offline | Every feature works with no connection; no RECORD_AUDIO or background location in any build and no location in `standard`, enforced by a build gate |
| Encrypted record | SQLCipher (AES-256) under a random key wrapped by an Android Keystore key that works only after the phone's own lock |
| Relock | After five minutes away from the app, thirty inside a session; a relock, crash or restart always asks for the credential |
| Retention | Sessions are deleted once idle past 24 hours by default, or a set period of 4 to 72 hours, or kept until deleted if the officer chooses; while the app is locked, everything is wiped once even the newest session is past retention; "Delete all data" destroys the keys |
| Screen | Screenshots blocked in the final release build (`secureScreen`); taps through another app's overlay ignored; no backup or device transfer |
| Keyboard | Every text field asks the keyboard not to learn from what is typed |
| Integrity | Every content file, the vision and sign-reader models and the assistant model are checked by SHA-256 before use |
| Landmarks | Biometric data: kept only from sign windows, in memory for the session, never stored or exported, except a skeleton drawing when the officer turns evidence mode on |
| Record integrity | Every row sealed into a SHA-256 hash chain; export re-checks it and adds a draft BSA s.63 certificate |
| Expiry | A build refuses new sessions 180 days after it was made; rooted phones are warned, list no saved sessions and delete a session when it is finished |
| Excluded by design | Identity columns in the database (personal details exist only as the subject's own logged answers, purged with the session), statements, audio or video recording, identity recognition, thumb or open-palm signals for yes, no or stop, body maps for the subject |

## 4. Mechanisms

Unlock and keys, write before show, the layered record, content integrity, pairing and export are described in [architecture](02-architecture.md) §6 to §12.

### 4.1 Typed text into signs

Typed text is split into words (a possessive keeps its stem; runs of up to three words that form one entry, such as "thank you" or "how many", stay together). Each word becomes, in order of preference: the recorded ISL sign, the authored sign, then the everyday gesture (with Signs on); the everyday gesture (with Gestures on); fingerspelling (with Spell on; grammar words such as "is" and "का" are skipped); or a short pause that holds the word's picture (with Pictures only). With Signs on, words play in draft ISL order: time words first, the other words as typed, then negation, then the question word; the caption keeps the typed order and lights each word as it is signed. Hindi is spelled akshara by akshara on the ISL two-handed alphabet (पानी is प, आ, न, ई); in ISL order words ISL leaves unsigned (in, at, with, it) are dropped, and the ISL word order setting can keep the typed order. A question mark adds the asking face. The ordering is a draft until Deaf review.

### 4.2 Show-back and say-back

A meaning is never inferred silently. When the subject gestures, the officer describes the place and action; candidates are ranked by both, then place, then action; the subject sees "Is this it?". Where the item has other pictures, the subject picks among two or three of them, and only that pick makes the meaning theirs (Confirmed); a Yes to a single reading is their Yes, and the reading stays the officer's.

### 4.3 Roles and rights

The role is "Not known yet" until the officer sets it, and it is exported only when set. With the role not known only shared groups appear. The sensitive group appears for a victim; the rights groups for an accused. The rights items say: grounds of arrest at once (BNSS s.47); bail if bailable, with surety (s.47); a relative or friend told (s.48); a lawyer during questioning (s.38); a government doctor, and a woman doctor for a woman (s.53); a Magistrate within 24 hours, travel time not counted (s.58, and s.187 for longer custody); no forced self-incrimination (Art. 20(3)). A young child, or an accused marked as possibly under 18, gets the child's rights under the Juvenile Justice Act instead of the adult arrest rights. Each rights item carries "rights must also be explained through a qualified interpreter" (an officer card and a subject line on the stage) and is recorded "shown, not proof of understanding". Disability rights (RPwD Act s.7(4)) appear for a victim. All rights wording is a draft for legal review.

### 4.4 Manual and Auto

Manual is the default. In Auto a rule-ordered sequencer over the reviewed bank picks the next welfare item (rapport, ground rules, welfare, practical, break, closing) and shows it after a short gap; it never proposes sensitive, free-recall, follow-up or rights items. Stop pauses Auto; a second Stop ends it. A typed question, a show-back or an event question sent by hand turns Auto off. Taking the phone back pauses Auto.

### 4.5 Instant Hindi caption

With a Hindi-reading subject, Latin text the officer types is translated on the phone, without a model, 250 ms after typing stops. A reviewed English and Hindi pair in the content wins: it is matched on lower case with punctuation, articles and contractions removed. Next come about 30 sentence patterns (Do you want X? Are you X? Is X hurt? Where is X? Did someone hurt you? Show me X, Can you X? I will X, Do not X, How many X? and others) with nouns from the picture cards, the gesture lexicon and words.json, polite आप forms, honorific agreement for parents and officials, and "हम" for the officer so no gender is assumed. Last, if every word is known, a word-by-word draft in Hindi order. Otherwise there is no caption. The draft is shown marked "machine translation, not checked"; a tap dismisses it for that message, and Send makes it again from the final text. The character signs the officer's own words; an assistant suggestion replaces the caption. The signed-answer draft uses the same idea for common verbs ("मुझे पानी चाहिए").

## 5. The character in detail

The character is a 3D VRM model rendered with three.js in a WebView that serves only the app's own avatar files. Motions stay numbers: keyframes in a 2D front view, placed on the model each frame.

| Part | Location | Content |
| --- | --- | --- |
| Host | `ui/avatar/Avatar.kt` | The WebView, its asset-only request filter, and the commands it sends (play, clip, view) |
| Avatar module | `avatar/src` (built to `assets/avatar/avatar.js`) | `rig.ts`: named body points, named and measured handshapes, face presets, keyframe interpolation with Catmull-Rom for recorded keys. `retarget.ts`: rig points to model space, two-bone arms at the model's lengths, hand turn, finger bends, expressions. `clip.ts`: 48-point clip kinematics. `stage.ts`: scene, model, playback |
| Model | `assets/avatar/model.vrm` | `ISL Sample 1` by Leo, VRM 1.0 |
| Motions | `assets/content/gestures.json`, `signs.json`, `isl_signs.json`, `clips.json` | Keyframes: time, each hand's target point, handshape, palm roll and angle, face, head and body; recorded sentence clips |

Each frame: the clock time selects two keyframes, which are blended with easing (or, for recorded keys less than 0.35 s apart, a Catmull-Rom curve); each hand is placed at a named point (for example `mouth`, `chest`, or `index_opp`, the other hand's index fingertip) and carried onto the model through matching landmarks; the arm is solved at the model's bone lengths; fingers bend by curl values; the face preset sets the model's expressions; breathing, blinking, sway and occasional glances are added. The reviewer portal renders the same module.

| Strengths | Limits |
| --- | --- |
| Offline; a motion is corrected by editing numbers; motions chain smoothly; faces carry ISL facial grammar (question brows, negation head shake); 517 signs follow real signers; one module for app and portal | Depth is a heuristic, so contact with the face or body is approximate; the 12 MB model needs a licence from its author before release; a WebView with WebGL costs memory beside the camera; the alphabet, authored and recorded signs await ISL review; no mouthing |

## 6. Camera and sign reading

Windows, frame rates, segmentation and the slower cue are in [hand model integration](09-hand-model-integration.md) §2 to §5. While signing the stage shows one dot per sign seen, never the reader's words. Every signed answer and account stays in view-model memory for the session, so an interpreter can replay it at ¼× or ½× with sign markers and enter a word per sign.

### 6.1 The subject owns meaning

- The app's reading, the officer's reading and the subject's answer are three separate layers ([architecture](02-architecture.md) §7).
- A reading becomes the subject's answer only when the subject picks it: for each sign, two or three picture cards in the subject's language (the officer's reading first, then the reader's guesses, then the item's own answer cards, in shuffled positions) and "None of these". They appear only when the officer taps "Ask them to choose"; until then a signed answer stays the app's or the officer's reading.

### 6.2 Context-limited vocabulary

- `assets/content/sign_words.json` (draft, needs ISL review) maps the reader's labels to the app's vocabulary concepts, for example house to home and bathroom to toilet.
- While a question with two or more answer pictures is on screen, every guess whose concept is one of those pictures ranks first, at any score; with one illustrative picture only the top three are reordered. A label can stand for several cards (`cards` in `sign_words.json`, for example police for a police station). On an unseen signer this puts the right lesson answer first 73% of the time and among the three shown 88% (`tools/ml/EVAL.md`).
- Core answer words the bundled reader cannot read: yes, no, water, pain, help, stop, don't know (yes and no come from nods, shakes and the buttons). Mother, father, police, home (house), toilet (bathroom), drink (water), come and give are covered.
- Fingerspelled names are read on the phone by a separate two-hand reader and win only when every letter is read with confidence; signs the officer reads as a word the reader did not offer are recognised for the rest of the session.

## 7. Exporting and the network

Export works without a network. The PDF is written on the phone and handed to Android's share sheet; only the application the officer chooses (for example WhatsApp or email) uses its own connection. The PDF says "Communication note, not a statement. A statement needs a qualified interpreter and the statutory video recording." at the top and bottom of every page, followed by a grade legend.

| Item | Exported | Reason |
| --- | --- | --- |
| Answers, confirmed meanings, notes, typed and written answers | Yes, each with its grade in brackets, its choice position and its trail | Part of the session record |
| "Shown as" rows | Yes | What the person actually saw |
| Personal details given | Yes, as a table before the conversation: each field, the value, how it was given (written, number pad, picture, list, sign read back by the officer) and whether the person confirmed it | Lesson-sheet answers in one place |
| Assistant caption with the officer's own words | Yes | Both are logged |
| Readings by the sign reader or the officer | Yes, in a separate section after the conversation ("Readings by the app or the officer. These are not the person's own answers.") | Proposals, not answers; source MACHINE or OFFICER |
| Subject confirmations of a signed meaning | Yes, as ordinary answers after a "Shown: Is this it? A / B / C" row | The subject's own pick |
| Mode, who chose each item, suggestions not acted on | Yes | Auto accountability |
| Role | Only when set | "Not known yet" is not a fact about the person |
| Answers from a second screen | Yes, marked "from second screen" | Provenance |
| Landmarks and replays | Never | Biometric data |
| Skeleton snapshot of a signed answer | Only with evidence mode on for the session | One drawn frame, no picture of the person |
| Hash-chain status and certificate | Yes: chain head, record digest on every page, draft BSA s.63 certificate | Integrity of the record |
| Machine draft summary | Only when the officer attaches it, marked not part of the record | A draft, not evidence |
| Camera video or photographs | Never | The application records nothing |
