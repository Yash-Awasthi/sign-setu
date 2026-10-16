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
| `letter.a` | Fingerspelling: letter A |  |  |  |  |
| `letter.e` | Fingerspelling: letter E |  |  |  |  |
| `letter.i` | Fingerspelling: letter I |  |  |  |  |
| `letter.o` | Fingerspelling: letter O |  |  |  |  |
| `letter.u` | Fingerspelling: letter U |  |  |  |  |
| `letter.b` | Fingerspelling: letter B |  |  |  |  |
| `letter.c` | Fingerspelling: letter C |  |  |  |  |
| `letter.d` | Fingerspelling: letter D |  |  |  |  |
| `letter.f` | Fingerspelling: letter F |  |  |  |  |
| `letter.g` | Fingerspelling: letter G |  |  |  |  |
| `letter.h` | Fingerspelling: letter H |  |  |  |  |
| `letter.j` | Fingerspelling: letter J |  |  |  |  |
| `letter.k` | Fingerspelling: letter K |  |  |  |  |
| `letter.l` | Fingerspelling: letter L |  |  |  |  |
| `letter.m` | Fingerspelling: letter M |  |  |  |  |
| `letter.n` | Fingerspelling: letter N |  |  |  |  |
| `letter.p` | Fingerspelling: letter P |  |  |  |  |
| `letter.q` | Fingerspelling: letter Q |  |  |  |  |
| `letter.r` | Fingerspelling: letter R |  |  |  |  |
| `letter.s` | Fingerspelling: letter S |  |  |  |  |
| `letter.t` | Fingerspelling: letter T |  |  |  |  |
| `letter.v` | Fingerspelling: letter V |  |  |  |  |
| `letter.w` | Fingerspelling: letter W |  |  |  |  |
| `letter.x` | Fingerspelling: letter X |  |  |  |  |
| `letter.y` | Fingerspelling: letter Y |  |  |  |  |
| `letter.z` | Fingerspelling: letter Z |  |  |  |  |
| `digit.0` | Number 0 |  |  |  |  |
| `digit.1` | Number 1 |  |  |  |  |
| `digit.2` | Number 2 |  |  |  |  |
| `digit.3` | Number 3 |  |  |  |  |
| `digit.4` | Number 4 |  |  |  |  |
| `digit.5` | Number 5 |  |  |  |  |
| `digit.6` | Number 6 |  |  |  |  |
| `digit.7` | Number 7 |  |  |  |  |
| `digit.8` | Number 8 |  |  |  |  |
| `digit.9` | Number 9 |  |  |  |  |
| `sign.thanks` | "thanks" |  |  |  |  |
| `sign.bad` | "bad" |  |  |  |  |
| `sign.doctor` | "doctor" |  |  |  |  |
| `sign.help` | "help" |  |  |  |  |
| `sign.come` | "come" |  |  |  |  |
| `sign.go` | "go" |  |  |  |  |
| `sign.stop` | "stop" |  |  |  |  |
| `sign.finish` | "finish" |  |  |  |  |
| `sign.child` | "child" |  |  |  |  |
| `sign.sit` | "sit" |  |  |  |  |
| `sign.sorry` | "sorry" |  |  |  |  |
| `sign.hit` | "hit" |  |  |  |  |
| `sign.time` | "time" |  |  |  |  |
| `sign.cry` | "cry" |  |  |  |  |
| `sign.see` | "see" |  |  |  |  |
| `sign.know` | "know" |  |  |  |  |
| `sign.wait` | "wait" |  |  |  |  |

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

## 3. Typed-word vocabulary

Typed words the character signs, gestures or pictures (`words.json`, D-22). Reviewers: language reviewer for the written forms of their language, ISL reviewer for the sign column.

| Written forms | Sign | Gesture | Picture | Language | ISL | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| i, me, my, mine, myself, मैं, मुझे, मेरा, मेरी, मेरे, main, mai, mujhe, mera, meri, mere | `me.chest` | `me.chest` | `person` |  |  |  |
| you, your, yours, आप, तुम, तू, आपका, आपकी, आपके, आपको, तुम्हारा, तुम्हें, aap, tum, tu, aapka, aapki, aapke, aapko, tumhara, tumhe | `you.palm` | `you.palm` |  |  |  |  |
| mother, mom, mum, mummy, amma, maa, ma, mata, माँ, मां, माता, मम्मी, अम्मा, दाई, याया | `mother.nosering` | `mother.bindi` | `mother` |  |  |  |
| father, dad, daddy, papa, baba, abba, पिता, पापा, बाबा, बापू, अब्बा, ददा | `father.moustache` | `father.moustache` | `father` |  |  |  |
| grandmother, granny, dadi, nani, दादी, नानी |  | `grandparent.stick` | `grandmother` |  |  |  |
| grandfather, grandpa, dada, nana, दादा, नाना |  | `grandparent.stick` | `grandfather` |  |  |  |
| baby, infant, शिशु, बेबी | `baby.rock` | `baby.rock` | `baby` |  |  |  |
| child, kid, kids, children, boy, girl, बच्चा, बच्ची, बच्चे, लड़का, लड़की, bachcha, bacha, bachchi, ladka, ladki | `sign.child` | `sign.child` |  |  |  |  |
| brother, bhai, bhaiya, भाई, भैया |  | `sibling.rakhi` | `brother` |  |  |  |
| sister, behen, bahan, didi, बहन, दीदी |  | `sibling.rakhi` | `sister` |  |  |  |
| home, house, ghar, makaan, घर, मकान, लोन | `home.roof` | `home.roof` | `home` |  |  |  |
| village, gaon, gaanv, गाँव, गांव |  | `home.roof` | `village` |  |  |  |
| eat, ate, eating, food, meal, khana, khaana, khao, khaya, bhojan, roti, खाना, खाओ, खाया, भोजन, रोटी, भात | `eat.fingers` | `eat.fingers` | `food` |  |  |  |
| hungry, hunger, bhook, bhukh, bhookh, भूख, भूखा, भूखी, भूखे |  | `hungry.belly` | `food` |  |  |  |
| water, drink, thirsty, pani, paani, pyaas, pyas, पानी, प्यास, प्यासा, प्यासी, पियो, पीना, एर | `water.thumb` | `water.cup` | `water` |  |  |  |
| toilet, bathroom, washroom, pee, potty, latrine, शौचालय, टॉयलेट, बाथरूम, पेशाब |  | `toilet.finger` | `toilet` |  |  |  |
| sleep, sleeping, slept, tired, rest, neend, sona, soya, aaram, नींद, सोना, सोओ, सोए, थका, थकी, थके, आराम | `sleep.palms` | `sleep.palms` | `rest` |  |  |  |
| pain, hurt, hurts, hurting, injury, injured, dard, chot, takleef, दर्द, चोट, तकलीफ | `pain.point` | `pain.point` | `pain` |  |  |  |
| sick, ill, fever, bimar, bukhar, बीमार, बुखार |  | `sick.forehead` | `sick` |  |  |  |
| medicine, tablet, pill, dawa, dawai, दवा, दवाई, गोली |  | `medicine.pill` | `medicine` |  |  |  |
| doctor, dr, daktar, डॉक्टर, डाक्टर | `sign.doctor` | `doctor.listen` | `doctor` |  |  |  |
| hospital, aspatal, अस्पताल | `sign.doctor` | `doctor.listen` | `hospital` |  |  |  |
| cold, thand, thanda, ठंड, ठंडा, ठंडी, सर्दी |  | `cold.hug` | `cold` |  |  |  |
| hot, heat, garmi, garam, गर्मी, गरम, गर्म |  | `hot.fan` | `hot` |  |  |  |
| scared, afraid, fear, frightened, darr, dar, dara, dari, डर, डरा, डरी, डरो, भय |  | `scared.cover` | `scared` |  |  |  |
| cry, crying, sad, rona, udaas, रोना, रोए, उदास, दुखी | `sign.cry` | `sign.cry` | `sad` |  |  |  |
| yes, yeah, haan, han, ji, हाँ, हां, जी | `yes.nod` | `yes.nod` | `yes` |  |  |  |
| no, not, nahi, nahin, mat, na, नहीं, ना, मत | `no.shake` | `no.shake` | `no` |  |  |  |
| ok, okay, fine, good, accha, achha, theek, thik, badhiya, अच्छा, अच्छी, ठीक, बढ़िया | `thumbs_up` | `thumbs_up` | `okay` |  |  |  |
| bad, wrong, bura, buri, galat, kharab, बुरा, बुरी, गलत, खराब | `sign.bad` | `sign.bad` |  |  |  |  |
| hello, hi, namaste, namaskar, johar, नमस्ते, नमस्कार, जोहार | `namaste` | `namaste` |  |  |  |  |
| bye, goodbye, alvida, अलविदा | `wave` | `wave` |  |  |  |  |
| thanks, thank, dhanyavad, dhanyawad, shukriya, धन्यवाद, शुक्रिया | `sign.thanks` | `sign.thanks` |  |  |  |  |
| sorry, maaf, maafi, माफ़, माफ, माफ़ी, माफी, क्षमा | `sign.sorry` | `sign.sorry` |  |  |  |  |
| help, madad, sahayata, bachao, मदद, सहायता, बचाओ | `sign.help` | `sign.help` |  |  |  |  |
| safe, surakshit, सुरक्षित | `thumbs_up` | `thumbs_up` | `okay` |  |  |  |
| come, aao, aaiye, आओ, आइए, आइये | `sign.come` | `sign.come` |  |  |  |  |
| go, went, gone, jao, jana, gaya, gayi, जाओ, जाना, गया, गई, गए | `sign.go` | `sign.go` |  |  |  |  |
| stop, ruko, roko, रुको, रोको | `sign.stop` | `sign.stop` | `stop` |  |  |  |
| wait, thahro, intezaar, ठहरो, इंतज़ार, इंतजार | `sign.wait` | `sign.wait` |  |  |  |  |
| sit, baitho, baithiye, बैठो, बैठिए, बैठिये | `sign.sit` | `sign.sit` |  |  |  |  |
| finish, finished, done, khatam, ख़त्म, खत्म, समाप्त | `sign.finish` | `sign.finish` |  |  |  |  |
| hit, beat, beaten, slap, slapped, maara, mara, maar, peeta, मारा, मार, पीटा, थप्पड़ | `sign.hit` | `sign.hit` |  |  |  |  |
| time, when, kab, samay, waqt, कब, समय, वक़्त, वक्त | `sign.time` | `sign.time` |  |  |  |  |
| see, saw, look, watch, dekho, dekha, dekhna, देखो, देखा, देखना | `sign.see` | `sign.see` |  |  |  |  |
| know, knew, pata, jaante, jaanta, jaanti, पता, जानते, जानता, जानती | `sign.know` | `sign.know` |  |  |  |  |
| show, dikhao, दिखाओ, दिखाइए |  | `show_me` |  |  |  |  |
| what, where, who, why, how, which, kya, kahan, kaha, kaun, kyon, kyun, kyu, kaise, kaisa, kitna, kitne, kitni, क्या, कहाँ, कहां, कौन, क्यों, कैसे, कैसा, कितना, कितने, कितनी | `ask` | `ask` |  |  |  |  |
| walk, walked, walking, chalo, chalna, paidal, चलो, चलना, पैदल |  | `walk.fingers` | `walk` |  |  |  |
| road, street, sadak, rasta, सड़क, रास्ता |  | `walk.fingers` | `road` |  |  |  |
| train, rail, railway, ट्रेन, रेल |  | `train.arm` | `train` |  |  |  |
| car, vehicle, gaadi, gadi, गाड़ी, कार |  | `vehicle.steer` | `car` |  |  |  |
| bus, बस |  | `vehicle.steer` | `bus` |  |  |  |
| auto, rickshaw, ऑटो, रिक्शा |  | `vehicle.steer` | `auto` |  |  |  |
| phone, call, mobile, फ़ोन, फोन, मोबाइल, कॉल | `phone.ear` | `phone.ear` | `phone` |  |  |  |
| money, rupees, rupee, paisa, paise, पैसा, पैसे, रुपये, रुपया | `money.rub` | `money.rub` | `money` |  |  |  |
| school, study, padhai, padhna, स्कूल, पढ़ाई, पढ़ना, विद्यालय |  | `school.write` | `school` |  |  |  |
| shop, market, dukaan, bazaar, दुकान, बाज़ार, बाजार |  | `money.rub` | `shop` |  |  |  |
| police, policeman, officer, cop, पुलिस, अधिकारी | `police.salute` | `police.salute` | `police` |  |  |  |
| head, सिर |  |  | `head` |  |  |  |
| eye, eyes, aankh, आँख, आंख |  |  | `eye` |  |  |  |
| ear, ears, kaan, कान |  |  | `ear` |  |  |  |
| mouth, muh, munh, मुँह, मुंह |  |  | `mouth` |  |  |  |
| throat, gala, गला |  |  | `throat` |  |  |  |
| stomach, tummy, belly, पेट |  | `hungry.belly` | `stomach` |  |  |  |
| kamar, peeth, कमर, पीठ |  |  | `back` |  |  |  |
| arm, hand, haath, हाथ, बाँह |  |  | `arm` |  |  |  |
| leg, टाँग, टांग |  |  | `leg` |  |  |  |
| foot, feet, पैर |  |  | `foot` |  |  |  |

## 4. Placeholder recordings

Every file under `assets/content/audio/` is machine-generated placeholder speech until a fluent speaker records it again or approves it. The language reviewer listens to each file in the folder for their language.

| Language folder | Files | Approved by | Notes |
| --- | --- | --- | --- |
| `en` | 37 |  |  |
| `greet` | 14 |  | One greeting per language-finder language; each goes to that language's reviewer |
| `hi` | 37 |  |  |

## 5. Sign-off

| Role | Name | Organisation | Signature | Date |
| --- | --- | --- | --- | --- |
| Child-welfare reviewer |  |  |  |  |
| ISL reviewer |  |  |  |  |
| Language reviewer (Hindi) |  |  |  |  |
| Language reviewer (English) |  |  |  |  |
| Language reviewer (Chhattisgarhi) |  |  |  |  |
| Language reviewer (Gondi) |  |  |  |  |
| Language reviewer (Halbi) |  |  |  |  |
| Content owner |  |  |  |  |
