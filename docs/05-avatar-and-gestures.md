# 05. Avatar and Gesture Design

Status: Accepted (D-12, D-13, D-14, D-20, D-21). Replaces the skeleton avatar in D-03. Release 1 draws both characters in code (D-21, section 5.7). Background and evidence are in the [critical review](archive/22-critical-review.md).

## 1. Answers in brief

| Question | Answer |
| --- | --- |
| Can we build an avatar? | Yes, in-house, with free tools and no budget (section 9). The rendering is the easy part; the cost is team time to author and review every sign and gesture. Another group is reported to be building an ISL avatar; we do not depend on it, but our clips use the open VRM rig standard so a compatible character could play them later. |
| Is there a prebuilt one we can use? | No usable one. See section 2. We build our own characters and reuse open-source tools for the pipeline ([document 24](09-open-source.md)). |
| Indian signs differ from general ones. | Correct. ISL is a separate language from ASL and BSL (different vocabulary; two-handed fingerspelling against ASL's one-handed), and ISL itself has regional varieties (often grouped as Mumbai-Delhi, Kolkata and Bangalore-Chennai-Hyderabad). ASL is also used in some Bengaluru schools. No foreign avatar or sign asset can be reused for vocabulary. |
| Small children do not know signs. | Correct, and this applies to most deaf children too (review C-1). Children get a separate **gesture lexicon** of iconic, culturally common gestures (bindi or sindoor for mother, moustache for father, roof for home, hand on chest for me), with several variants per concept. |
| Two avatars, a child hero and an adult? | Yes: two original characters on one shared rig, playing from one motion library. Copying Shin-chan or Nobita is not possible (section 4). |

## 2. What already exists, and why none of it fits

| Product or project | What it is | Why not |
| --- | --- | --- |
| CWASA / JASigning (UEA) with SiGML and HamNoSys | Research signing-avatar engine driven by sign notation | Research licence; ISL coverage negligible; every sign must be written in HamNoSys by an expert |
| GitHub student projects (Unity or Blender ISL avatars, "text to ISL" pipelines) | Prototypes | Unreviewed ISL, word-for-word grammar, no licence clarity, unmaintained |
| Ishaara | Commercial browser text-to-ISL avatar | Proprietary web service; would send officer text to a third party; ISL quality not independently validated |
| sign.mt | Open multilingual sign translation app | CC BY-NC-SA licence; no ISL model found |
| Google SignGemma | Sign-to-text model | ASL only, recognition not generation; weights not openly released as of August 2026 |
| Ready Player Me | Hosted avatar platform | Shut down on 31 January 2026 after its sale to Netflix; shows the risk of depending on hosted avatar services |
| Hand Talk, Signapse and similar | Commercial signing avatars | ASL, BSL or Libras only; proprietary |

The lesson from the list: anything hosted can disappear, and anything that generates ISL automatically produces unreviewed output. Both are unacceptable here.

## 3. Two lexicons

The avatar never improvises. It plays entries from two reviewed lexicons.

| | ISL lexicon | Gesture lexicon |
| --- | --- | --- |
| For | Deaf people who know ISL (adults, older students) | Young children, deaf children without ISL, anyone non-verbal |
| Source | ISLRTC dictionary (10,000 terms, with regional signs) as the reference [permission to confirm]; a fluent Deaf signer performs | Special educators from deaf schools, child-welfare staff, parents' common gestures; cross-checked against ISL, since many iconic gestures overlap ISL signs |
| Reviewer | Qualified Deaf ISL reviewer; regional reviewer per state | Child-welfare reviewer plus educators from at least three regions and from different communities |
| Variants | Regional variant tagged per entry | Several variants per concept, tagged by community and region; never one variant alone |
| Grammar | Phrase bank: reviewed sentences with slots, including facial grammar | Single concepts; the question is carried by the officer's pointing, the pictures and the character's facial expression |
| Shown with | Caption and picture | Picture card, and audio for hearing children |

### Gesture lexicon starter list

Every entry below is a **candidate**. None ships until the review above approves it.

| Concept | Candidate variants | Notes and risks |
| --- | --- | --- |
| Me | Flat hand on own chest; index finger to own chest | A hand on the heart can also read as "love" or "promise"; show with the picture |
| You | Open palm towards the person | Finger-pointing at a person is rude in many homes |
| Mother | Fingertip to the centre of the forehead (bindi); finger along the hair parting (sindoor); hand drawing a head covering (dupatta, ghunghat, hijab); touch at the nose (nose ring) | Bindi and sindoor are Hindu, mainly married-women markers; many communities use neither; always offer several and the picture |
| Father | Stroking a moustache; stroking a beard; hands circling the head (turban); hand on head (cap) | Many fathers have none of these; same caution |
| Grandparent | Bent back with a walking stick | Avoid mocking portrayal |
| Baby | Rocking cradled arms | |
| Brother or sister | Tying a thread on the wrist (rakhi); hand at a height (taller or shorter than me) | Rakhi is widely known but community-specific |
| Home | Two hands meeting in a roof shape | |
| Eat, food | Bunched fingertips to the mouth | Very widely understood in India |
| Hungry | Rubbing the belly | |
| Water, drink | Thumb to the mouth from a closed fist (pouring); cupped hand to the mouth | |
| Toilet | Raised little finger (common school gesture for urination) | Embarrassing for some children; show the picture card first |
| Sleep, tired | Palms together under a tilted head | |
| Pain, hurt | Wince and rub or point to the body part | Use with the body-parts board |
| Cold, hot | Hug self and shiver; fan the face | |
| Scared | Hands over the face; hugging self | Sensitive; not on the first screen |
| Yes | Clear vertical nod; thumbs up | Never the sideways head wobble (review M-1) |
| No | Head shake with an index-finger wag | |
| Come | Indian beckon: palm down, fingers sweeping in | The Western palm-up beckon can be read as rude |
| Go, stop | Palm pushing away; palm held up | |
| Wait | Fingertips bunched pointing up, small shake | Same gesture also means "what?"; use only with caption |
| Walk | Two fingers walking on the palm | |
| Train | Forearm circling beside the body | |
| Bus, car | Hands on a steering wheel | |
| Phone | Thumb and little finger at the ear | |
| Money | Thumb rubbing the fingertips | |
| School | Writing on the palm | |
| Police | Salute | Children may fear police; the officer introduces themself with this and the picture card |
| Doctor | Two fingers from the ears to the chest (stethoscope) | Avoid the injection gesture, which frightens children |
| Where? | Palms up, raised eyebrows, small shrug | The face carries the question |

## 4. The two characters

| | Child character ("hero") | Adult character |
| --- | --- | --- |
| For | Young children; any subject in the *Young child* profile | ISL users and adults; phrases where no human ISL clip exists |
| Look | Original Indian child in a simple anime-like style (built in VRoid Studio, section 9), large head and eyes, large expressive eyebrows and mouth (which help facial grammar), clear five-fingered hands slightly oversized | Neutral stylised adult shown from head to waist: head, neck, shoulders, arms and hands in the signing space; plain dark top so hands stand out; simplified face |
| Face | Big, readable expressions | Eyebrows, eyes, mouth and head movement must work; this is ISL grammar, not decoration |
| Clothes | Plain, everyday, regionally neutral; no school uniform, no police uniform, no religious markers | Plain dark top; no uniform |
| Lexicon | Gesture lexicon; ISL lexicon later for deaf children who sign | ISL lexicon and phrase bank |
| Background | Plain, calm, warm | Plain, contrasting with skin and clothes |

**Why not Shin-chan or Nobita.** Crayon Shin-chan and Doraemon (with Nobita) are protected characters owned by their Japanese rights holders, who enforce them: Fujiko Pro has pursued look-alike characters abroad. A "replica" infringes copyright and, for character names and likeness, trademark rights, whatever the purpose. A government police app cannot ship it without a licence, and a licence for this use is unlikely and slow. Shin-chan was also taken off air in India in 2008-09 after complaints about its content and returned only in an edited form, which is a reputational risk for a police app. The same applies to Chhota Bheem and other commercial characters. What the team wants from them, a friendly, familiar cartoon child, is achievable with an original character in a similar simple style. The team designs the character itself; the custom parts belong to the department (section 9).

**No blank face for adults (decided).** A blank or featureless "mask" face was considered and rejected: without eyebrows and mouth, ISL questions read as statements and negation is lost. The adult character has a simplified, non-realistic face that still moves its brows, eyes and mouth. This keeps the neutrality a mask was meant to give, avoids the uncanny valley, and resembles no real person.

**Character review.** A child psychologist reviews both characters before animation starts, and they are tested with children (section 8): a character that reads as a toy invites play, and one that reads as a stranger may frighten.

## 5. Technical design

### 5.1 One rig, two skins, one motion library

Both characters share one humanoid upper-body rig, so every clip plays on either character.

- **Bones:** the VRM 1.0 humanoid set from hips to head, both arms, and all finger bones (three joints per finger, plus thumb). VRM is an open specification with open-source tooling.
- **Face:** custom expressions for brows (raise, furrow), eyes (open wide, squint, blink), mouth (open, round, press, smile, frown), cheek puff; head nod, shake and tilt through bones.
- **Contact points:** named markers at forehead centre, hair parting, nose, upper lip, chin, chest, belly and wrist, so a clean-up step can check that a fingertip actually touches the forehead for "mother" rather than floating in front of it.

### 5.2 Lexicon entry

```
LexiconEntry
  id                 // "gesture.mother.bindi", "isl.water"
  kind               // GESTURE | ISL_SIGN | ISL_PHRASE
  concept            // "mother"
  variant            // "bindi"
  tags               // region, community, age group
  slots?             // phrase bank only: ["thing"]
  clip               // motion clip id
  pictureCardId
  captions{lang}
  audio{lang}?       // hearing subjects
  review             // reviewer roles, names, dates, approval
```

### 5.3 Authoring pipeline

1. A performer (Deaf signer for ISL; educator for gestures) is filmed against a plain background, after signing the [consent form](governance/forms/isl-signer-consent.md).
2. Pose, hand and face landmarks are extracted with MediaPipe.
3. Landmarks are solved to rig rotations (the open-source Kalidokit solver, or a Blender add-on) and cleaned by hand in Blender: contacts fixed, fingers corrected, start and end returned to a neutral rest pose so clips join smoothly.
4. The reviewer approves the clip on both characters.
5. The approved clip is exported as compact keyframes (stored in the repository) and, for v1, rendered to video.

Raw footage of the performer is kept only until the clip is approved, then deleted; the keyframes contain no image of the performer.

### 5.4 Runtime, in stages

| Stage | When | What the subject sees | New code or dependencies |
| --- | --- | --- | --- |
| 0. Code-drawn characters (built) | Phase 1 | Both characters drawn live on a 2D rig, playing `gestures.json` keyframes with jointed fingers and facial expression, beside the picture card (D-21, section 5.7) | None; Compose Canvas |
| 1. Pre-rendered clips | Phase 2 | Short videos of each lexicon entry on each character, rendered offline in Blender; phrase-bank sentences played as a gapless playlist of clips; slow-speed replay | None beyond Media3 ExoPlayer, already planned |
| 2. Real-time 3D | After the pilot, only if needed | Same clips played live on the rig, with blended transitions and any camera angle; enables a large lexicon without large downloads | Filament (or SceneView) for glTF skinning and morph targets |

Stage 1 is enough for a fixed, reviewed lexicon and runs on the oldest phone. Stage 2 is justified only if the lexicon grows into the thousands, because then video size becomes the limit.

### 5.5 Size budget (estimate, measure in phase 2)

At 480x480, H.264, about 400 kbit/s and 1.5 s per clip, one clip is about 75 KB.

| Pack | Clips | Size |
| --- | --- | --- |
| Base: child character, gesture lexicon (about 30 concepts, 2 variants) | about 60 | about 4.5 MB |
| Base: human ISL clips for the triage set (about 50 phrases, 4 s each) | 50 | about 10 MB |
| Optional pack: adult character, ISL phrase bank (about 200 signs and phrases) | about 200 | about 15 MB |
| Optional pack: child character, ISL for deaf children who sign | about 100 | about 7.5 MB |

The base stays inside the 30 MB budget (NFR-05) with room for cards and fonts.

### 5.6 Playback rules

- Plays only on an officer action; never loops on its own; replay and slow-speed buttons (FR-18).
- The caption and picture are shown first; the clip plays with them, never alone.
- If a clip is missing, the picture and caption stand alone and the officer sees "gesture not available".

## 6. Officer gesture lookup (instead of camera recognition)

When the subject makes a gesture the officer does not understand:

1. Officer taps **They gestured**.
2. **Where** did the hand go? Body map: forehead, eyes, nose, mouth, ear, chin, chest, belly, arm, away from the body.
3. **What** did it do? Point, touch, tap, stroke, circle, flat hand, fist, both hands together.
4. The app lists matching lexicon entries, each with its picture and clip.
5. Officer picks the likely one and taps **Show back**: the subject sees the gesture and picture with "Is this it?" and Yes/No cards.
6. Only a confirmed match is logged as an answer; unconfirmed lookups are logged as "unclear gesture".

This works offline, needs no model, and works with home signs, because the confirmation step catches mismatches. The officer's list shows each candidate as the character's pose with its picture. The stage's camera mirror (D-20) lets the subject see their own hands; when the partner team's hand model is installed, its suggestions enter this same list and still end with Show back.

## 7. Fallback chain

**Officer to subject**, use the first that works:

1. A human interpreter on a call (ISL users; **Call an interpreter**).
2. A recorded human ISL clip (ISL users, triage set).
3. A character clip from the reviewed lexicon (gesture or ISL phrase).
4. Picture card with caption, plus recorded audio for hearing subjects.
5. The officer performs the gesture, copying it from the card on their panel or the printed quick card.

**Subject to officer:** picture card, Yes/No, drawing, gesture with lookup and Show back, interpreter.

## 8. Validation

| What | Who | Pass mark [confirm with reviewers] |
| --- | --- | --- |
| Each ISL clip and phrase | Deaf ISL reviewer; then 5 or more Deaf adults from at least two regions | 80% understand the phrase without the caption |
| Each gesture entry | Child-welfare reviewer and educators; then children in deaf and hearing schools | 70% of children pick the right picture after seeing the gesture |
| Both characters | Child psychologist; then children | No child shows fear; most children prefer the character to no character |
| Officer gesture lookup | Officers in UAT | Right meaning in the top three for 80% of scripted gestures |

Testing with children requires ethics approval, guardian consent, the child's assent and a child-welfare professional present ([test plan](06-test-plan.md) §7). Rescued children are never test participants.

## 9. Building it in-house with no budget

There is no funding for an artist or animator, so the team builds the characters with free tools chosen so that no 3D modelling skill is needed.

**Characters: VRoid Studio.** Free character maker for anime-style figures. It exports a VRM model that already has the full humanoid rig, all finger bones and face expressions, which is exactly the rig in section 5.1, so no separate rigging work is needed. Body sliders allow shorter, larger-headed child proportions (test this first). Under VRoid Studio's terms, textures and hair the team draws itself belong to the creator (the department), and pixiv's preset meshes and items may be used commercially unless an item states otherwise; check each preset item used and record it in the licensing register. Custom brow and mouth expressions are added in Blender if the presets are not enough.

**Posing and clips: Blender with the VRM Add-on for Blender (MIT).** Imports the VRM model, poses it, keyframes it and renders it headless from a script.

**Gesture cards (stage 0) without an illustrator.** Pose the child character in Blender for each gesture and render a still with a motion arrow. Cards and later clips then show the same character.

**Motion.** Simple gestures (bindi touch, moustache stroke, roof, hand on chest) are keyframed by hand: three to five keyframes each. ISL signs, which need accurate finger movement, are captured from a volunteer Deaf signer's video with MediaPipe and the Kalidokit solver, then corrected in Blender (section 5.3).

**People.** Two team members learn VRoid Studio and Blender basics (about one week, using free tutorials). Performers and reviewers are volunteers from Deaf associations, deaf schools and child-welfare bodies, recruited through the department; consent per the [signer consent form](governance/forms/isl-signer-consent.md) with payment marked Nil.

| Work | Rough effort (team, learning included) |
| --- | --- |
| Learn VRoid Studio and Blender basics | 1 week, two people |
| Two characters in VRoid Studio, with review rounds | 1-2 weeks |
| Gesture cards for about 30 concepts (posed stills) | 1 week |
| Gesture clips, about 60, keyframed by hand | 2-3 weeks |
| ISL clips, about 200, captured and cleaned | 3-4 hours per clip at first; 3-4 months part-time, or fewer clips |
| Render script and playback in the app | 1 week |

These are planning estimates; measure the first 20 clips and re-plan. Stage 0 cards fit phase 1; gesture clips fit phase 2; the ISL phrase bank is the long pole and should start with the triage phrases only.

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| A gesture means something different, or something rude, in some community | Multiple variants, community reviewers, picture always shown, Show back confirmation |
| Character resembles a commercial cartoon | Original design, no reference images of commercial characters during design, legal check of the final design before animation |
| Children treat the character as a game | Psychologist review; character only plays on officer action; no game elements |
| Facial grammar too subtle on a small screen | Exaggerated expressions on the child character; face fills a larger share of the frame than in human clips |
| Lexicon grows beyond what video packs can carry | Stage 2 real-time renderer, decided after the pilot |
| Performer withdraws consent | Keyframes are re-performed by another performer and re-reviewed; characters keep working, unlike human clips |
| Team lacks 3D skills; clips take longer than planned | VRoid Studio removes modelling and rigging; hand-keyframed gestures first; measure the first 20 clips and cut scope, not review |
| Volunteer performers and reviewers are not available in time | Recruit through the department in phase 0; human ISL triage clips and gesture cards do not depend on animation |

## Sources

- ISL and ASL differences, ISL varieties: https://home.csulb.edu/~lemaster/Asia/ASIA%20India.pdf, https://cse.iitk.ac.in/users/amit/books/zeshan-2000-sign-language-in.html, https://caravanmagazine.in/lede/on-the-other-hand-american-sign-language-bengaluru, https://atypicaladvantage.in/atypical-blog/post/difference-between-american-and-indian-sign-language
- ISLRTC dictionary: https://www.outlookindia.com/amp/story/national/from-just-3-000-to-10-000-words-now-indian-sign-language-dictionary-grows-to-help-hearing-impaired-news-322677
- Existing avatars: https://vh.cmp.uea.ac.uk/index.php/CWA_Signing_Avatars_Demos, https://aclweb.org/anthology/2020.lrec-1.739.pdf, https://www.ishaara.app/, https://arxiv.org/html/2310.05064v2, https://multilingual.com/google-signgemma-on-device-asl-translation/, https://www.pocketgamer.biz/netflix-acquires-avatar-creation-platform-ready-player-me, https://avatarsdk.com/blog/2026/08/31/avatar-platforms-2026-whos-alive-whos-gone/
- Avatar evidence: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.730917/full, https://www.researchgate.net/publication/254007035_Assessing_the_deaf_user_perspective_on_sign_language_avatars
- Character rights and history: https://www.marcus-spectrum.com/Blog/files/DoraemonIP413.html, https://deccanchronicle.com/entertainment/shinchan-was-lewd-they-would-censor-it-all-says-dubbing-artist-1827765, https://iopn.library.illinois.edu/journals/jams/article/view/2124
- Gesture ambiguity: https://folklore.usc.edu/confusing-gestures/, https://madrascourier.com/?p=26828
- Tools: https://github.com/vrm-c/vrm-specification, https://github.com/yeemachine/kalidokit, https://github.com/google/filament, https://github.com/saturday06/VRM-Addon-for-Blender
- VRoid Studio terms: https://vroid.com/en/studio/guidelines, https://vroid.pixiv.help/hc/en-us/articles/4405813333657-Can-I-use-the-models-created-with-VRoid-Studio-Stable-Ver-for-commercial-purposes

## 5.7 The code-drawn rig (release 1)

- **Space.** Each character lives in a 200 by 200 unit square, facing the viewer; the character's right hand appears on the viewer's left. Proportions (head, shoulders, arm lengths, hand size) differ per character; the child's hands are drawn about 1.6 times life size so fingers read on a small screen.
- **Arms.** A key gives a target for the wrist, palm or index fingertip at a named contact point (`forehead`, `mouth`, `chin`, `cheek`, `ear`, `chest`, `belly`, `shoulder`, `front`, ...) with an offset; the elbow is solved from the two bone lengths, bending outward unless the key says otherwise.
- **Hands.** Five fingers of three joints. A hand shape (`FLAT`, `POINT`, `BUNCH`, `PINKY`, `Y`, `CUP`, `PINCH`, counting shapes and others) sets each finger's curl; `roll` shows the palm, the thumb side or the back of the hand. Shapes blend between keys, so fingers move rather than jump.
- **Face and body.** Brows (raise, worry, frown), eyes (open, smile, blink), mouth (smile, open, round), head tilt, nod and turn, body lean, shrug, hunch and shiver. Questions without a lexicon entry use a gentle asking face, not an invented sign.
- **Playback.** A gesture eases in from rest and back to rest; it plays once on the officer's action and on replay; slow replay is half speed (FR-18). Once a question's gesture ends, the character glances down at the answers below it, inviting a reply without words. The character breathes and blinks between gestures unless the phone's "remove animations" setting is on.
- **Phrases.** A phrase-bank sentence chains its signs ("you", the chosen item, the asking pose) into one gesture; each part starts from the pose it has when played alone, so a reviewed sign never changes inside a sentence. Each slot carries its own complete sentence in every language, because word order and agreement change with the item.
- **Review.** Debug builds include a gallery of every gesture on both characters, frozen at the moment of contact, for the reviewers in section 8. Every entry remains a candidate until reviewed.
