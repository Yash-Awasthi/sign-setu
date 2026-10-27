import { describe, expect, it } from "vite-plus/test";
import { clipFace, getCalibratedArmTargets } from "./clip";

/** One clip frame: shoulders, elbows and wrists as process_sign_videos.py writes them (y up, shoulders at y 0). */
function frame(rightWristY: number): number[] {
  const arm = [0.2, 0, 0, -0.2, 0, 0, 0.25, -0.35, -0.1, -0.25, -0.35, -0.1, 0.1, -0.5, -0.2, -0.05, rightWristY, -0.4];
  return [...arm, ...Array(42 * 3).fill(0)];
}

describe("getCalibratedArmTargets", () => {
  it("raises the wrist for a sign at the chin above one at the chest", () => {
    const chest = getCalibratedArmTargets(frame(-0.2)).avatar.rWrist.y;
    const chin = getCalibratedArmTargets(frame(0.12)).avatar.rWrist.y;
    expect(chin - chest).toBeGreaterThan(0.1);
  });

  it("points the forearm up rather than forward for a sign at the chin", () => {
    const { rElbow, rWrist } = getCalibratedArmTargets(frame(0.12)).avatar;
    expect(rWrist.y - rElbow.y).toBeGreaterThan(rWrist.z - rElbow.z);
  });

  it("never lifts the wrist past eye level", () => {
    expect(getCalibratedArmTargets(frame(1.2)).avatar.rWrist.y).toBeLessThanOrEqual(0.29);
  });
});

describe("clipFace", () => {
  it("leaves the rig's face alone when no face was tracked", () => {
    expect(clipFace(Array(52).fill(0))).toBeNull();
    expect(clipFace([])).toBeNull();
    expect(clipFace(undefined)).toBeNull();
  });

  it("reads raised brows, an open jaw and closed eyes", () => {
    const b = Array(52).fill(0);
    b[3] = 0.8; b[4] = 0.8; b[5] = 0.8; b[25] = 0.5; b[9] = 1; b[10] = 1;
    const f = clipFace(b)!;
    expect(f.browRaise).toBeCloseTo(0.8);
    expect(f.browWorry).toBeCloseTo(0);
    expect(f.mouthOpen).toBeCloseTo(0.5);
    expect(f.eyeOpen).toBeCloseTo(0);
  });
});
