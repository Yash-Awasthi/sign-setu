// Renders the VRM character and plays content gestures or 48-point clips on it.
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { VRMLoaderPlugin, type VRM, type VRMHumanBoneName } from "@pixiv/three-vrm";
import { applyClipFrame } from "./clip";
import { arm, expressions, fingerAngles, handTurn, solveArm, type Body } from "./retarget";
import { FACES, L, R, Timeline, blinkAt, glanceAt, livePose, type Gesture, type Pose, type QuestionKind, type Side } from "./rig";

export type View = "body" | "head";

export interface Play {
  keys: Gesture["keys"];
  speed?: number;
  /** Holds the pose at this many seconds instead of playing. */
  at?: number | null;
  idle?: boolean;
  glance?: boolean;
  question?: QuestionKind | null;
  token?: number;
}

export interface Clip {
  frames: number[][];
  fps?: number;
  token?: number;
}

const armRest = (side: Side) => new THREE.Vector3(side, 0, 0);
/** Smoothing the clip solver uses between frames. */
const CLIP_SLERP = 0.65;

export class AvatarStage {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(30, 1, 0.1, 20);
  private readonly clock = new THREE.Clock();
  private vrm: VRM | null = null;
  private body: MeasuredBody | null = null;
  private view: View = "body";
  private timeline = new Timeline(null);
  private request: Play = { keys: [] };
  private clipData: Clip | null = null;
  private started = 0;
  private finishedToken: number | undefined;
  private frame = 0;
  /** Last elbows in rest space, per side. */
  private readonly elbows = new Map<Side, THREE.Vector3>();

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly onFinished: (token: number) => void = () => {},
  ) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    const light = new THREE.DirectionalLight(0xffffff, 1.8);
    light.position.set(1, 2, 2);
    this.scene.add(light, new THREE.AmbientLight(0xffffff, 0.9));
  }

  async load(url: string) {
    const loader = new GLTFLoader();
    loader.register((parser) => new VRMLoaderPlugin(parser));
    const vrm = (await loader.loadAsync(url)).userData.vrm as VRM;
    // Flatter bust depth gives the arms clearance across the chest.
    for (const name of ["J_Sec_L_Bust1", "J_Sec_R_Bust1"]) vrm.scene.getObjectByName(name)?.scale.set(0.7, 0.7, 0.45);
    vrm.scene.traverse((o) => (o.frustumCulled = false));
    this.scene.add(vrm.scene);
    this.vrm = vrm;
    this.body = measure(vrm);
    this.frameView();
    this.wake();
  }

  play(p: Play) {
    this.request = p;
    this.clipData = null;
    this.timeline = new Timeline({ id: "", keys: p.keys });
    this.begin(p.token);
  }

  clip(c: Clip) {
    this.clipData = c;
    this.begin(c.token);
  }

  setView(v: View) {
    this.view = v;
    this.frameView();
    this.wake();
  }

  resize(width: number, height: number) {
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / Math.max(height, 1);
    this.frameView();
    this.wake();
  }

  private begin(token: number | undefined) {
    this.elbows.clear();
    this.started = performance.now();
    this.finishedToken = token;
    this.wake();
  }

  private elapsed = () => (performance.now() - this.started) / 1000;

  private duration(): number {
    if (this.clipData) return this.clipData.frames.length / (this.clipData.fps ?? 30);
    return this.timeline.duration / (this.request.speed ?? 1);
  }

  private moving = () => (this.clipData !== null || this.request.at == null) && this.elapsed() < this.duration();

  private wake() {
    if (!this.frame) this.frame = requestAnimationFrame(this.tick);
  }

  private readonly tick = () => {
    this.frame = 0;
    const vrm = this.vrm;
    if (!vrm || !this.body) return;
    const delta = this.clock.getDelta();
    const idle = (this.request.idle ?? true) && !this.clipData;
    const now = performance.now() / 1000;
    if (this.clipData) {
      const c = this.clipData;
      const i = Math.min(c.frames.length - 1, Math.floor(this.elapsed() * (c.fps ?? 30)));
      if (i >= 0) applyClipFrame(vrm, c.frames[i], CLIP_SLERP);
      setFace(vrm, null, blinkAt(now));
    } else {
      const p = this.request;
      const seconds = p.at ?? this.elapsed() * (p.speed ?? 1);
      const gaze =
        p.glance && p.at == null && seconds >= this.timeline.duration ? Math.min(1, (seconds - this.timeline.duration - 0.3) / 0.5) : 0;
      const still = livePose(this.timeline, seconds, p.question ?? null, Math.max(0, gaze), this.moving());
      this.pose(vrm, this.body, still, idle ? now : 0, idle || this.moving() ? blinkAt(now) : 1);
    }
    vrm.update(delta);
    this.renderer.render(this.scene, this.camera);
    if (this.moving()) return this.wake();
    const clipEnded = this.clipData !== null;
    this.clipData = null;
    if (this.finishedToken !== undefined && (clipEnded || this.request.at == null)) {
      const token = this.finishedToken;
      this.finishedToken = undefined;
      this.onFinished(token);
    }
    // Idle breathing and blinking keep drawing, and a finished clip goes back to the pose; a still pose draws once.
    if (clipEnded || idle || this.request.glance) this.wake();
  };

  private pose(vrm: VRM, body: MeasuredBody, still: Pose, time: number, blink: number) {
    const p = {
      ...still,
      lean: still.lean + Math.sin((time * 2 * Math.PI) / 6.5) * 0.06,
      tilt: still.tilt + Math.sin((time * 2 * Math.PI) / 8.3) * 1.4,
      turn: still.turn + glanceAt(time),
    };
    const bone = (name: string) => vrm.humanoid.getNormalizedBoneNode(name as VRMHumanBoneName);
    const shiver = p.shiver > 0 ? Math.sin(time * 55) * p.shiver * 0.02 : 0;
    bone("spine")?.rotation.set(p.hunch * 0.25, shiver, -p.lean * 0.25);
    bone("chest")?.rotation.set(Math.sin((time * 2 * Math.PI) / 3.8) * 0.015, 0, 0);
    bone("leftShoulder")?.rotation.set(0, 0, p.shrug * 0.25);
    bone("rightShoulder")?.rotation.set(0, 0, -p.shrug * 0.25);
    const nod = p.nod * 0.35;
    const turn = p.turn * 0.45;
    const tilt = -THREE.MathUtils.degToRad(p.tilt);
    bone("neck")?.rotation.set(nod * 0.4, turn * 0.4, tilt * 0.4);
    bone("head")?.rotation.set(nod * 0.6, turn * 0.6, tilt * 0.6);

    // Targets are placed at rest, so they follow the chest as it leans.
    const chest = bone("upperChest") ?? bone("chest")!;
    chest.updateWorldMatrix(true, false);
    const follow = chest.matrixWorld.clone().multiply(body.chestRestInverse);
    const turnChest = new THREE.Quaternion().setFromRotationMatrix(follow);
    for (const side of [R, L] as Side[]) {
      const prefix = side === R ? "right" : "left";
      const upper = bone(`${prefix}UpperArm`)!;
      const lower = bone(`${prefix}LowerArm`)!;
      const hand = bone(`${prefix}Hand`)!;
      const t = arm(p, side, body, this.elbows.get(side));
      const s = upper.getWorldPosition(new THREE.Vector3());
      const { elbow, wrist } = solveArm(s, t.wrist.applyMatrix4(follow), t.pole.applyMatrix4(follow), body.upper, body.fore);
      this.elbows.set(side, elbow.clone().applyMatrix4(follow.clone().invert()));
      const upperWorld = aim(upper, armRest(side), elbow.clone().sub(s));
      const lowerWorld = aim(lower, armRest(side), wrist.clone().sub(elbow), upperWorld);
      const h = side === R ? p.r : p.l;
      const turn = handTurn(h, side, wrist.clone().sub(elbow).applyQuaternion(turnChest.clone().invert()));
      hand.quaternion.copy(lowerWorld.invert().multiply(turnChest.clone().multiply(turn)));
      for (const [name, [x, y, z]] of Object.entries(fingerAngles(h.shape, side))) {
        bone(`${prefix}${name}`)?.rotation.set(x, y, z, name.endsWith("Proximal") && !name.startsWith("Thumb") ? "YZX" : "XYZ");
      }
    }
    setFace(vrm, p, blink);
  }

  private frameView() {
    const b = this.body;
    if (!b) return;
    const mid = b.right.clone().add(b.left).multiplyScalar(0.5);
    const [top, bottom] = this.view === "head" ? [b.eyeY + 0.16, b.head.y - 0.12] : [b.eyeY + 0.2, b.hipsY - 0.12];
    const width = this.view === "head" ? 0.4 : b.right.distanceTo(b.left) * 2.6;
    const half = Math.tan(THREE.MathUtils.degToRad(this.camera.fov / 2));
    const distance = Math.max((top - bottom) / 2 / half, width / 2 / half / this.camera.aspect);
    const y = (top + bottom) / 2;
    this.camera.position.set(mid.x, y, mid.z + distance);
    this.camera.lookAt(mid.x, y, mid.z);
    this.camera.updateProjectionMatrix();
  }
}

/** Turns [node] so its rest direction points along [dir] in the world; returns its new world turn. */
function aim(node: THREE.Object3D, rest: THREE.Vector3, dir: THREE.Vector3, parentWorld?: THREE.Quaternion): THREE.Quaternion {
  const parent = parentWorld ?? node.parent!.getWorldQuaternion(new THREE.Quaternion());
  const world = new THREE.Quaternion().setFromUnitVectors(rest, dir.normalize());
  node.quaternion.copy(parent.clone().invert().multiply(world));
  return world;
}

/** Face weights from a rig face, or a neutral face that only blinks when [p] is null. */
function setFace(vrm: VRM, p: Pose | null, blink: number) {
  const m = vrm.expressionManager;
  if (!m) return;
  for (const [k, v] of Object.entries(expressions(p?.face ?? FACES.neutral, blink))) m.setValue(k, p ? v : k === "blink" ? v : 0);
}

type MeasuredBody = Body & { chestRestInverse: THREE.Matrix4 };

function measure(vrm: VRM): MeasuredBody {
  vrm.scene.updateMatrixWorld(true);
  const at = (name: string) => vrm.humanoid.getNormalizedBoneNode(name as VRMHumanBoneName)?.getWorldPosition(new THREE.Vector3());
  const right = at("rightUpperArm")!;
  const left = at("leftUpperArm")!;
  const chest = vrm.humanoid.getNormalizedBoneNode("upperChest") ?? vrm.humanoid.getNormalizedBoneNode("chest")!;
  return {
    right,
    left,
    head: at("head")!,
    eyeY: (at("rightEye") ?? at("head")!.add(new THREE.Vector3(0, 0.06, 0))).y,
    hipsY: at("hips")!.y,
    upper: right.distanceTo(at("rightLowerArm")!),
    fore: at("rightLowerArm")!.distanceTo(at("rightHand")!),
    chestRestInverse: chest.matrixWorld.clone().invert(),
  };
}
