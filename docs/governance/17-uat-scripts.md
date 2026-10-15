# 17. User Acceptance Test Scripts

Run by department officers on department phones with the release candidate. One tester acts as officer, one as subject. Record Pass / Fail / Notes per step. A script passes when every step passes.

| Script | Steps | Pass when |
| --- | --- | --- |
| UAT-01 First run | Install; open; pick Hindi; read notice; skip permissions | Split view shown in under 2 s; notice says "aid, not interpreter" |
| UAT-02 Lost child, offline | Airplane mode; Flip; ask "Are you safe?", "Are you hurt?", "Do you need water?"; subject taps answers | Clip, caption, cards appear at once; officer sees each answer with time |
| UAT-03 Sensitive question gate | Try to reach "What happened?" | Needs a second deliberate tap; not on first screen |
| UAT-04 Deaf subject | Network on; type "Where is your home?"; subject signs "home", "no" | Avatar plays or shows "sign not available"; officer sees recognised words with confidence |
| UAT-05 Voice | Hold mic, ask in Hindi; edit transcript; send | Nothing reaches subject before send; caption correct after edit |
| UAT-06 Drawing | Subject draws house; officer saves to notes | Drawing in log |
| UAT-07 Failure | Turn data off mid-question; deny camera | Plain message and alternatives; no crash |
| UAT-08 Interruptions | Take a phone call; lock phone; reopen after 6 minutes | Session intact; app asks for unlock |
| UAT-09 Export and clear | End session; export; share passphrase separately; clear | Export opens only with passphrase; session gone from phone |
| UAT-10 Privacy | Try screenshot; open recents | Screenshot blocked; recents thumbnail blank |
| UAT-11 Auto-delete | Leave an unexported session past the auto-delete period; reopen | Session deleted |
| UAT-12 Old phone | Repeat UAT-02 and UAT-04 on the oldest supported phone | Same results; avatar smooth |

**Sign-off:** department nominee signs the completed sheet; failures become defects with severity.
