// Plays 48-point clips (6 arm joints, then 21 left-hand and 21 right-hand points, x y z each) on a VRM.
// Kinematics from the SignSetu avatar module by Lilesh Lahre.
import * as THREE from "three";
import type { VRM, VRMHumanBoneName } from "@pixiv/three-vrm";

const LEFT_REST = new THREE.Vector3(1, 0, 0);
const RIGHT_REST = new THREE.Vector3(-1, 0, 0);
/** The shipped model's eyes above its shoulder line, in this solver's 0.39-wide shoulder units. */
const EYE_LEVEL = 0.29;

// Extracts 3D keypoint and negates Z so +Z points forward
function getVec3(coords: number[], idx: number): THREE.Vector3 {
  const i = idx * 3;
  return new THREE.Vector3(coords[i] || 0, coords[i + 1] || 0, -(coords[i + 2] || 0));
}

/**
 * Self-normalizing upper-body calibration:
 * - Centers any JSON coordinate space around the shoulder midpoint and normalizes by shoulder width.
 * - Adapts automatically whether a JSON file uses +Z or -Z for forward reach.
 * - Prevents face covering, shirt clipping, and hand-to-hand overlapping.
 */
export function getCalibratedArmTargets(coords: number[]) {
  const p0 = getVec3(coords, 0);
  const p1 = getVec3(coords, 1);

  // Auto-center around shoulder midpoint and normalize scale to standard 0.39m shoulder width
  const midShoulder = new THREE.Vector3().addVectors(p0, p1).multiplyScalar(0.5);
  const detectedWidth = p0.distanceTo(p1);
  const bodyScale = detectedWidth > 1e-4 ? 0.39 / detectedWidth : 1.0;

  function getNormalizedBodyPt(idx: number) {
    return getVec3(coords, idx).sub(midShoulder).multiplyScalar(bodyScale);
  }

  const rawLShoulder = getNormalizedBodyPt(0);
  const rawRShoulder = getNormalizedBodyPt(1);
  const rawLElbow = getNormalizedBodyPt(2);
  const rawRElbow = getNormalizedBodyPt(3);
  const rawLWrist = getNormalizedBodyPt(4);
  const rawRWrist = getNormalizedBodyPt(5);

  rawLShoulder.z = 0;
  rawRShoulder.z = 0;

  // Use Math.abs(z) so forward reach is always +Z regardless of the JSON's Z sign convention
  const lReachZ = Math.abs(rawLWrist.z);
  const rReachZ = Math.abs(rawRWrist.z);

  const lActive = THREE.MathUtils.smoothstep(rawLWrist.y, -0.74, -0.45);
  const rActive = THREE.MathUtils.smoothstep(rawRWrist.y, -0.74, -0.45);

  const lShoulder = rawLShoulder.clone();
  const rShoulder = rawRShoulder.clone();
  const lElbow = rawLElbow.clone();
  const rElbow = rawRElbow.clone();
  const lWrist = rawLWrist.clone();
  const rWrist = rawRWrist.clone();

  const lInward = THREE.MathUtils.clamp((0.22 - lWrist.x) / 0.3, 0, 1) * lActive;
  const rInward = THREE.MathUtils.clamp((rWrist.x - -0.22) / 0.3, 0, 1) * rActive;
  const bothCrossing = lActive * rActive * Math.max(lInward, rInward);

  // 1. Elbows outside ribcage and pitched forward when signing
  lElbow.x = Math.max(0.26, lElbow.x + 0.04);
  rElbow.x = Math.min(-0.26, rElbow.x - 0.04);
  lElbow.z = 0.06 + lActive * 0.22 + lInward * 0.1;
  rElbow.z = 0.06 + rActive * 0.25 + rInward * 0.14 + bothCrossing * 0.05;

  // 2. Horizontal Wrist X
  const lIdleWristX = lElbow.x + 0.05;
  const rIdleWristX = rElbow.x - 0.05;
  const lSignWristX = Math.max(-0.08, lWrist.x + 0.07);
  const rSignWristX = Math.min(0.08, rWrist.x - 0.07);

  lWrist.x = THREE.MathUtils.lerp(lIdleWristX, lSignWristX, lActive);
  rWrist.x = THREE.MathUtils.lerp(rIdleWristX, rSignWristX, rActive);

  // 3. Vertical Wrist Y (compensates for the VRM's long forearm, so a raised hand lands where the signer's did)
  const lForearmDy = lWrist.y - lElbow.y;
  const rForearmDy = rWrist.y - rElbow.y;
  if (lForearmDy > 0) lWrist.y = lElbow.y + lForearmDy * 0.58;
  if (rForearmDy > 0) rWrist.y = rElbow.y + rForearmDy * 0.58;

  // Chin and face signs need the hand up to eye level (0.29 above the shoulders here), never above it.
  lWrist.y = Math.min(EYE_LEVEL, lWrist.y);
  rWrist.y = Math.min(EYE_LEVEL, rWrist.y);

  // 4. Forward Wrist Z
  const lIdleZ = lElbow.z + 0.07;
  const rIdleZ = rElbow.z + 0.07;
  // A forearm raised toward the face folds up instead of reaching forward; tracked wrist depth overstates reach.
  const lRise = THREE.MathUtils.clamp(lForearmDy / 0.3, 0, 1);
  const rRise = THREE.MathUtils.clamp(rForearmDy / 0.3, 0, 1);
  const lSignZ = lElbow.z + THREE.MathUtils.lerp(Math.max(0.26, lReachZ * 0.38), 0.1, lRise) + lInward * 0.08;
  const rSignZ = rElbow.z + THREE.MathUtils.lerp(Math.max(0.3, rReachZ * 0.38), 0.1, rRise) + rInward * 0.12 + bothCrossing * 0.07;

  lWrist.z = THREE.MathUtils.lerp(lIdleZ, lSignZ, lActive);
  rWrist.z = THREE.MathUtils.lerp(rIdleZ, rSignZ, rActive);

  // 5. Hand-to-Hand Separation Envelope
  if (lActive > 0.25 && rActive > 0.25) {
    const xGap = lWrist.x - rWrist.x;
    const minXGap = 0.15;
    if (xGap < minXGap) {
      const push = (minXGap - xGap) * 0.5;
      lWrist.x += push;
      rWrist.x -= push;
    }
    if (xGap < 0.22 && rWrist.z < lWrist.z + 0.07) {
      rWrist.z = lWrist.z + 0.07;
    }
  }

  return { avatar: { lShoulder, rShoulder, lElbow, rElbow, lWrist, rWrist, lActive, rActive } };
}

// --- 5. Direct Quaternion Chain & 3-Axis Hand/Finger Kinematics ---
const _vDir = new THREE.Vector3();
const _qWorld = new THREE.Quaternion();
const _qLocal = new THREE.Quaternion();
const _qParentInv = new THREE.Quaternion();

function solveLimbBone(
  vrm: VRM,
  boneName: VRMHumanBoneName,
  posA: THREE.Vector3,
  posB: THREE.Vector3,
  restDir: THREE.Vector3,
  parentWorldQuat: THREE.Quaternion,
  slerpAlpha: number,
): THREE.Quaternion {
  const boneNode = vrm.humanoid.getNormalizedBoneNode(boneName);
  _vDir.subVectors(posB, posA).normalize();
  _qWorld.setFromUnitVectors(restDir, _vDir);

  if (boneNode) {
    _qParentInv.copy(parentWorldQuat).invert();
    _qLocal.multiplyQuaternions(_qParentInv, _qWorld);
    boneNode.quaternion.slerp(_qLocal, slerpAlpha);
  }

  const currentWorldQuat = new THREE.Quaternion();
  if (boneNode) {
    currentWorldQuat.multiplyQuaternions(parentWorldQuat, boneNode.quaternion);
  } else {
    currentWorldQuat.copy(_qWorld);
  }
  return currentWorldQuat;
}

function solveHandAndFingers(
  vrm: VRM,
  side: "left" | "right",
  coords: number[],
  baseIdx: number,
  lowerArmWorldQuat: THREE.Quaternion,
  activeWeight: number,
  slerpAlpha: number,
) {
  const isLeft = side === "left";
  const curlSign = isLeft ? -1 : 1;
  const handNode = vrm.humanoid.getNormalizedBoneNode(`${side}Hand` as VRMHumanBoneName);
  if (!handNode) return;

  // Check if hand keypoints exist in this JSON frame
  if (coords.length < (baseIdx + 21) * 3) return;

  const wrist = getVec3(coords, baseIdx + 0);
  const thumbCMC = getVec3(coords, baseIdx + 1);
  const indexMCP = getVec3(coords, baseIdx + 5);
  const middleMCP = getVec3(coords, baseIdx + 9);
  const pinkyMCP = getVec3(coords, baseIdx + 17);

  const uPalm = new THREE.Vector3().subVectors(middleMCP, wrist);
  if (uPalm.lengthSq() < 1e-8) return;
  uPalm.normalize();

  const vAcross = new THREE.Vector3().subVectors(indexMCP, pinkyMCP).normalize();

  const xAxis = new THREE.Vector3();
  const yAxis = new THREE.Vector3();
  const zAxis = new THREE.Vector3();

  if (isLeft) {
    xAxis.copy(uPalm);
    yAxis.crossVectors(vAcross, xAxis).normalize();
    zAxis.crossVectors(xAxis, yAxis).normalize();
  } else {
    xAxis.copy(uPalm).negate();
    yAxis.crossVectors(vAcross, xAxis).normalize();
    zAxis.crossVectors(xAxis, yAxis).normalize();
  }

  const palmBasisMat = new THREE.Matrix4().makeBasis(xAxis, yAxis, zAxis);
  const qHandWorld = new THREE.Quaternion().setFromRotationMatrix(palmBasisMat);

  _qParentInv.copy(lowerArmWorldQuat).invert();
  const qHandSignLocal = new THREE.Quaternion().multiplyQuaternions(_qParentInv, qHandWorld);

  const maxWristRad = THREE.MathUtils.degToRad(50);
  const wristAngle = 2 * Math.acos(Math.min(1, Math.abs(qHandSignLocal.w)));
  if (wristAngle > maxWristRad) {
    qHandSignLocal.slerp(new THREE.Quaternion(), 1 - maxWristRad / wristAngle);
  }

  const qHandIdleLocal = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, isLeft ? -0.15 : 0.15, curlSign * 0.12));
  const qHandFinalLocal = qHandIdleLocal.slerp(qHandSignLocal, activeWeight);
  handNode.quaternion.slerp(qHandFinalLocal, slerpAlpha);

  const fingers = [
    { name: "Index", ids: [5, 6, 7, 8], baseSplay: 0.04 },
    { name: "Middle", ids: [9, 10, 11, 12], baseSplay: 0.0 },
    { name: "Ring", ids: [13, 14, 15, 16], baseSplay: -0.03 },
    { name: "Little", ids: [17, 18, 19, 20], baseSplay: -0.06 },
  ];

  const s0 = new THREE.Vector3();
  const s1 = new THREE.Vector3();
  const s2 = new THREE.Vector3();
  const s3 = new THREE.Vector3();
  const euler = new THREE.Euler();
  const qFinger = new THREE.Quaternion();

  for (const f of fingers) {
    const pMCP = getVec3(coords, baseIdx + f.ids[0]);
    const pPIP = getVec3(coords, baseIdx + f.ids[1]);
    const pDIP = getVec3(coords, baseIdx + f.ids[2]);
    const pTIP = getVec3(coords, baseIdx + f.ids[3]);

    s0.subVectors(pMCP, wrist).normalize();
    s1.subVectors(pPIP, pMCP).normalize();
    s2.subVectors(pDIP, pPIP).normalize();
    s3.subVectors(pTIP, pDIP).normalize();

    const rawA1 = s0.angleTo(s1);
    const rawA2 = s1.angleTo(s2);
    const rawA3 = s2.angleTo(s3);

    const signCurl1 = Math.min(1.5, rawA1 + rawA2 * 0.35);
    const signCurl2 = Math.min(1.65, rawA2 * 0.95);
    const signCurl3 = Math.min(1.45, rawA3 + rawA2 * 0.25);

    const lateralDot = s1.dot(zAxis) - s0.dot(zAxis);
    const splayAngle = THREE.MathUtils.clamp(lateralDot * 0.8 + f.baseSplay, -0.25, 0.25);

    const c1 = THREE.MathUtils.lerp(0.2, signCurl1, activeWeight);
    const c2 = THREE.MathUtils.lerp(0.25, signCurl2, activeWeight);
    const c3 = THREE.MathUtils.lerp(0.18, signCurl3, activeWeight);
    const sp = THREE.MathUtils.lerp(f.baseSplay, splayAngle, activeWeight);

    const nProx = vrm.humanoid.getNormalizedBoneNode(`${side}${f.name}Proximal` as VRMHumanBoneName);
    const nInt = vrm.humanoid.getNormalizedBoneNode(`${side}${f.name}Intermediate` as VRMHumanBoneName);
    const nDist = vrm.humanoid.getNormalizedBoneNode(`${side}${f.name}Distal` as VRMHumanBoneName);

    if (nProx) {
      euler.set(0, isLeft ? -sp : sp, curlSign * c1, "YZX");
      nProx.quaternion.slerp(qFinger.setFromEuler(euler), slerpAlpha);
    }
    if (nInt) {
      euler.set(0, 0, curlSign * c2);
      nInt.quaternion.slerp(qFinger.setFromEuler(euler), slerpAlpha);
    }
    if (nDist) {
      euler.set(0, 0, curlSign * c3);
      nDist.quaternion.slerp(qFinger.setFromEuler(euler), slerpAlpha);
    }
  }

  // Thumb Kinematics
  const tMCP = getVec3(coords, baseIdx + 2);
  const tIP = getVec3(coords, baseIdx + 3);
  const tTIP = getVec3(coords, baseIdx + 4);

  s0.subVectors(tMCP, thumbCMC).normalize();
  s1.subVectors(tIP, tMCP).normalize();
  s2.subVectors(tTIP, tIP).normalize();

  const tBend1 = THREE.MathUtils.lerp(0.15, Math.min(1.1, s0.angleTo(s1)), activeWeight);
  const tBend2 = THREE.MathUtils.lerp(0.15, Math.min(1.2, s1.angleTo(s2)), activeWeight);

  const palmWidth = indexMCP.distanceTo(pinkyMCP) || 0.08;
  const thumbToIndexDist = tTIP.distanceTo(indexMCP) / palmWidth;
  const opposition = THREE.MathUtils.lerp(0.1, THREE.MathUtils.clamp(1.5 - thumbToIndexDist, 0, 1.2) * 0.45, activeWeight);

  const nThumbMeta = vrm.humanoid.getNormalizedBoneNode(`${side}ThumbMetacarpal` as VRMHumanBoneName);
  const nThumbProx = vrm.humanoid.getNormalizedBoneNode(`${side}ThumbProximal` as VRMHumanBoneName);
  const nThumbDist = vrm.humanoid.getNormalizedBoneNode(`${side}ThumbDistal` as VRMHumanBoneName);

  if (nThumbMeta) {
    euler.set(0, -curlSign * opposition, curlSign * tBend1 * 0.4);
    nThumbMeta.quaternion.slerp(qFinger.setFromEuler(euler), slerpAlpha);
  }
  if (nThumbProx) {
    euler.set(0, -curlSign * tBend1 * 0.7, curlSign * tBend1 * 0.3);
    nThumbProx.quaternion.slerp(qFinger.setFromEuler(euler), slerpAlpha);
  }
  if (nThumbDist) {
    euler.set(0, -curlSign * tBend2 * 0.85, 0);
    nThumbDist.quaternion.slerp(qFinger.setFromEuler(euler), slerpAlpha);
  }
}

const IDENTITY_QUAT = new THREE.Quaternion();

/** Poses [vrm] from one clip frame; [slerpAlpha] 1 snaps, lower values smooth toward the frame. */
export function applyClipFrame(vrm: VRM, coords: number[], slerpAlpha: number) {
  if (coords.length < 18) return;
  const { avatar } = getCalibratedArmTargets(coords);
  const qLeftUpperWorld = solveLimbBone(vrm, "leftUpperArm", avatar.lShoulder, avatar.lElbow, LEFT_REST, IDENTITY_QUAT, slerpAlpha);
  const qLeftLowerWorld = solveLimbBone(vrm, "leftLowerArm", avatar.lElbow, avatar.lWrist, LEFT_REST, qLeftUpperWorld, slerpAlpha);
  const qRightUpperWorld = solveLimbBone(vrm, "rightUpperArm", avatar.rShoulder, avatar.rElbow, RIGHT_REST, IDENTITY_QUAT, slerpAlpha);
  const qRightLowerWorld = solveLimbBone(vrm, "rightLowerArm", avatar.rElbow, avatar.rWrist, RIGHT_REST, qRightUpperWorld, slerpAlpha);
  solveHandAndFingers(vrm, "left", coords, 6, qLeftLowerWorld, avatar.lActive, slerpAlpha);
  solveHandAndFingers(vrm, "right", coords, 27, qRightLowerWorld, avatar.rActive, slerpAlpha);
}
