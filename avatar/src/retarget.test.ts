import { describe, expect, it } from "vite-plus/test";
import { Quaternion, Vector3 } from "three";
import { arm, expressions, handTurn, place, solveArm, splitTwist, type Body } from "./retarget";
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
  fingerRest: new Vector3(0.073, 0.009, 0.002).normalize(),
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
    expect(place(100, BUILD.anchors.chest[1], body).z).toBeGreaterThan(body.right.z + 0.2);
    expect(place(100, BUILD.anchors.belly[1], body).z).toBeGreaterThan(body.right.z + 0.2);
  });

  it("hangs resting hands nearly straight at the sides", () => {
    const rest = restPose(BUILD);
    const wrist = place(rest.r.x, rest.r.y, body);
    expect(wrist.distanceTo(body.right)).toBeGreaterThan((body.upper + body.fore) * 0.9);
    expect(wrist.z - body.right.z).toBeLessThan(0.06);
    expect(Math.abs(wrist.x)).toBeGreaterThan(Math.abs(body.right.x) + 0.06);
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

  it("never folds the elbow tighter than the mesh can show", () => {
    const { elbow, wrist } = solveArm(
      body.right,
      body.right.clone().add(new Vector3(0.02, 0, 0.02)),
      new Vector3(-0.3, 1.1, 0.1),
      body.upper,
      body.fore,
    );
    expect(wrist.distanceTo(body.right)).toBeGreaterThanOrEqual(0.45 * (body.upper + body.fore) - 1e-6);
    expect(elbow.distanceTo(body.right)).toBeCloseTo(body.upper, 5);
  });

  it("keeps the elbow out to the side and in front of the shoulder line when a hand crosses the body", () => {
    const t = new Timeline({ id: "x", keys: [{ t: 0, R: { at: "chest_opp", elbow: "in" } }, { t: 0.5 }] });
    const a = arm(t.pose(0.6), R, body);
    const { elbow } = solveArm(body.right, a.wrist, a.pole, body.upper, body.fore);
    expect(elbow.x).toBeLessThan(body.right.x);
    expect(elbow.z).toBeGreaterThan(body.right.z);
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

describe("splitTwist", () => {
  it("gives the forearm the turn about its length and leaves the wrist only a bend", () => {
    const axis = new Vector3(1, 0, 0);
    const turn = new Quaternion().setFromAxisAngle(axis, 1.2);
    const bend = new Quaternion().setFromAxisAngle(new Vector3(0, 0, 1), 0.4);
    const { twist, swing } = splitTwist(turn.clone().multiply(bend), axis);
    expect(twist.angleTo(turn)).toBeLessThan(1e-6);
    expect(swing.angleTo(bend)).toBeLessThan(1e-6);
    expect(twist.clone().multiply(swing).angleTo(turn.clone().multiply(bend))).toBeLessThan(1e-6);
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
      expect(palm(q).z).toBeGreaterThan(0.9);
    }
  });

  it("shows the back of the hand at roll -1 and turns the palms to each other, thumbs to the body, at roll 0", () => {
    expect(palm(handTurn(hand({ angle: 0, roll: -1 }), R, up)).z).toBeLessThan(-0.9);
    expect(palm(handTurn(hand({ angle: 0, roll: 0 }), R, up)).x).toBeGreaterThan(0.95);
    expect(palm(handTurn(hand({ angle: 0, roll: 0 }), L, up)).x).toBeLessThan(-0.95);
    for (const side of [R, L]) {
      const q = handTurn(hand({ angle: 0, roll: 0 }), side, up);
      const thumb = fingers(q, side).cross(palm(q)).multiplyScalar(-side);
      expect(thumb.z, `thumb of side ${side}`).toBeLessThan(-0.99);
    }
  });

  it("keeps a tilted rest knuckle line pointing where the fingers are asked to point", () => {
    const tilted = new Vector3(0.073, 0.009, 0.002).normalize();
    for (const side of [R, L]) {
      const q = handTurn(hand({ angle: 0, roll: 0 }), side, up, tilted);
      expect(new Vector3(tilted.x * side, tilted.y, tilted.z).applyQuaternion(q).y).toBeGreaterThan(0.999);
    }
  });

  it("follows the forearm when the angle is unset", () => {
    const q = handTurn(hand({ angle: NaN, roll: 1 }), R, up);
    expect(fingers(q, R).y).toBeGreaterThan(0.9);
  });
});

describe("expressions", () => {
  it("rests neutral and opens with the face", () => {
    const n = expressions(FACES.neutral, 1);
    expect(n.relaxed).toBeCloseTo(0.3);
    expect(Object.entries(n).every(([k, v]) => k === "relaxed" || v === 0)).toBe(true);
    expect(expressions(FACES.happy, 1).relaxed).toBeGreaterThan(n.relaxed);
    expect(expressions(FACES.happy, 1).aa).toBeLessThan(0.3);
    expect(expressions(FACES.concern, 1).sad).toBeGreaterThan(0.5);
    expect(expressions(FACES.round, 1).ou).toBeGreaterThan(0.4);
    expect(expressions(FACES.neutral, 0).blink).toBe(1);
  });

  it("covers every rig shape name", () => {
    expect(Object.keys(SHAPES)).toContain("RELAX");
  });
});
