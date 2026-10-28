# 07. Two-Device Bluetooth Test

Acceptance test for the second screen in the `standard` flavor. The officer phone keeps the encrypted store (`session.db`), the keys and the controls; the stage device shows the subject's stage and stores nothing about a session. The link is RFCOMM to a bonded device, P-256 ECDH with commit then reveal, and AES-256-GCM frames ([02-architecture.md](02-architecture.md#9-second-screen)).

Preconditions: two phones with the same release APK from the [latest release](https://github.com/Yash-Awasthi/sign-setu-pvt/releases/latest), paired once in Android Bluetooth settings.

## 1. Pairing without scan or location permissions

1. On the stage phone, tap **Use as stage screen** on the start screen.
2. On the officer phone, open a session, then More, **Second screen**, and pick the stage phone.

Pass: no scan or location permission is asked on either phone (only Nearby devices, `BLUETOOTH_CONNECT`); the stage phone shows a 6-digit code.

## 2. Code and check number

1. Type a wrong code three times. Pass: the link closes on the third miss.
2. Pair again and type the right code. Pass: both screens show the same 4-digit check number; tapping **Match** on the stage phone goes Live.
3. Cancel on either side during the code. Pass: both sides drop the keys and return to their previous screen.

## 3. Exchange

1. Show a question with picture cards.
2. On the stage phone tap a card, then Yes, then "I don't know", then Stop.

Pass: each item appears on the stage phone without visible delay; each answer appears in the officer conversation, linked to the item it answers; Stop opens the break on the stage and the banner on the officer phone.

## 4. Nothing kept on the stage

1. Run a 15-minute mock interview with 20 questions, cards and a drawing.
2. End the session on the officer phone.
3. On a debug build of the stage phone:

```bash
adb -s <stage> shell run-as in.signsetu ls -la databases files cache
```

Pass: no `session.db`, no drawings, reports or answers; the only stored item is the `stageDevice` settings flag.

## 5. Loss and recovery

1. With a question on the stage, turn Bluetooth off on the stage phone (or walk out of range).
2. Turn it back on within 120 s and tap **Reconnect**.
3. Repeat, and this time tap **Use this phone**.

Pass: a broken link shows at once, a silent one after 30 s without a frame (pings every 10 s); Reconnect within 120 s resumes without a new code; Use this phone moves the item to the officer phone's own stage and no logged event is lost. After 120 s the phones must pair again.
