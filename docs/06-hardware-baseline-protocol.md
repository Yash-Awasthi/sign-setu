# 06. Hardware Baseline Protocol

How to measure the app on physical arm64 phones against the non-functional requirements in [01-requirements.md](01-requirements.md#5-non-functional-requirements). MediaPipe Holistic, CameraX analysis (848 by 480), the character's WebView (three.js) and SQLCipher all run on the phone, so an emulator is not a valid benchmark target.

## 1. Devices

| Tier | Reference SoC | Example models | RAM, storage | OS | Purpose |
| --- | --- | --- | --- | --- | --- |
| 1, floor | Helio G80, Snapdragon 665 | Redmi 9 Prime, Vivo Y20, Realme 5 | 3 GB, 32 GB | Android 10 (API 29) | Performance floor, memory pressure, throttling |
| 2, reference | Snapdragon 720G, Helio G95 | Redmi Note 9 Pro, Galaxy M21 | 4 GB, 64 GB | Android 11 or 12 | Typical officer phone |
| 3, current | Snapdragon 695, Dimensity 6020 | Moto G62, Redmi 12 5G | 6 GB, 128 GB | Android 13 or 14 | Strong biometrics, GPU delegate |

Install the arm64 release APK from the [latest release](https://github.com/Yash-Awasthi/sign-setu-pvt/releases/latest).

## 2. Measurements

### 2.1 Cold start

Requirement: usable officer screen in under 2 s on the oldest device.

```bash
adb shell am force-stop in.signsetu
adb shell am start -W -n in.signsetu/app.signsetu.MainActivity
```

Record `TotalTime` over 5 cold starts (reboot between runs, or `echo 3 > /proc/sys/vm/drop_caches` on a rooted phone). Pass: median under 2,000 ms on tier 1.

### 2.2 Stage frame rate

Requirement: clips play at their source rate without visible stutter; the camera keeps tracking the wrists during a sign window.

```bash
adb shell dumpsys gfxinfo in.signsetu reset
# 30 s of continuous signing on the stage, camera on
adb shell dumpsys gfxinfo in.signsetu
```

Pass: janky frames at most 5%; 90th percentile frame time at most 16.6 ms on tier 2; no lost wrist in a steady sign.

### 2.3 Heat and battery

1. Charge to 80% and unplug.
2. Read the baseline: `adb shell dumpsys battery | grep temperature` and `adb shell dumpsys thermalservice | grep Status`.
3. Run a session with the stage camera on for 15 minutes of signing and taps, at an ambient 30 to 35 °C.
4. Read both again and note the battery drop.

Pass: temperature rise at most 5 °C; battery drop at most 6% in 15 minutes; thermal status never `SEVERE`. Under severe heat the app itself drops analysis to one frame per 250 ms.

### 2.4 Memory

Requirement: under 300 MB during a session.

```bash
adb shell dumpsys meminfo in.signsetu
```

Read `TOTAL PSS` with the stage camera on and the on-device assistant model not loaded. Pass: under 300 MB; leaving the stage for the start screen frees at least 40 MB within 5 s.

## 3. Record

For each device: model, Android version, build version, the four results and pass or fail. A failing tier 1 result blocks release; a failing tier 2 or 3 result is a defect.
