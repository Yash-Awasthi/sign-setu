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
| `sign.now` | "now" |  |  |  |  |
| `sign.today` | "today" |  |  |  |  |
| `sign.yesterday` | "yesterday" |  |  |  |  |
| `sign.tomorrow` | "tomorrow" |  |  |  |  |
| `sign.morning` | "morning" |  |  |  |  |
| `sign.night` | "night" |  |  |  |  |
| `sign.day` | "day" |  |  |  |  |
| `sign.man` | "man" |  |  |  |  |
| `sign.woman` | "woman" |  |  |  |  |
| `sign.friend` | "friend" |  |  |  |  |
| `sign.name` | "name" |  |  |  |  |
| `sign.give` | "give" |  |  |  |  |
| `sign.take` | "take" |  |  |  |  |
| `sign.touch` | "touch" |  |  |  |  |
| `sign.run` | "run" |  |  |  |  |
| `sign.hide` | "hide" |  |  |  |  |
| `sign.lost` | "lost" |  |  |  |  |
| `sign.find` | "find" |  |  |  |  |
| `sign.play` | "play" |  |  |  |  |
| `sign.wash` | "wash" |  |  |  |  |
| `sign.ten` | "ten" |  |  |  |  |
| `sign.body.head` | "head" |  |  |  |  |
| `sign.body.eye` | "eye" |  |  |  |  |
| `sign.body.ear` | "ear" |  |  |  |  |
| `sign.body.nose` | "nose" |  |  |  |  |
| `sign.body.mouth` | "mouth" |  |  |  |  |
| `sign.body.stomach` | "stomach" |  |  |  |  |
| `sign.body.arm` | "arm" |  |  |  |  |
| `sign.body.leg` | "leg" |  |  |  |  |
| `sign.body.back` | "back" |  |  |  |  |

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
| time, when, kab, samay, waqt, kal, कब, समय, वक़्त, वक्त, कल | `sign.time` | `sign.time` |  |  |  |  |
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
| phone, call, mobile, फ़ोन, फोन, मोबाइल, कॉल, bulao, bulaya, बुलाओ, बुलाया | `phone.ear` | `phone.ear` | `phone` |  |  |  |
| money, rupees, rupee, paisa, paise, पैसा, पैसे, रुपये, रुपया | `money.rub` | `money.rub` | `money` |  |  |  |
| school, study, padhai, padhna, स्कूल, पढ़ाई, पढ़ना, विद्यालय |  | `school.write` | `school` |  |  |  |
| shop, market, dukaan, bazaar, दुकान, बाज़ार, बाजार |  | `money.rub` | `shop` |  |  |  |
| police, policeman, officer, cop, पुलिस, अधिकारी | `police.salute` | `police.salute` | `police` |  |  |  |
| head, sar, सिर | `sign.body.head` |  | `head` |  |  |  |
| eye, eyes, aankh, आँख, आंख | `sign.body.eye` |  | `eye` |  |  |  |
| ear, ears, kaan, कान | `sign.body.ear` |  | `ear` |  |  |  |
| mouth, muh, munh, मुँह, मुंह | `sign.body.mouth` |  | `mouth` |  |  |  |
| throat, gala, गला |  |  | `throat` |  |  |  |
| stomach, tummy, belly, पेट | `sign.body.stomach` | `hungry.belly` | `stomach` |  |  |  |
| back, kamar, peeth, कमर, पीठ | `sign.body.back` |  | `back` |  |  |  |
| arm, hand, haath, हाथ, बाँह | `sign.body.arm` |  | `arm` |  |  |  |
| leg, legs, tang, taang, टाँग, टांग | `sign.body.leg` |  | `leg` |  |  |  |
| foot, feet, पैर |  |  | `foot` |  |  |  |
| nose, naak, नाक | `sign.body.nose` |  |  |  |  |  |
| today, aaj, आज | `sign.today` |  |  |  |  |  |
| yesterday | `sign.yesterday` |  |  |  |  |  |
| tomorrow | `sign.tomorrow` |  |  |  |  |  |
| now, abhi, अभी | `sign.now` |  |  |  |  |  |
| morning, subah, सुबह | `sign.morning` |  |  |  |  |  |
| night, tonight, raat, रात | `sign.night` |  |  |  |  |  |
| day, din, दिन | `sign.day` |  |  |  |  |  |
| man, men, aadmi, admi, आदमी | `sign.man` |  | `person` |  |  |  |
| woman, women, lady, aurat, mahila, औरत, महिला | `sign.woman` |  |  |  |  |  |
| friend, friends, dost, mitra, दोस्त, मित्र | `sign.friend` |  |  |  |  |  |
| name, naam, नाम | `sign.name` |  |  |  |  |  |
| give, gave, do, de, dena, diya, दो, दे, देना, दिया | `sign.give` |  |  |  |  |  |
| take, took, lo, le, lena, liya, लो, ले, लेना, लिया | `sign.take` |  |  |  |  |  |
| touch, touched, chhoo, chhua, chhuo, छू, छुआ, छुओ | `sign.touch` |  |  |  |  |  |
| run, ran, running, bhaag, bhago, bhaaga, bhaga, भाग, भागो, भागा | `sign.run` |  |  |  |  |  |
| hide, hid, hiding, chhup, chhupo, chhupa, छुप, छुपो, छुपा, छिपा | `sign.hide` |  |  |  |  |  |
| lost, lose, kho, khoya, khoyi, खो, खोया, खोई | `sign.lost` |  |  |  |  |  |
| find, found, search, dhoondo, dhundo, dhoondh, ढूंढो, ढूँढो, ढूंढ | `sign.find` |  |  |  |  |  |
| play, played, playing, khelo, khel, khela, खेल, खेलो, खेला | `sign.play` |  |  |  |  |  |
| wash, washed, dho, dhoo, dhona, dhoyo, धो, धोओ, धोना | `sign.wash` |  |  |  |  |  |
| one, ek, एक | `digit.1` |  | `n1` |  |  |  |
| two | `digit.2` |  | `n2` |  |  |  |
| three, teen, तीन | `digit.3` |  | `n3` |  |  |  |
| four, chaar, char, चार | `digit.4` |  | `n4` |  |  |  |
| five, paanch, panch, पाँच, पांच | `digit.5` |  | `n5` |  |  |  |
| six, chhah, chhe, छह, छः | `digit.6` |  | `n6` |  |  |  |
| seven, saat, सात | `digit.7` |  | `n7` |  |  |  |
| eight, aath, आठ | `digit.8` |  | `n8` |  |  |  |
| nine, nau, नौ | `digit.9` |  | `n9` |  |  |  |
| ten, das, दस | `sign.ten` |  | `n10` |  |  |  |

## 4. Placeholder recordings

Every file under `assets/content/audio/` is machine-generated placeholder speech until a fluent speaker records it again or approves it. The language reviewer listens to each file in the folder for their language.

| Language folder | Files | Approved by | Notes |
| --- | --- | --- | --- |
| `en` | 37 |  |  |
| `greet` | 14 |  | One greeting per language-finder language; each goes to that language's reviewer |
| `hi` | 37 |  |  |

## 5. Prepared questions and subject screen words

The officer's prepared questions (`triage.json`) and the words on the subject's screen (`subject_ui.json`). Reviewers: child-welfare reviewer for the wording, language reviewer for each language.

| Item | English | Hindi | Child welfare | English | Hindi | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `s1` | I am a police officer. I am here to help you. | मैं पुलिस अधिकारी हूँ। मैं आपकी मदद के लिए यहाँ हूँ। |  |  |  |  |
| `s2` | Are you hurt? | क्या आपको चोट लगी है? |  |  |  |  |
| `s3` | Is someone with you? | क्या आपके साथ कोई है? |  |  |  |  |
| `s4` | Do you feel safe here? | क्या आप यहाँ सुरक्षित महसूस करते हैं? |  |  |  |  |
| `h1` | Does it hurt anywhere? Show me where. | क्या कहीं दर्द है? मुझे दिखाइए कहाँ। |  |  |  |  |
| `h2` | Do you need a doctor? | क्या आपको डॉक्टर चाहिए? |  |  |  |  |
| `h3` | Do you feel unwell? | क्या आपकी तबीयत ठीक नहीं है? |  |  |  |  |
| `h4` | Do you take any medicine? | क्या आप कोई दवा लेते हैं? |  |  |  |  |
| `n1` | Do you want water? | क्या आपको पानी चाहिए? |  |  |  |  |
| `n2` | Are you hungry? | क्या आपको भूख लगी है? |  |  |  |  |
| `n3` | Do you need the toilet? | क्या आपको शौचालय जाना है? |  |  |  |  |
| `n4` | Are you cold? | क्या आपको ठंड लग रही है? |  |  |  |  |
| `n5` | Do you want to rest? | क्या आप आराम करना चाहते हैं? |  |  |  |  |
| `i1` | Can you show me your name? You can write or draw it. | क्या आप अपना नाम दिखा सकते हैं? आप उसे लिख या बना सकते हैं। |  |  |  |  |
| `i2` | How old are you? Show me with your fingers. | आपकी उम्र कितनी है? उँगलियों से दिखाइए। |  |  |  |  |
| `i3` | Do you go to school? | क्या आप स्कूल जाते हैं? |  |  |  |  |
| `f1` | Who do you live with? | आप किसके साथ रहते हैं? |  |  |  |  |
| `f2` | Is your mother nearby? | क्या आपकी माँ आस-पास हैं? |  |  |  |  |
| `f3` | Is your father nearby? | क्या आपके पिता आस-पास हैं? |  |  |  |  |
| `f4` | Do you know a phone number? You can write it. | क्या आपको कोई फ़ोन नंबर याद है? आप उसे लिख सकते हैं। |  |  |  |  |
| `l1` | Can you show me where you live? | क्या आप मुझे दिखा सकते हैं कि आप कहाँ रहते हैं? |  |  |  |  |
| `l2` | How did you come here? | आप यहाँ कैसे आए? |  |  |  |  |
| `l3` | Is your home far from here? | क्या आपका घर यहाँ से दूर है? |  |  |  |  |
| `x1` | Can you show me what happened? You can draw it. | क्या आप मुझे दिखा सकते हैं कि क्या हुआ? आप उसे बना सकते हैं। |  |  |  |  |
| `x2` | Is there anything that has happened that you want to tell me about? | क्या कुछ हुआ है जिसके बारे में आप मुझे बताना चाहते हैं? |  |  |  |  |
| `x3` | Is anything worrying you? | क्या कोई बात आपको परेशान कर रही है? |  |  |  |  |
| `g1` | If you do not know, that is OK. Tap I don't know. | अगर आपको नहीं पता, तो कोई बात नहीं। 'पता नहीं' दबाएँ। |  |  |  |  |
| `g2` | If you do not understand me, tap I don't understand. | अगर आपको मेरी बात समझ न आए, तो 'मुझे समझ नहीं आया' दबाएँ। |  |  |  |  |
| `g3` | If I get something wrong, tell me. | अगर मुझसे कुछ ग़लत हो जाए, तो मुझे बताएँ। |  |  |  |  |
| `g4` | You can tap Stop any time. | आप कभी भी 'रुको' दबा सकते हैं। |  |  |  |  |
| `k1` | Do you want a break? | क्या आप थोड़ा आराम करना चाहते हैं? |  |  |  |  |
| `k2` | You are doing well. We can go slowly. | आप अच्छा कर रहे हैं। हम धीरे-धीरे चल सकते हैं। |  |  |  |  |
| `k3` | Can we carry on? | क्या हम आगे बढ़ें? |  |  |  |  |
| `c1` | Thank you for helping me. | मेरी मदद करने के लिए धन्यवाद। |  |  |  |  |
| `c2` | Is there anything you want to tell me or ask me? | क्या आप मुझे कुछ बताना या पूछना चाहते हैं? |  |  |  |  |
| `r1` | You must be told at once why you are arrested. | आपको तुरंत बताया जाना चाहिए कि आपको क्यों गिरफ़्तार किया गया है। |  |  |  |  |
| `r2` | If the offence is bailable, you can get bail. Someone can stand surety for you. | अगर अपराध ज़मानती है, तो आपको ज़मानत मिल सकती है। कोई आपकी ज़मानत ले सकता है। |  |  |  |  |
| `r3` | The police must tell a relative or friend you name where you are held. | पुलिस को आपके बताए किसी रिश्तेदार या दोस्त को बताना होगा कि आपको कहाँ रखा गया है। |  |  |  |  |
| `r4` | You can meet a lawyer of your choice during questioning. | पूछताछ के दौरान आप अपनी पसंद के वकील से मिल सकते हैं। |  |  |  |  |
| `r5` | A government doctor must examine you soon. A woman is examined by a woman doctor. | एक सरकारी डॉक्टर जल्द आपकी जाँच करेगा। महिला की जाँच महिला डॉक्टर करेगी। |  |  |  |  |
| `r6` | You must be taken before a Magistrate within 24 hours, not counting travel time. | आपको 24 घंटे के अंदर मजिस्ट्रेट के सामने ले जाना होगा। यात्रा का समय इसमें नहीं गिना जाता। |  |  |  |  |
| `r7` | No one can force you to give evidence against yourself. | कोई भी आपको अपने ही ख़िलाफ़ गवाही देने के लिए मजबूर नहीं कर सकता। |  |  |  |  |
| `j1` | You will not be put in a lock-up or in handcuffs. | आपको हवालात में नहीं रखा जाएगा और हथकड़ी नहीं लगाई जाएगी। |  |  |  |  |
| `j2` | Your parent or guardian will be told where you are. | आपके माता-पिता या अभिभावक को बताया जाएगा कि आप कहाँ हैं। |  |  |  |  |
| `j3` | A child welfare police officer will look after you. | बाल कल्याण पुलिस अधिकारी आपकी देखभाल करेंगे। |  |  |  |  |
| `j4` | You will be taken before the Juvenile Justice Board within 24 hours, not counting travel time. | आपको 24 घंटे के अंदर किशोर न्याय बोर्ड के सामने ले जाया जाएगा। यात्रा का समय इसमें नहीं गिना जाता। |  |  |  |  |
| `pw1` | You can ask the Executive Magistrate for protection. The officer will give you their details. | आप कार्यकारी मजिस्ट्रेट से सुरक्षा माँग सकते हैं। अधिकारी आपको उनकी जानकारी देंगे। |  |  |  |  |
| `pw2` | The officer will tell you about the nearest group that helps people with disabilities. | अधिकारी आपको दिव्यांगजनों की मदद करने वाली सबसे नज़दीकी संस्था के बारे में बताएँगे। |  |  |  |  |
| `pw3` | You can get a lawyer for free. Call 15100 or the District Legal Services Authority. | आपको मुफ़्त वकील मिल सकता है। 15100 पर फ़ोन करें या ज़िला विधिक सेवा प्राधिकरण से संपर्क करें। |  |  |  |  |
| `pw4` | You have the right to make a complaint. | आपको शिकायत दर्ज करने का अधिकार है। |  |  |  |  |
| `is_this_it` | Is this it? | क्या यही है? |  |  |  |  |
| `draw` | Draw | बनाओ |  |  |  |  |
| `write` | Write | लिखो |  |  |  |  |
| `done` | Done | हो गया |  |  |  |  |
| `clear` | Start again | फिर से |  |  |  |  |
| `yes` | Yes | हाँ |  |  |  |  |
| `no` | No | नहीं |  |  |  |  |
| `dont_know` | I don't know | पता नहीं |  |  |  |  |
| `stop` | Stop | रुको |  |  |  |  |
| `okay` | Okay | ठीक है |  |  |  |  |
| `dont_understand` | I don't understand | मुझे समझ नहीं आया |  |  |  |  |
| `wrong` | That's wrong | यह ग़लत है |  |  |  |  |
| `break` | Break | थोड़ा आराम |  |  |  |  |
| `ready` | I'm ready | मैं तैयार हूँ |  |  |  |  |
| `next` | Next | आगे |  |  |  |  |
| `rights_interpreter` | A qualified interpreter must also explain this to you. | यह बात एक योग्य दुभाषिया भी आपको समझाएगा। |  |  |  |  |
| `watching` | I'm watching. Take your time. | आराम से बताइए, हम देख रहे हैं। |  |  |  |  |
| `sign_slower` | A little slower, please | थोड़ा धीरे-धीरे दिखाइए |  |  |  |  |
| `none_of_these` | None of these | इनमें से कोई नहीं |  |  |  |  |
| `rule_not_there` | I was not there. Only you know what happened. | मैं वहाँ नहीं था। क्या हुआ, यह सिर्फ़ आप जानते हैं। |  |  |  |  |
| `rule_dont_know` | If you don't know, you can say "I don't know". | अगर आपको नहीं पता, तो आप "पता नहीं" कह सकते हैं। |  |  |  |  |
| `rule_wrong` | If I get something wrong, tell me. | अगर मैं कुछ ग़लत समझूँ, तो मुझे बताइए। |  |  |  |  |
| `rule_break` | You can take a break at any time. | आप कभी भी थोड़ा आराम ले सकते हैं। |  |  |  |  |
| `practice_morning` | Show me what you did this morning. | मुझे दिखाइए, आज सुबह आपने क्या किया। |  |  |  |  |

## 6. Sign-off

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
