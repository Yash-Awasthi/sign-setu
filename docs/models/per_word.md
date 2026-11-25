# Per-word accuracy

Written 2026-11-26 by `tools/ml/per_word.py` over the shipped readers (`tools/ml/runtime.py`, the same NumPy
forward pass the app's Kotlin mirrors). Clips the readers were not trained on: INCLUDE's official test split
(ISL, 262 words), the ISLRTC dictionary signer (ISL, one signer never trained on), and ASL Citizen (ASL, not
in the ASL reader's PopSign and Kaggle training sets). Re-run the tool after any retrain; do not edit this
file by hand.

A word is **held to confirm** when its top-1 is under 0.60 over at least two clips: at that point two or
more reads in five are wrong, so the word is a poor first suggestion to put in front of the subject. A word
with one clip is shown but not judged, because 0 or 1 says nothing about it.

On ASL, ASL Citizen sometimes signs a gloss in a variant PopSign does not: a low row there can mean a different
citation form rather than a reader that cannot see the sign. It is still a word to hold, because the phone will
meet the same variation. On ISL, the ISLRTC signer is the only new signer on disk, and a low row there is the
reader meeting a signer unlike its training signers.

Holding a word costs nothing in the app's flow: no read is recorded until the subject confirms it, picking
among up to three picture choices (`SignReader.kt`, `SignConfirm` in `sign/SignChoices.kt`), and the item's
own answer cards are offered whether or not the reader named them. The hold is a note for the Deaf reviewer
and the recording list; a held word is improved by re-recording it or by adding signers to the training
cache, not by a code change.

### ISL, INCLUDE test split

The reader's own signers, held out by INCLUDE. Clips 816, words 262, top-1 0.979. Judged words (n >= 2) 222, held 3.

| Word | n | Top-1 | Top-3 | Read as | Verdict |
|---|---|---|---|---|---|
| `clothing` | 4 | 0.50 | 1.00 | dress | hold to confirm |
| `friend` | 4 | 0.50 | 1.00 | child | hold to confirm |
| `red` | 4 | 0.50 | 1.00 | pink | hold to confirm |
| `bag` | 3 | 0.67 | 0.67 | box | ok |
| `bill` | 3 | 0.67 | 1.00 | family | ok |
| `key` | 3 | 0.67 | 0.67 | paint | ok |
| `kitchen` | 3 | 0.67 | 1.00 | bathroom | ok |
| `letter` | 3 | 0.67 | 0.67 | paper | ok |
| `page` | 3 | 0.67 | 0.67 | key | ok |
| `pencil` | 3 | 0.67 | 1.00 | bathroom | ok |
| `colour` | 4 | 0.75 | 1.00 | dead | ok |
| `fish` | 4 | 0.75 | 1.00 | slow | ok |
| `man` | 4 | 0.75 | 1.00 | male | ok |
| `pink` | 4 | 0.75 | 1.00 | red | ok |
| `actor` | 3 | 1.00 | 1.00 |  | ok |
| `adult` | 4 | 1.00 | 1.00 |  | ok |
| `afternoon` | 1 | 1.00 | 1.00 |  | not judged |
| `alive` | 1 | 1.00 | 1.00 |  | not judged |
| `alright` | 4 | 1.00 | 1.00 |  | ok |
| `animal` | 4 | 1.00 | 1.00 |  | ok |
| `artist` | 3 | 1.00 | 1.00 |  | ok |
| `attack` | 3 | 1.00 | 1.00 |  | ok |
| `author` | 3 | 1.00 | 1.00 |  | ok |
| `baby` | 4 | 1.00 | 1.00 |  | ok |
| `bad` | 4 | 1.00 | 1.00 |  | ok |
| `ball` | 3 | 1.00 | 1.00 |  | ok |
| `bank` | 4 | 1.00 | 1.00 |  | ok |
| `bathroom` | 3 | 1.00 | 1.00 |  | ok |
| `beautiful` | 1 | 1.00 | 1.00 |  | not judged |
| `bed` | 3 | 1.00 | 1.00 |  | ok |
| `bedroom` | 3 | 1.00 | 1.00 |  | ok |
| `bicycle` | 4 | 1.00 | 1.00 |  | ok |
| `big large` | 4 | 1.00 | 1.00 |  | ok |
| `bird` | 4 | 1.00 | 1.00 |  | ok |
| `black` | 4 | 1.00 | 1.00 |  | ok |
| `blind` | 1 | 1.00 | 1.00 |  | not judged |
| `blue` | 4 | 1.00 | 1.00 |  | ok |
| `boat` | 4 | 1.00 | 1.00 |  | ok |
| `book` | 3 | 1.00 | 1.00 |  | ok |
| `box` | 3 | 1.00 | 1.00 |  | ok |
| `boy` | 4 | 1.00 | 1.00 |  | ok |
| `brother` | 4 | 1.00 | 1.00 |  | ok |
| `brown` | 4 | 1.00 | 1.00 |  | ok |
| `bus` | 4 | 1.00 | 1.00 |  | ok |
| `camera` | 3 | 1.00 | 1.00 |  | ok |
| `car` | 4 | 1.00 | 1.00 |  | ok |
| `card` | 3 | 1.00 | 1.00 |  | ok |
| `cat` | 4 | 1.00 | 1.00 |  | ok |
| `cell phone` | 3 | 1.00 | 1.00 |  | ok |
| `chair` | 3 | 1.00 | 1.00 |  | ok |
| `cheap` | 2 | 1.00 | 1.00 |  | ok |
| `child` | 4 | 1.00 | 1.00 |  | ok |
| `city` | 4 | 1.00 | 1.00 |  | ok |
| `clean` | 1 | 1.00 | 1.00 |  | not judged |
| `clock` | 3 | 1.00 | 1.00 |  | ok |
| `cold` | 4 | 1.00 | 1.00 |  | ok |
| `computer` | 3 | 1.00 | 1.00 |  | ok |
| `cool` | 4 | 1.00 | 1.00 |  | ok |
| `court` | 4 | 1.00 | 1.00 |  | ok |
| `cow` | 4 | 1.00 | 1.00 |  | ok |
| `crowd` | 4 | 1.00 | 1.00 |  | ok |
| `curved` | 1 | 1.00 | 1.00 |  | not judged |
| `daughter` | 4 | 1.00 | 1.00 |  | ok |
| `dead` | 1 | 1.00 | 1.00 |  | not judged |
| `deaf` | 1 | 1.00 | 1.00 |  | not judged |
| `death` | 3 | 1.00 | 1.00 |  | ok |
| `deep` | 2 | 1.00 | 1.00 |  | ok |
| `dirty` | 1 | 1.00 | 1.00 |  | not judged |
| `doctor` | 3 | 1.00 | 1.00 |  | ok |
| `dog` | 4 | 1.00 | 1.00 |  | ok |
| `door` | 3 | 1.00 | 1.00 |  | ok |
| `dream` | 3 | 1.00 | 1.00 |  | ok |
| `dress` | 4 | 1.00 | 1.00 |  | ok |
| `dry` | 4 | 1.00 | 1.00 |  | ok |
| `election` | 3 | 1.00 | 1.00 |  | ok |
| `energy` | 3 | 1.00 | 1.00 |  | ok |
| `evening` | 1 | 1.00 | 1.00 |  | not judged |
| `exercise` | 3 | 1.00 | 1.00 |  | ok |
| `expensive` | 2 | 1.00 | 1.00 |  | ok |
| `fall` | 3 | 1.00 | 1.00 |  | ok |
| `family` | 3 | 1.00 | 1.00 |  | ok |
| `famous` | 1 | 1.00 | 1.00 |  | not judged |
| `fan` | 3 | 1.00 | 1.00 |  | ok |
| `fast` | 4 | 1.00 | 1.00 |  | ok |
| `father` | 4 | 1.00 | 1.00 |  | ok |
| `female` | 2 | 1.00 | 1.00 |  | ok |
| `flat` | 1 | 1.00 | 1.00 |  | not judged |
| `friday` | 1 | 1.00 | 1.00 |  | not judged |
| `gift` | 3 | 1.00 | 1.00 |  | ok |
| `girl` | 4 | 1.00 | 1.00 |  | ok |
| `god` | 3 | 1.00 | 1.00 |  | ok |
| `good` | 4 | 1.00 | 1.00 |  | ok |
| `good afternoon` | 6 | 1.00 | 1.00 |  | ok |
| `good evening` | 7 | 1.00 | 1.00 |  | ok |
| `good morning` | 4 | 1.00 | 1.00 |  | ok |
| `good night` | 4 | 1.00 | 1.00 |  | ok |
| `grandfather` | 3 | 1.00 | 1.00 |  | ok |
| `grandmother` | 3 | 1.00 | 1.00 |  | ok |
| `green` | 4 | 1.00 | 1.00 |  | ok |
| `grey` | 4 | 1.00 | 1.00 |  | ok |
| `ground` | 4 | 1.00 | 1.00 |  | ok |
| `gun` | 3 | 1.00 | 1.00 |  | ok |
| `happy` | 4 | 1.00 | 1.00 |  | ok |
| `hard` | 2 | 1.00 | 1.00 |  | ok |
| `hat` | 4 | 1.00 | 1.00 |  | ok |
| `he` | 4 | 1.00 | 1.00 |  | ok |
| `healthy` | 4 | 1.00 | 1.00 |  | ok |
| `heavy` | 1 | 1.00 | 1.00 |  | not judged |
| `hello` | 4 | 1.00 | 1.00 |  | ok |
| `high` | 2 | 1.00 | 1.00 |  | ok |
| `horse` | 4 | 1.00 | 1.00 |  | ok |
| `hospital` | 4 | 1.00 | 1.00 |  | ok |
| `hot` | 4 | 1.00 | 1.00 |  | ok |
| `hour` | 1 | 1.00 | 1.00 |  | not judged |
| `house` | 4 | 1.00 | 1.00 |  | ok |
| `how are you` | 4 | 1.00 | 1.00 |  | ok |
| `husband` | 4 | 1.00 | 1.00 |  | ok |
| `i` | 4 | 1.00 | 1.00 |  | ok |
| `india` | 4 | 1.00 | 1.00 |  | ok |
| `it` | 4 | 1.00 | 1.00 |  | ok |
| `job` | 3 | 1.00 | 1.00 |  | ok |
| `king` | 4 | 1.00 | 1.00 |  | ok |
| `lamp` | 3 | 1.00 | 1.00 |  | ok |
| `laptop` | 3 | 1.00 | 1.00 |  | ok |
| `lawyer` | 3 | 1.00 | 1.00 |  | ok |
| `library` | 4 | 1.00 | 1.00 |  | ok |
| `light` | 2 | 1.00 | 1.00 |  | ok |
| `location` | 4 | 1.00 | 1.00 |  | ok |
| `lock` | 3 | 1.00 | 1.00 |  | ok |
| `long` | 4 | 1.00 | 1.00 |  | ok |
| `loose` | 1 | 1.00 | 1.00 |  | not judged |
| `loud` | 4 | 1.00 | 1.00 |  | ok |
| `low` | 1 | 1.00 | 1.00 |  | not judged |
| `male` | 2 | 1.00 | 1.00 |  | ok |
| `manager` | 3 | 1.00 | 1.00 |  | ok |
| `market` | 3 | 1.00 | 1.00 |  | ok |
| `marriage` | 3 | 1.00 | 1.00 |  | ok |
| `mean` | 2 | 1.00 | 1.00 |  | ok |
| `medicine` | 3 | 1.00 | 1.00 |  | ok |
| `minute` | 1 | 1.00 | 1.00 |  | not judged |
| `monday` | 1 | 1.00 | 1.00 |  | not judged |
| `money` | 3 | 1.00 | 1.00 |  | ok |
| `monsoon` | 3 | 1.00 | 1.00 |  | ok |
| `month` | 1 | 1.00 | 1.00 |  | not judged |
| `morning` | 1 | 1.00 | 1.00 |  | not judged |
| `mother` | 4 | 1.00 | 1.00 |  | ok |
| `mouse` | 4 | 1.00 | 1.00 |  | ok |
| `narrow` | 4 | 1.00 | 1.00 |  | ok |
| `neighbour` | 4 | 1.00 | 1.00 |  | ok |
| `new` | 4 | 1.00 | 1.00 |  | ok |
| `newspaper` | 3 | 1.00 | 1.00 |  | ok |
| `nice` | 1 | 1.00 | 1.00 |  | not judged |
| `night` | 1 | 1.00 | 1.00 |  | not judged |
| `office` | 4 | 1.00 | 1.00 |  | ok |
| `old` | 4 | 1.00 | 1.00 |  | ok |
| `orange` | 4 | 1.00 | 1.00 |  | ok |
| `paint` | 3 | 1.00 | 1.00 |  | ok |
| `pant` | 4 | 1.00 | 1.00 |  | ok |
| `paper` | 3 | 1.00 | 1.00 |  | ok |
| `parent` | 4 | 1.00 | 1.00 |  | ok |
| `park` | 4 | 1.00 | 1.00 |  | ok |
| `patient` | 3 | 1.00 | 1.00 |  | ok |
| `peace` | 3 | 1.00 | 1.00 |  | ok |
| `pen` | 3 | 1.00 | 1.00 |  | ok |
| `photograph` | 3 | 1.00 | 1.00 |  | ok |
| `plane` | 4 | 1.00 | 1.00 |  | ok |
| `player` | 4 | 1.00 | 1.00 |  | ok |
| `pleased` | 4 | 1.00 | 1.00 |  | ok |
| `pocket` | 4 | 1.00 | 1.00 |  | ok |
| `police` | 3 | 1.00 | 1.00 |  | ok |
| `poor` | 1 | 1.00 | 1.00 |  | not judged |
| `president` | 4 | 1.00 | 1.00 |  | ok |
| `price` | 3 | 1.00 | 1.00 |  | ok |
| `priest` | 3 | 1.00 | 1.00 |  | ok |
| `queen` | 4 | 1.00 | 1.00 |  | ok |
| `quiet` | 4 | 1.00 | 1.00 |  | ok |
| `race (ethnicity)` | 3 | 1.00 | 1.00 |  | ok |
| `radio` | 3 | 1.00 | 1.00 |  | ok |
| `religion` | 3 | 1.00 | 1.00 |  | ok |
| `reporter` | 3 | 1.00 | 1.00 |  | ok |
| `restaurant` | 4 | 1.00 | 1.00 |  | ok |
| `rich` | 2 | 1.00 | 1.00 |  | ok |
| `ring` | 3 | 1.00 | 1.00 |  | ok |
| `sad` | 1 | 1.00 | 1.00 |  | not judged |
| `saturday` | 1 | 1.00 | 1.00 |  | not judged |
| `school` | 4 | 1.00 | 1.00 |  | ok |
| `science` | 3 | 1.00 | 1.00 |  | ok |
| `screen` | 3 | 1.00 | 1.00 |  | ok |
| `season` | 3 | 1.00 | 1.00 |  | ok |
| `second` | 2 | 1.00 | 1.00 |  | ok |
| `secretary` | 3 | 1.00 | 1.00 |  | ok |
| `shallow` | 1 | 1.00 | 1.00 |  | not judged |
| `she` | 4 | 1.00 | 1.00 |  | ok |
| `shirt` | 4 | 1.00 | 1.00 |  | ok |
| `shoes` | 4 | 1.00 | 1.00 |  | ok |
| `short` | 4 | 1.00 | 1.00 |  | ok |
| `sick` | 4 | 1.00 | 1.00 |  | ok |
| `sign` | 3 | 1.00 | 1.00 |  | ok |
| `sister` | 4 | 1.00 | 1.00 |  | ok |
| `skirt` | 4 | 1.00 | 1.00 |  | ok |
| `slow` | 4 | 1.00 | 1.00 |  | ok |
| `small little` | 4 | 1.00 | 1.00 |  | ok |
| `soap` | 3 | 1.00 | 1.00 |  | ok |
| `soft` | 1 | 1.00 | 1.00 |  | not judged |
| `soldier` | 3 | 1.00 | 1.00 |  | ok |
| `son` | 4 | 1.00 | 1.00 |  | ok |
| `sport` | 3 | 1.00 | 1.00 |  | ok |
| `spring` | 3 | 1.00 | 1.00 |  | ok |
| `store or shop` | 3 | 1.00 | 1.00 |  | ok |
| `street or road` | 4 | 1.00 | 1.00 |  | ok |
| `strong` | 2 | 1.00 | 1.00 |  | ok |
| `student` | 3 | 1.00 | 1.00 |  | ok |
| `suit` | 4 | 1.00 | 1.00 |  | ok |
| `summer` | 3 | 1.00 | 1.00 |  | ok |
| `sunday` | 1 | 1.00 | 1.00 |  | not judged |
| `t-shirt` | 4 | 1.00 | 1.00 |  | ok |
| `table` | 3 | 1.00 | 1.00 |  | ok |
| `tall` | 4 | 1.00 | 1.00 |  | ok |
| `teacher` | 3 | 1.00 | 1.00 |  | ok |
| `team` | 3 | 1.00 | 1.00 |  | ok |
| `technology` | 3 | 1.00 | 1.00 |  | ok |
| `telephone` | 3 | 1.00 | 1.00 |  | ok |
| `television` | 3 | 1.00 | 1.00 |  | ok |
| `temple` | 4 | 1.00 | 1.00 |  | ok |
| `thank you` | 4 | 1.00 | 1.00 |  | ok |
| `they` | 4 | 1.00 | 1.00 |  | ok |
| `thick` | 2 | 1.00 | 1.00 |  | ok |
| `thin` | 1 | 1.00 | 1.00 |  | not judged |
| `thursday` | 1 | 1.00 | 1.00 |  | not judged |
| `tight` | 1 | 1.00 | 1.00 |  | not judged |
| `time` | 1 | 1.00 | 1.00 |  | not judged |
| `today` | 1 | 1.00 | 1.00 |  | not judged |
| `tomorrow` | 1 | 1.00 | 1.00 |  | not judged |
| `tool` | 3 | 1.00 | 1.00 |  | ok |
| `train` | 4 | 1.00 | 1.00 |  | ok |
| `train station` | 4 | 1.00 | 1.00 |  | ok |
| `train ticket` | 4 | 1.00 | 1.00 |  | ok |
| `transportation` | 4 | 1.00 | 1.00 |  | ok |
| `truck` | 4 | 1.00 | 1.00 |  | ok |
| `tuesday` | 1 | 1.00 | 1.00 |  | not judged |
| `ugly` | 2 | 1.00 | 1.00 |  | ok |
| `university` | 4 | 1.00 | 1.00 |  | ok |
| `waiter` | 3 | 1.00 | 1.00 |  | ok |
| `war` | 3 | 1.00 | 1.00 |  | ok |
| `warm` | 4 | 1.00 | 1.00 |  | ok |
| `we` | 4 | 1.00 | 1.00 |  | ok |
| `weak` | 2 | 1.00 | 1.00 |  | ok |
| `wednesday` | 1 | 1.00 | 1.00 |  | not judged |
| `week` | 1 | 1.00 | 1.00 |  | not judged |
| `wet` | 4 | 1.00 | 1.00 |  | ok |
| `white` | 4 | 1.00 | 1.00 |  | ok |
| `wide` | 4 | 1.00 | 1.00 |  | ok |
| `wife` | 4 | 1.00 | 1.00 |  | ok |
| `window` | 3 | 1.00 | 1.00 |  | ok |
| `winter` | 3 | 1.00 | 1.00 |  | ok |
| `woman` | 4 | 1.00 | 1.00 |  | ok |
| `year` | 1 | 1.00 | 1.00 |  | not judged |
| `yellow` | 4 | 1.00 | 1.00 |  | ok |
| `yesterday` | 1 | 1.00 | 1.00 |  | not judged |
| `you` | 4 | 1.00 | 1.00 |  | ok |
| `you (plural)` | 4 | 1.00 | 1.00 |  | ok |
| `young` | 4 | 1.00 | 1.00 |  | ok |

### ISL, ISLRTC dictionary signer

One signer never in training, the hardest real set on disk. Clips 269, words 200, top-1 0.546. Judged words (n >= 2) 52, held 34.

| Word | n | Top-1 | Top-3 | Read as | Verdict |
|---|---|---|---|---|---|
| `actor` | 2 | 0.00 | 0.00 | season | hold to confirm |
| `artist` | 1 | 0.00 | 0.00 | house | not judged |
| `baby` | 1 | 0.00 | 0.00 | plane | not judged |
| `bag` | 1 | 0.00 | 0.00 | kitchen | not judged |
| `bed` | 1 | 0.00 | 0.00 | spring | not judged |
| `blue` | 1 | 0.00 | 1.00 | morning | not judged |
| `boat` | 1 | 0.00 | 0.00 | volcano | not judged |
| `box` | 1 | 0.00 | 0.00 | wide | not judged |
| `bus` | 2 | 0.00 | 1.00 | truck | hold to confirm |
| `card` | 1 | 0.00 | 0.00 | volcano | not judged |
| `colour` | 2 | 0.00 | 0.00 | red | hold to confirm |
| `cool` | 1 | 0.00 | 0.00 | election | not judged |
| `dead` | 1 | 0.00 | 0.00 | race (ethnicity) | not judged |
| `doctor` | 1 | 0.00 | 0.00 | train ticket | not judged |
| `door` | 1 | 0.00 | 0.00 | loud | not judged |
| `dream` | 1 | 0.00 | 1.00 | tall | not judged |
| `dress` | 1 | 0.00 | 1.00 | shirt | not judged |
| `dry` | 1 | 0.00 | 0.00 | tight | not judged |
| `energy` | 1 | 0.00 | 0.00 | strong | not judged |
| `evening` | 1 | 0.00 | 1.00 | night | not judged |
| `exercise` | 1 | 0.00 | 0.00 | energy | not judged |
| `fan` | 1 | 0.00 | 1.00 | tall | not judged |
| `friday` | 1 | 0.00 | 0.00 | family | not judged |
| `friend` | 1 | 0.00 | 0.00 | adult | not judged |
| `god` | 1 | 0.00 | 1.00 | letter | not judged |
| `good evening` | 1 | 0.00 | 0.00 | night | not judged |
| `good morning` | 1 | 0.00 | 1.00 | morning | not judged |
| `good night` | 1 | 0.00 | 1.00 | night | not judged |
| `grandfather` | 1 | 0.00 | 1.00 | grandmother | not judged |
| `green` | 1 | 0.00 | 0.00 | alive | not judged |
| `gun` | 2 | 0.00 | 0.00 | alive | hold to confirm |
| `happy` | 1 | 0.00 | 0.00 | thank you | not judged |
| `he` | 1 | 0.00 | 0.00 | cold | not judged |
| `heavy` | 1 | 0.00 | 1.00 | light | not judged |
| `hot` | 1 | 0.00 | 0.00 | train station | not judged |
| `it` | 1 | 0.00 | 0.00 | key | not judged |
| `job` | 2 | 0.00 | 0.00 | chair | hold to confirm |
| `king` | 1 | 0.00 | 1.00 | queen | not judged |
| `letter` | 1 | 0.00 | 0.00 | year | not judged |
| `market` | 1 | 0.00 | 0.00 | maybe | not judged |
| `medicine` | 1 | 0.00 | 0.00 | white | not judged |
| `monday` | 1 | 0.00 | 0.00 | month | not judged |
| `mother` | 1 | 0.00 | 0.00 | father | not judged |
| `mouse` | 1 | 0.00 | 0.00 | computer | not judged |
| `nice` | 2 | 0.00 | 0.50 | pen | hold to confirm |
| `orange` | 1 | 0.00 | 0.00 | sunday | not judged |
| `park` | 1 | 0.00 | 0.00 | elephant | not judged |
| `patient` | 1 | 0.00 | 0.00 | screen | not judged |
| `pencil` | 7 | 0.00 | 0.00 | black | hold to confirm |
| `pink` | 2 | 0.00 | 0.00 | young | hold to confirm |
| `president` | 1 | 0.00 | 0.00 | manager | not judged |
| `price` | 1 | 0.00 | 0.00 | money | not judged |
| `reporter` | 1 | 0.00 | 1.00 | he | not judged |
| `ring` | 1 | 0.00 | 0.00 | president | not judged |
| `school` | 1 | 0.00 | 1.00 | thank you | not judged |
| `screen` | 1 | 0.00 | 0.00 | window | not judged |
| `shallow` | 3 | 0.00 | 0.00 | elephant | hold to confirm |
| `shoes` | 1 | 0.00 | 1.00 | flat | not judged |
| `short` | 1 | 0.00 | 0.00 | they | not judged |
| `sign` | 1 | 0.00 | 0.00 | race (ethnicity) | not judged |
| `slow` | 1 | 0.00 | 1.00 | fish | not judged |
| `soldier` | 1 | 0.00 | 0.00 | reporter | not judged |
| `son` | 1 | 0.00 | 1.00 | daughter | not judged |
| `spring` | 1 | 0.00 | 0.00 | cow | not judged |
| `student` | 1 | 0.00 | 0.00 | cook | not judged |
| `suit` | 1 | 0.00 | 0.00 | boat | not judged |
| `summer` | 1 | 0.00 | 1.00 | job | not judged |
| `they` | 1 | 0.00 | 0.00 | morning | not judged |
| `thursday` | 1 | 0.00 | 1.00 | friend | not judged |
| `ugly` | 1 | 0.00 | 0.00 | volcano | not judged |
| `university` | 1 | 0.00 | 0.00 | rich | not judged |
| `warm` | 2 | 0.00 | 0.50 | book | hold to confirm |
| `wide` | 1 | 0.00 | 0.00 | student | not judged |
| `wife` | 1 | 0.00 | 1.00 | marriage | not judged |
| `winter` | 1 | 0.00 | 1.00 | cold | not judged |
| `yellow` | 1 | 0.00 | 0.00 | white | not judged |
| `adult` | 3 | 0.33 | 0.67 | switch | hold to confirm |
| `paint` | 3 | 0.33 | 0.33 | team | hold to confirm |
| `pen` | 3 | 0.33 | 0.67 | husband | hold to confirm |
| `bad` | 2 | 0.50 | 0.50 | good night | hold to confirm |
| `bank` | 2 | 0.50 | 0.50 | monday | hold to confirm |
| `book` | 2 | 0.50 | 0.50 | budget | hold to confirm |
| `child` | 2 | 0.50 | 0.50 | karnataka | hold to confirm |
| `city` | 2 | 0.50 | 0.50 | election | hold to confirm |
| `cow` | 2 | 0.50 | 0.50 | exercise | hold to confirm |
| `expensive` | 2 | 0.50 | 0.50 | cool | hold to confirm |
| `fall` | 2 | 0.50 | 1.00 | heavy | hold to confirm |
| `family` | 2 | 0.50 | 1.00 | book | hold to confirm |
| `fast` | 2 | 0.50 | 1.00 | friend | hold to confirm |
| `fish` | 2 | 0.50 | 1.00 | i | hold to confirm |
| `good` | 2 | 0.50 | 0.50 | pen | hold to confirm |
| `location` | 2 | 0.50 | 1.00 | curved | hold to confirm |
| `monsoon` | 2 | 0.50 | 0.50 | hug | hold to confirm |
| `month` | 2 | 0.50 | 1.00 | deaf | hold to confirm |
| `restaurant` | 2 | 0.50 | 0.50 | job | hold to confirm |
| `secretary` | 2 | 0.50 | 0.50 | brother | hold to confirm |
| `sick` | 2 | 0.50 | 1.00 | yellow | hold to confirm |
| `team` | 2 | 0.50 | 0.50 | we | hold to confirm |
| `thank you` | 2 | 0.50 | 1.00 | hello | hold to confirm |
| `war` | 2 | 0.50 | 0.50 | vegetables | hold to confirm |
| `mean` | 3 | 0.67 | 0.67 | pigeon | ok |
| `truck` | 3 | 0.67 | 0.67 | shallow | ok |
| `weak` | 3 | 0.67 | 1.00 | artist | ok |
| `afternoon` | 1 | 1.00 | 1.00 |  | not judged |
| `alive` | 1 | 1.00 | 1.00 |  | not judged |
| `attack` | 1 | 1.00 | 1.00 |  | not judged |
| `ball` | 1 | 1.00 | 1.00 |  | not judged |
| `bathroom` | 1 | 1.00 | 1.00 |  | not judged |
| `beautiful` | 2 | 1.00 | 1.00 |  | ok |
| `bedroom` | 1 | 1.00 | 1.00 |  | not judged |
| `bill` | 2 | 1.00 | 1.00 |  | ok |
| `black` | 1 | 1.00 | 1.00 |  | not judged |
| `boy` | 1 | 1.00 | 1.00 |  | not judged |
| `brown` | 1 | 1.00 | 1.00 |  | not judged |
| `camera` | 1 | 1.00 | 1.00 |  | not judged |
| `car` | 1 | 1.00 | 1.00 |  | not judged |
| `cat` | 2 | 1.00 | 1.00 |  | ok |
| `chair` | 1 | 1.00 | 1.00 |  | not judged |
| `clock` | 2 | 1.00 | 1.00 |  | ok |
| `cold` | 1 | 1.00 | 1.00 |  | not judged |
| `computer` | 1 | 1.00 | 1.00 |  | not judged |
| `court` | 1 | 1.00 | 1.00 |  | not judged |
| `daughter` | 1 | 1.00 | 1.00 |  | not judged |
| `deaf` | 1 | 1.00 | 1.00 |  | not judged |
| `dog` | 2 | 1.00 | 1.00 |  | ok |
| `election` | 1 | 1.00 | 1.00 |  | not judged |
| `famous` | 1 | 1.00 | 1.00 |  | not judged |
| `father` | 1 | 1.00 | 1.00 |  | not judged |
| `female` | 1 | 1.00 | 1.00 |  | not judged |
| `gift` | 1 | 1.00 | 1.00 |  | not judged |
| `girl` | 1 | 1.00 | 1.00 |  | not judged |
| `good afternoon` | 1 | 1.00 | 1.00 |  | not judged |
| `grandmother` | 1 | 1.00 | 1.00 |  | not judged |
| `grey` | 2 | 1.00 | 1.00 |  | ok |
| `hard` | 2 | 1.00 | 1.00 |  | ok |
| `hat` | 1 | 1.00 | 1.00 |  | not judged |
| `hello` | 1 | 1.00 | 1.00 |  | not judged |
| `horse` | 1 | 1.00 | 1.00 |  | not judged |
| `hospital` | 1 | 1.00 | 1.00 |  | not judged |
| `hour` | 1 | 1.00 | 1.00 |  | not judged |
| `husband` | 1 | 1.00 | 1.00 |  | not judged |
| `india` | 5 | 1.00 | 1.00 |  | ok |
| `key` | 1 | 1.00 | 1.00 |  | not judged |
| `kitchen` | 1 | 1.00 | 1.00 |  | not judged |
| `lamp` | 1 | 1.00 | 1.00 |  | not judged |
| `laptop` | 2 | 1.00 | 1.00 |  | ok |
| `lawyer` | 2 | 1.00 | 1.00 |  | ok |
| `library` | 1 | 1.00 | 1.00 |  | not judged |
| `lock` | 1 | 1.00 | 1.00 |  | not judged |
| `long` | 1 | 1.00 | 1.00 |  | not judged |
| `loud` | 1 | 1.00 | 1.00 |  | not judged |
| `low` | 1 | 1.00 | 1.00 |  | not judged |
| `man` | 1 | 1.00 | 1.00 |  | not judged |
| `manager` | 1 | 1.00 | 1.00 |  | not judged |
| `morning` | 1 | 1.00 | 1.00 |  | not judged |
| `narrow` | 1 | 1.00 | 1.00 |  | not judged |
| `new` | 1 | 1.00 | 1.00 |  | not judged |
| `night` | 1 | 1.00 | 1.00 |  | not judged |
| `office` | 1 | 1.00 | 1.00 |  | not judged |
| `paper` | 2 | 1.00 | 1.00 |  | ok |
| `police` | 1 | 1.00 | 1.00 |  | not judged |
| `poor` | 1 | 1.00 | 1.00 |  | not judged |
| `queen` | 1 | 1.00 | 1.00 |  | not judged |
| `quiet` | 1 | 1.00 | 1.00 |  | not judged |
| `radio` | 1 | 1.00 | 1.00 |  | not judged |
| `red` | 1 | 1.00 | 1.00 |  | not judged |
| `religion` | 2 | 1.00 | 1.00 |  | ok |
| `rich` | 1 | 1.00 | 1.00 |  | not judged |
| `sad` | 1 | 1.00 | 1.00 |  | not judged |
| `saturday` | 1 | 1.00 | 1.00 |  | not judged |
| `science` | 1 | 1.00 | 1.00 |  | not judged |
| `season` | 1 | 1.00 | 1.00 |  | not judged |
| `she` | 1 | 1.00 | 1.00 |  | not judged |
| `shirt` | 1 | 1.00 | 1.00 |  | not judged |
| `sister` | 1 | 1.00 | 1.00 |  | not judged |
| `skirt` | 1 | 1.00 | 1.00 |  | not judged |
| `soap` | 1 | 1.00 | 1.00 |  | not judged |
| `sunday` | 1 | 1.00 | 1.00 |  | not judged |
| `tall` | 4 | 1.00 | 1.00 |  | ok |
| `teacher` | 1 | 1.00 | 1.00 |  | not judged |
| `technology` | 1 | 1.00 | 1.00 |  | not judged |
| `television` | 1 | 1.00 | 1.00 |  | not judged |
| `temple` | 1 | 1.00 | 1.00 |  | not judged |
| `thin` | 1 | 1.00 | 1.00 |  | not judged |
| `time` | 1 | 1.00 | 1.00 |  | not judged |
| `today` | 1 | 1.00 | 1.00 |  | not judged |
| `tomorrow` | 1 | 1.00 | 1.00 |  | not judged |
| `tool` | 1 | 1.00 | 1.00 |  | not judged |
| `train` | 2 | 1.00 | 1.00 |  | ok |
| `tuesday` | 2 | 1.00 | 1.00 |  | ok |
| `waiter` | 1 | 1.00 | 1.00 |  | not judged |
| `we` | 1 | 1.00 | 1.00 |  | not judged |
| `wednesday` | 1 | 1.00 | 1.00 |  | not judged |
| `wet` | 1 | 1.00 | 1.00 |  | not judged |
| `white` | 1 | 1.00 | 1.00 |  | not judged |
| `window` | 1 | 1.00 | 1.00 |  | not judged |
| `year` | 1 | 1.00 | 1.00 |  | not judged |
| `yesterday` | 1 | 1.00 | 1.00 |  | not judged |
| `you` | 1 | 1.00 | 1.00 |  | not judged |
| `young` | 1 | 1.00 | 1.00 |  | not judged |

### ASL, ASL Citizen

Signers of a different ASL corpus; 171 of the app's 250 words appear. Clips 2504, words 171, top-1 0.702. Judged words (n >= 2) 171, held 52.

| Word | n | Top-1 | Top-3 | Read as | Verdict |
|---|---|---|---|---|---|
| `finger` | 14 | 0.00 | 0.00 | cry | hold to confirm |
| `pencil` | 14 | 0.00 | 0.14 | pen | hold to confirm |
| `time` | 15 | 0.00 | 0.20 | arm | hold to confirm |
| `dance` | 15 | 0.07 | 0.27 | fall | hold to confirm |
| `pen` | 15 | 0.07 | 0.07 | fast | hold to confirm |
| `sleep` | 13 | 0.08 | 0.92 | sleepy | hold to confirm |
| `on` | 16 | 0.12 | 0.50 | clean | hold to confirm |
| `touch` | 16 | 0.12 | 0.38 | find | hold to confirm |
| `yourself` | 16 | 0.12 | 0.12 | every | hold to confirm |
| `read` | 14 | 0.14 | 0.29 | chair | hold to confirm |
| `sick` | 14 | 0.14 | 0.29 | empty | hold to confirm |
| `thankyou` | 16 | 0.19 | 0.75 | napkin | hold to confirm |
| `tongue` | 16 | 0.19 | 0.75 | goose | hold to confirm |
| `jump` | 16 | 0.25 | 0.56 | chair | hold to confirm |
| `nose` | 16 | 0.25 | 0.62 | lips | hold to confirm |
| `pretend` | 16 | 0.25 | 1.00 | mouse | hold to confirm |
| `after` | 15 | 0.27 | 0.40 | bedroom | hold to confirm |
| `cowboy` | 15 | 0.27 | 0.27 | jeans | hold to confirm |
| `red` | 14 | 0.36 | 0.43 | chin | hold to confirm |
| `who` | 14 | 0.36 | 0.71 | moon | hold to confirm |
| `hate` | 16 | 0.38 | 0.62 | grandma | hold to confirm |
| `yucky` | 16 | 0.38 | 0.88 | hate | hold to confirm |
| `awake` | 13 | 0.38 | 1.00 | wake | hold to confirm |
| `bug` | 13 | 0.38 | 0.92 | listen | hold to confirm |
| `clean` | 15 | 0.40 | 0.67 | puppy | hold to confirm |
| `hen` | 15 | 0.40 | 0.80 | pen | hold to confirm |
| `sun` | 12 | 0.42 | 1.00 | shower | hold to confirm |
| `tree` | 12 | 0.42 | 0.67 | morning | hold to confirm |
| `fish` | 14 | 0.43 | 0.57 | mitten | hold to confirm |
| `lips` | 14 | 0.43 | 0.79 | eye | hold to confirm |
| `boat` | 16 | 0.44 | 0.81 | arm | hold to confirm |
| `book` | 15 | 0.47 | 0.73 | fine | hold to confirm |
| `hide` | 15 | 0.47 | 0.73 | clean | hold to confirm |
| `chin` | 14 | 0.50 | 0.86 | tongue | hold to confirm |
| `scissors` | 14 | 0.50 | 1.00 | cut | hold to confirm |
| `mouth` | 17 | 0.53 | 0.76 | eye | hold to confirm |
| `penny` | 17 | 0.53 | 0.88 | think | hold to confirm |
| `all` | 15 | 0.53 | 0.67 | puppy | hold to confirm |
| `giraffe` | 15 | 0.53 | 0.60 | napkin | hold to confirm |
| `go` | 15 | 0.53 | 0.80 | closet | hold to confirm |
| `see` | 15 | 0.53 | 0.87 | look | hold to confirm |
| `will` | 15 | 0.53 | 0.80 | bedroom | hold to confirm |
| `yes` | 15 | 0.53 | 0.80 | carrot | hold to confirm |
| `toothbrush` | 16 | 0.56 | 0.88 | cheek | hold to confirm |
| `because` | 14 | 0.57 | 0.86 | why | hold to confirm |
| `have` | 14 | 0.57 | 0.93 | before | hold to confirm |
| `mouse` | 14 | 0.57 | 0.79 | pretend | hold to confirm |
| `not` | 14 | 0.57 | 0.93 | nuts | hold to confirm |
| `refrigerator` | 14 | 0.57 | 0.71 | car | hold to confirm |
| `that` | 14 | 0.57 | 0.86 | stay | hold to confirm |
| `thirsty` | 14 | 0.57 | 1.00 | chin | hold to confirm |
| `where` | 14 | 0.57 | 0.71 | dog | hold to confirm |
| `home` | 14 | 0.64 | 0.86 | kiss | ok |
| `make` | 14 | 0.64 | 0.86 | refrigerator | ok |
| `morning` | 14 | 0.64 | 0.79 | there | ok |
| `stay` | 14 | 0.64 | 0.86 | that | ok |
| `there` | 14 | 0.64 | 0.86 | hesheit | ok |
| `happy` | 15 | 0.67 | 0.87 | please | ok |
| `like` | 15 | 0.67 | 0.87 | white | ok |
| `listen` | 12 | 0.67 | 0.92 | hear | ok |
| `child` | 16 | 0.69 | 0.94 | bye | ok |
| `fast` | 16 | 0.69 | 0.88 | because | ok |
| `girl` | 16 | 0.69 | 0.81 | every | ok |
| `bad` | 13 | 0.69 | 0.77 | cereal | ok |
| `cheek` | 17 | 0.71 | 0.82 | every | ok |
| `animal` | 14 | 0.71 | 0.79 | bath | ok |
| `chair` | 14 | 0.71 | 0.86 | ride | ok |
| `chocolate` | 14 | 0.71 | 0.93 | into | ok |
| `drawer` | 14 | 0.71 | 1.00 | dog | ok |
| `frenchfries` | 14 | 0.71 | 0.93 | feet | ok |
| `green` | 14 | 0.71 | 0.86 | store | ok |
| `blue` | 15 | 0.73 | 0.87 | bed | ok |
| `car` | 15 | 0.73 | 1.00 | drawer | ok |
| `drop` | 15 | 0.73 | 0.93 | can | ok |
| `dry` | 15 | 0.73 | 1.00 | dryer | ok |
| `give` | 15 | 0.73 | 0.87 | mitten | ok |
| `hungry` | 15 | 0.73 | 0.87 | please | ok |
| `quiet` | 15 | 0.73 | 0.87 | brown | ok |
| `brother` | 16 | 0.75 | 0.88 | hide | ok |
| `ear` | 16 | 0.75 | 0.94 | snack | ok |
| `face` | 16 | 0.75 | 0.81 | mouth | ok |
| `later` | 16 | 0.75 | 0.94 | tomorrow | ok |
| `yellow` | 16 | 0.75 | 1.00 | backyard | ok |
| `wet` | 13 | 0.77 | 0.85 | sleepy | ok |
| `elephant` | 14 | 0.79 | 0.79 | into | ok |
| `horse` | 14 | 0.79 | 1.00 | donkey | ok |
| `moon` | 14 | 0.79 | 0.86 | listen | ok |
| `pretty` | 14 | 0.79 | 0.93 | sleepy | ok |
| `rain` | 14 | 0.79 | 0.93 | stairs | ok |
| `sad` | 14 | 0.79 | 0.93 | sleepy | ok |
| `wolf` | 14 | 0.79 | 1.00 | nap | ok |
| `bye` | 15 | 0.80 | 0.87 | donkey | ok |
| `farm` | 15 | 0.80 | 0.87 | grandpa | ok |
| `open` | 15 | 0.80 | 1.00 | person | ok |
| `person` | 15 | 0.80 | 1.00 | beside | ok |
| `room` | 15 | 0.80 | 1.00 | bedroom | ok |
| `dirty` | 16 | 0.81 | 0.94 | pig | ok |
| `think` | 16 | 0.81 | 0.88 | hungry | ok |
| `vacuum` | 16 | 0.81 | 1.00 | hide | ok |
| `zipper` | 16 | 0.81 | 0.81 | pig | ok |
| `high` | 12 | 0.83 | 1.00 | shower | ok |
| `taste` | 13 | 0.85 | 0.92 | grass | ok |
| `bath` | 14 | 0.86 | 0.93 | car | ok |
| `first` | 14 | 0.86 | 1.00 | green | ok |
| `grass` | 14 | 0.86 | 0.86 | mitten | ok |
| `icecream` | 14 | 0.86 | 1.00 | aunt | ok |
| `mad` | 14 | 0.86 | 0.93 | radio | ok |
| `stairs` | 14 | 0.86 | 0.93 | any | ok |
| `tiger` | 14 | 0.86 | 0.93 | mad | ok |
| `water` | 14 | 0.86 | 0.93 | boat | ok |
| `black` | 15 | 0.87 | 0.93 | toothbrush | ok |
| `cereal` | 15 | 0.87 | 0.87 | underwear | ok |
| `clown` | 15 | 0.87 | 0.93 | mad | ok |
| `doll` | 15 | 0.87 | 0.93 | pretend | ok |
| `flower` | 15 | 0.87 | 1.00 | napkin | ok |
| `now` | 15 | 0.87 | 1.00 | callonphone | ok |
| `outside` | 15 | 0.87 | 0.93 | shower | ok |
| `pig` | 15 | 0.87 | 0.93 | table | ok |
| `table` | 15 | 0.87 | 0.87 | bedroom | ok |
| `cut` | 16 | 0.88 | 0.94 | pretty | ok |
| `sticky` | 16 | 0.88 | 0.94 | kitty | ok |
| `tomorrow` | 16 | 0.88 | 1.00 | not | ok |
| `apple` | 17 | 0.88 | 1.00 | ear | ok |
| `airplane` | 12 | 0.92 | 1.00 | yesterday | ok |
| `brown` | 13 | 0.92 | 1.00 | open | ok |
| `cow` | 13 | 0.92 | 1.00 | yesterday | ok |
| `many` | 13 | 0.92 | 0.92 | morning | ok |
| `old` | 13 | 0.92 | 0.92 | dirty | ok |
| `wait` | 13 | 0.92 | 1.00 | mitten | ok |
| `bedroom` | 14 | 0.93 | 0.93 | kitty | ok |
| `bird` | 14 | 0.93 | 1.00 | goose | ok |
| `boy` | 14 | 0.93 | 1.00 | listen | ok |
| `donkey` | 14 | 0.93 | 1.00 | listen | ok |
| `dryer` | 14 | 0.93 | 1.00 | weus | ok |
| `gum` | 14 | 0.93 | 1.00 | napkin | ok |
| `hello` | 14 | 0.93 | 1.00 | sun | ok |
| `orange` | 14 | 0.93 | 1.00 | old | ok |
| `please` | 14 | 0.93 | 1.00 | minemy | ok |
| `radio` | 14 | 0.93 | 0.93 | bedroom | ok |
| `up` | 14 | 0.93 | 1.00 | brother | ok |
| `white` | 14 | 0.93 | 1.00 | zebra | ok |
| `any` | 15 | 0.93 | 1.00 | another | ok |
| `aunt` | 15 | 0.93 | 0.93 | pizza | ok |
| `blow` | 15 | 0.93 | 0.93 | snack | ok |
| `napkin` | 15 | 0.93 | 1.00 | quiet | ok |
| `gift` | 16 | 0.94 | 1.00 | doll | ok |
| `no` | 16 | 0.94 | 1.00 | goose | ok |
| `close` | 17 | 0.94 | 1.00 | quiet | ok |
| `zebra` | 17 | 0.94 | 0.94 | pool | ok |
| `TV` | 15 | 1.00 | 1.00 |  | ok |
| `backyard` | 14 | 1.00 | 1.00 |  | ok |
| `balloon` | 14 | 1.00 | 1.00 |  | ok |
| `before` | 14 | 1.00 | 1.00 |  | ok |
| `better` | 14 | 1.00 | 1.00 |  | ok |
| `down` | 15 | 1.00 | 1.00 |  | ok |
| `find` | 14 | 1.00 | 1.00 |  | ok |
| `finish` | 15 | 1.00 | 1.00 |  | ok |
| `for` | 15 | 1.00 | 1.00 |  | ok |
| `frog` | 15 | 1.00 | 1.00 |  | ok |
| `head` | 16 | 1.00 | 1.00 |  | ok |
| `hot` | 16 | 1.00 | 1.00 |  | ok |
| `if` | 15 | 1.00 | 1.00 |  | ok |
| `lion` | 14 | 1.00 | 1.00 |  | ok |
| `loud` | 13 | 1.00 | 1.00 |  | ok |
| `man` | 15 | 1.00 | 1.00 |  | ok |
| `owl` | 12 | 1.00 | 1.00 |  | ok |
| `shower` | 13 | 1.00 | 1.00 |  | ok |
| `stuck` | 15 | 1.00 | 1.00 |  | ok |
| `uncle` | 17 | 1.00 | 1.00 |  | ok |
| `why` | 14 | 1.00 | 1.00 |  | ok |
| `yesterday` | 16 | 1.00 | 1.00 |  | ok |

## Coverage

INCLUDE's test split has no clips for the 52 words that came from the Kaggle set, and ASL Citizen has
no clips of 79 of the app's 250 ASL words; a word absent from a table is unmeasured, not perfect. The
ISLRTC signer's clips cover 200 words, 148 of them with a single clip, so its table judges fewer words than
it lists.

