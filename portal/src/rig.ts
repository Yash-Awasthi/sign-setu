// Port of app/src/main/java/app/signsetu/ui/avatar/Rig.kt. Keep the two in step: rig.test.ts compares them.

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

export interface HandKey {
  at?: string;
  dx?: number;
  dy?: number;
  shape?: string;
  roll?: number;
  angle?: number | null;
  elbow?: string;
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
  elbowOut: number;
  prop: string | null;
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

export const ChildBuild: Build = {
  headX: 100,
  headY: 76,
  headRx: 40,
  headRy: 42,
  neckTop: 110,
  neckWidth: 17,
  shoulderY: 140,
  shoulderHalf: 39,
  upperArm: 38,
  foreArm: 35,
  armWidth: 12,
  handScale: 1.6,
  anchors: {
    hairline: [100, 40],
    forehead: [100, 56],
    eye: [84, 74],
    nose: [100, 85],
    nose_side: [95, 86],
    mouth: [100, 99],
    chin: [100, 114],
    cheek: [78, 92],
    ear: [58, 80],
    temple: [66, 62],
    hair_side: [64, 48],
    head_top: [100, 30],
    chest: [100, 156],
    belly: [100, 188],
    shoulder: [61, 140],
    side: [56, 176],
    front: [100, 168],
    arm: [58, 166],
    hip: [70, 196],
    low: [100, 196],
  },
};

export const AdultBuild: Build = {
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
  handScale: 1.4,
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
  touch: "wrist",
  prop: null,
};
const full = (k: HandKey): FullHandKey => ({ ...REST_KEY, ...k });
const OTHER_HAND = new Set(["wrist_opp", "palm_opp", "thumb_opp", "index_opp", "middle_opp", "ring_opp", "pinky_opp"]);
export const LEAD_IN = 0.35;
export const LEAD_OUT = 0.45;

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
const restFrame = (t = 0): Frame => ({ t, r: REST_KEY, l: REST_KEY, face: FACES.neutral, head: head(), body: body() });

const ease = (f: number) => (f < 0.5 ? 4 * f * f * f : 1 - (-2 * f + 2) ** 3 / 2);
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
    return { x, y, shape: SHAPES.RELAX, roll: -1, angle: NaN, elbowOut: 1, prop: null };
  };
  return { r: hand(R), l: hand(L), face: FACES.neutral, tilt: 0, nod: 0, turn: 0, lean: 0, shrug: 0, hunch: 0, shiver: 0, gaze: 0 };
}

/** Plays a gesture: eases in from rest, through the keys, and back to rest. */
export class Timeline {
  private readonly keys: Key[];
  readonly duration: number;

  constructor(
    gesture: Gesture | null,
    private readonly build: Build,
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
    const f = b.t > a.t ? ease(clamp((t - a.t) / (b.t - a.t), 0, 1)) : 0;
    return this.blend(a, b, f);
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

  private blend(a: Frame, b: Frame, f: number): Pose {
    // The hand placed on the other one is solved second; fingerspelling places the right hand on the left.
    const rFollows = OTHER_HAND.has(a.r.at) || OTHER_HAND.has(b.r.at);
    let r: HandPose;
    let l: HandPose;
    if (rFollows) {
      l = this.hand(a.l, b.l, f, L);
      r = this.hand(a.r, b.r, f, R, l);
    } else {
      r = this.hand(a.r, b.r, f, R);
      l = this.hand(a.l, b.l, f, L, r);
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

  private hand(a: FullHandKey, b: FullHandKey, f: number, side: Side, other?: HandPose): HandPose {
    const pa = this.resolve(a, side, other);
    const pb = this.resolve(b, side, other);
    const sa = SHAPES[a.shape] ?? SHAPES.RELAX;
    const sb = SHAPES[b.shape] ?? SHAPES.RELAX;
    const angleA = a.angle != null ? a.angle * side : NaN;
    const angleB = b.angle != null ? b.angle * side : NaN;
    let angle: number;
    if (Number.isNaN(angleA) && Number.isNaN(angleB)) angle = NaN;
    else if (Number.isNaN(angleA)) angle = f < 0.5 ? NaN : angleB;
    else if (Number.isNaN(angleB)) angle = f < 0.5 ? angleA : NaN;
    else angle = lerp(angleA, angleB, f);
    return {
      x: lerp(pa[0], pb[0], f),
      y: lerp(pa[1], pb[1], f),
      shape: blendShape(sa, sb, f),
      roll: lerp(a.roll, b.roll, f),
      angle,
      elbowOut: lerp(elbow(a.elbow), elbow(b.elbow), f),
      prop: f < 0.5 ? a.prop : b.prop,
    };
  }

  private resolve(k: FullHandKey, side: Side, other?: HandPose): [number, number] {
    let base: [number, number];
    if (k.at === "rest") base = rest(this.build, side);
    else if (OTHER_HAND.has(k.at) && other) base = point(other, k.at.slice(0, -4), -side as Side, this.build.handScale);
    else base = anchor(this.build, k.at, side) ?? rest(this.build, side);
    // +dx is outward: towards the viewer's left for the right hand.
    return this.reachBack(k, [base[0] + k.dx * side, base[1] + k.dy], side);
  }

  /** Moves the target back along the hand so the fingertip or palm, not the wrist, lands on the anchor. */
  private reachBack(k: FullHandKey, p: [number, number], side: Side): [number, number] {
    let reach: number;
    if (k.touch === "palm") reach = 6.5;
    else if (k.touch === "thumb") reach = 9;
    else if (k.touch === "tip") reach = 12 + 9.5 * (1 - 0.75 * (SHAPES[k.shape]?.index ?? 0));
    else return p;
    reach *= this.build.handScale;
    const a = rad((k.angle ?? 0) * side);
    return [p[0] - Math.sin(a) * reach, p[1] + Math.cos(a) * reach];
  }
}

/** Two-bone arm: returns the elbow for a shoulder, wrist target and segment lengths. */
export function solveElbow(
  sx: number,
  sy: number,
  tx: number,
  ty: number,
  upper: number,
  fore: number,
  side: Side,
  elbowOut: number,
): [number, number] {
  const dx = tx - sx;
  const dy = ty - sy;
  const d = clamp(Math.hypot(dx, dy), Math.abs(upper - fore) + 0.01, upper + fore - 0.01);
  const base = Math.atan2(dy, dx);
  const offset = Math.acos(clamp((upper * upper + d * d - fore * fore) / (2 * upper * d), -1, 1));
  const e1: [number, number] = [sx + upper * Math.cos(base + offset), sy + upper * Math.sin(base + offset)];
  const e2: [number, number] = [sx + upper * Math.cos(base - offset), sy + upper * Math.sin(base - offset)];
  const score = (e: [number, number]) => {
    const outward = (e[0] - CENTER) * side;
    return elbowOut >= 0 ? outward * elbowOut + e[1] * (1 - elbowOut) : -outward;
  };
  // A level reach gives two mirror elbows with equal scores; the margin settles float noise on e1, as in Rig.kt.
  return score(e1) >= score(e2) - 1e-3 ? e1 : e2;
}
