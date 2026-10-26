from types import SimpleNamespace

import numpy as np
import pytest

mp = pytest.importorskip("mediapipe")
import process_sign_videos as psv


def pose(points):
    landmarks = [SimpleNamespace(x=0.0, y=0.0, z=0.0) for _ in range(33)]
    for i, (x, y) in points.items():
        landmarks[i] = SimpleNamespace(x=x, y=y, z=0.0)
    return landmarks


def body_in_frame(width, height):
    """One signer, shoulders 200 px apart and a wrist 150 px below the left shoulder, in normalised image units."""
    px = {11: (500, 400), 12: (700, 400), 15: (500, 550)}
    return pose({i: (x / width, y / height) for i, (x, y) in px.items()})


def test_portrait_and_landscape_frames_give_the_same_body():
    landscape = psv.extract_normalized_keypoints(body_in_frame(1920, 1080), None, None, aspect=1920 / 1080)
    portrait = psv.extract_normalized_keypoints(body_in_frame(1200, 1600), None, None, aspect=1200 / 1600)
    np.testing.assert_allclose(landscape, portrait, atol=1e-5)
