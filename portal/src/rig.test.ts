import { describe, expect, it } from "vite-plus/test";
import fixtures from "./rig-fixtures.json";
import { gestures } from "./content";
import {
  AdultBuild,
  ChildBuild,
  L,
  R,
  Timeline,
  curls,
  point,
  shoulder,
  solveElbow,
  type Build,
  type HandPose,
  type Pose,
  type Side,
} from "./rig";

function hand(h: HandPose, side: Side, b: Build): (number | null)[] {
  const [sx, sy] = shoulder(b, side);
  const [ex, ey] = solveElbow(sx, sy, h.x, h.y, b.upperArm, b.foreArm, side, h.elbowOut);
  const [tx, ty] = point(h, "index", side, b.handScale);
  return [
    h.x,
    h.y,
    h.roll,
    Number.isNaN(h.angle) ? null : h.angle,
    h.elbowOut,
    ...curls(h.shape),
    h.shape.thumbAcross,
    h.shape.spread,
    ex,
    ey,
    tx,
    ty,
  ];
}

const rest = (p: Pose) => [...Object.values(p.face), p.tilt, p.nod, p.turn, p.lean, p.shrug, p.hunch, p.shiver];

describe("TypeScript rig", () => {
  const byId = new Map(gestures.map((g) => [g.id, g]));

  it("covers every gesture in the fixtures", () => {
    expect(new Set(fixtures.samples.map((s) => s.gesture))).toEqual(new Set(byId.keys()));
  });

  it.each(fixtures.samples)("$gesture on $character at $seconds s matches Rig.kt", (s) => {
    const b = s.character === "child" ? ChildBuild : AdultBuild;
    const p = new Timeline(byId.get(s.gesture)!, b).pose(s.seconds);
    const got = [...hand(p.r, R, b), ...hand(p.l, L, b), ...rest(p)];
    expect(got.length).toBe(s.pose.length);
    // Rig.kt works in 32-bit floats and the fixtures keep four decimals.
    got.forEach((v, i) => {
      const want = s.pose[i];
      if (want === null || v === null) expect(v).toBe(want);
      else expect(Math.abs(v - want), `value ${i}`).toBeLessThan(0.01);
    });
  });
});
