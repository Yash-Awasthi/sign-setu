# Content Review Record

Sign-off sheet for the subject-facing content in this release, generated from the content files by `tools/content/review_record.py`. Reviewers follow the [content policy](../policies/content-policy.md); every item stays a candidate until it is approved here.

Verdicts: **A** approved, **C** change needed (say what in the notes), **R** reject.

## 1. Gestures

Seen in the gesture gallery of a debug build, frozen at the moment of contact:

```
adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es only <ids, comma separated>
adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es c adult --es only <ids>
```

Reviewers: child-welfare reviewer for every gesture; ISL reviewer as well for entries a Deaf subject may see.

| Gesture | Meaning | How it is made | Child | Adult | Notes |
| --- | --- | --- | --- | --- | --- |
| `okay` | Acknowledgement after any answer: a nod with "okay" |  |  |  |  |
| `namaste` | Greeting on the home screen and at the start of a session |  |  |  |  |
| `wave` | Hello, with the language finder |  |  |  |  |
| `ask` | General asking pose (palms up, raised brows) for typed questions and pictures |  |  |  |  |
| `show_me` | "Show me" |  |  |  |  |
| `thumbs_up` | "Good" |  |  |  |  |
| `count` | Counting on the fingers (age question) |  |  |  |  |
| `sick.forehead` | Feeling unwell: hand to the forehead |  |  |  |  |
| `medicine.pill` | Medicine: taking a pill |  |  |  |  |
| `me.chest` | Me | Flat hand or one finger on own chest. |  |  |  |
| `you.palm` | You | Open palm held towards the other person. |  |  |  |
| `mother.bindi` | Mother (bindi) | Fingertip touches the centre of the forehead. |  |  |  |
| `mother.sindoor` | Mother (sindoor) | Finger drawn along the hair parting. |  |  |  |
| `mother.headcover` | Mother (head covering) | Hands draw a covering over the head. |  |  |  |
| `mother.nosering` | Mother (nose ring) | Fingertip touches the side of the nose. |  |  |  |
| `father.moustache` | Father (moustache) | Finger and thumb stroke above the upper lip. |  |  |  |
| `father.beard` | Father (beard) | Hand strokes down the chin. |  |  |  |
| `father.turban` | Father (turban) | Hand circles around the head. |  |  |  |
| `grandparent.stick` | Grandparent (walking stick) | Bent back, fist resting on an imaginary stick. |  |  |  |
| `baby.rock` | Baby | Arms cradled and rocked at the chest. |  |  |  |
| `sibling.rakhi` | Brother or sister (rakhi) | Fingers tie a thread around the other wrist. |  |  |  |
| `home.roof` | Home | Fingertips of both hands meet in a roof shape. |  |  |  |
| `eat.fingers` | Eat, food | Bunched fingertips brought to the mouth. |  |  |  |
| `hungry.belly` | Hungry | Hand rubs the belly in circles. |  |  |  |
| `water.thumb` | Water (thumb) | Closed fist, thumb tipped towards the mouth. |  |  |  |
| `water.cup` | Water (cupped hand) | Cupped hand raised to the mouth. |  |  |  |
| `toilet.finger` | Toilet | Little finger raised. |  |  |  |
| `sleep.palms` | Sleep, rest | Palms together under a tilted head. |  |  |  |
| `pain.point` | Pain here | Points to or holds a body part, often with a wince. |  |  |  |
| `cold.hug` | Cold | Arms hug the body, shivering. |  |  |  |
| `hot.fan` | Hot | Hand fans the face. |  |  |  |
| `scared.cover` | Scared | Hands cover the face or eyes. |  |  |  |
| `yes.nod` | Yes (clear nod) | Head moves clearly up and down. A sideways wobble is not reliable; confirm with the cards. |  |  |  |
| `no.shake` | No | Head shakes side to side, or index finger wags. |  |  |  |
| `walk.fingers` | Walk | Two fingers walk across the other palm. |  |  |  |
| `train.arm` | Train | Forearm circles beside the body. |  |  |  |
| `vehicle.steer` | Bus or car | Both hands turn a steering wheel. |  |  |  |
| `phone.ear` | Phone | Thumb and little finger held at the ear. |  |  |  |
| `money.rub` | Money | Thumb rubs across the fingertips. |  |  |  |
| `school.write` | School | Writing on the other palm. |  |  |  |
| `police.salute` | Police | Flat hand to the forehead in a salute. |  |  |  |
| `doctor.listen` | Doctor | Two fingers from the ears to the chest, like a stethoscope. |  |  |  |

## 2. Phrase-bank sentences

Each sentence is signed as the sequence shown, with its picture held up. Reviewers: language reviewer for each language, ISL reviewer for the sign sequence, child-welfare reviewer for the wording.

| Sentence (English) | Sentence (Hindi) | Signs | English | Hindi | Signs | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Do you want water? | क्या आपको पानी चाहिए? | `you.palm`, `water.cup`, `ask` |  |  |  |  |
| Do you want food? | क्या आपको खाना चाहिए? | `you.palm`, `eat.fingers`, `ask` |  |  |  |  |
| Do you need the toilet? | क्या आपको शौचालय जाना है? | `you.palm`, `toilet.finger`, `ask` |  |  |  |  |
| Do you want to rest? | क्या आपको आराम चाहिए? | `you.palm`, `sleep.palms`, `ask` |  |  |  |  |
| Do you want a doctor? | क्या आपको डॉक्टर चाहिए? | `you.palm`, `doctor.listen`, `ask` |  |  |  |  |
| Do you want to make a phone call? | क्या आपको फ़ोन करना है? | `you.palm`, `phone.ear`, `ask` |  |  |  |  |
| Are you cold? | क्या आपको ठंड लग रही है? | `you.palm`, `cold.hug`, `ask` |  |  |  |  |
| Are you hot? | क्या आपको गर्मी लग रही है? | `you.palm`, `hot.fan`, `ask` |  |  |  |  |
| Are you hungry? | क्या आपको भूख लगी है? | `you.palm`, `hungry.belly`, `ask` |  |  |  |  |
| Are you in pain? | क्या आपको दर्द हो रहा है? | `you.palm`, `pain.point`, `ask` |  |  |  |  |

## 3. Placeholder recordings

Every file under `assets/content/audio/` is machine-generated placeholder speech until a fluent speaker records it again or approves it. The language reviewer listens to each file in the folder for their language.

| Language folder | Files | Approved by | Notes |
| --- | --- | --- | --- |
| `en` | 37 |  |  |
| `greet` | 14 |  | One greeting per language-finder language; each goes to that language's reviewer |
| `hi` | 37 |  |  |

## 4. Sign-off

| Role | Name | Organisation | Signature | Date |
| --- | --- | --- | --- | --- |
| Child-welfare reviewer |  |  |  |  |
| ISL reviewer |  |  |  |  |
| Language reviewer (Hindi) |  |  |  |  |
| Language reviewer (English) |  |  |  |  |
| Content owner |  |  |  |  |
