# Sign quality review

Written 2026-10-29. Every motion was played on the shipped character by the app's own avatar module (`avatar/src/stage.ts` at 60 Hz) in a headless browser, eight frames of each set beside its source at the same moments, and the character's joints were measured against the track the motion was made from. Verdicts: ok, fix (a generator change below, or a note for the next one), replace (an ISLRTC dictionary clip of the word is on disk and reads better), drop (remove so the word falls back to spelling or other signs). Every sign stays a draft until Deaf ISL review.

## Fixes made in the generators

- `tools/text2isl/export_clips.py`: finger points are smoothed about their own wrist (`SIGMA_HAND`, 2 frames), and each clip is cut to its signing (`signing_span`: both wrists more than 1.6 shoulder widths below the shoulders is rest, 5 frames kept either side). The team's recordings rested up to 3 s at the ends: the 112 clips play 457.0 s, now 423.0 s, and "where do you live" 8.00 s, now 4.72 s.
- `tools/isl/retarget.py`: the rig's torso is half as tall as its shoulder width implies (chest anchor 18 units below the shoulders, belly 52, against about 0.4 and 1.15 shoulder widths on a signer), so `to_rig` now halves heights below the shoulder line (`TORSO_Y`). Before, 29% of all hand keys sat on the frame's bottom clamp, and 13 signs never left the waist (horse, it, mouse, pant, radio, skirt, change, have, here, stomach, things, busy, still).

## Not fixed here (player, `avatar/src/clip.ts`)

- Raising and lowering: a wrist crossing the solver's active band (-0.74 to -0.45) switches its x and depth from rest to signing within about three frames, so each raise and drop reads as a quick swoop (up to 0.6 shoulder widths in one 30 Hz frame in "where do you live"). The wrist paths in between are steady.
- Hands are capped at eye level; only "comb your hair" goes higher (61% of its frames), so its hand stays at the temple.

## Sentence clips, worst jitter first

Jitter is the mean hand acceleration of `clips.json` (x and y of points 6 onward, per frame). Finger flicker and palm wobble are the mean second difference, per frame, of finger bend and of the wrist-to-knuckle angle, in degrees. Jitter can rise after the fix, because the resting frames it trims had none.

| Sentence | Jitter | Finger flicker | Palm wobble | Length s | Verdict | Notes |
|---|---|---|---|---|---|---|
| this place is beautiful | 0.0130 to 0.0173 | 10.3 to 7.7 | 6.3 to 6.9 | 4.48 to 2.76 | ok |  |
| why did you ran from home | 0.0127 to 0.0128 | 6.2 to 4.1 | 5.5 to 2.9 | 4.40 to 4.00 | ok |  |
| you need a medicine, take this one | 0.0113 to 0.0122 | 6.9 to 4.2 | 9.1 to 4.7 | 4.72 to 4.72 | ok |  |
| are you hiding something | 0.0107 to 0.0113 | 6.5 to 4.3 | 5.8 to 4.3 | 3.48 to 2.88 | ok |  |
| where are you from | 0.0115 to 0.0112 | 10.9 to 5.4 | 12.0 to 4.6 | 3.80 to 3.80 | ok |  |
| who do you live with | 0.0089 to 0.0105 | 6.3 to 4.5 | 5.6 to 4.4 | 9.80 to 6.36 | ok |  |
| how many people are in your family | 0.0098 to 0.0089 | 6.5 to 3.8 | 4.6 to 3.2 | 7.44 to 6.60 | ok |  |
| comb your hair | 0.0079 to 0.0087 | 6.4 to 4.2 | 1.9 to 1.6 | 3.64 to 3.16 | ok |  |
| he is going into the room | 0.0087 to 0.0084 | 5.8 to 4.0 | 2.9 to 2.5 | 5.12 to 3.88 | ok |  |
| i am really grateful | 0.0078 to 0.0081 | 13.6 to 7.0 | 8.6 to 5.2 | 3.00 to 3.00 | ok |  |
| what is your phone number | 0.0072 to 0.0080 | 4.0 to 4.7 | 2.6 to 3.1 | 9.12 to 6.40 | ok |  |
| which collegeschool are you from | 0.0097 to 0.0079 | 7.5 to 3.6 | 6.1 to 3.6 | 5.16 to 5.16 | ok |  |
| who are you | 0.0088 to 0.0078 | 8.7 to 5.1 | 8.7 to 2.5 | 2.00 to 2.00 | ok |  |
| do not be stubborn | 0.0090 to 0.0072 | 6.6 to 5.2 | 3.9 to 4.5 | 4.88 to 4.40 | ok |  |
| i am afraid of that | 0.0088 to 0.0069 | 6.6 to 4.0 | 8.8 to 4.5 | 3.00 to 3.00 | ok |  |
| can i help you | 0.0060 to 0.0068 | 6.0 to 4.0 | 2.4 to 1.6 | 3.04 to 2.60 | ok |  |
| do you need something | 0.0067 to 0.0068 | 5.7 to 4.2 | 6.5 to 5.4 | 2.00 to 2.00 | ok |  |
| i am tired | 0.0062 to 0.0068 | 6.0 to 4.4 | 4.5 to 2.3 | 3.80 to 3.80 | ok |  |
| we are all with you | 0.0059 to 0.0066 | 10.1 to 6.0 | 9.3 to 2.9 | 2.00 to 2.00 | ok |  |
| why are you disappointed | 0.0069 to 0.0066 | 8.9 to 5.1 | 3.6 to 1.8 | 3.00 to 3.00 | ok |  |
| go and sleep | 0.0079 to 0.0063 | 8.2 to 4.5 | 2.9 to 1.5 | 2.00 to 2.00 | ok |  |
| shall we go outside | 0.0070 to 0.0061 | 4.5 to 3.3 | 2.6 to 1.7 | 4.28 to 4.28 | ok |  |
| how can i trust you | 0.0060 to 0.0060 | 6.7 to 4.9 | 4.6 to 2.4 | 2.00 to 2.00 | ok |  |
| why are you crying | 0.0071 to 0.0059 | 8.4 to 4.8 | 8.3 to 4.5 | 4.64 to 4.64 | ok |  |
| what is your father's name | 0.0064 to 0.0059 | 3.8 to 2.5 | 1.4 to 0.4 | 8.08 to 7.04 | ok |  |
| why are you frightened | 0.0059 to 0.0058 | 5.3 to 3.7 | 3.0 to 3.7 | 5.00 to 3.48 | ok |  |
| what is your age | 0.0059 to 0.0058 | 3.3 to 2.7 | 1.1 to 1.1 | 3.68 to 2.24 | ok |  |
| prepare the bed | 0.0060 to 0.0057 | 8.5 to 4.5 | 5.7 to 4.5 | 3.00 to 3.00 | ok |  |
| serve the food | 0.0059 to 0.0057 | 6.4 to 3.7 | 5.4 to 2.4 | 5.04 to 5.04 | ok |  |
| what do you want to become | 0.0060 to 0.0056 | 5.9 to 3.8 | 3.7 to 2.1 | 3.40 to 3.40 | ok |  |
| do not abuse him | 0.0059 to 0.0056 | 4.2 to 3.3 | 2.1 to 1.8 | 5.60 to 4.88 | ok |  |
| i do not mean it | 0.0056 to 0.0056 | 7.5 to 3.6 | 5.9 to 3.5 | 4.20 to 4.20 | ok |  |
| try to understand | 0.0052 to 0.0054 | 4.4 to 2.9 | 4.7 to 2.9 | 4.76 to 4.76 | ok |  |
| are you free today | 0.0052 to 0.0053 | 4.5 to 3.5 | 1.5 to 1.2 | 3.12 to 2.80 | ok |  |
| tell me truth | 0.0045 to 0.0053 | 14.1 to 5.1 | 5.0 to 1.9 | 3.00 to 3.00 | ok |  |
| he came by train | 0.0052 to 0.0052 | 6.6 to 4.3 | 4.1 to 2.4 | 4.00 to 4.00 | ok |  |
| do me a favour | 0.0047 to 0.0052 | 3.8 to 3.4 | 1.4 to 0.8 | 3.16 to 2.72 | ok |  |
| he she is my friend | 0.0050 to 0.0051 | 11.2 to 6.5 | 5.5 to 3.1 | 2.00 to 2.00 | ok |  |
| thank you so much | 0.0053 to 0.0051 | 7.4 to 5.1 | 2.2 to 1.6 | 2.00 to 2.00 | ok |  |
| where do you live | 0.0056 to 0.0051 | 4.1 to 3.2 | 5.1 to 3.2 | 8.00 to 4.72 | ok |  |
| i really appreciate it | 0.0052 to 0.0050 | 6.3 to 4.4 | 2.0 to 1.1 | 3.00 to 3.00 | ok |  |
| what have you planned for your career | 0.0052 to 0.0050 | 7.5 to 5.1 | 4.0 to 2.0 | 3.96 to 3.96 | ok |  |
| i promise | 0.0058 to 0.0050 | 8.0 to 4.2 | 2.8 to 1.8 | 2.00 to 2.00 | ok |  |
| i want to see picture | 0.0046 to 0.0049 | 4.0 to 2.9 | 2.2 to 1.8 | 9.64 to 6.36 | ok |  |
| it was nice chatting with you | 0.0055 to 0.0046 | 5.9 to 3.4 | 2.9 to 1.5 | 5.36 to 5.36 | ok |  |
| could you please talk slower | 0.0046 to 0.0046 | 3.4 to 2.3 | 1.0 to 0.5 | 4.20 to 3.76 | ok |  |
| no need to worry dont worry | 0.0038 to 0.0046 | 2.9 to 2.7 | 1.5 to 0.7 | 3.88 to 2.64 | ok |  |
| i do not agree | 0.0067 to 0.0045 | 5.9 to 3.5 | 2.9 to 1.2 | 4.40 to 4.40 | ok |  |
| i am sitting in the class | 0.0052 to 0.0045 | 6.8 to 4.1 | 4.2 to 2.0 | 6.92 to 6.92 | ok |  |
| pour some more water into the glass | 0.0042 to 0.0045 | 9.5 to 5.8 | 8.7 to 4.0 | 3.00 to 3.00 | ok |  |
| do not make me angry | 0.0052 to 0.0045 | 4.3 to 3.2 | 2.9 to 1.2 | 3.88 to 3.88 | ok |  |
| i am feeling bored | 0.0052 to 0.0044 | 7.7 to 4.6 | 3.2 to 2.4 | 4.76 to 4.76 | ok |  |
| i am in dilemma what to do | 0.0051 to 0.0044 | 5.2 to 2.7 | 2.8 to 1.5 | 6.12 to 6.12 | ok |  |
| how are things | 0.0045 to 0.0044 | 4.9 to 3.0 | 1.9 to 1.1 | 4.00 to 4.00 | ok |  |
| that is so kind of you | 0.0044 to 0.0043 | 3.9 to 3.3 | 2.4 to 1.5 | 3.00 to 3.00 | ok |  |
| do not worry | 0.0053 to 0.0043 | 14.4 to 5.6 | 6.6 to 2.6 | 3.00 to 3.00 | ok |  |
| what is your mother's name | 0.0046 to 0.0042 | 4.4 to 2.2 | 1.4 to 0.4 | 8.40 to 6.00 | ok |  |
| i am fine. thank you sir | 0.0046 to 0.0042 | 6.1 to 3.5 | 2.1 to 1.1 | 4.36 to 4.36 | ok |  |
| i am suffering from fever | 0.0042 to 0.0041 | 5.6 to 4.4 | 2.6 to 2.5 | 4.04 to 4.04 | ok |  |
| what is your name | 0.0047 to 0.0041 | 4.1 to 2.6 | 0.7 to 0.6 | 6.08 to 4.64 | ok |  |
| i got hurt | 0.0039 to 0.0041 | 8.4 to 3.9 | 4.1 to 1.8 | 3.00 to 3.00 | ok |  |
| i am not really sure | 0.0041 to 0.0041 | 7.0 to 5.0 | 3.5 to 2.4 | 3.00 to 3.00 | ok |  |
| how many brothers and sisters do you have | 0.0042 to 0.0041 | 3.7 to 2.6 | 1.2 to 1.1 | 8.12 to 6.04 | ok |  |
| now onwards he will never hurt you | 0.0047 to 0.0040 | 5.2 to 3.1 | 2.8 to 1.5 | 5.84 to 5.84 | ok |  |
| i need water | 0.0041 to 0.0039 | 5.5 to 4.7 | 2.5 to 2.3 | 3.00 to 3.00 | ok |  |
| how dare you | 0.0042 to 0.0039 | 5.3 to 3.6 | 5.1 to 4.2 | 3.72 to 3.72 | ok |  |
| i enjoyed a lot | 0.0041 to 0.0038 | 2.9 to 2.2 | 1.3 to 0.7 | 2.84 to 2.84 | ok |  |
| you are welcome | 0.0039 to 0.0038 | 5.9 to 4.4 | 2.5 to 1.9 | 3.32 to 3.32 | ok |  |
| why are you angry | 0.0040 to 0.0038 | 4.7 to 3.2 | 3.5 to 2.8 | 3.52 to 3.52 | ok |  |
| do not hurt me | 0.0033 to 0.0037 | 4.9 to 4.4 | 4.7 to 2.5 | 3.00 to 3.00 | ok |  |
| i do not like it | 0.0034 to 0.0037 | 6.0 to 4.0 | 4.2 to 1.6 | 3.52 to 3.52 | ok |  |
| wear the shirt | 0.0040 to 0.0037 | 5.6 to 4.4 | 3.1 to 2.2 | 4.40 to 4.40 | ok |  |
| i dont agree | 0.0048 to 0.0036 | 3.4 to 2.7 | 2.0 to 1.0 | 4.00 to 4.00 | ok |  |
| let him take time | 0.0039 to 0.0035 | 4.2 to 2.3 | 3.0 to 1.9 | 3.44 to 3.44 | ok |  |
| what happened | 0.0036 to 0.0035 | 3.7 to 2.9 | 1.9 to 1.0 | 3.00 to 3.00 | ok |  |
| what you want | 0.0038 to 0.0035 | 4.4 to 2.9 | 7.5 to 5.5 | 3.68 to 3.68 | ok |  |
| can you repeat that please | 0.0033 to 0.0035 | 3.3 to 1.8 | 3.3 to 0.5 | 3.68 to 2.88 | ok |  |
| take care of yourself | 0.0036 to 0.0034 | 3.3 to 2.5 | 0.9 to 0.6 | 3.00 to 3.00 | ok |  |
| he would be coming today | 0.0033 to 0.0033 | 5.9 to 3.6 | 2.5 to 1.2 | 3.00 to 3.00 | ok |  |
| i can not help you there | 0.0036 to 0.0033 | 4.4 to 2.5 | 3.3 to 2.2 | 4.36 to 4.36 | ok |  |
| help me | 0.0031 to 0.0032 | 5.0 to 3.3 | 4.1 to 3.1 | 2.84 to 2.84 | ok |  |
| you can do it | 0.0035 to 0.0032 | 5.9 to 3.4 | 2.1 to 1.0 | 2.00 to 2.00 | ok |  |
| i am so sorry to hear that | 0.0034 to 0.0032 | 4.4 to 3.4 | 0.9 to 0.7 | 3.00 to 3.00 | ok |  |
| i am crying | 0.0034 to 0.0031 | 3.0 to 2.1 | 0.7 to 0.4 | 3.08 to 3.08 | ok |  |
| hi how are you | 0.0037 to 0.0031 | 4.5 to 2.9 | 1.8 to 1.2 | 3.68 to 3.68 | ok |  |
| how can i help you | 0.0033 to 0.0030 | 4.8 to 2.8 | 4.3 to 2.3 | 3.64 to 3.64 | ok |  |
| what do you think | 0.0030 to 0.0030 | 3.8 to 3.0 | 2.4 to 1.7 | 4.00 to 4.00 | ok |  |
| bring water for me | 0.0028 to 0.0029 | 5.0 to 2.4 | 1.2 to 0.7 | 3.64 to 3.12 | ok |  |
| you do anything, i do not care | 0.0042 to 0.0029 | 6.3 to 3.9 | 1.9 to 1.0 | 7.44 to 7.44 | ok |  |
| he is on the way | 0.0034 to 0.0029 | 4.9 to 3.5 | 1.2 to 0.8 | 4.56 to 4.56 | ok |  |
| i like you i love you | 0.0034 to 0.0029 | 3.1 to 2.3 | 1.7 to 1.1 | 3.88 to 3.88 | ok |  |
| i am very happy | 0.0030 to 0.0028 | 4.1 to 3.6 | 1.7 to 1.3 | 3.48 to 3.48 | ok |  |
| i somehow got to know about it | 0.0034 to 0.0028 | 4.1 to 2.8 | 3.0 to 1.9 | 4.16 to 4.16 | ok |  |
| when will the train leave | 0.0028 to 0.0028 | 5.0 to 3.8 | 3.6 to 2.8 | 4.00 to 4.00 | ok |  |
| turn on light turn off light | 0.0028 to 0.0028 | 4.4 to 3.0 | 1.5 to 0.7 | 2.00 to 2.00 | ok |  |
| do not take it to the heart | 0.0031 to 0.0028 | 3.8 to 3.3 | 1.7 to 1.1 | 4.80 to 4.80 | ok |  |
| what did you tell him | 0.0029 to 0.0027 | 6.3 to 3.7 | 2.4 to 1.1 | 3.00 to 3.00 | ok |  |
| it does not make any difference to me | 0.0029 to 0.0024 | 4.7 to 3.1 | 2.5 to 1.2 | 4.84 to 4.84 | ok |  |
| speak softly | 0.0028 to 0.0024 | 2.5 to 1.4 | 1.9 to 1.1 | 3.36 to 3.36 | ok |  |
| my name is xxxxxxxx | 0.0028 to 0.0024 | 4.4 to 2.6 | 0.9 to 0.5 | 3.52 to 3.52 | ok |  |
| you are good | 0.0026 to 0.0023 | 4.9 to 3.5 | 1.5 to 0.9 | 2.80 to 2.80 | ok |  |
| what are you doing | 0.0028 to 0.0022 | 3.3 to 2.3 | 1.9 to 0.8 | 4.48 to 4.48 | ok |  |
| i am hungry | 0.0027 to 0.0022 | 3.1 to 2.0 | 1.3 to 0.6 | 4.24 to 4.24 | ok |  |
| nice to meet you | 0.0022 to 0.0021 | 2.3 to 1.6 | 0.5 to 0.4 | 4.44 to 4.44 | ok |  |
| congratulations | 0.0024 to 0.0021 | 3.9 to 2.6 | 2.4 to 1.0 | 2.52 to 1.80 | ok |  |
| you are bad | 0.0024 to 0.0020 | 3.1 to 2.2 | 1.3 to 0.9 | 2.92 to 2.92 | ok |  |
| i am (age) | 0.0019 to 0.0017 | 3.1 to 2.5 | 0.9 to 0.6 | 2.64 to 2.64 | ok |  |
| i was stopped by some one | 0.0018 to 0.0017 | 5.0 to 3.6 | 2.0 to 1.1 | 5.00 to 5.00 | ok |  |
| i am feeling cold | 0.0020 to 0.0017 | 3.6 to 2.3 | 0.7 to 0.5 | 3.24 to 3.24 | ok |  |
| had your food | 0.0019 to 0.0016 | 2.0 to 1.2 | 0.6 to 0.3 | 3.48 to 3.48 | ok |  |
| what do you do | 0.0019 to 0.0016 | 1.9 to 1.5 | 1.1 to 0.8 | 3.68 to 3.68 | ok |  |
| how old are you | 0.0014 to 0.0010 | 1.6 to 1.2 | 0.7 to 0.4 | 4.36 to 4.36 | ok |  |

## Word signs (`isl_signs.json`)

Placement is the median wrist distance from the source in shoulder widths, after one fitted gain and offset for the whole set (the character's upper-arm joints sit narrower than MediaPipe's shoulders). Path is the cosine between the source's and the character's centred wrist paths; finger bend is the mean absolute difference in summed joint bend.

| Sign | Source | Verdict | Why |
|---|---|---|---|
| `isl.blue` | Colours/49. Blue/MVI_4026 | replace | R placed 0.54 off; R shape 23 deg; dictionary clip `B/Blue.mp4` |
| `isl.boy` | People/77. Boy/MVI_4242 | replace | R placed 0.53 off; R shape 22 deg; dictionary clip `B/Boy.mp4` |
| `isl.camera` | Electronics/58. Camera/MVI_5407 | replace | R placed 0.52 off; L placed 0.54 off; dictionary clip `C/Camera.mp4` |
| `isl.city` | Places/18. City/MVI_3589 | replace | L shape 26 deg; lead L on the source, R on the character; dictionary clip `C/City.mp4` |
| `isl.door` | Home/29. Door/MVI_4369 | replace | R placed 0.46 off; R path 0.24; dictionary clip `D/Door.mp4` |
| `isl.dry` | Adjectives/97. dry/MVI_5326 | replace | R shape 50 deg; L shape 27 deg; dictionary clip `D/Dry.mp4` |
| `isl.exercise` | Society/18. Exercise/MVI_8981 | replace | R placed 0.65 off; R path 0.38; L placed 0.58 off; L path 0.29; dictionary clip `E/Exercise.mp4` |
| `isl.he` | Pronouns/42. he/MVI_0008 | replace | R placed 0.62 off; R path 0.44; dictionary clip `H/He.mp4` |
| `isl.hot` | Adjectives/87. hot/MVI_5295 | replace | R path 0.07; L shape 38 deg; dictionary clip `H/Hot.mp4` |
| `isl.india` | Places/33. India/MVI_3573 | replace | R placed 0.49 off; R path 0.28; R shape 24 deg; dictionary clip `I/India.mp4` |
| `isl.lamp` | Electronics/52. Lamp/MVI_4530 | replace | R placed 0.59 off; R shape 24 deg; dictionary clip `L/Lamp.mp4` |
| `isl.monsoon` | Seasons/Ex. Monsoon/MVI_5082 | replace | R placed 0.64 off; L placed 0.80 off; dictionary clip `M/Monsoon.mp4` |
| `isl.month` | Days_and_Time/77. Month/MVI_9172 | replace | R shape 22 deg; L shape 27 deg; dictionary clip `M/Month.mp4` |
| `isl.orange` | Colours/53. Orange/MVI_5203 | replace | R placed 0.60 off; R path 0.36; dictionary clip `O/Orange.mp4` |
| `isl.queen` | People/74. Queen/MVI_3807 | replace | R placed 0.49 off; R path 0.05; R shape 23 deg; dictionary clip `Q/Queen.mp4` |
| `isl.science` | Society/22. Science/MVI_4863 | replace | R shape 27 deg; L path 0.26; dictionary clip `S/Science.mp4` |
| `isl.short` | Adjectives/79. short/MVI_5271 | replace | R path 0.39; R shape 26 deg; dictionary clip `S/Short.mp4` |
| `isl.sign` | Society/21. Sign/MVI_4333 | replace | R placed 0.46 off; R path 0.39; dictionary clip `S/Sign.mp4` |
| `isl.team` | Society/7. Team/MVI_4277 | replace | R path 0.36; L path 0.41; dictionary clip `T/Team.mp4` |
| `isl.window` | Home/28. Window/MVI_4364 | replace | R placed 0.53 off; L placed 0.58 off; L shape 24 deg; dictionary clip `W/Window.mp4` |
| `isl.yesterday` | Days_and_Time/75. Yesterday/MVI_5478 | replace | R placed 0.54 off; R path 0.39; dictionary clip `Y/Yesterday.mp4` |
| `isl.young` | Adjectives/93. young/MVI_9427 | replace | R path 0.33; R shape 22 deg; dictionary clip `Y/Young.mp4` |
| `isl.actor` | Jobs/98. Actor/MVI_4792 | fix | L shape 29 deg |
| `isl.afternoon` | Days_and_Time/83. Afternoon/MVI_5063 | fix | R placed 0.49 off |
| `isl.alone` | ISLRTC/A/Alone.mp4 | fix | not rendered after the rebuild |
| `isl.angry` | ISLRTC/A/Angry_Enraged_Furious.mp4 | fix | R placed 0.51 off |
| `isl.artist` | Jobs/94. Artist/MVI_4780 | fix | R placed 0.51 off |
| `isl.author` | Jobs/95. Author/MVI_8911 | fix | R path 0.20 |
| `isl.baby` | People/63. Baby/MVI_3769 | fix | R path 0.27 |
| `isl.back` | ISLRTC/B/Back.mp4 | fix | R placed 0.47 off; R shape 34 deg |
| `isl.bag` | Home/45. Bag/MVI_4436 | fix | R path 0.05 |
| `isl.bathroom` | Home/32. Bathroom/MVI_4381 | fix | R path 0.45 |
| `isl.bedroom` | Home/30. Bedroom/MVI_4372 | fix | R shape 23 deg |
| `isl.big` | ISLRTC/B/Big.mp4 | fix | R placed 0.58 off; L placed 0.60 off |
| `isl.bird` | Animals/4. Bird/MVI_3013 | fix | R path 0.36; L path 0.42 |
| `isl.black` | Colours/54. Black/MVI_5207 | fix | R placed 0.46 off |
| `isl.body` | ISLRTC/B/Body.mp4 | fix | R placed 0.49 off; L placed 0.53 off |
| `isl.boss` | ISLRTC/B/Boss.mp4 | fix | R placed 0.45 off; R path 0.37; R shape 28 deg |
| `isl.break` | ISLRTC/B/Break.mp4 | fix | not rendered after the rebuild |
| `isl.bye` | ISLRTC/New 2500 ISL Dictionary Videos/Yunikee 1/New English/Bye.mp4 | fix | R path 0.42; R shape 26 deg |
| `isl.car` | Means_of_Transportation/11. Car/MVI_3210 | fix | L shape 28 deg |
| `isl.cell phone` | Electronics/54. Cell phone/MVI_4539 | fix | R placed 0.46 off |
| `isl.change` | ISLRTC/C/Change.mp4 | fix | R shape 23 deg |
| `isl.come` | ISLRTC/C/Come.mp4 | fix | R shape 28 deg |
| `isl.cry` | Kaggle/Cry/WIN_20231103_10_51_45_Pro.mp4 | fix | R shape 23 deg |
| `isl.dead` | Adjectives/33. dead/MVI_9701 | fix | R shape 23 deg |
| `isl.deep` | Adjectives/27. deep/MVI_9797 | fix | R shape 27 deg |
| `isl.district` | ISLRTC/D/District.mp4 | fix | R shape 23 deg |
| `isl.down` | ISLRTC/D/Down - English.mp4 | fix | not rendered after the rebuild |
| `isl.ear` | ISLRTC/E/Ear.mp4 | fix | R placed 0.46 off |
| `isl.eat` | ISLRTC/E/Eat.mp4 | fix | R shape 22 deg |
| `isl.election` | Society/14. Election/MVI_4304 | fix | R placed 0.57 off |
| `isl.english` | ISLRTC/E/English.mp4 | fix | R placed 0.46 off |
| `isl.fall` | Seasons/64. Fall/MVI_5435 | fix | R shape 29 deg |
| `isl.false` | ISLRTC/F/False.mp4 | fix | R shape 24 deg |
| `isl.fan` | Electronics/53. Fan/MVI_4965 | fix | R path 0.36 |
| `isl.fedup` | Kaggle/Fedup/WIN_20231103_13_03_36_Pro.mp4 | fix | R placed 0.52 off |
| `isl.feelings` | ISLRTC/F/Feeling.mp4 | fix | R shape 28 deg |
| `isl.female` | Adjectives/20. female/MVI_9645 | fix | R path 0.35 |
| `isl.fever` | Kaggle/Fever/WIN_20230901_10_05_57_Pro.mp4 | fix | R placed 0.47 off |
| `isl.find` | ISLRTC/F/Find.mp4 | fix | R shape 24 deg |
| `isl.foot` | ISLRTC/F/Foot_(Body_Part).mp4 | fix | L shape 24 deg |
| `isl.forget` | ISLRTC/F/Forget.mp4 | fix | not rendered after the rebuild |
| `isl.four` | ISLRTC/Numbers/4_Four.mp4 | fix | R shape 23 deg |
| `isl.friday` | Days_and_Time/71. Friday/MVI_4610 | fix | R shape 33 deg |
| `isl.get` | ISLRTC/G/Get.mp4 | fix | R shape 24 deg |
| `isl.girl` | People/78. Girl/MVI_4243 | fix | R shape 26 deg |
| `isl.go` | ISLRTC/G/Go.mp4 | fix | R placed 0.58 off |
| `isl.god` | Society/23. God/MVI_8996 | fix | R shape 29 deg |
| `isl.government` | ISLRTC/G/Government.mp4 | fix | R shape 28 deg |
| `isl.gun` | Society/16. Gun/MVI_8974 | fix | R shape 33 deg |
| `isl.happy` | Adjectives/3. happy/MVI_5342 | fix | R shape 25 deg |
| `isl.hard` | Adjectives/26. hard/MVI_9666 | fix | R path 0.28 |
| `isl.hat` | Clothes/37. Hat/MVI_4866 | fix | R shape 26 deg |
| `isl.have` | ISLRTC/H/Have.mp4 | fix | R path 0.26 |
| `isl.hear` | ISLRTC/H/Hear_(Eg_News).mp4 | fix | R placed 0.58 off |
| `isl.hello` | Greetings/48. Hello/MVI_0032 | fix | R placed 0.51 off |
| `isl.high` | Adjectives/23. high/MVI_9656 | fix | R path 0.43 |
| `isl.hindi` | ISLRTC/H/Hindi.mp4 | fix | R placed 0.51 off; R path 0.20 |
| `isl.hospital` | Places/30. Hospital/MVI_3480 | fix | R placed 0.48 off |
| `isl.hug` | Kaggle/Hug/WIN_20230831_14_36_40_Pro.mp4 | fix | L shape 24 deg |
| `isl.hungry` | ISLRTC/H/Hungry.mp4 | fix | R shape 26 deg |
| `isl.injury` | Kaggle/Injury/WIN_20230831_14_21_03_Pro.mp4 | fix | R shape 33 deg |
| `isl.juvenile` | ISLRTC/J/Juvenile.mp4 | fix | R path -0.19; R shape 39 deg; lead R on the source, L on the character |
| `isl.kill` | ISLRTC/K/Kill_Murder.mp4 | fix | R shape 28 deg |
| `isl.king` | People/73. King/MVI_5269 | fix | R placed 0.49 off |
| `isl.kitchen` | Home/31. Kitchen/MVI_4376 | fix | R path 0.13 |
| `isl.know` | ISLRTC/K/Know.mp4 | fix | R placed 0.45 off |
| `isl.left` | ISLRTC/L/Left_(Hand).mp4 | fix | R shape 26 deg |
| `isl.location` | Places/36. Location/MVI_3427 | fix | L shape 25 deg |
| `isl.lunch` | ISLRTC/L/Lunch.mp4 | fix | R shape 23 deg |
| `isl.make` | ISLRTC/M/Make.mp4 | fix | L shape 38 deg |
| `isl.male` | Adjectives/19. male/MVI_9639 | fix | R placed 0.52 off |
| `isl.man` | People/64. Man/MVI_3923 | fix | R placed 0.57 off |
| `isl.market` | Places/32. Market/MVI_3488 | fix | R shape 23 deg |
| `isl.mouse` | Animals/6. Mouse/MVI_3052 | fix | R path 0.47 |
| `isl.mouth` | ISLRTC/M/Mouth.mp4 | fix | R path 0.29 |
| `isl.must` | ISLRTC/M/Must_Should.mp4 | fix | R shape 36 deg; L shape 43 deg |
| `isl.namaste` | ISLRTC/N/Namaste.mp4 | fix | R shape 25 deg |
| `isl.name` | ISLRTC/N/Name.mp4 | fix | R shape 25 deg; L placed 0.59 off |
| `isl.neighbour` | People/76. Neighbour/MVI_4241 | fix | R shape 27 deg |
| `isl.nine` | ISLRTC/Numbers/9_Nine.mp4 | fix | R placed 0.54 off; R shape 24 deg |
| `isl.number` | ISLRTC/N/Number.mp4 | fix | R shape 32 deg |
| `isl.old` | Adjectives/92. old/MVI_5232 | fix | R shape 22 deg |
| `isl.once` | ISLRTC/O/Once.mp4 | fix | L shape 28 deg |
| `isl.owner` | ISLRTC/O/Owner.mp4 | fix | R shape 22 deg |
| `isl.pen` | Home/34. Pen/MVI_4389 | fix | R path 0.40 |
| `isl.place` | ISLRTC/P/Place.mp4 | fix | R placed 0.56 off |
| `isl.places` | ISLRTC/P/Place.mp4 | fix | R placed 0.56 off |
| `isl.play` | ISLRTC/P/Play.mp4 | fix | R shape 24 deg |
| `isl.pond` | ISLRTC/P/Pond.mp4 | fix | R shape 25 deg; L shape 28 deg |
| `isl.price` | Society/20. Price/MVI_4331 | fix | R path 0.35 |
| `isl.pull` | ISLRTC/P/Pull.mp4 | fix | not rendered after the rebuild |
| `isl.push` | ISLRTC/P/Push.mp4 | fix | not rendered after the rebuild |
| `isl.race (ethnicity)` | Society/8. Race (ethnicity)/MVI_4279 | fix | R path 0.46; L shape 27 deg |
| `isl.remember` | ISLRTC/R/Remember.mp4 | fix | R placed 0.53 off |
| `isl.reporter` | Jobs/97. Reporter/MVI_4790 | fix | R placed 0.51 off |
| `isl.rich` | Adjectives/11. rich/MVI_9599 | fix | R path 0.34 |
| `isl.rights` | ISLRTC/R/Rights.mp4 | fix | not rendered after the rebuild |
| `isl.river` | ISLRTC/R/River.mp4 | fix | not rendered after the rebuild |
| `isl.saturday` | Days_and_Time/72. Saturday/MVI_5466 | fix | R placed 0.50 off |
| `isl.secretary` | Jobs/90. Secretary/MVI_5340 | fix | lead L on the source, R on the character |
| `isl.see` | ISLRTC/S/See.mp4 | fix | R shape 26 deg |
| `isl.sell` | ISLRTC/S/Sell.mp4 | fix | R shape 23 deg; L shape 25 deg |
| `isl.sensitive` | ISLRTC/S/Sensitive.mp4 | fix | not rendered after the rebuild |
| `isl.services` | ISLRTC/S/Services.mp4 | fix | not rendered after the rebuild |
| `isl.she` | Pronouns/43. she/MVI_0009 | fix | R placed 0.57 off |
| `isl.soft` | Adjectives/25. soft/MVI_9791 | fix | L path 0.46 |
| `isl.son` | People/58. Son/MVI_4053 | fix | R placed 0.49 off |
| `isl.sports` | ISLRTC/S/Sports.mp4 | fix | L shape 22 deg |
| `isl.statement` | ISLRTC/S/Statement.mp4 | fix | not rendered after the rebuild |
| `isl.stick` | ISLRTC/S/Stick.mp4 | fix | not rendered after the rebuild |
| `isl.subject` | ISLRTC/S/Subject.mp4 | fix | not rendered after the rebuild |
| `isl.sunday` | Days_and_Time/66. Sunday/MVI_5444 | fix | R placed 0.47 off |
| `isl.telephone` | Home/44. Telephone/MVI_8834 | fix | R placed 0.48 off; R path 0.45; R shape 25 deg |
| `isl.there` | ISLRTC/T/There.mp4 | fix | R placed 0.77 off |
| `isl.they` | Pronouns/47. they/MVI_9911 | fix | R placed 0.89 off |
| `isl.thin` | Adjectives/14. thin/MVI_9610 | fix | R shape 23 deg |
| `isl.threat` | ISLRTC/T/Threat - English.mp4 | fix | not rendered after the rebuild |
| `isl.thursday` | Days_and_Time/70. Thursday/MVI_9152 | fix | R shape 22 deg |
| `isl.today` | Days_and_Time/73. Today/MVI_5471 | fix | R path 0.13 |
| `isl.tomorrow` | Days_and_Time/74. Tomorrow/MVI_5476 | fix | R placed 0.50 off |
| `isl.travel` | ISLRTC/T/Travel.mp4 | fix | R placed 0.50 off |
| `isl.truth` | ISLRTC/T/Truth.mp4 | fix | not rendered after the rebuild |
| `isl.university` | Places/26. University/MVI_3465 | fix | R path 0.44 |
| `isl.wait` | ISLRTC/W/Wait.mp4 | fix | R shape 23 deg |
| `isl.war` | Society/11. War/MVI_4291 | fix | R shape 28 deg |
| `isl.warm` | Adjectives/89. warm/MVI_5143 | fix | R placed 0.47 off |
| `isl.welfare` | ISLRTC/W/Welfare.mp4 | fix | not rendered after the rebuild |
| `isl.wide` | Adjectives/81. wide/MVI_5277 | fix | L shape 24 deg |
| `isl.yellow` | Colours/50. Yellow/MVI_3728 | fix | R path 0.49 |
| `isl.account` | ISLRTC/A/Account.mp4 | ok |  |
| `isl.address` | ISLRTC/A/Address.mp4 | ok |  |
| `isl.adult` | People/80. Adult/MVI_5298 | ok |  |
| `isl.after` | ISLRTC/A/After.mp4 | ok |  |
| `isl.again` | ISLRTC/A/Again_Repeat.mp4 | ok |  |
| `isl.age` | ISLRTC/A/Age.mp4 | ok |  |
| `isl.alive` | Adjectives/34. alive/MVI_9704 | ok |  |
| `isl.alright` | Greetings/50. Alright/MVI_9989 | ok |  |
| `isl.animal` | Animals/8. Animal/MVI_3056 | ok |  |
| `isl.answers` | ISLRTC/A/Answer.mp4 | ok |  |
| `isl.arm` | ISLRTC/A/Arm.mp4 | ok |  |
| `isl.arrest` | ISLRTC/A/Arrest.mp4 | ok |  |
| `isl.arrested` | ISLRTC/A/Arrest.mp4 | ok |  |
| `isl.art` | ISLRTC/A/Art.mp4 | ok |  |
| `isl.ask` | ISLRTC/A/Ask_Question.mp4 | ok |  |
| `isl.attack` | Society/13. Attack/MVI_4299 | ok |  |
| `isl.aunt` | ISLRTC/A/Aunt.mp4 | ok |  |
| `isl.authority` | ISLRTC/A/Authority.mp4 | ok |  |
| `isl.bad` | Adjectives/95. bad/MVI_5241 | ok |  |
| `isl.bail` | ISLRTC/B/Bail.mp4 | ok |  |
| `isl.ball` | Society/19. Ball/MVI_4326 | ok |  |
| `isl.bank` | Places/35. Bank/MVI_3502 | ok |  |
| `isl.bear` | Kaggle/Bear/WIN_20231102_12_39_24_Pro.mp4 | ok |  |
| `isl.beautiful` | Adjectives/5. Beautiful/MVI_9569 | ok |  |
| `isl.bed` | Home/26. Bed/MVI_9009 | ok |  |
| `isl.before` | ISLRTC/B/Before.mp4 | ok |  |
| `isl.bicycle` | Means_of_Transportation/13. Bicycle/MVI_4182 | ok |  |
| `isl.big large` | Adjectives/83. big large/MVI_5123 | ok |  |
| `isl.bill` | Society/5. Bill/MVI_8939 | ok |  |
| `isl.blind` | Adjectives/8. Blind/MVI_9587 | ok |  |
| `isl.blood` | ISLRTC/B/Blood.mp4 | ok |  |
| `isl.board` | ISLRTC/B/Board_(Panel).mp4 | ok |  |
| `isl.boat` | Means_of_Transportation/15. Boat/MVI_4188 | ok |  |
| `isl.book` | Home/37. Book/MVI_9041 | ok |  |
| `isl.box` | Home/46. Box/MVI_4438 | ok |  |
| `isl.brinjal` | Kaggle/Brinjal/WIN_20231102_10_56_38_Pro.mp4 | ok |  |
| `isl.brother` | People/66. Brother/MVI_3781 | ok |  |
| `isl.brown` | Colours/51. Brown/MVI_5197 | ok |  |
| `isl.budget` | Kaggle/Budget/WIN_20230901_09_29_50_Pro.mp4 | ok |  |
| `isl.bus` | Means_of_Transportation/14. Bus/MVI_3249 | ok |  |
| `isl.busy` | Kaggle/Busy/WIN_20230831_14_46_17_Pro.mp4 | ok |  |
| `isl.cabbage` | Kaggle/Cabbage/WIN_20231102_11_08_18_Pro.mp4 | ok |  |
| `isl.cap` | ISLRTC/C/Cap.mp4 | ok |  |
| `isl.card` | Home/48. Card/MVI_4447 | ok |  |
| `isl.carrot` | Kaggle/Carrot/WIN_20231102_11_18_27_Pro.mp4 | ok |  |
| `isl.case` | ISLRTC/C/Case.mp4 | ok |  |
| `isl.cat` | Animals/2. Cat/MVI_3005 | ok |  |
| `isl.cauliflower` | Kaggle/Cauliflower/WIN_20231102_11_35_26_Pro.mp4 | ok |  |
| `isl.chair` | Home/25. Chair/MVI_9004 | ok |  |
| `isl.cheap` | Adjectives/16. cheap/MVI_9618 | ok |  |
| `isl.child` | People/79. Child/MVI_4116 | ok |  |
| `isl.chilli` | Kaggle/Chilli/WIN_20231102_11_48_08_Pro.mp4 | ok |  |
| `isl.class` | ISLRTC/C/Class.mp4 | ok |  |
| `isl.clean` | Adjectives/29. clean/MVI_9679 | ok |  |
| `isl.clock` | Electronics/51. Clock/MVI_5382 | ok |  |
| `isl.close` | Kaggle/Close/WIN_20231103_10_35_13_Pro.mp4 | ok |  |
| `isl.clothing` | Clothes/46. Clothing/MVI_5181 | ok |  |
| `isl.cold` | Adjectives/88. cold/MVI_5221 | ok |  |
| `isl.college` | ISLRTC/C/College.mp4 | ok |  |
| `isl.colour` | Colours/57. Colour/MVI_5216 | ok |  |
| `isl.computer` | Electronics/55. Computer/MVI_5397 | ok |  |
| `isl.cook` | Kaggle/Cook/WIN_20231103_10_46_17_Pro.mp4 | ok |  |
| `isl.cool` | Adjectives/90. cool/MVI_9498 | ok |  |
| `isl.court` | Places/23. Court/MVI_3454 | ok |  |
| `isl.cow` | Animals/5. Cow/MVI_3017 | ok |  |
| `isl.crocodile` | Kaggle/Crocodile/WIN_20231102_12_46_55_Pro.mp4 | ok |  |
| `isl.crowd` | People/83. Crowd/MVI_5307 | ok |  |
| `isl.cucumber` | Kaggle/Cucumber/WIN_20231102_11_57_21_Pro.mp4 | ok |  |
| `isl.curved` | Adjectives/18. curved/MVI_9761 | ok |  |
| `isl.daughter` | People/59. Daughter/MVI_4934 | ok |  |
| `isl.day` | ISLRTC/D/Day.mp4 | ok |  |
| `isl.deaf` | Adjectives/7. Deaf/MVI_9848 | ok |  |
| `isl.death` | Society/2. Death/MVI_8931 | ok |  |
| `isl.deer` | Kaggle/Deer/WIN_20231102_12_52_54_Pro.mp4 | ok |  |
| `isl.details` | ISLRTC/D/Detail_Feature.mp4 | ok |  |
| `isl.dirty` | Adjectives/30. dirty/MVI_9685 | ok |  |
| `isl.disability` | ISLRTC/D/Disability.mp4 | ok |  |
| `isl.doctor` | Jobs/87. Doctor/MVI_4755 | ok |  |
| `isl.dog` | Animals/1. Dog/MVI_4146 | ok |  |
| `isl.dream` | Home/27. Dream/MVI_9011 | ok |  |
| `isl.dress` | Clothes/38. Dress/MVI_3694 | ok |  |
| `isl.drink` | Kaggle/Drink/WIN_20231103_10_55_55_Pro.mp4 | ok |  |
| `isl.during` | ISLRTC/D/During.mp4 | ok |  |
| `isl.eight` | ISLRTC/Numbers/8_Eight.mp4 | ok |  |
| `isl.elephant` | Kaggle/Elephant/WIN_20231102_12_59_25_Pro.mp4 | ok |  |
| `isl.energy` | Society/10. Energy/MVI_8954 | ok |  |
| `isl.evening` | Days_and_Time/84. Evening/MVI_4661 | ok |  |
| `isl.exam` | Kaggle/Exam/WIN_20231103_11_20_57_Pro.mp4 | ok |  |
| `isl.expensive` | Adjectives/15. expensive/MVI_9614 | ok |  |
| `isl.explain` | ISLRTC/E/Explain.mp4 | ok |  |
| `isl.eye` | ISLRTC/E/Eye.mp4 | ok |  |
| `isl.family` | People/68. Family/MVI_5250 | ok |  |
| `isl.famous` | Adjectives/39. famous/MVI_9718 | ok |  |
| `isl.far` | ISLRTC/F/Far.mp4 | ok |  |
| `isl.fast` | Adjectives/86. fast/MVI_5293 | ok |  |
| `isl.father` | People/61. Father/MVI_4061 | ok |  |
| `isl.favourite` | ISLRTC/F/Favourite.mp4 | ok |  |
| `isl.fight` | ISLRTC/F/Fight.mp4 | ok |  |
| `isl.finish` | ISLRTC/F/Finish.mp4 | ok |  |
| `isl.fish` | Animals/3. Fish/MVI_3034 | ok |  |
| `isl.five` | ISLRTC/Numbers/5_Five.mp4 | ok |  |
| `isl.flat` | Adjectives/17. flat/MVI_9757 | ok |  |
| `isl.force` | ISLRTC/F/Force.mp4 | ok |  |
| `isl.forest` | ISLRTC/F/Forest.mp4 | ok |  |
| `isl.free` | ISLRTC/F/Free.mp4 | ok |  |
| `isl.friend` | People/81. Friend/MVI_3977 | ok |  |
| `isl.from` | ISLRTC/F/From.mp4 | ok |  |
| `isl.gift` | Home/47. Gift/MVI_9079 | ok |  |
| `isl.giraffe` | Kaggle/Giraffe/WIN_20231102_13_05_48_Pro.mp4 | ok |  |
| `isl.give` | Kaggle/Give/WIN_20231103_10_59_43_Pro.mp4 | ok |  |
| `isl.good` | Adjectives/94. good/MVI_5317 | ok |  |
| `isl.good afternoon` | Greetings/52. Good afternoon/MVI_9973 | ok |  |
| `isl.good evening` | Greetings/53. Good evening/MVI_0107 | ok |  |
| `isl.good morning` | Greetings/51. Good Morning/MVI_9968 | ok |  |
| `isl.good night` | Greetings/54. Good night/MVI_9983 | ok |  |
| `isl.grandfather` | People/69. Grandfather/MVI_4086 | ok |  |
| `isl.grandmother` | People/70. Grandmother/MVI_4087 | ok |  |
| `isl.green` | Colours/48. Green/MVI_3722 | ok |  |
| `isl.grey` | Colours/56. Grey/MVI_5212 | ok |  |
| `isl.ground` | Places/34. Ground/MVI_3498 | ok |  |
| `isl.group` | ISLRTC/G/Group.mp4 | ok |  |
| `isl.handcuffs` | ISLRTC/H/Handcuffs.mp4 | ok |  |
| `isl.happen` | ISLRTC/H/Happen.mp4 | ok |  |
| `isl.happened` | ISLRTC/H/Happen.mp4 | ok |  |
| `isl.head` | ISLRTC/H/Head.mp4 | ok |  |
| `isl.health` | ISLRTC/H/Health.mp4 | ok |  |
| `isl.healthy` | Adjectives/99. healthy/MVI_5254 | ok |  |
| `isl.heavy` | Adjectives/35. heavy/MVI_9710 | ok |  |
| `isl.help` | ISLRTC/H/Help.mp4 | ok |  |
| `isl.helps` | ISLRTC/H/Help.mp4 | ok |  |
| `isl.here` | ISLRTC/H/Here.mp4 | ok |  |
| `isl.hit` | ISLRTC/H/Hit.mp4 | ok |  |
| `isl.horse` | Animals/7. Horse/MVI_3054 | ok |  |
| `isl.hour` | Days_and_Time/79. Hour/MVI_5496 | ok |  |
| `isl.hours` | ISLRTC/H/Hour.mp4 | ok |  |
| `isl.house` | Places/19. House/MVI_3438 | ok |  |
| `isl.how` | ISLRTC/H/How_(Explanation).mp4 | ok |  |
| `isl.how are you` | Greetings/49. How are you/MVI_0033 | ok |  |
| `isl.how many` | ISLRTC/H/How_Many.mp4 | ok |  |
| `isl.husband` | People/71. Husband/MVI_4090 | ok |  |
| `isl.i` | Pronouns/40. I/MVI_9879 | ok |  |
| `isl.interview` | Kaggle/Interview/WIN_20230901_09_45_57_Pro.mp4 | ok |  |
| `isl.introduce` | ISLRTC/I/Introduce.mp4 | ok |  |
| `isl.it` | Pronouns/44. it/MVI_9900 | ok |  |
| `isl.job` | Jobs/99. Job/MVI_4794 | ok |  |
| `isl.judge` | ISLRTC/J/Judge.mp4 | ok |  |
| `isl.jump` | Kaggle/Jump/WIN_20231103_11_03_40_Pro.mp4 | ok |  |
| `isl.karnataka` | Kaggle/Karnataka/WIN_20231103_11_14_01_Pro.mp4 | ok |  |
| `isl.key` | Home/39. Key/MVI_4411 | ok |  |
| `isl.knife` | ISLRTC/K/Knife.mp4 | ok |  |
| `isl.laptop` | Electronics/56. Laptop/MVI_5402 | ok |  |
| `isl.law` | ISLRTC/L/Law.mp4 | ok |  |
| `isl.lawyer` | Jobs/86. Lawyer/MVI_5320 | ok |  |
| `isl.legal` | ISLRTC/L/Legal.mp4 | ok |  |
| `isl.lemon` | Kaggle/Lemon/WIN_20231102_12_05_29_Pro.mp4 | ok |  |
| `isl.less` | ISLRTC/L/Less_Few.mp4 | ok |  |
| `isl.letter` | Home/41. Letter/MVI_4419 | ok |  |
| `isl.library` | Places/29. Library/MVI_3477 | ok |  |
| `isl.lie` | ISLRTC/L/Lie_False.mp4 | ok |  |
| `isl.light` | Adjectives/36. light/MVI_9713 | ok |  |
| `isl.like` | ISLRTC/L/Like.mp4 | ok |  |
| `isl.lion` | Kaggle/Lion/WIN_20231102_13_12_11_Pro.mp4 | ok |  |
| `isl.live` | ISLRTC/L/Live.mp4 | ok |  |
| `isl.lock` | Home/43. Lock/MVI_9061 | ok |  |
| `isl.long` | Adjectives/78. long/MVI_5188 | ok |  |
| `isl.loose` | Adjectives/22. loose/MVI_9653 | ok |  |
| `isl.lost` | ISLRTC/L/Lose.mp4 | ok |  |
| `isl.loud` | Adjectives/1. loud/MVI_5177 | ok |  |
| `isl.low` | Adjectives/24. low/MVI_9658 | ok |  |
| `isl.manager` | Jobs/96. Manager/MVI_5364 | ok |  |
| `isl.many` | ISLRTC/M/Many_Numerous.mp4 | ok |  |
| `isl.marriage` | Society/6. Marriage/MVI_8942 | ok |  |
| `isl.marry` | ISLRTC/M/Marry_Wed.mp4 | ok |  |
| `isl.maths` | ISLRTC/M/Maths.mp4 | ok |  |
| `isl.maybe` | Kaggle/Maybe/WIN_20230901_09_18_02_Pro.mp4 | ok |  |
| `isl.mean` | Adjectives/10. Mean/MVI_9592 | ok |  |
| `isl.medicine` | Society/3. Medicine/MVI_4262 | ok |  |
| `isl.meet` | ISLRTC/M/Meet.mp4 | ok |  |
| `isl.minute` | Days_and_Time/80. Minute/MVI_5054 | ok |  |
| `isl.mistake` | ISLRTC/M/Mistake_Error.mp4 | ok |  |
| `isl.monday` | Days_and_Time/67. Monday/MVI_5011 | ok |  |
| `isl.money` | Society/4. Money/MVI_8936 | ok |  |
| `isl.monkey` | Kaggle/Monkey/WIN_20231102_13_15_19_Pro.mp4 | ok |  |
| `isl.more` | ISLRTC/M/More.mp4 | ok |  |
| `isl.morning` | Days_and_Time/82. Morning/MVI_5508 | ok |  |
| `isl.mosque` | ISLRTC/M/Mosque.mp4 | ok |  |
| `isl.mother` | People/60. Mother/MVI_4939 | ok |  |
| `isl.music` | ISLRTC/M/Music.mp4 | ok |  |
| `isl.narrow` | Adjectives/82. narrow/MVI_5282 | ok |  |
| `isl.near` | ISLRTC/N/Near_Nearby_Close_By.mp4 | ok |  |
| `isl.new` | Adjectives/91. new/MVI_9501 | ok |  |
| `isl.newspaper` | Society/15. Newspaper/MVI_4311 | ok |  |
| `isl.next` | ISLRTC/N/Next.mp4 | ok |  |
| `isl.nice` | Adjectives/9. Nice/MVI_9588 | ok |  |
| `isl.night` | Days_and_Time/85. Night/MVI_4664 | ok |  |
| `isl.no` | ISLRTC/N/No.mp4 | ok |  |
| `isl.none` | ISLRTC/N/None_Nothing.mp4 | ok |  |
| `isl.nose` | ISLRTC/N/Nose.mp4 | ok |  |
| `isl.offence` | ISLRTC/O/Offence.mp4 | ok |  |
| `isl.office` | Places/25. Office/MVI_3461 | ok |  |
| `isl.ok` | ISLRTC/O/Okay.mp4 | ok |  |
| `isl.one` | ISLRTC/Numbers/1_One.mp4 | ok |  |
| `isl.onion` | Kaggle/Onion/WIN_20231102_12_13_53_Pro.mp4 | ok |  |
| `isl.open` | ISLRTC/O/Open.mp4 | ok |  |
| `isl.other` | ISLRTC/O/Other.mp4 | ok |  |
| `isl.page` | Home/38. Page/MVI_4403 | ok |  |
| `isl.pain` | ISLRTC/P/Pain.mp4 | ok |  |
| `isl.paint` | Home/40. Paint/MVI_4415 | ok |  |
| `isl.pant` | Clothes/43. Pant/MVI_4005 | ok |  |
| `isl.paper` | Home/42. Paper/MVI_4421 | ok |  |
| `isl.parent` | People/62. Parent/MVI_5233 | ok |  |
| `isl.parents` | ISLRTC/P/Parents.mp4 | ok |  |
| `isl.park` | Places/27. Park/MVI_3470 | ok |  |
| `isl.patient` | Jobs/88. Patient/MVI_5329 | ok |  |
| `isl.pay` | ISLRTC/P/Pay.mp4 | ok |  |
| `isl.peace` | Society/12. Peace/MVI_4295 | ok |  |
| `isl.peacock` | Kaggle/Peacock/WIN_20231103_12_51_54_Pro.mp4 | ok |  |
| `isl.pencil` | Home/33. Pencil/MVI_4386 | ok |  |
| `isl.people` | ISLRTC/P/People.mp4 | ok |  |
| `isl.personal` | ISLRTC/P/Personal.mp4 | ok |  |
| `isl.phone` | ISLRTC/C/Call_(Phone).mp4 | ok |  |
| `isl.photograph` | Home/35. Photograph/MVI_4393 | ok |  |
| `isl.pigeon` | Kaggle/Pigeon/WIN_20231103_12_54_54_Pro.mp4 | ok |  |
| `isl.pink` | Colours/52. Pink/MVI_4213 | ok |  |
| `isl.plane` | Means_of_Transportation/10. Plane/MVI_3236 | ok |  |
| `isl.player` | People/82. Player/MVI_4247 | ok |  |
| `isl.please` | ISLRTC/P/Please.mp4 | ok |  |
| `isl.pleased` | Greetings/56. Pleased/MVI_0009 | ok |  |
| `isl.pocket` | Clothes/45. Pocket/MVI_4013 | ok |  |
| `isl.police` | Jobs/92. Police/MVI_5347 | ok |  |
| `isl.poor` | Adjectives/12. poor/MVI_9737 | ok |  |
| `isl.pour` | Kaggle/Pour/WIN_20231103_11_09_17_Pro.mp4 | ok |  |
| `isl.president` | People/75. President/MVI_3956 | ok |  |
| `isl.priest` | Jobs/91. Priest/MVI_4489 | ok |  |
| `isl.put` | ISLRTC/P/Put.mp4 | ok |  |
| `isl.quiet` | Adjectives/2. quiet/MVI_5262 | ok |  |
| `isl.radio` | Electronics/60. Radio/MVI_5417 | ok |  |
| `isl.radish` | Kaggle/Radish/WIN_20231102_12_27_07_Pro.mp4 | ok |  |
| `isl.railway station` | ISLRTC/S/Station.mp4 | ok |  |
| `isl.rape` | ISLRTC/R/Rape.mp4 | ok |  |
| `isl.read` | ISLRTC/R/Read.mp4 | ok |  |
| `isl.ready` | ISLRTC/R/Ready.mp4 | ok |  |
| `isl.red` | Colours/47. Red/MVI_3719 | ok |  |
| `isl.relatives` | ISLRTC/R/Relatives.mp4 | ok |  |
| `isl.religion` | Society/1. Religion/MVI_8929 | ok |  |
| `isl.restaurant` | Places/22. Restaurant/MVI_3449 | ok |  |
| `isl.right` | ISLRTC/R/Right.mp4 | ok |  |
| `isl.ring` | Home/49. Ring/MVI_4452 | ok |  |
| `isl.road` | ISLRTC/R/Road.mp4 | ok |  |
| `isl.room` | ISLRTC/R/Room.mp4 | ok |  |
| `isl.run` | ISLRTC/R/Run.mp4 | ok |  |
| `isl.sad` | Adjectives/4. sad/MVI_9720 | ok |  |
| `isl.safe` | ISLRTC/S/Safe.mp4 | ok |  |
| `isl.scared` | ISLRTC/F/Fear.mp4 | ok |  |
| `isl.school` | Places/24. School/MVI_3458 | ok |  |
| `isl.screen` | Electronics/57. Screen/MVI_5406 | ok |  |
| `isl.season` | Seasons/65. Season/MVI_9137 | ok |  |
| `isl.second` | Days_and_Time/81. Second/MVI_9185 | ok |  |
| `isl.send` | ISLRTC/S/Send.mp4 | ok |  |
| `isl.seven` | ISLRTC/Numbers/7_Seven.mp4 | ok |  |
| `isl.shallow` | Adjectives/28. shallow/MVI_9675 | ok |  |
| `isl.shirt` | Clothes/41. Shirt/MVI_4880 | ok |  |
| `isl.shoe` | ISLRTC/S/Shoes.mp4 | ok |  |
| `isl.shoes` | Clothes/44. Shoes/MVI_4888 | ok |  |
| `isl.shop` | ISLRTC/S/Shop.mp4 | ok |  |
| `isl.show` | ISLRTC/S/Show.mp4 | ok |  |
| `isl.sick` | Adjectives/98. sick/MVI_5253 | ok |  |
| `isl.sister` | People/67. Sister/MVI_4080 | ok |  |
| `isl.sit` | ISLRTC/S/Sit.mp4 | ok |  |
| `isl.six` | ISLRTC/Numbers/6_Six.mp4 | ok |  |
| `isl.skirt` | Clothes/40. Skirt/MVI_4876 | ok |  |
| `isl.slow` | Adjectives/85. slow/MVI_9483 | ok |  |
| `isl.slowly` | ISLRTC/S/Slow.mp4 | ok |  |
| `isl.small` | ISLRTC/S/Small.mp4 | ok |  |
| `isl.small little` | Adjectives/84. small little/MVI_5287 | ok |  |
| `isl.soap` | Home/36. Soap/MVI_9038 | ok |  |
| `isl.social` | ISLRTC/S/Social.mp4 | ok |  |
| `isl.soldier` | Jobs/93. Soldier/MVI_8895 | ok |  |
| `isl.something` | ISLRTC/S/Something.mp4 | ok |  |
| `isl.soon` | ISLRTC/S/Soon.mp4 | ok |  |
| `isl.sorry` | ISLRTC/S/Sorry.mp4 | ok |  |
| `isl.sparrow` | Kaggle/Sparrow/WIN_20231103_12_58_35_Pro.mp4 | ok |  |
| `isl.speak` | ISLRTC/S/Speak_Speech.mp4 | ok |  |
| `isl.sport` | Society/17. Sport/MVI_8976 | ok |  |
| `isl.spring` | Seasons/62. Spring/MVI_5427 | ok |  |
| `isl.stand` | ISLRTC/S/Stand.mp4 | ok |  |
| `isl.start` | ISLRTC/S/Start_Begin.mp4 | ok |  |
| `isl.state` | ISLRTC/S/State.mp4 | ok |  |
| `isl.steal` | ISLRTC/S/Steal.mp4 | ok |  |
| `isl.still` | Kaggle/Still/WIN_20231014_19_05_10_Pro_Trim - Copy_cropped.mp4 | ok |  |
| `isl.stomach` | ISLRTC/S/Stomach.mp4 | ok |  |
| `isl.stop` | ISLRTC/S/Stop.mp4 | ok |  |
| `isl.store or shop` | Places/28. Store or Shop/MVI_3472 | ok |  |
| `isl.street or road` | Places/20. Street or Road/MVI_3440 | ok |  |
| `isl.strong` | Adjectives/31. strong/MVI_9690 | ok |  |
| `isl.student` | Jobs/85. Student/MVI_8869 | ok |  |
| `isl.suit` | Clothes/39. Suit/MVI_3992 | ok |  |
| `isl.summer` | Seasons/61. Summer/MVI_5421 | ok |  |
| `isl.switch` | Kaggle/Switch/WIN_20230901_09_08_32_Pro.mp4 | ok |  |
| `isl.t-shirt` | Clothes/42. T-Shirt/MVI_4882 | ok |  |
| `isl.table` | Home/24. Table/MVI_4346 | ok |  |
| `isl.take` | ISLRTC/T/Take.mp4 | ok |  |
| `isl.talk` | ISLRTC/T/Talk_Chat.mp4 | ok |  |
| `isl.tall` | Adjectives/80. tall/MVI_5274 | ok |  |
| `isl.tap` | ISLRTC/T/Tap_(Sign_2).mp4 | ok |  |
| `isl.tea` | Kaggle/Tea/WIN_20230831_12_03_54_Pro.mp4 | ok |  |
| `isl.teacher` | Jobs/84. Teacher/MVI_5310 | ok |  |
| `isl.technology` | Society/9. Technology/MVI_4825 | ok |  |
| `isl.television` | Electronics/59. Television/MVI_4985 | ok |  |
| `isl.temple` | Places/31. Temple/MVI_3401 | ok |  |
| `isl.thank you` | Greetings/55. Thank you/MVI_9987 | ok |  |
| `isl.thick` | Adjectives/13. thick/MVI_9607 | ok |  |
| `isl.things` | ISLRTC/T/Things.mp4 | ok |  |
| `isl.three` | ISLRTC/Numbers/3_Three.mp4 | ok |  |
| `isl.throat` | ISLRTC/T/Throat.mp4 | ok |  |
| `isl.thumb` | ISLRTC/T/Thumb.mp4 | ok |  |
| `isl.tiger` | Kaggle/Tiger/WIN_20231102_13_19_00_Pro.mp4 | ok |  |
| `isl.tight` | Adjectives/21. tight/MVI_9647 | ok |  |
| `isl.time` | Days_and_Time/86. Time/MVI_9202 | ok |  |
| `isl.toilet` | ISLRTC/B/Bathroom.mp4 | ok |  |
| `isl.tool` | Home/50. Tool/MVI_4456 | ok |  |
| `isl.touch` | ISLRTC/T/Touch.mp4 | ok |  |
| `isl.train` | Means_of_Transportation/9. Train/MVI_3231 | ok |  |
| `isl.train station` | Places/21. Train Station/MVI_3602 | ok |  |
| `isl.train ticket` | Means_of_Transportation/16. train ticket/MVI_3224 | ok |  |
| `isl.transportation` | Means_of_Transportation/17. Transportation/MVI_3258 | ok |  |
| `isl.truck` | Means_of_Transportation/12. Truck/MVI_3212 | ok |  |
| `isl.true` | ISLRTC/T/True.mp4 | ok |  |
| `isl.tuesday` | Days_and_Time/68. Tuesday/MVI_5450 | ok |  |
| `isl.turtle` | Kaggle/Turtle/WIN_20231102_13_23_44_Pro.mp4 | ok |  |
| `isl.two` | ISLRTC/Numbers/2_Two.mp4 | ok |  |
| `isl.ugly` | Adjectives/6. Ugly/MVI_9576 | ok |  |
| `isl.umbrella` | Kaggle/Umbrella/WIN_20230831_13_07_31_Pro.mp4 | ok |  |
| `isl.uncle` | ISLRTC/U/Uncle.mp4 | ok |  |
| `isl.understand` | ISLRTC/U/Understand_Comprehend.mp4 | ok |  |
| `isl.up` | ISLRTC/U/Up.mp4 | ok |  |
| `isl.vegetables` | Kaggle/Vegetables/WIN_20231102_10_41_26_Pro.mp4 | ok |  |
| `isl.village` | ISLRTC/V/Village.mp4 | ok |  |
| `isl.volcano` | Kaggle/Volcano/WIN_20231103_11_29_32_Pro.mp4 | ok |  |
| `isl.waiter` | Jobs/89. Waiter/MVI_4763 | ok |  |
| `isl.walk` | ISLRTC/W/Walk.mp4 | ok |  |
| `isl.want` | ISLRTC/W/Want_Desire_Wish_For.mp4 | ok |  |
| `isl.wash` | ISLRTC/W/Wash_(Face).mp4 | ok |  |
| `isl.water` | ISLRTC/W/Water.mp4 | ok |  |
| `isl.we` | Pronouns/45. we/MVI_0018 | ok |  |
| `isl.weak` | Adjectives/32. weak/MVI_9819 | ok |  |
| `isl.wear` | ISLRTC/W/Wear.mp4 | ok |  |
| `isl.wednesday` | Days_and_Time/69. Wednesday/MVI_9150 | ok |  |
| `isl.week` | Days_and_Time/76. Week/MVI_9170 | ok |  |
| `isl.wet` | Adjectives/96. wet/MVI_5245 | ok |  |
| `isl.what` | ISLRTC/W/What.mp4 | ok |  |
| `isl.what is your name` | Kaggle/What is your Name/WIN_20230901_09_59_26_Pro.mp4 | ok |  |
| `isl.where` | ISLRTC/W/Where.mp4 | ok |  |
| `isl.which` | ISLRTC/W/Which.mp4 | ok |  |
| `isl.white` | Colours/55. White/MVI_4922 | ok |  |
| `isl.why` | ISLRTC/W/Why_(Sign_1).mp4 | ok |  |
| `isl.wife` | People/72. Wife/MVI_5265 | ok |  |
| `isl.winter` | Seasons/63. Winter/MVI_9129 | ok |  |
| `isl.woman` | People/65. Woman/MVI_3776 | ok |  |
| `isl.work` | ISLRTC/W/Work.mp4 | ok |  |
| `isl.writer` | Kaggle/Writer/WIN_20230901_09_50_44_Pro.mp4 | ok |  |
| `isl.wrong` | Kaggle/Wrong/WIN_20230831_13_18_20_Pro.mp4 | ok |  |
| `isl.year` | Days_and_Time/78. Year/MVI_5491 | ok |  |
| `isl.yes` | ISLRTC/Y/Yes.mp4 | ok |  |
| `isl.you` | Pronouns/41. you/MVI_9997 | ok |  |
| `isl.you (plural)` | Pronouns/46. you (plural)/MVI_9945 | ok |  |

Totals: 517 signs; ok 366, fix 129, replace 22, drop 0.
