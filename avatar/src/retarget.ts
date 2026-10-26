// Turns a rig pose (200-unit front view) into targets for a VRM humanoid: wrists, elbows, hand turn, fingers and face.
import { MathUtils, Matrix4, Quaternion, Vector3 } from "three";
import { BUILD, CENTER, L, R, type Face, type HandPose, type HandShape, type Pose, type Side } from "./rig";

/** Rest measurements of the loaded model, in scene space. */
export interface Body {
  /** Upper arm joints. */
  right: Vector3;
  left: Vector3;
  /** Head bone, where the skull meets the neck. */
  head: Vector3;
  eyeY: number;
  hipsY: number;
  upper: number;
  fore: number;
}

const A = BUILD.anchors;
const smooth = (x: number, a: number, b: number) => MathUtils.smoothstep(x, a, b);

/** Piecewise-linear lookup through (x, y) points sorted by x, extended past both ends. */
function through(points: [number, number][], x: number): number {
  let i = 0;
  while (i < points.length - 2 && x > points[i + 1][0]) i++;
  const [x0, y0] = points[i];
  const [x1, y1] = points[i + 1];
  return y0 + ((x - x0) / (x1 - x0)) * (y1 - y0);
}

/** A rig point in scene space. Heights go through matching landmarks (eyes, chin, shoulders, hips, hanging hand)
 * because the model's head, neck and arms are in other proportions than the rig's. */
export function place(x: number, y: number, body: Body): Vector3 {
  const chinY = body.head.y - 0.01;
  const face = (body.eyeY - chinY) / (A.chin[1] - A.eye[1]);
  const width = (body.left.x - body.right.x) / 2 + 0.04;
  const restY = BUILD.shoulderY + BUILD.upperArm + BUILD.foreArm - 9;
  const height = through(
    [
      [A.eye[1], body.eyeY],
      [A.chin[1], chinY],
      [BUILD.shoulderY, body.right.y],
      [A.hip[1], body.hipsY],
      [restY, body.right.y - (body.upper + body.fore) * 0.93],
    ],
    y,
  );
  const w = smooth(y, A.chin[1], BUILD.shoulderY);
  const out = Math.abs(x - CENTER);
  // ponytail: depth is a front-of-body heuristic per region; record z per anchor if signs need true contact.
  const faceZ = body.head.z + 0.12 - 0.08 * smooth(out, 14, 30);
  const inside = 1 - smooth(out, BUILD.shoulderHalf - 4, BUILD.shoulderHalf + 16);
  const bodyZ = body.right.z + 0.04 + 0.14 * inside * (1 - smooth(y, 180, restY));
  return new Vector3(
    (body.left.x + body.right.x) / 2 + (x - CENTER) * MathUtils.lerp(face, width / BUILD.shoulderHalf, w),
    height,
    MathUtils.lerp(faceZ, bodyZ, w),
  );
}

/** Two-bone arm: the elbow for shoulder s and wrist target t, bent toward pole. The wrist comes back within reach. */
export function solveArm(s: Vector3, t: Vector3, pole: Vector3, upper: number, fore: number): { elbow: Vector3; wrist: Vector3 } {
  const dir = t.clone().sub(s);
  const d = MathUtils.clamp(dir.length(), Math.abs(upper - fore) + 1e-3, upper + fore - 1e-3);
  dir.normalize();
  const a = (upper * upper + d * d - fore * fore) / (2 * d);
  const h = Math.sqrt(Math.max(0, upper * upper - a * a));
  const bend = pole.clone().sub(s);
  bend.addScaledVector(dir, -bend.dot(dir));
  if (bend.lengthSq() < 1e-10) bend.set(0, -1, 0).addScaledVector(dir, -dir.y);
  bend.normalize();
  return { elbow: s.clone().addScaledVector(dir, a).addScaledVector(bend, h), wrist: s.clone().addScaledVector(dir, d) };
}

export interface ArmTarget {
  /** Where the wrist should go. */
  wrist: Vector3;
  /** A point the elbow bends toward: down and back, out or in as the key's elbow says. */
  pole: Vector3;
}

/** The arm for one side of [p], placed on the model at rest; solve it with [solveArm] from the live shoulder.
 * Passing last frame's elbow keeps the bend steady where a wrist near the shoulder would swing it round. */
export function arm(p: Pose, side: Side, body: Body, lastElbow?: Vector3): ArmTarget {
  const h = side === R ? p.r : p.l;
  const s = side === R ? body.right : body.left;
  const pole = s.clone().add(new Vector3(side * (0.12 + 0.18 * h.elbowOut), -0.4, -0.12));
  if (lastElbow) pole.lerp(lastElbow, 0.7);
  return { wrist: place(h.x, h.y, body), pole };
}

/** World turn of the hand: fingers along the rig's screen angle, or along [forearm] when it is unset, and the palm
 * turned by roll: 1 to the viewer, 0 thumb side to the viewer, -1 back of the hand to the viewer. */
export function handTurn(h: HandPose, side: Side, forearm: Vector3): Quaternion {
  let fingers: Vector3;
  if (Number.isNaN(h.angle)) fingers = forearm.clone().normalize();
  else {
    const a = MathUtils.degToRad(h.angle);
    fingers = new Vector3(Math.sin(a), Math.cos(a), 0.25).normalize();
  }
  const phi = ((1 - h.roll) * Math.PI) / 2;
  const palm = new Vector3(Math.sin(phi) * side, 0, Math.cos(phi));
  palm.addScaledVector(fingers, -palm.dot(fingers));
  if (palm.lengthSq() < 1e-8) palm.set(0, 0, 1).addScaledVector(fingers, -fingers.z);
  palm.normalize();
  // A normalized VRM hand rests with fingers along its arm's side of X and the palm down.
  const restFingers = new Vector3(side, 0, 0);
  const restPalm = new Vector3(0, -1, 0);
  const basis = (f: Vector3, n: Vector3) => new Matrix4().makeBasis(f, n, f.clone().cross(n));
  const m = basis(fingers, palm).multiply(basis(restFingers, restPalm).transpose());
  return new Quaternion().setFromRotationMatrix(m);
}

const FINGERS = ["Index", "Middle", "Ring", "Little"] as const;
const SPLAY = [0.12, 0.02, -0.08, -0.16];

/** Local euler angles (x, y, z) for each finger bone of a hand shape, as the clip solver bends them. */
export function fingerAngles(s: HandShape, side: Side): Record<string, [number, number, number]> {
  const sign = side === L ? -1 : 1;
  const out: Record<string, [number, number, number]> = {};
  const c = [s.index, s.middle, s.ring, s.pinky];
  FINGERS.forEach((f, i) => {
    const sp = SPLAY[i] * s.spread;
    out[`${f}Proximal`] = [0, side === L ? -sp : sp, sign * c[i] * 1.5];
    out[`${f}Intermediate`] = [0, 0, sign * c[i] * 1.6];
    out[`${f}Distal`] = [0, 0, sign * c[i] * 1.2];
  });
  const opposition = 0.1 + s.thumbAcross * 0.5;
  const bend = s.thumb * 1.1;
  out.ThumbMetacarpal = [0, -sign * opposition, sign * bend * 0.4];
  out.ThumbProximal = [0, -sign * bend * 0.7, sign * bend * 0.3];
  out.ThumbDistal = [0, -sign * s.thumb * 1.2 * 0.85, 0];
  return out;
}

/** VRM preset expression weights for a rig face; [blink] is 1 open, 0 shut. */
export function expressions(f: Face, blink: number): Record<string, number> {
  const c = (v: number) => MathUtils.clamp(v, 0, 1);
  return {
    happy: c((f.smile - 0.35) * 0.6 + f.eyeSmile * 0.3),
    sad: c(f.browWorry * 0.7 + Math.max(0, -f.smile) * 0.6),
    angry: c(f.browFrown * 0.7),
    surprised: c(f.browRaise * 0.5 + (f.eyeOpen - 1) * 2),
    aa: f.mouthRound > 0.5 ? 0 : c(f.mouthOpen * 1.4),
    ou: c(f.mouthRound),
    blink: c(1 - f.eyeOpen * blink),
  };
}
