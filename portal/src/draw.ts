// Port of the drawing in app/src/main/java/app/signsetu/ui/avatar/Avatar.kt, still (no idle sway or blink).
import { CENTER, L, R, lerp, shoulder, solveElbow, curls, type Build, type Face, type HandPose, type Pose, type Side } from "./rig";

type Ctx = CanvasRenderingContext2D;
type Pt = [number, number];

interface Look {
  skin: string;
  skinShade: string;
  line: string;
  hair: string;
  hairShine: string;
  top: string;
  topShade: string;
  trim: string;
  iris: string;
  lip: string;
  blush: string;
  longSleeves: boolean;
}

export const ChildLook: Look = {
  skin: "#C98D5E",
  skinShade: "#AE7345",
  line: "#7C4A27",
  hair: "#2A1F1B",
  hairShine: "#4A3A33",
  top: "#2E9C93",
  topShade: "#227E77",
  trim: "#F6BE4B",
  iris: "#3A2416",
  lip: "#8C3A32",
  blush: "#F07C6455",
  longSleeves: false,
};
export const AdultLook: Look = {
  skin: "#BC7E52",
  skinShade: "#9F6842",
  line: "#6E4123",
  hair: "#1E1816",
  hairShine: "#3A302C",
  top: "#33405F",
  topShade: "#283350",
  trim: "#4A5A80",
  iris: "#2E1D12",
  lip: "#7E3A33",
  blush: "#F07C6422",
  longSleeves: true,
};

const WHITE = "#FFFFFF";
const rad = (d: number) => (d * Math.PI) / 180;

function mix(a: string, b: string, f: number): string {
  const p = (c: string) => [1, 3, 5, 7].map((i) => parseInt(c.slice(i, i + 2) || "ff", 16));
  const [x, y] = [p(a), p(b)];
  const v = x.map((c, i) => Math.round(lerp(c, y[i], f)));
  return `rgba(${v[0]},${v[1]},${v[2]},${v[3] / 255})`;
}

function stroke(ctx: Ctx, color: string, width: number, cap: CanvasLineCap = "round", alpha = 1) {
  ctx.globalAlpha = alpha;
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.lineCap = cap;
  ctx.stroke();
  ctx.globalAlpha = 1;
}
function fill(ctx: Ctx, color: string | CanvasGradient, alpha = 1) {
  ctx.globalAlpha = alpha;
  ctx.fillStyle = color;
  ctx.fill();
  ctx.globalAlpha = 1;
}
function line(ctx: Ctx, color: string, a: Pt, b: Pt, width: number, cap: CanvasLineCap = "round", alpha = 1) {
  ctx.beginPath();
  ctx.moveTo(a[0], a[1]);
  ctx.lineTo(b[0], b[1]);
  stroke(ctx, color, width, cap, alpha);
}
/** Oval in a box, as Compose drawOval(topLeft, size). */
function oval(ctx: Ctx, x: number, y: number, w: number, h: number) {
  ctx.beginPath();
  ctx.ellipse(x + w / 2, y + h / 2, Math.abs(w / 2), Math.abs(h / 2), 0, 0, Math.PI * 2);
}
/** Arc in a box, as Compose drawArc(start, sweep, useCenter = false, topLeft, size). */
function arc(ctx: Ctx, start: number, sweep: number, x: number, y: number, w: number, h: number) {
  ctx.beginPath();
  ctx.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, rad(start), rad(start + sweep));
}
function roundRect(ctx: Ctx, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}
function circle(ctx: Ctx, r: number, c: Pt) {
  ctx.beginPath();
  ctx.arc(c[0], c[1], Math.max(r, 0), 0, Math.PI * 2);
}
const lerpPt = (a: Pt, b: Pt, f: number): Pt => [lerp(a[0], b[0], f), lerp(a[1], b[1], f)];

/** Draws [pose] for [b] on the whole canvas, the character's 200 units filling its height. */
export function drawCharacter(ctx: Ctx, b: Build, look: Look, pose: Pose, child: boolean) {
  const { width, height } = ctx.canvas;
  ctx.clearRect(0, 0, width, height);
  const unit = height / 200;
  ctx.save();
  ctx.translate((width - 200 * unit) / 2, 0);
  ctx.scale(unit, unit);
  const bodyDy = pose.hunch * 6;
  const lift = pose.shrug * 7;
  ctx.save();
  ctx.translate(pose.lean * 6, bodyDy);
  drawTorso(ctx, b, look, lift, child);
  ctx.restore();
  drawNeck(ctx, b, look, pose.lean * 7, bodyDy);
  ctx.save();
  ctx.translate(pose.lean * 8 + pose.turn * 2, bodyDy + pose.nod * 6 + pose.hunch * 10);
  const pivot: Pt = [b.headX, b.neckTop + 4];
  ctx.translate(pivot[0], pivot[1]);
  ctx.rotate(rad(pose.tilt + pose.hunch * 8));
  ctx.translate(-pivot[0], -pivot[1]);
  drawHead(ctx, b, look, pose, child);
  ctx.restore();
  const shift: Pt = [pose.lean * 6, bodyDy - lift];
  drawArm(ctx, b, look, pose.r, R, shift);
  drawArm(ctx, b, look, pose.l, L, shift);
  ctx.restore();
}

function drawTorso(ctx: Ctx, b: Build, look: Look, lift: number, child: boolean) {
  const c = CENTER;
  const sh = b.shoulderHalf;
  const top = b.shoulderY - 9 - lift;
  const torso = () => {
    ctx.beginPath();
    ctx.moveTo(c - sh - 12, 214);
    ctx.bezierCurveTo(c - sh - 10, b.shoulderY + 30, c - sh - 12, top + 4, c - sh + 12, top);
    ctx.lineTo(c + sh - 12, top);
    ctx.bezierCurveTo(c + sh + 12, top + 4, c + sh + 10, b.shoulderY + 30, c + sh + 12, 214);
    ctx.closePath();
  };
  const g = ctx.createLinearGradient(0, top, 0, 214);
  g.addColorStop(0, mix(look.top, WHITE, 0.14));
  g.addColorStop(0.5, look.top);
  g.addColorStop(1, look.topShade);
  torso();
  fill(ctx, g);
  stroke(ctx, look.line, 1.1, "butt", 0.22);
  oval(ctx, c - sh - 6, b.shoulderY + 26, 14, 60);
  fill(ctx, look.topShade, 0.55);
  oval(ctx, c + sh - 8, b.shoulderY + 26, 14, 60);
  fill(ctx, look.topShade, 0.55);
  if (child) {
    arc(ctx, 10, 160, c - 16, top - 13, 32, 26);
    stroke(ctx, look.trim, 4.2);
  } else {
    ctx.beginPath();
    ctx.moveTo(c - 13, top + 0.5);
    ctx.lineTo(c, top + 13);
    ctx.lineTo(c + 13, top + 0.5);
    stroke(ctx, look.trim, 3);
  }
}

function drawNeck(ctx: Ctx, b: Build, look: Look, dx: number, dy: number) {
  const c = CENTER + dx;
  roundRect(ctx, c - b.neckWidth / 2, b.neckTop + dy, b.neckWidth, b.shoulderY - b.neckTop - 2, 5);
  fill(ctx, look.skin);
  oval(ctx, c - b.neckWidth / 2, b.neckTop + dy - 2, b.neckWidth, 9);
  fill(ctx, look.skinShade, 0.8);
}

function drawHead(ctx: Ctx, b: Build, look: Look, pose: Pose, child: boolean) {
  const f = pose.face;
  const hx = b.headX;
  const hy = b.headY;
  const fx = hx + pose.turn * 7;
  const fy = hy + pose.nod * 2.5;

  const earY = hy + (child ? 5 : 3);
  for (const s of [-1, 1]) {
    const ex = hx + s * (b.headRx - 1) - pose.turn * 3;
    oval(ctx, ex - 6.5, earY - 8.5, 13, 17);
    fill(ctx, look.skin);
    oval(ctx, ex - 3 + s * 0.5, earY - 5, 6, 10);
    fill(ctx, look.skinShade, 0.7);
  }
  const lx = hx - b.headRx * 0.28;
  const ly = hy - b.headRy * 0.32;
  const light = ctx.createRadialGradient(lx, ly, 0, lx, ly, b.headRy * 1.35);
  light.addColorStop(0, mix(look.skin, WHITE, 0.13));
  light.addColorStop(0.5, look.skin);
  light.addColorStop(1, mix(look.skin, look.skinShade, 0.7));
  oval(ctx, hx - b.headRx, hy - b.headRy, b.headRx * 2, b.headRy * 2);
  fill(ctx, light);
  stroke(ctx, look.line, 1.1, "butt", 0.28);

  drawHair(ctx, b, look, child, pose.turn);

  // Brows carry the question and worry.
  const browY = fy - (child ? 15 : 13) - f.browRaise * 5 + f.browFrown * 2;
  const browGap = child ? 7 : 6;
  const browLen = child ? 13 : 12;
  for (const s of [-1, 1]) {
    const inner: Pt = [fx + s * browGap, browY - f.browWorry * 4 + f.browFrown * 3.5];
    const outer: Pt = [fx + s * (browGap + browLen), browY + f.browWorry * 1.5 - f.browFrown + 1];
    const archH = (child ? 3.2 : 2.4) * (1 - f.browFrown);
    ctx.beginPath();
    ctx.moveTo(inner[0], inner[1]);
    ctx.quadraticCurveTo((inner[0] + outer[0]) / 2, Math.min(inner[1], outer[1]) - archH, outer[0], outer[1]);
    stroke(ctx, look.hair, child ? 3.4 : 3.2);
  }

  const open = Math.min(Math.max(f.eyeOpen * (1 - pose.gaze * 0.18), 0.05), 1.2);
  const eyeY = fy - 1 + pose.gaze * (child ? 2.2 : 0.6);
  for (const s of [-1, 1]) {
    const ex = fx + s * (child ? 15 : 13);
    if (f.eyeSmile > 0.55 || open < 0.18) {
      ctx.beginPath();
      ctx.moveTo(ex - 6, eyeY + 1);
      ctx.quadraticCurveTo(ex, eyeY - 6 * (open < 0.18 ? -0.3 : 1), ex + 6, eyeY + 1);
      stroke(ctx, look.iris, 2.6);
      continue;
    }
    const o = Math.min(open, 1);
    if (child) {
      const rx = 6.2;
      const ry = 7.6 * open;
      const iris = ctx.createRadialGradient(ex, eyeY + ry * 0.35, 0, ex, eyeY + ry * 0.35, rx * 1.3);
      iris.addColorStop(0, mix(look.iris, "#9A6A45", 0.55));
      iris.addColorStop(1, look.iris);
      oval(ctx, ex - rx, eyeY - ry, rx * 2, ry * 2);
      fill(ctx, iris);
      if (open > 0.5) {
        arc(ctx, 215, 110, ex - rx - 0.6, eyeY - ry - 0.8, rx * 2 + 1.2, ry * 2 + 1.2);
        stroke(ctx, look.hair, 1.2);
      }
      circle(ctx, 2.1 * o, [ex + 2.2, eyeY - 2.6 * open]);
      fill(ctx, WHITE);
      circle(ctx, o, [ex - 2.2, eyeY + 2.8 * open]);
      fill(ctx, WHITE);
    } else {
      const rx = 5.2;
      const ry = 4 * open;
      oval(ctx, ex - rx, eyeY - ry, rx * 2, ry * 2);
      fill(ctx, "#FBF7F2");
      const irisY = eyeY + pose.gaze * 1.2;
      circle(ctx, 2.9 * o, [ex, irisY]);
      fill(ctx, look.iris);
      circle(ctx, 0.9, [ex + 1, irisY - 1]);
      fill(ctx, WHITE);
      line(ctx, look.line, [ex - rx, eyeY - ry * 0.6], [ex + rx, eyeY - ry * 0.6], 1.2);
    }
    if (f.eyeSmile > 0.1) {
      oval(ctx, ex - 7, eyeY + 7.5 - f.eyeSmile * 6, 14, 8);
      fill(ctx, look.skin);
    }
  }

  if (child) {
    oval(ctx, fx - 31, fy + 8, 14, 8);
    fill(ctx, look.blush);
    oval(ctx, fx + 17, fy + 8, 14, 8);
    fill(ctx, look.blush);
  }
  const noseY = fy + (child ? 8 : 9);
  ctx.beginPath();
  ctx.moveTo(fx - 2.5, noseY + 2);
  ctx.quadraticCurveTo(fx, noseY + 4.5, fx + 2.5, noseY + 2);
  stroke(ctx, look.line, 1.6, "round", 0.7);
  if (!child) line(ctx, look.skinShade, [fx + 1, noseY - 9], [fx + 2, noseY + 1], 1.6);

  drawMouth(ctx, fx, fy + (child ? 19 : 20), f, look, child);
}

function drawHair(ctx: Ctx, b: Build, look: Look, child: boolean, turn: number) {
  const hx = b.headX + turn * 2;
  const top = b.headY - b.headRy;
  const rx = b.headRx;
  const y = b.headY;
  ctx.beginPath();
  if (child) {
    ctx.moveTo(hx - rx - 1, y + 4);
    ctx.bezierCurveTo(hx - rx - 6, top - 20, hx + rx + 6, top - 20, hx + rx + 1, y + 4);
    ctx.lineTo(hx + rx - 3, y - 12);
    ctx.quadraticCurveTo(hx + rx - 12, y - 10, hx + 14, y - 20);
    ctx.quadraticCurveTo(hx + 8, y - 13, hx - 2, y - 20);
    ctx.quadraticCurveTo(hx - 10, y - 13, hx - 17, y - 21);
    ctx.quadraticCurveTo(hx - rx + 10, y - 10, hx - rx + 3, y - 12);
    ctx.closePath();
  } else {
    ctx.moveTo(hx - rx - 1, y - 2);
    ctx.bezierCurveTo(hx - rx - 4, top - 15, hx + rx + 4, top - 15, hx + rx + 1, y - 2);
    ctx.lineTo(hx + rx - 2, y - 14);
    ctx.quadraticCurveTo(hx + 6, y - 26, hx - 6, y - 25);
    ctx.quadraticCurveTo(hx - rx + 6, y - 22, hx - rx + 2, y - 12);
    ctx.closePath();
  }
  const g = ctx.createLinearGradient(0, top - (child ? 20 : 15), 0, y);
  g.addColorStop(0, look.hairShine);
  g.addColorStop(0.5, look.hair);
  g.addColorStop(1, look.hair);
  fill(ctx, g);
  if (child) {
    ctx.beginPath();
    ctx.moveTo(hx - 2, top + 2);
    ctx.quadraticCurveTo(hx + 4, top - 11, hx + 12, top - 6);
    ctx.quadraticCurveTo(hx + 5, top - 4, hx + 4, top + 3);
    ctx.closePath();
    fill(ctx, look.hair);
    arc(ctx, 215, 50, hx - rx * 0.55, top + 5, rx * 0.9, 22);
    stroke(ctx, look.hairShine, 2.6);
  } else {
    line(ctx, look.hairShine, [hx - 8, top + 4], [hx - 16, y - 24], 1.6);
  }
}

function drawMouth(ctx: Ctx, x: number, y: number, f: Face, look: Look, child: boolean) {
  const w = child ? 8.5 : 7.5;
  if (f.mouthRound > 0.5) {
    oval(ctx, x - 3.6, y - 4, 7.2, 8.5);
    fill(ctx, look.lip);
    return;
  }
  const s = f.smile;
  const corner = y - s * 2.2;
  ctx.beginPath();
  if (f.mouthOpen > 0.08) {
    const open = f.mouthOpen * 9;
    ctx.moveTo(x - w, corner);
    ctx.quadraticCurveTo(x, y + s * 3 - 1, x + w, corner);
    ctx.quadraticCurveTo(x, y + s * 6.5 + open, x - w, corner);
    ctx.closePath();
    fill(ctx, look.lip);
    oval(ctx, x - w * 0.45, y + s * 2 + open * 0.45, w * 0.9, open * 0.45 + 1);
    fill(ctx, "#E58A86");
  } else {
    ctx.moveTo(x - w, corner);
    ctx.quadraticCurveTo(x, y + s * 6.5, x + w, corner);
    stroke(ctx, look.lip, 2.4);
  }
}

function drawArm(ctx: Ctx, b: Build, look: Look, h: HandPose, side: Side, shift: Pt) {
  const [sx0, sy0] = shoulder(b, side);
  const s: Pt = [sx0 + shift[0], sy0 + shift[1]];
  const e = solveElbow(s[0], s[1], h.x, h.y, b.upperArm, b.foreArm, side, h.elbowOut);
  const w: Pt = [h.x, h.y];
  const aw = b.armWidth;

  line(ctx, look.line, s, e, aw + 1.2, "round", 0.35);
  if (look.longSleeves) {
    line(ctx, look.top, s, e, aw + 1);
    const cuff = lerpPt(e, w, 0.78);
    line(ctx, look.skin, cuff, w, aw * 0.72);
    line(ctx, look.top, e, cuff, aw * 0.92);
    line(ctx, look.trim, lerpPt(e, w, 0.74), cuff, aw * 0.95, "butt");
  } else {
    line(ctx, look.skin, s, e, aw * 0.86);
    line(ctx, look.skin, e, w, aw * 0.78);
    const sleeveEnd = lerpPt(s, e, 0.55);
    line(ctx, look.top, s, sleeveEnd, aw + 2.5);
    line(ctx, look.topShade, lerpPt(s, e, 0.5), sleeveEnd, aw + 2.5, "butt", 0.5);
  }
  if (h.prop === "stick") {
    line(ctx, "#6B4423", [w[0] + 1, w[1] - 6], [w[0] + 4, 230], 5);
    line(ctx, "#8A5A33", [w[0] + 1, w[1] - 6], [w[0] + 4, 230], 2);
  }
  const angle = Number.isNaN(h.angle) ? (Math.atan2(w[0] - e[0], -(w[1] - e[1])) * 180) / Math.PI : h.angle;
  ctx.save();
  ctx.translate(w[0], w[1]);
  ctx.rotate(rad(angle));
  ctx.scale(b.handScale, b.handScale);
  drawHand(ctx, look, h, side);
  ctx.restore();
}

interface Seg {
  a: Pt;
  b: Pt;
  width: number;
  behind: boolean;
  shade: number;
}

function drawHand(ctx: Ctx, look: Look, h: HandPose, side: Side) {
  const roll = Math.min(Math.max(h.roll, -1), 1);
  const shape = h.shape;
  const c = curls(shape);
  const lengths = [9.2, 10.2, 9.6, 7.7];
  if (Math.abs(roll) >= 0.35) {
    const palmFacing = roll > 0;
    const thumbSide = palmFacing ? -side : side;
    const wf = lerp(0.6, 1, Math.min(Math.max((Math.abs(roll) - 0.35) / 0.65, 0), 1));
    const pw = 13 * wf;
    const ph = 12;
    const widths = [3.3, 3.4, 3.2, 2.8];
    const fingers: Seg[][] = [];
    for (let k = 0; k < 4; k++) {
      const bx = thumbSide * (pw / 2 - 1.8 - (k * (pw - 3.6)) / 3);
      const dir = rad(shape.spread * (k - 1.5) * -9 * thumbSide);
      fingers.push(frontalChain([bx, -ph + 1.5], dir, lengths[k], widths[k] * (0.82 + 0.18 * wf), c[k + 1], palmFacing));
    }
    const thumbDir = rad(thumbSide * (52 - shape.thumbAcross * 112));
    const thumb = frontalChain([thumbSide * (pw / 2 - 0.5), -3], thumbDir, 8.8, 3.7, c[0] * 0.6, palmFacing, true);
    const thumbInFront = palmFacing && shape.thumbAcross > 0.5;

    if (!thumbInFront) drawChain(ctx, look, thumb);
    fingers.forEach((segs) =>
      drawChain(
        ctx,
        look,
        segs.filter((s) => s.behind),
      ),
    );
    roundRect(ctx, -pw / 2 - 0.7, -ph - 0.7, pw + 1.4, ph + 1.4, 4.5);
    fill(ctx, look.line, 0.55);
    roundRect(ctx, -pw / 2, -ph, pw, ph, 4);
    fill(ctx, palmFacing ? mix(look.skin, WHITE, 0.08) : look.skin);
    if (palmFacing) {
      arc(ctx, 200, 110, -pw * 0.35, -ph * 0.75, pw * 0.7, ph * 0.9);
      stroke(ctx, look.skinShade, 0.8, "butt", 0.7);
    }
    [...fingers].reverse().forEach((segs) =>
      drawChain(
        ctx,
        look,
        segs.filter((s) => !s.behind),
      ),
    );
    if (!palmFacing) {
      for (let k = 0; k < 4; k++) {
        if (c[k + 1] > 0.45) {
          const bx = thumbSide * (pw / 2 - 1.8 - (k * (pw - 3.6)) / 3);
          circle(ctx, 1.9, [bx, -ph + 1]);
          fill(ctx, look.skin);
          stroke(ctx, look.line, 0.5, "butt", 0.5);
        }
      }
    }
    if (thumbInFront) drawChain(ctx, look, thumb);
  } else {
    // Side view: the palm faces the body's midline unless rotated.
    const palmSide = -side;
    const nearThumb = roll >= 0;
    const ph = 12;
    roundRect(ctx, -3.9, -ph - 0.7, 7.8, ph + 1.4, 3.5);
    fill(ctx, look.line, 0.55);
    roundRect(ctx, -3.2, -ph, 6.4, ph, 3);
    fill(ctx, look.skin);
    const order = nearThumb ? [3, 2, 1, 0] : [0, 1, 2, 3];
    const thumbSegs = sideChain([palmSide * 2.4, -3.5], rad(palmSide * (28 + shape.thumbAcross * 30)), 8.6, 3.5, c[0], palmSide, 0.5, 0);
    if (!nearThumb) drawChain(ctx, look, thumbSegs);
    order.forEach((k, depth) => {
      const dir = rad(shape.spread * (k - 1.5) * 4);
      const base: Pt = [palmSide * (0.6 - k * 0.35) * (nearThumb ? 1 : -1), -ph + 1.2 + k * 0.8];
      drawChain(ctx, look, sideChain(base, dir, lengths[k], 3.2, c[k + 1], palmSide, 1, (3 - depth) * 0.12));
    });
    if (nearThumb) drawChain(ctx, look, thumbSegs);
  }
}

function frontalChain(base: Pt, dir: number, length: number, width: number, curl: number, palmFacing: boolean, thumb = false): Seg[] {
  const fractions = thumb ? [0.55, 0.45] : [0.46, 0.3, 0.24];
  const bends = thumb ? [40, 80] : [85, 100, 72];
  const ux = Math.sin(dir);
  const uy = -Math.cos(dir);
  let p = base;
  let phi = 0;
  return fractions.map((fr, j) => {
    phi += bends[j] * curl;
    const len = length * fr * Math.cos(rad(phi));
    const next: Pt = [p[0] + ux * len, p[1] + uy * len];
    // Folded segments face away from a back-of-hand view, so they go behind the palm.
    const seg = { a: p, b: next, width: width * (1 - j * 0.07), behind: !palmFacing && phi > 95 && !thumb, shade: phi > 95 ? 0.18 : 0 };
    p = next;
    return seg;
  });
}

function sideChain(
  base: Pt,
  dir: number,
  length: number,
  width: number,
  curl: number,
  palmSide: number,
  scale: number,
  shade: number,
): Seg[] {
  const fractions = [0.46, 0.3, 0.24];
  const bends = [80, 95, 70];
  let p = base;
  let a = dir;
  return fractions.map((fr, j) => {
    a += rad(palmSide * bends[j] * curl);
    const len = length * fr * scale;
    const next: Pt = [p[0] + Math.sin(a) * len, p[1] - Math.cos(a) * len];
    const seg = { a: p, b: next, width: width * (1 - j * 0.07), behind: false, shade };
    p = next;
    return seg;
  });
}

function drawChain(ctx: Ctx, look: Look, segs: Seg[]) {
  for (const s of segs) line(ctx, look.line, s.a, s.b, s.width + 1.3, "round", 0.6);
  for (const s of segs) {
    const color = s.shade > 0 ? mix(look.skin, look.skinShade, Math.min(s.shade, 1) * 2.5) : look.skin;
    line(ctx, color, s.a, s.b, s.width);
  }
}
