import { describe, expect, it } from "vite-plus/test";
import { Vector3 } from "three";
import { arm, expressions, handTurn, place, solveArm, type Body } from "./retarget";
import { BUILD, FACES, L, R, SHAPES, Timeline, restPose, type HandPose } from "./rig";

// Rest bones of the shipped model (app/src/main/assets/avatar/model.vrm).
const body: Body = {
  right: new Vector3(-0.109, 1.325, 0.001),
  left: new Vector3(0.109, 1.325, 0.001),
  head: new Vector3(0, 1.427, 0.002),
  eyeY: 1.488,
  hipsY: 0.939,
  upper: 0.243,
  fore: 0.23,
};
const hand = (h: Partial<HandPose>): HandPose => ({ ...restPose(BUILD).r, ...h });

describe("place", () => {
  it("puts rig landmarks on the model's", () => {
    expect(place(100, BUILD.anchors.eye[1], body).y).toBeCloseTo(body.eyeY, 3);
    expect(place(100, BUILD.shoulderY, body).y).toBeCloseTo(body.right.y, 3);
    expect(place(100, BUILD.anchors.hip[1], body).y).toBeCloseTo(body.hipsY, 3);
    expect(place(100, 150, body).x).toBeCloseTo(0, 6);
  });

  it("keeps the character's right hand on the viewer's left, in front of the face and chest", () => {
    expect(place(60, 150, body).x).toBeLessThan(0);
    expect(place(100, BUILD.anchors.mouth[1], body).z).toBeGreaterThan(body.head.z + 0.08);
    expect(place(100, BUILD.anchors.chest[1], body).z).toBeGreaterThan(body.right.z + 0.1);
  });

  it("hangs resting hands nearly straight at the sides", () => {
    const rest = restPose(BUILD);
    const wrist = place(rest.r.x, rest.r.y, body);
    expect(wrist.distanceTo(body.right)).toBeGreaterThan((body.upper + body.fore) * 0.9);
    expect(wrist.z - body.right.z).toBeLessThan(0.06);
  });
});

describe("solveArm", () => {
  it("keeps both bones at the model's lengths and bends toward the pole", () => {
    const s = body.right;
    const { elbow, wrist } = solveArm(s, new Vector3(0, 1.3, 0.2), new Vector3(-0.3, 1.1, -0.1), body.upper, body.fore);
    expect(elbow.distanceTo(s)).toBeCloseTo(body.upper, 5);
    expect(elbow.distanceTo(wrist)).toBeCloseTo(body.fore, 5);
    expect(elbow.y).toBeLessThan(s.y);
  });

  it("pulls an out-of-reach wrist back to arm length", () => {
    const { wrist } = solveArm(body.right, new Vector3(-2, 1.3, 0), new Vector3(0, 0, 0), body.upper, body.fore);
    expect(wrist.distanceTo(body.right)).toBeCloseTo(body.upper + body.fore, 2);
  });

  it("keeps the elbow in step with the wrist through a sign", () => {
    const t = new Timeline({
      id: "x",
      keys: [
        { t: 0, R: { at: "mouth" } },
        { t: 0.4, R: { at: "chest_opp" } },
        { t: 0.8, R: { at: "ear" } },
      ],
    });
    let prev: { elbow: Vector3; wrist: Vector3 } | undefined;
    for (let s = 0; s <= t.duration; s += 1 / 30) {
      const a = arm(t.pose(s), R, body, prev?.elbow);
      const now = solveArm(body.right, a.wrist, a.pole, body.upper, body.fore);
      if (prev) expect(now.elbow.distanceTo(prev.elbow), `at ${s.toFixed(2)}`).toBeLessThan(now.wrist.distanceTo(prev.wrist) + 0.08);
      expect(now.elbow.x, `right elbow crosses the body at ${s.toFixed(2)}`).toBeLessThan(0);
      prev = now;
    }
  });
});

describe("handTurn", () => {
  const fingers = (q: ReturnType<typeof handTurn>, side: 1 | -1) => new Vector3(side, 0, 0).applyQuaternion(q);
  const palm = (q: ReturnType<typeof handTurn>) => new Vector3(0, -1, 0).applyQuaternion(q);
  const up = new Vector3(0, 1, 0);

  it("points fingers up with the palm to the viewer at angle 0, roll 1", () => {
    for (const side of [R, L]) {
      const q = handTurn(hand({ angle: 0, roll: 1 }), side, up);
      expect(fingers(q, side).y).toBeGreaterThan(0.9);
      expect(palm(q).z).toBeGreaterThan(0.95);
    }
  });

  it("shows the back of the hand at roll -1 and turns the palm outward at roll 0", () => {
    expect(palm(handTurn(hand({ angle: 0, roll: -1 }), R, up)).z).toBeLessThan(-0.95);
    expect(palm(handTurn(hand({ angle: 0, roll: 0 }), R, up)).x).toBeLessThan(-0.99);
    expect(palm(handTurn(hand({ angle: 0, roll: 0 }), L, up)).x).toBeGreaterThan(0.99);
  });

  it("follows the forearm when the angle is unset", () => {
    const q = handTurn(hand({ angle: NaN, roll: 1 }), R, up);
    expect(fingers(q, R).y).toBeGreaterThan(0.9);
  });
});

describe("expressions", () => {
  it("rests neutral and opens with the face", () => {
    const n = expressions(FACES.neutral, 1);
    expect(Object.values(n).every((v) => v === 0)).toBe(true);
    expect(expressions(FACES.happy, 1).happy).toBeGreaterThan(0.3);
    expect(expressions(FACES.concern, 1).sad).toBeGreaterThan(0.5);
    expect(expressions(FACES.round, 1).ou).toBeGreaterThan(0.7);
    expect(expressions(FACES.neutral, 0).blink).toBe(1);
  });

  it("covers every rig shape name", () => {
    expect(Object.keys(SHAPES)).toContain("RELAX");
  });
});
