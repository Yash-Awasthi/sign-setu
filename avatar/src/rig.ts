// Plays content keyframes as a pose in a 200-unit front view: x right, y down, the character's right hand on the left.

export type Side = 1 | -1;
export const R: Side = -1;
export const L: Side = 1;

export interface HandShape {
  thumb: number;
  index: number;
  middle: number;
  ring: number;
  pinky: number;
  thumbAcross: number;
  spread: number;
}

const shape = (thumb: number, index: number, middle: number, ring: number, pinky: number, thumbAcross = 0, spread = 0): HandShape => ({
  thumb,
  index,
  middle,
  ring,
  pinky,
  thumbAcross,
  spread,
});

export const curls = (s: HandShape) => [s.thumb, s.index, s.middle, s.ring, s.pinky];

export const SHAPES: Record<string, HandShape> = {
  RELAX: shape(0.25, 0.2, 0.28, 0.34, 0.4, 0.2),
  FLAT: shape(0, 0, 0, 0, 0, 0.15),
  OPEN: shape(0, 0, 0, 0, 0, 0, 1),
  FIST: shape(0.55, 1, 1, 1, 1, 1),
  POINT: shape(0.55, 0, 1, 1, 1, 1),
  TWO: shape(0.55, 0, 0, 1, 1, 1, 0.6),
  THREE: shape(0.55, 0, 0, 0, 1, 1, 0.4),
  FOUR: shape(0.9, 0, 0, 0, 0, 1, 0.35),
  PINKY: shape(0.55, 1, 1, 1, 0, 1),
  THUMB: shape(0, 1, 1, 1, 1, 0),
  Y: shape(0, 1, 1, 1, 0, 0, 0.4),
  BUNCH: shape(0.45, 0.5, 0.5, 0.5, 0.52, 0.7, -1),
  CUP: shape(0.3, 0.35, 0.35, 0.38, 0.4, 0.35, -0.3),
  PINCH: shape(0.45, 0.55, 1, 1, 1, 0.6),
  CLAW: shape(0.35, 0.6, 0.6, 0.62, 0.65, 0.3, 0.3),
  SALUTE: shape(0.1, 0, 0, 0, 0, 0.6, -0.2),
  WALK_A: shape(0.55, 0, 0.45, 1, 1, 1, 0.4),
  WALK_B: shape(0.55, 0.45, 0, 1, 1, 1, 0.4),
  RUB: shape(0.7, 0.55, 0.55, 0.6, 0.65, 0.9, -0.6),
  HOOK: shape(0.55, 0.5, 1, 1, 1, 1),
};

/** A named shape, or a measured "c:" shape (five curls, thumbAcross, spread, in percent). */
export function handShape(name: string): HandShape | undefined {
  if (SHAPES[name]) return SHAPES[name];
  if (!name.startsWith("c:")) return undefined;
  const v = name
    .slice(2)
    .split(",")
    .map((s) => (/^\s*-?\d+\s*$/.test(s) ? parseInt(s, 10) : NaN));
  if (v.length !== 7 || v.some(Number.isNaN)) return undefined;
  const c = (i: number) => Math.min(1, Math.max(0, v[i] / 100));
  return shape(c(0), c(1), c(2), c(3), c(4), c(5), Math.min(1, Math.max(-1, v[6] / 100)));
}

export interface HandKey {
  at?: string;
  dx?: number;
  dy?: number;
  shape?: string;
  roll?: number;
  angle?: number | null;
  elbow?: string;
  ex?: number | null;
  ey?: number | null;
  touch?: string;
  prop?: string | null;
}
type FullHandKey = Required<HandKey>;
export interface HeadKey {
  tilt?: number;
  nod?: number;
  turn?: number;
}
export interface BodyKey {
  lean?: number;
  shrug?: number;
  hunch?: number;
  shiver?: number;
}
export interface Key {
  t: number;
  R?: HandKey | null;
  L?: HandKey | null;
  face?: string | null;
  head?: HeadKey | null;
  body?: BodyKey | null;
}
export interface Gesture {
  id: string;
  keys: Key[];
}

export interface Face {
  browRaise: number;
  browWorry: number;
  browFrown: number;
  eyeOpen: number;
  eyeSmile: number;
  smile: number;
  mouthOpen: number;
  mouthRound: number;
}
const NEUTRAL: Face = {
  browRaise: 0,
  browWorry: 0,
  browFrown: 0,
  eyeOpen: 1,
  eyeSmile: 0,
  smile: 0.35,
  mouthOpen: 0,
  mouthRound: 0,
};
const face = (f: Partial<Face>): Face => ({ ...NEUTRAL, ...f });
export const FACES: Record<string, Face> = {
  neutral: face({}),
  smile: face({ smile: 0.8, eyeSmile: 0.25 }),
  happy: face({ smile: 1, eyeSmile: 0.6, mouthOpen: 0.35, browRaise: 0.2 }),
  ask: face({ browRaise: 0.85, smile: 0.2, mouthOpen: 0.15, eyeOpen: 1.1 }),
  gentle: face({ browWorry: 0.45, smile: 0.45 }),
  concern: face({ browWorry: 0.8, smile: -0.25 }),
  wince: face({ browFrown: 0.8, eyeOpen: 0.45, smile: -0.5, mouthOpen: 0.1 }),
  sleepy: face({ eyeOpen: 0.1, smile: 0.3, browRaise: -0.2 }),
  round: face({ mouthRound: 0.8, mouthOpen: 0.4, browRaise: 0.3 }),
  cold: face({ browWorry: 0.6, eyeOpen: 0.6, smile: -0.3, mouthOpen: 0.12 }),
  hot: face({ browWorry: 0.3, eyeOpen: 0.7, mouthOpen: 0.3, smile: 0 }),
};
export const FACE_KEYS = Object.keys(NEUTRAL) as (keyof Face)[];

export interface HandPose {
  x: number;
  y: number;
  shape: HandShape;
  roll: number;
  /** Screen angle in degrees, NaN to follow the forearm. */
  angle: number;
  /** 1 elbow out, 0 down, -1 in. */
  elbowOut: number;
}

export interface Pose {
  r: HandPose;
  l: HandPose;
  face: Face;
  tilt: number;
  nod: number;
  turn: number;
  lean: number;
  shrug: number;
  hunch: number;
  shiver: number;
  gaze: number;
}

export const lerp = (a: number, b: number, f: number) => a + (b - a) * f;
const rad = (deg: number) => (deg * Math.PI) / 180;
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);

export function point(h: HandPose, part: string, side: Side, handScale: number): [number, number] {
  const pw = 13;
  const thumbSide = h.roll > 0 ? -side : side;
  const c = curls(h.shape);
  let hx: number;
  let hy: number;
  if (part === "wrist") [hx, hy] = [0, 0];
  else if (part === "palm") [hx, hy] = [0, -6];
  else if (part === "thumb") {
    const a = rad(thumbSide * (52 - h.shape.thumbAcross * 112));
    const len = 8.8 * (1 - 0.4 * c[0]);
    [hx, hy] = [thumbSide * (pw / 2 - 0.5) + Math.sin(a) * len, -3 - Math.cos(a) * len];
  } else {
    const k = Math.max(0, ["index", "middle", "ring", "pinky"].indexOf(part));
    const len = [9.2, 10.2, 9.6, 7.7][k] * (1 - 0.8 * c[k + 1]);
    const across = Math.abs(h.roll) < 0.35 ? 0 : thumbSide * (pw / 2 - 1.8 - (k * (pw - 3.6)) / 3);
    [hx, hy] = [across, -12 - len];
  }
  const a = rad(Number.isNaN(h.angle) ? 0 : h.angle);
  const rx = (hx * Math.cos(a) - hy * Math.sin(a)) * handScale;
  const ry = (hx * Math.sin(a) + hy * Math.cos(a)) * handScale;
  return [h.x + rx, h.y + ry];
}

export const CENTER = 100;
const SIDED = new Set(["cheek", "ear", "shoulder", "temple", "side", "eye", "hip", "arm", "nose_side", "hair_side"]);

export interface Build {
  headX: number;
  headY: number;
  headRx: number;
  headRy: number;
  neckTop: number;
  neckWidth: number;
  shoulderY: number;
  shoulderHalf: number;
  upperArm: number;
  foreArm: number;
  armWidth: number;
  handScale: number;
  anchors: Record<string, [number, number]>;
}

export function anchor(b: Build, name: string, side: Side): [number, number] | null {
  const opposite = name.endsWith("_opp");
  const base = opposite ? name.slice(0, -4) : name;
  const p = b.anchors[base];
  if (!p) return null;
  const s = opposite ? (-side as Side) : side;
  return SIDED.has(base) ? [CENTER + (p[0] - CENTER) * (s / R), p[1]] : [p[0], p[1]];
}
export const shoulder = (b: Build, side: Side): [number, number] => [CENTER + side * b.shoulderHalf, b.shoulderY];
const rest = (b: Build, side: Side): [number, number] => [CENTER + side * (b.shoulderHalf - 6), 206];

export const BUILD: Build = {
  headX: 100,
  headY: 70,
  headRx: 31,
  headRy: 37,
  neckTop: 104,
  neckWidth: 17,
  shoulderY: 136,
  shoulderHalf: 44,
  upperArm: 41,
  foreArm: 38,
  armWidth: 13,
  handScale: 2.0,
  anchors: {
    hairline: [100, 38],
    forehead: [100, 52],
    eye: [88, 67],
    nose: [100, 78],
    nose_side: [96, 80],
    mouth: [100, 91],
    chin: [100, 104],
    cheek: [84, 84],
    ear: [68, 72],
    temple: [73, 58],
    hair_side: [72, 46],
    head_top: [100, 33],
    chest: [100, 154],
    belly: [100, 188],
    shoulder: [56, 136],
    side: [52, 178],
    front: [100, 166],
    arm: [53, 168],
    hip: [66, 196],
    low: [100, 196],
  },
};

const REST_KEY: FullHandKey = {
  at: "rest",
  dx: 0,
  dy: 0,
  shape: "RELAX",
  roll: -1,
  angle: null,
  elbow: "out",
  ex: null,
  ey: null,
  touch: "wrist",
  prop: null,
};
const full = (k: HandKey): FullHandKey => ({ ...REST_KEY, ...k });
// Resting arms hang with the elbows down and palms to the thighs; elbows out there reads as hands on hips.
const RESTING: FullHandKey = { ...REST_KEY, elbow: "down", roll: 0 };
export const OTHER_HAND = new Set(["wrist_opp", "palm_opp", "thumb_opp", "index_opp", "middle_opp", "ring_opp", "pinky_opp"]);
export const LEAD_IN = 0.35;
export const LEAD_OUT = 0.45;
const FLOW = 0.35;

interface Frame {
  t: number;
  r: FullHandKey;
  l: FullHandKey;
  face: Face;
  head: Required<HeadKey>;
  body: Required<BodyKey>;
}
const head = (h: HeadKey = {}) => ({ tilt: 0, nod: 0, turn: 0, ...h });
const body = (b: BodyKey = {}) => ({ lean: 0, shrug: 0, hunch: 0, shiver: 0, ...b });
const restFrame = (t = 0): Frame => ({ t, r: RESTING, l: RESTING, face: FACES.neutral, head: head(), body: body() });

const ease = (f: number) => (f < 0.5 ? 4 * f * f * f : 1 - (-2 * f + 2) ** 3 / 2);
const hermite = (p1: number, p2: number, m1: number, m2: number, u: number) =>
  (2 * u ** 3 - 3 * u ** 2 + 1) * p1 + (u ** 3 - 2 * u ** 2 + u) * m1 + (-2 * u ** 3 + 3 * u ** 2) * p2 + (u ** 3 - u ** 2) * m2;
const measured = (f: Frame) => f.r.shape.startsWith("c:") || f.l.shape.startsWith("c:");
/** Recorded keys close together sample one movement. */
const flows = (frames: Frame[], i: number) =>
  i >= 0 && i < frames.length - 1 && frames[i + 1].t - frames[i].t < FLOW && measured(frames[i]) && measured(frames[i + 1]);
const easeOutBack = (f: number, c1 = 1.1) => 1 + (c1 + 1) * (f - 1) ** 3 + c1 * (f - 1) ** 2;
const elbow = (name: string) => (name === "in" ? -1 : name === "down" ? 0 : 1);
const blendShape = (a: HandShape, b: HandShape, f: number): HandShape =>
  shape(
    lerp(a.thumb, b.thumb, f),
    lerp(a.index, b.index, f),
    lerp(a.middle, b.middle, f),
    lerp(a.ring, b.ring, f),
    lerp(a.pinky, b.pinky, f),
    lerp(a.thumbAcross, b.thumbAcross, f),
    lerp(a.spread, b.spread, f),
  );

export function restPose(b: Build): Pose {
  const hand = (side: Side): HandPose => {
    const [x, y] = rest(b, side);
    return { x, y, shape: SHAPES.RELAX, roll: 0, angle: NaN, elbowOut: 0 };
  };
  return {
    r: hand(R),
    l: hand(L),
    face: FACES.neutral,
    tilt: 0,
    nod: 0,
    turn: 0,
    lean: 0,
    shrug: 0,
    hunch: 0,
    shiver: 0,
    gaze: 0,
  };
}

/** Plays a gesture: eases in from rest, through the keys, and back to rest. */
export class Timeline {
  private readonly keys: Key[];
  readonly duration: number;

  constructor(
    gesture: Gesture | null,
    private readonly build: Build = BUILD,
  ) {
    this.keys = [...(gesture?.keys ?? [])].sort((a, b) => a.t - b.t);
    this.duration = this.keys.length === 0 ? 0 : LEAD_IN + this.keys[this.keys.length - 1].t + LEAD_OUT;
  }

  pose(seconds: number): Pose {
    if (this.keys.length === 0) return restPose(this.build);
    const t = seconds - LEAD_IN;
    const last = this.keys[this.keys.length - 1].t;
    const frames = [restFrame(-LEAD_IN), ...this.filled(), restFrame(last + LEAD_OUT)];
    let i = 0;
    for (let j = 0; j < frames.length; j++) if (frames[j].t <= t) i = j;
    const a = frames[i];
    const b = frames[i + 1] ?? a;
    if (flows(frames, i)) return this.flow(frames, i, clamp((t - a.t) / (b.t - a.t), 0, 1));
    // Each move lands a little early and holds.
    const hold = Math.min(0.08, 0.25 * (b.t - a.t));
    const raw = b.t > a.t ? clamp((t - a.t) / (b.t - a.t - hold), 0, 1) : 0;
    return this.blend(a, b, raw);
  }

  private filled(): Frame[] {
    let prev = restFrame();
    return this.keys.map((k) => {
      prev = {
        t: k.t,
        r: k.R ? full(k.R) : prev.r,
        l: k.L ? full(k.L) : prev.l,
        face: (k.face != null ? FACES[k.face] : undefined) ?? prev.face,
        head: k.head ? head(k.head) : prev.head,
        body: k.body ? body(k.body) : prev.body,
      };
      return prev;
    });
  }

  /** Catmull-Rom through the recorded keys. */
  private flow(frames: Frame[], i: number, u: number): Pose {
    const base = this.blend(frames[i], frames[i + 1], u);
    const key = (k: number, side: Side) => (side === R ? frames[k].r : frames[k].l);
    const track = (k: number, side: Side): number[] => {
      const hk = key(k, side);
      const [x, y] = this.resolve(hk, side);
      const s = handShape(hk.shape) ?? SHAPES.RELAX;
      return [x, y, hk.roll, (hk.angle ?? 0) * side, s.thumb, s.index, s.middle, s.ring, s.pinky, s.thumbAcross, s.spread];
    };
    const at = (side: Side, h: HandPose): HandPose => {
      const p1 = track(i, side);
      const p2 = track(i + 1, side);
      const p0 = flows(frames, i - 1) ? track(i - 1, side) : null;
      const p3 = flows(frames, i + 1) ? track(i + 2, side) : null;
      const t1 = frames[i].t;
      const t2 = frames[i + 1].t;
      const v = p1.map((_, j) => {
        const m1 = p0 ? (p2[j] - p0[j]) / (t2 - frames[i - 1].t) : 0;
        const m2 = p3 ? (p3[j] - p1[j]) / (frames[i + 2].t - t1) : 0;
        return hermite(p1[j], p2[j], m1 * (t2 - t1), m2 * (t2 - t1), u);
      });
      const curl = (j: number) => clamp(v[j], 0, 1);
      return {
        ...h,
        x: v[0],
        y: v[1],
        roll: v[2],
        angle: key(i, side).angle != null && key(i + 1, side).angle != null ? v[3] : h.angle,
        shape: shape(curl(4), curl(5), curl(6), curl(7), curl(8), curl(9), clamp(v[10], -1, 1)),
      };
    };
    return { ...base, r: at(R, base.r), l: at(L, base.l) };
  }

  private blend(a: Frame, b: Frame, raw: number): Pose {
    const f = ease(raw);
    // The hand placed on the other one is solved second; fingerspelling places the right hand on the left.
    const rFollows = OTHER_HAND.has(a.r.at) || OTHER_HAND.has(b.r.at);
    let r: HandPose;
    let l: HandPose;
    if (rFollows) {
      l = this.hand(a.l, b.l, raw, L);
      r = this.hand(a.r, b.r, raw, R, l);
    } else {
      r = this.hand(a.r, b.r, raw, R);
      l = this.hand(a.l, b.l, raw, L, r);
    }
    const fc = {} as Face;
    for (const k of FACE_KEYS) fc[k] = lerp(a.face[k], b.face[k], f);
    return {
      r,
      l,
      face: fc,
      tilt: lerp(a.head.tilt, b.head.tilt, f),
      nod: lerp(a.head.nod, b.head.nod, f),
      turn: lerp(a.head.turn, b.head.turn, f),
      lean: lerp(a.body.lean, b.body.lean, f),
      shrug: lerp(a.body.shrug, b.body.shrug, f),
      hunch: lerp(a.body.hunch, b.body.hunch, f),
      shiver: lerp(a.body.shiver, b.body.shiver, f),
      gaze: 0,
    };
  }

  private hand(a: FullHandKey, b: FullHandKey, raw: number, side: Side, other?: HandPose): HandPose {
    const f = ease(raw);
    // A free move overshoots slightly and settles; contacts and moves to or from rest never pass their target.
    const free = b.touch === "wrist" && !OTHER_HAND.has(b.at) && b.at !== "rest" && a.at !== "rest";
    const m = free ? easeOutBack(raw) : f;
    const pa = this.resolve(a, side, other);
    const pb = this.resolve(b, side, other);
    const sa = handShape(a.shape) ?? SHAPES.RELAX;
    const sb = handShape(b.shape) ?? SHAPES.RELAX;
    const angleA = a.angle != null ? a.angle * side : NaN;
    const angleB = b.angle != null ? b.angle * side : NaN;
    let angle: number;
    if (Number.isNaN(angleA) && Number.isNaN(angleB)) angle = NaN;
    else if (Number.isNaN(angleA)) angle = f < 0.5 ? NaN : angleB;
    else if (Number.isNaN(angleB)) angle = f < 0.5 ? angleA : NaN;
    else angle = lerp(angleA, angleB, m);
    return {
      x: lerp(pa[0], pb[0], m),
      y: lerp(pa[1], pb[1], m),
      shape: blendShape(sa, sb, f),
      roll: lerp(a.roll, b.roll, f),
      angle,
      elbowOut: lerp(elbow(a.elbow), elbow(b.elbow), f),
    };
  }

  private resolve(k: FullHandKey, side: Side, other?: HandPose): [number, number] {
    let base: [number, number];
    if (k.at === "rest") base = rest(this.build, side);
    else if (OTHER_HAND.has(k.at) && other) base = point(other, k.at.slice(0, -4), -side as Side, this.build.handScale);
    else base = anchor(this.build, k.at, side) ?? rest(this.build, side);
    // +dx is outward: towards the viewer's left for the right hand. The 3D arm, longer than this view's, sets the reach.
    return this.reachBack(k, [base[0] + k.dx * side, base[1] + k.dy], side);
  }

  /** Moves the target back along the hand so the fingertip or palm, not the wrist, lands on the anchor. */
  private reachBack(k: FullHandKey, p: [number, number], side: Side): [number, number] {
    let reach: number;
    if (k.touch === "palm") reach = 6.5;
    else if (k.touch === "thumb") reach = 9;
    else if (k.touch === "tip") reach = 12 + 9.5 * (1 - 0.75 * (handShape(k.shape)?.index ?? 0));
    else return p;
    reach *= this.build.handScale;
    const a = rad((k.angle ?? 0) * side);
    return [p[0] - Math.sin(a) * reach, p[1] + Math.cos(a) * reach];
  }
}

/** The part of the 200-unit space the body view shows: to the hips, so both hands stay inside at rest. */
export const VIEW = { left: -10, top: 0, width: 220, height: 258 };

export type QuestionKind = "YES_NO" | "WH";

/** 0 to 1 over the lead-in, 1 while the gesture plays, back to 0 over the lead-out. */
export const envelope = (t: number, duration: number) => clamp(t / LEAD_IN, 0, 1) * clamp((duration - t) / LEAD_OUT, 0, 1);

/** The face that marks a question in ISL: raised brows for yes-no, furrowed brows for a question word. */
export function questionFace(p: Pose, kind: QuestionKind, e: number): Pose {
  const f = p.face;
  if (kind === "YES_NO")
    return { ...p, face: { ...f, browRaise: Math.max(f.browRaise, 0.85 * e), eyeOpen: f.eyeOpen * (1 + 0.1 * e) }, nod: p.nod - 0.15 * e };
  return {
    ...p,
    face: { ...f, browFrown: Math.max(f.browFrown, 0.75 * e), browRaise: f.browRaise * (1 - e), eyeOpen: f.eyeOpen * (1 - 0.12 * e) },
    nod: p.nod + 0.12 * e,
    tilt: p.tilt + 2 * e,
  };
}

/** ISL negation: a side to side head shake with slightly lowered brows, over the whole sentence. */
export function negationFace(p: Pose, seconds: number, e: number): Pose {
  return {
    ...p,
    turn: p.turn + 0.2 * e * Math.sin(seconds * 2 * Math.PI * 1.6),
    face: { ...p.face, browFrown: Math.max(p.face.browFrown, 0.35 * e) },
  };
}

/** The pose shown at [seconds]: the gesture with its question and negation face, a lowered glance and the mouth
 * moving while it signs. */
export function livePose(
  timeline: Timeline,
  seconds: number,
  question: QuestionKind | null,
  gaze: number,
  moving: boolean,
  negated = false,
): Pose {
  const d = timeline.duration;
  const e = d > 0 ? envelope(seconds, d) : 0;
  let p = timeline.pose(seconds);
  if (gaze > 0) p = { ...p, nod: p.nod + gaze * 0.5, gaze };
  if (question && e > 0) p = questionFace(p, question, e);
  if (negated && e > 0) p = negationFace(p, seconds, e);
  const f = p.face;
  if (moving && d > 0 && seconds < d && f.mouthOpen < 0.2 && f.mouthRound < 0.5) {
    const talk = 0.07 * (0.5 + 0.5 * Math.sin(seconds * 2 * Math.PI * 2.3)) * e;
    const smile = 0.05 * (0.5 + 0.5 * Math.cos(seconds * 2 * Math.PI * 1.5)) * e;
    p = { ...p, face: { ...f, mouthOpen: f.mouthOpen + talk, smile: f.smile + smile } };
  }
  return p;
}

/** 1 open, 0 shut. One blink per 4 s slot at a hashed offset, so gaps vary from 2.6 to 5.4 s; slot 0 blinks at once. */
export function blinkAt(time: number): number {
  const n = Math.floor(time / 4);
  const offset = n === 0 ? 0 : (((((Math.imul(n, 1103515245) + 12345) | 0) >>> 16) % 1000) / 1000) * 1.4;
  const p = time - n * 4 - offset;
  if (p < 0 || p >= 0.13) return 1;
  return p < 0.05 ? 1 - p / 0.05 : (p - 0.05) / 0.08;
}

/** A resting body is never quite still: now and then a glance aside, as a head turn. */
export function glanceAt(time: number): number {
  const phase = time % 9;
  if (phase < 5 || phase > 6.4) return 0;
  const side = Math.floor(time / 9) % 2 === 0 ? 1 : -1;
  return side * 0.28 * Math.sin(((phase - 5) / 1.4) * Math.PI);
}
