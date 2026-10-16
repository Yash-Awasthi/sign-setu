"""Feature extraction shared by training and export; LandmarkSignReader.kt must match it exactly.

Input per frame, MediaPipe Holistic normalised image coordinates (0..1, origin top-left, unmirrored):
left and right hand (21 points, or missing) and pose (33 points, or missing).
"""
import numpy as np

FRAMES = 32
ARMS = [11, 12, 13, 14, 15, 16]  # shoulders, elbows, wrists
ARMS_FLIPPED = [12, 11, 14, 13, 16, 15]
FEATURES = 21 * 2 * 2 + len(ARMS) * 2 + 2  # left hand, right hand, arms, two presence flags


def resample_index(n: int, frames: int = FRAMES) -> np.ndarray:
    """Nearest source frame for each output frame, in integer arithmetic so Kotlin rounds identically."""
    i = np.arange(frames)
    return (i * (n - 1) * 2 + (frames - 1)) // (2 * (frames - 1))


def isotropic(left, right, pose, aspect: float):
    """Scales y by height/width so both axes share one unit; aspect is frame width / height."""
    k = np.array([1.0, 1.0 / aspect])
    return left * k, right * k, pose * k


def featurize(left, right, pose, has_left, has_right, has_pose):
    """left, right: (N, 21, 2); pose: (N, 33, 2) isotropic; has_*: (N,) bool. Returns (FRAMES, FEATURES) or None."""
    if not has_pose.any():
        return None
    p = pose[has_pose]
    center = ((p[:, 11] + p[:, 12]) / 2).mean(0)
    scale = np.linalg.norm(p[:, 11] - p[:, 12], axis=1).mean()
    if scale < 1e-6:
        return None
    idx = resample_index(len(pose))
    out = np.zeros((FRAMES, FEATURES), np.float32)
    for t, j in enumerate(idx):
        if has_left[j]:
            out[t, 0:42] = ((left[j] - center) / scale).reshape(-1)
            out[t, 96] = 1
        if has_right[j]:
            out[t, 42:84] = ((right[j] - center) / scale).reshape(-1)
            out[t, 97] = 1
        if has_pose[j]:
            out[t, 84:96] = ((pose[j, ARMS] - center) / scale).reshape(-1)
    return out
