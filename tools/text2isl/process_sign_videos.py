import argparse
import sys
from pathlib import Path
import cv2
import numpy as np
import mediapipe as mp

from mediapipe.tasks.python import BaseOptions
from mediapipe.tasks.python.vision import (
    FaceLandmarker, FaceLandmarkerOptions,
    PoseLandmarker, PoseLandmarkerOptions,
    HandLandmarker, HandLandmarkerOptions,
    RunningMode
)

FEATURE_DIM = 196  # 52 Face Blendshapes + 144 Normalized 3D Coordinates (48 points x 3)


def get_landmark_xyz(landmarks, index):
    """Safely extracts 3D landmark coordinates, keeping Z forward (+Z is front)."""
    if landmarks is None or index >= len(landmarks):
        return None
    lm = landmarks[index]
    return [float(lm.x), -float(lm.y), float(lm.z)]


def extract_normalized_keypoints(pose_landmarks, left_hand_landmarks, right_hand_landmarks):
    l_sh = get_landmark_xyz(pose_landmarks, 11)
    r_sh = get_landmark_xyz(pose_landmarks, 12)

    if l_sh is not None and r_sh is not None:
        root_center = np.array(
            [(l_sh[0] + r_sh[0]) / 2.0, (l_sh[1] + r_sh[1]) / 2.0, (l_sh[2] + r_sh[2]) / 2.0],
            dtype=np.float32
        )
        shoulder_dist = float(np.linalg.norm(np.array(l_sh) - np.array(r_sh)))
        scale = (shoulder_dist * 2.5) if shoulder_dist > 0.05 else 0.75
    else:
        root_center = np.array([0.5, -0.5, 0.0], dtype=np.float32)
        scale = 0.75

    keypoints = []

    # 1. Body joints
    body_indices = [11, 12, 13, 14, 15, 16]
    for idx in body_indices:
        pt = get_landmark_xyz(pose_landmarks, idx)
        if pt is None:
            keypoints.extend([0.0, 0.0, 0.0])
        else:
            norm_pt = (np.array(pt, dtype=np.float32) - root_center) / scale

            if idx in [13, 14]:
                norm_pt[2] = max(0.0, norm_pt[2])

            norm_pt = np.clip(norm_pt, -1.5, 1.5)
            keypoints.extend(norm_pt.tolist())

    # 2. Left Hand
    for idx in range(21):
        pt = get_landmark_xyz(left_hand_landmarks, idx)
        if pt is None:
            keypoints.extend([0.0, 0.0, 0.0])
        else:
            norm_pt = (np.array(pt, dtype=np.float32) - root_center) / scale
            norm_pt = np.clip(norm_pt, -1.5, 1.5)
            keypoints.extend(norm_pt.tolist())

    # 3. Right Hand
    for idx in range(21):
        pt = get_landmark_xyz(right_hand_landmarks, idx)
        if pt is None:
            keypoints.extend([0.0, 0.0, 0.0])
        else:
            norm_pt = (np.array(pt, dtype=np.float32) - root_center) / scale
            norm_pt = np.clip(norm_pt, -1.5, 1.5)
            keypoints.extend(norm_pt.tolist())

    return keypoints


def extract_face_blendshapes(face_result):
    if not face_result or not getattr(face_result, "face_blendshapes", None):
        return [0.0] * 52

    blendshapes = face_result.face_blendshapes
    if not blendshapes or len(blendshapes) == 0:
        return [0.0] * 52

    values = [float(item.score) for item in blendshapes[0]]
    if len(values) < 52:
        values.extend([0.0] * (52 - len(values)))
    return values[:52]


def interpolate_missing_points(feature_matrix: np.ndarray) -> np.ndarray:
    T = feature_matrix.shape[0]
    if T <= 1:
        return feature_matrix

    coords = feature_matrix[:, 52:].reshape(T, 48, 3)

    for pt_idx in range(48):
        pts = coords[:, pt_idx, :]
        valid_mask = ~np.all(pts == 0.0, axis=-1)

        if not np.any(valid_mask):
            continue

        valid_indices = np.where(valid_mask)[0]
        for c in range(3):
            coords[:, pt_idx, c] = np.interp(
                np.arange(T),
                valid_indices,
                pts[valid_indices, c]
            )

    l_wrist = coords[:, 4:5, :]
    for pt_idx in range(6, 27):
        mask = np.all(coords[:, pt_idx, :] == 0.0, axis=-1)
        coords[mask, pt_idx, :] = l_wrist[mask, 0, :]

    r_wrist = coords[:, 5:6, :]
    for pt_idx in range(27, 48):
        mask = np.all(coords[:, pt_idx, :] == 0.0, axis=-1)
        coords[mask, pt_idx, :] = r_wrist[mask, 0, :]

    feature_matrix[:, 52:] = coords.reshape(T, 144)
    return feature_matrix


def process_video(video_path: Path, output_dir: Path, face_landmarker, pose_landmarker, hand_landmarker):
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return None

    all_frames = []

    while cap.isOpened():
        success, frame = cap.read()
        if not success:
            break

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

        pose_landmarks = None
        if pose_landmarker:
            pose_res = pose_landmarker.detect(mp_image)
            if pose_res and pose_res.pose_landmarks:
                pose_landmarks = pose_res.pose_landmarks[0]

        left_hand_landmarks = None
        right_hand_landmarks = None
        if hand_landmarker:
            hand_res = hand_landmarker.detect(mp_image)
            if hand_res and hand_res.hand_landmarks:
                for landmarks, handedness in zip(hand_res.hand_landmarks, hand_res.handedness):
                    label = handedness[0].category_name if handedness else None
                    if label == "Left":
                        left_hand_landmarks = landmarks
                    elif label == "Right":
                        right_hand_landmarks = landmarks

        face_bs = [0.0] * 52
        if face_landmarker:
            face_res = face_landmarker.detect(mp_image)
            face_bs = extract_face_blendshapes(face_res)

        kpts = extract_normalized_keypoints(pose_landmarks, left_hand_landmarks, right_hand_landmarks)
        all_frames.append(face_bs + kpts)

    cap.release()

    if not all_frames:
        return None

    feature_matrix = np.asarray(all_frames, dtype=np.float32)
    feature_matrix = interpolate_missing_points(feature_matrix)

    output_dir.mkdir(parents=True, exist_ok=True)
    out_file = output_dir / f"{video_path.stem}.npy"
    np.save(out_file, feature_matrix)
    print(f"Processed: {video_path.name} -> Shape: {feature_matrix.shape}")
    return out_file


def process_directory(input_dir: Path, output_dir: Path, models_dir: Path):
    seen_paths = set()
    video_files = []
    for file_path in sorted(input_dir.rglob("*")):
        if file_path.suffix.lower() == ".mp4":
            canonical_path = file_path.resolve()
            if canonical_path not in seen_paths:
                seen_paths.add(canonical_path)
                video_files.append(file_path)

    if not video_files:
        return

    face_task = models_dir / "face_landmarker.task"
    pose_task = models_dir / "pose_landmarker.task"
    hand_task = models_dir / "hand_landmarker.task"

    face_landmarker = FaceLandmarker.create_from_options(
        FaceLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(face_task)), running_mode=RunningMode.IMAGE, num_faces=1, output_face_blendshapes=True)
    ) if face_task.exists() else None

    pose_landmarker = PoseLandmarker.create_from_options(
        PoseLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(pose_task)), running_mode=RunningMode.IMAGE, num_poses=1)
    ) if pose_task.exists() else None

    hand_landmarker = HandLandmarker.create_from_options(
        HandLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(hand_task)), running_mode=RunningMode.IMAGE, num_hands=2)
    ) if hand_task.exists() else None

    for v in video_files:
        process_video(v, output_dir, face_landmarker, pose_landmarker, hand_landmarker)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input_dir", type=str, default="hmm/vdo")
    parser.add_argument("--output_dir", type=str, default="./processed_data")
    parser.add_argument("--models_dir", type=str, default="./models")
    args = parser.parse_args()

    process_directory(Path(args.input_dir), Path(args.output_dir), Path(args.models_dir))