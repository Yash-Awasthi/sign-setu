import { describe, expect, it } from "vite-plus/test";
import aslFile from "../../app/src/main/assets/content/asl_signs.json";
import gesturesFile from "../../app/src/main/assets/content/gestures.json";
import islFile from "../../app/src/main/assets/content/isl_signs.json";
import signsFile from "../../app/src/main/assets/content/signs.json";
import {
  BUILD,
  L,
  LEAD_IN,
  OTHER_HAND,
  R,
  SHAPES,
  Timeline,
  VIEW,
  anchor,
  handShape,
  point,
  restPose,
  shoulder,
  type Gesture,
  type HandPose,
  type Side,
} from "./rig";

const authored = [...gesturesFile.gestures, ...signsFile.gestures] as Gesture[];
const recorded = islFile.gestures as Gesture[];
const asl = aslFile.gestures as Gesture[];
const byId = new Map([...authored, ...recorded].map((g) => [g.id, g]));
const PARTS = ["wrist", "thumb", "index", "middle", "ring", "pinky"];
const dist = (a: [number, number], b: [number, number]) => Math.hypot(a[0] - b[0], a[1] - b[1]);

/** Wrist and every fingertip: a hand without an angle points along the line from its shoulder. */
function handPoints(h: HandPose, side: Side): [number, number][] {
  const [sx, sy] = shoulder(BUILD, side);
  const drawn = Number.isNaN(h.angle) ? { ...h, angle: (Math.atan2(h.x - sx, sy - h.y) * 180) / Math.PI } : h;
  return PARTS.map((part) => point(drawn, part, side, BUILD.handScale));
}

function* steps(t: Timeline, from = 0, to = t.duration) {
  for (let s = from; s <= to + 1e-6; s += 0.05) yield s;
}

describe("content gestures", () => {
  it("use known anchors, shapes and touches", () => {
    for (const g of [...authored, ...asl]) {
      expect(
        g.keys.every((k, i) => i === 0 || g.keys[i - 1].t < k.t),
        `${g.id} keys out of order`,
      ).toBe(true);
      for (const h of g.keys.flatMap((k) => [k.R, k.L]).filter((h) => h != null)) {
        expect(handShape(h.shape ?? "RELAX"), `${g.id} shape ${h.shape}`).toBeDefined();
        expect(["wrist", "palm", "tip", "thumb"], `${g.id} touch`).toContain(h.touch ?? "wrist");
        const at = h.at ?? "rest";
        if (at !== "rest" && !OTHER_HAND.has(at)) expect(anchor(BUILD, at, R), `${g.id} anchor ${at}`).not.toBeNull();
      }
    }
  });

  it("start and end at rest", () => {
    const rest = restPose(BUILD);
    for (const g of authored) {
      const t = new Timeline(g);
      for (const p of [t.pose(0), t.pose(t.duration)]) {
        expect(p.r.x, g.id).toBeCloseTo(rest.r.x, 2);
        expect(p.r.y, g.id).toBeCloseTo(rest.r.y, 2);
        expect(p.l.x, g.id).toBeCloseTo(rest.l.x, 2);
      }
    }
  });

  it("keep every hand point inside the body view", () => {
    // Recorded signs round dx and dy to whole rig units, so half a unit past a 2-unit margin is rounding.
    const edge = 1.5;
    const rest = restPose(BUILD);
    for (const [h, side] of [
      [rest.r, R],
      [rest.l, L],
    ] as const)
      for (const [, y] of handPoints(h, side)) expect(y).toBeLessThanOrEqual(VIEW.top + VIEW.height - 4);
    const outside: string[] = [];
    for (const g of [...authored, ...recorded, ...asl]) {
      const t = new Timeline(g);
      for (const s of steps(t)) {
        const p = t.pose(s);
        for (const [h, side] of [
          [p.r, R],
          [p.l, L],
        ] as const)
          for (const [x, y] of handPoints(h, side)) {
            const inside =
              x >= VIEW.left + edge && x <= VIEW.left + VIEW.width - edge && y >= VIEW.top + edge && y <= VIEW.top + VIEW.height - edge;
            if (!inside) outside.push(`${g.id} ${s.toFixed(2)}s (${x.toFixed(0)}, ${y.toFixed(0)})`);
          }
      }
    }
    expect(outside.slice(0, 8)).toEqual([]);
  });

  it("keep authored hands on their own side and in the frame", () => {
    // Hugging yourself crosses the arms on purpose; signed questions are chains of these signs.
    const crossing = new Set(["cold.hug"]);
    for (const g of authored.filter((g) => !g.id.startsWith("q."))) {
      const used = (side: "R" | "L") => g.keys.some((k) => k[side] && (k[side]!.at ?? "rest") !== "rest");
      const t = new Timeline(g);
      const last = LEAD_IN + Math.max(...g.keys.map((k) => k.t));
      for (const s of steps(t, LEAD_IN, last)) {
        const p = t.pose(s);
        if (used("R")) expect(p.r.x >= 4 && p.r.x <= 196 && p.r.y >= 4 && p.r.y <= 196, `${g.id} right at ${s}`).toBe(true);
        if (used("L")) expect(p.l.x >= 4 && p.l.x <= 196 && p.l.y >= 4 && p.l.y <= 196, `${g.id} left at ${s}`).toBe(true);
        if (used("R") && used("L") && !crossing.has(g.id)) expect(p.r.x, `${g.id} crosses at ${s}`).toBeLessThanOrEqual(p.l.x + 4);
      }
    }
  });

  it("place a hand on the other hand's fingertip", () => {
    const p = new Timeline(byId.get("letter.e")!).pose(LEAD_IN + 0.2);
    expect(dist(point(p.l, "index", L, BUILD.handScale), point(p.r, "index", R, BUILD.handScale))).toBeLessThan(3);
  });

  it("land a held letter early and then hold still", () => {
    for (const id of ["letter.a", "letter.e", "letter.hi.क", "letter.hi.प"]) {
      const g = byId.get(id)!;
      const hold = g.keys[g.keys.length - 1].t;
      expect(hold, `${id} held too briefly to read`).toBeGreaterThanOrEqual(0.3);
      expect(g.keys[0].R, `${id} hold drifts`).toEqual(g.keys[g.keys.length - 1].R);
      const t = new Timeline(g);
      const settled = t.pose(LEAD_IN + hold * 0.75).r;
      for (let step = 0; step <= 5; step++) {
        const p = t.pose(LEAD_IN + hold * (0.75 + 0.05 * step)).r;
        expect(p.x).toBeCloseTo(settled.x, 3);
        expect(p.y).toBeCloseTo(settled.y, 3);
      }
    }
  });

  it("sign base aksharas exactly as their Latin letters", () => {
    for (const [ak, lat] of [
      ["क", "k"],
      ["प", "p"],
      ["न", "n"],
    ]) {
      const hi = new Timeline(byId.get(`letter.hi.${ak}`)!);
      const en = new Timeline(byId.get(`letter.${lat}`)!);
      expect(hi.duration).toBeCloseTo(en.duration, 6);
      for (const s of steps(hi)) expect(hi.pose(s)).toEqual(en.pose(s));
    }
  });

  it("pass through close recorded keys without stopping", () => {
    let moving = 0;
    for (const g of recorded) {
      const t = new Timeline(g);
      const keys = [...g.keys].sort((a, b) => a.t - b.t);
      for (let k = 1; k < keys.length - 1; k++) {
        if (keys[k].t - keys[k - 1].t >= 0.35 || keys[k + 1].t - keys[k].t >= 0.35) continue;
        const at = LEAD_IN + keys[k].t;
        const v = (s: number): [number, number] => {
          const a = t.pose(s).r;
          const b = t.pose(s + 5e-4).r;
          return [(b.x - a.x) / 5e-4, (b.y - a.y) / 5e-4];
        };
        const before = v(at - 5e-4);
        const after = v(at);
        const speed = Math.max(Math.hypot(...before), Math.hypot(...after));
        expect(dist(before, after), `${g.id} jerks at ${keys[k].t}`).toBeLessThan(0.15 * speed + 4);
        if (speed > 20) moving++;
      }
    }
    expect(moving).toBeGreaterThan(50);
  });
});

describe("hand shapes", () => {
  it("parse measured shapes and clamp them", () => {
    expect(handShape("FIST")).toEqual(SHAPES.FIST);
    expect(handShape("c:10,20,95,100,100,50,-30")).toEqual({
      thumb: 0.1,
      index: 0.2,
      middle: 0.95,
      ring: 1,
      pinky: 1,
      thumbAcross: 0.5,
      spread: -0.3,
    });
    expect(handShape("c:-5,140,0,0,0,0,200")).toEqual({ thumb: 0, index: 1, middle: 0, ring: 0, pinky: 0, thumbAcross: 0, spread: 1 });
    for (const bad of ["c:1,2,3", "c:a,0,0,0,0,0,0", "c:", "SPOON"]) expect(handShape(bad), bad).toBeUndefined();
  });

  it("play a measured shape", () => {
    const g: Gesture = { id: "isl.x", keys: [{ t: 0, R: { at: "chest", shape: "c:0,100,100,100,100,0,0" } }, { t: 0.5 }] };
    const p = new Timeline(g).pose(LEAD_IN);
    expect(p.r.shape.index).toBeCloseTo(1, 4);
    expect(p.r.shape.thumb).toBeCloseTo(0, 4);
  });
});
