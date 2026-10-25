# 07. Bluetooth Two-Device Acceptance Testing Protocol

## 1. Scope & Objective

The Sign Setu reference architecture (`standard` flavor) provides an optional two-device operational mode:
- **Device A (Officer Device):** Holds the encrypted SQLite database (`session.db`), Keystore DEK, audit logs, and question controls.
- **Device B (Stage Display):** Serves as an interactive visual terminal for the deaf or non-verbal subject.

Communication is conducted exclusively over **bonded RFCOMM Bluetooth**, using P-256 ECDH key negotiation and AES-256-GCM authenticated frame encryption. The Stage device **must store zero session data**.

This document establishes the official test cases, acceptance criteria, and physical QA verification procedure.

---

## 2. Test Case Specifications

### 2.1 Bonded RFCOMM Pairing without Scan/Location Permissions
- **Principle:** Android 12+ requires runtime `BLUETOOTH_SCAN` and `ACCESS_FINE_LOCATION` only when discovering unbonded devices. Sign Setu uses pre-bonded devices via Android OS settings (`BluetoothAdapter.bondedDevices`), completely eliminating location tracking or discovery permissions from the release manifest.
- **Preconditions:** Officer phone and Stage phone are paired and bonded in Android OS Bluetooth Settings.
- **Procedure:**
  1. On Stage phone: Open Sign Setu, tap "Use as Stage".
  2. On Officer phone: Tap the Second Screen icon on Start Screen or Session Screen.
  3. Select the bonded Stage device from the list.
- **Acceptance Gate:** Connection initiates immediately; no location or scan permission prompt appears on either device.

---

### 2.2 Commit-Then-Reveal Cryptographic Handshake & PIN Verification
- **Principle:** Neither side should be able to force a predictable confirmation PIN. The Officer device commits to its public key hash first before receiving the Stage's public key.
- **Procedure:**
  1. Trigger pairing from Officer device.
  2. The Stage device shows a 6-digit code (`%06d`); the officer types it on the Officer phone. Three wrong codes close the link.
  3. Both screens then show the same 4-digit check number (`%04d`); the officer compares them and taps Match on the Stage device.
- **Acceptance Gate:**
  - The typed 6-digit code is accepted and the 4-digit check number matches on both screens.
  - If rejected or cancelled, both devices immediately discard temporary keys and return to their prior state.

---

### 2.3 Real-Time Event Transmission & Stage Responsiveness
- **Procedure:**
  1. Establish active link between Officer and Stage devices.
  2. Officer selects a question card (e.g., "WATER", "FOOD", "FAMILY").
  3. Subject selects an answer or taps "YES" / "NO" / "DONT_KNOW" / "STOP".
- **Acceptance Gate:**
  - Card arrives and displays on Stage device within 100 ms.
  - Subject's touch response is transmitted back to Officer pane within 80 ms and logged to Officer SQLite database.
  - No frame tearing or audio desync occurs.

---

### 2.4 Ephemeral Zero-Data Security Audit on Stage Device
- **Principle:** If the Stage device is stolen, dropped, or examined forensically, **zero case data or subject answers must exist on its flash memory**.
- **Audit Procedure:**
  1. Complete a 15-minute mock interview with 20 questions, cards, and drawings on the two-device setup.
  2. End session on Officer phone.
  3. Inspect file storage on Stage device using `adb`:
     ```bash
     adb -s <STAGE_DEVICE_ID> shell ls -la /data/data/in.signsetu/databases/
     adb -s <STAGE_DEVICE_ID> shell ls -la /data/data/in.signsetu/files/
     adb -s <STAGE_DEVICE_ID> shell ls -la /data/data/in.signsetu/cache/
     ```
- **Acceptance Gate:**
  - `/databases/` folder does NOT contain `session.db` on Stage device.
  - No transcript files, drawings, exported PDFs, or subject responses exist in `/files/` or `/cache/`.
  - All stage state exists exclusively in volatile RAM and is completely wiped on link termination or app close.

---

### 2.5 Connection Interruption, Recovery & Graceful Fallback
- **Procedure:**
  1. While a question is active on Stage device, walk Officer phone out of Bluetooth range (> 15 meters) or toggle Bluetooth OFF on Stage device.
  2. Observe state on Officer phone.
  3. Walk back into range or toggle Bluetooth back ON.
- **Acceptance Gate:**
  - A broken connection is detected at once; a silent one after 30 s with no frame (pings every 10 s). The officer phone then offers **Reconnect** and **Use this phone**.
  - **Use this phone** moves what was on the stage to the officer phone's own stage without losing any recorded event.
  - **Reconnect** within 120 s resumes with the resume key and needs no new 6-digit code; after that the devices pair again.
