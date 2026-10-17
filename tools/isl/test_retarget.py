"""Retarget maths on synthetic landmarks: python tools/isl/test_retarget.py"""
import math
import unittest

import numpy as np

import retarget as rt


def flat_hand(side="R", palm_to_viewer=True, fist=False, turn=0.0):
    """21 points in pixels: wrist at the origin, fingers up (screen y down), turned [turn] degrees on screen."""
    thumb_x = 1.0 if (side == "R") == palm_to_viewer else -1.0  # facing the camera, a right thumb is on image right
    h = np.zeros((21, 3))
    h[1:5] = [[thumb_x * (10 + 8 * i), -10 - 8 * i, 0] for i in range(4)]
    for f, mcp in enumerate((5, 9, 13, 17)):
        x = thumb_x * (8 - 8 * f)
        h[mcp] = [x, -40, 0]
        for j in range(1, 4):
            h[mcp + j] = [x, -40 - 12 * j, 0] if not fist else [x, -40 + [6, 14, 10][j - 1], -[12, 10, 0][j - 1]]
    if fist:
        h[4] = [thumb_x * -6, -36, -8]
    a = math.radians(turn)
    rot = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    return h @ rot.T


class HandShape(unittest.TestCase):
    def test_flat_hand_is_straight_and_fist_is_curled(self):
        flat, fist = flat_hand(), flat_hand(fist=True)
        for f in rt.FINGERS:
            self.assertLess(rt.finger_curl(flat, f), 0.1)
            self.assertGreater(rt.finger_curl(fist, f), 0.7)
        self.assertGreater(rt.thumb(fist)[1], rt.thumb(flat)[1])

    def test_shape_code_is_seven_percentages(self):
        code = rt.shape_code(flat_hand())
        self.assertTrue(code.startswith("c:"))
        vals = [int(v) for v in code[2:].split(",")]
        self.assertEqual(len(vals), 7)
        self.assertTrue(all(v % 5 == 0 for v in vals))

    def test_roll_reads_palm_or_back_for_either_hand(self):
        for side in ("R", "L"):
            self.assertAlmostEqual(rt.roll(flat_hand(side, True), side), 1.0, places=3)
            self.assertAlmostEqual(rt.roll(flat_hand(side, False), side), -1.0, places=3)

    def test_angle_is_zero_upright_and_positive_when_tipped_outward(self):
        self.assertAlmostEqual(rt.angle(flat_hand(), "R"), 0.0, places=3)
        # On screen the right hand's outside is image left: turning the fingers there is outward.
        self.assertAlmostEqual(rt.angle(flat_hand(turn=-40), "R"), 40.0, places=3)
        self.assertAlmostEqual(rt.angle(flat_hand("L", turn=40), "L"), 40.0, places=3)


class Placement(unittest.TestCase):
    pose = np.zeros((1, 33, 2))

    def setUp(self):
        p = np.zeros((33, 2))
        p[11], p[12] = [300, 400], [100, 400]  # signer's left shoulder on image right
        p[2], p[5], p[9], p[10] = [220, 300], [180, 300], [210, 335], [190, 335]
        self.body, self.face = rt.body_frame(p[None]), rt.face_frame(p[None])

    def test_body_points_scale_with_the_shoulders(self):
        # Half a shoulder width below the shoulders, centre and right side.
        np.testing.assert_allclose(rt.to_rig(np.array([200.0, 500.0]), self.body, self.face), [100, 180])
        np.testing.assert_allclose(rt.to_rig(np.array([100.0, 500.0]), self.body, self.face), [56, 180])

    def test_face_points_map_onto_the_rig_face(self):
        np.testing.assert_allclose(rt.to_rig(np.array([200.0, 335.0]), self.body, self.face), rt.RIG_MOUTH, atol=1e-6)
        np.testing.assert_allclose(rt.to_rig(np.array([200.0, 300.0]), self.body, self.face), rt.RIG_EYES_MID, atol=1e-6)

    def test_place_names_the_nearest_anchor_with_an_outward_offset(self):
        self.assertEqual(rt.place(np.array([100.0, 95.0]), "R"), {"at": "mouth", "dx": 0, "dy": 4})
        # 6 units to the viewer's left of the right shoulder is 6 outward for the right hand.
        self.assertEqual(rt.place(np.array([50.0, 136.0]), "R"), {"at": "shoulder", "dx": 6, "dy": 0})
        self.assertEqual(rt.place(np.array([150.0, 136.0]), "L"), {"at": "shoulder", "dx": 6, "dy": 0})

    def test_elbow_is_scaled_to_the_rig_arm_and_outward_positive(self):
        pose = np.zeros((30, 33, 2))
        pose[:, 12] = [100, 400]  # right shoulder
        pose[:, 14] = [100, 600]  # hanging: the full 200 px upper arm
        pose[10:20, 14] = [0, 400]  # half length towards image left, outward for the right arm
        e = rt.elbow_offsets(pose, "R")
        np.testing.assert_allclose(e[0], [0, rt.UPPER_ARM], atol=1e-6)
        np.testing.assert_allclose(e[15], [rt.UPPER_ARM / 2, 0], atol=1e-6)

    def test_reach_and_frame_are_clamped(self):
        p = rt.clamp_reach(np.array([-100.0, 400.0]), "R")
        self.assertLessEqual(np.linalg.norm(p - [56, 136]), rt.REACH + 1e-6)
        self.assertTrue(10 <= p[0] <= 190 and 20 <= p[1] <= 190)


class Time(unittest.TestCase):
    def test_gaps_are_filled_linearly(self):
        x = np.array([[0.0], [0.0], [0.0], [6.0]])
        np.testing.assert_allclose(rt.fill_gaps(x, np.array([True, False, False, True])).ravel(), [0, 2, 4, 6])

    def test_keys_keep_both_ends_and_the_turn(self):
        out = np.stack([np.linspace(0, 40, 13), np.zeros(13)], 1)
        back = np.stack([np.linspace(40, 0, 13), np.zeros(13)], 1)
        frames = rt.key_frames(np.concatenate([out, back[1:]]), fps=25.0)
        self.assertEqual(frames[0], 0)
        self.assertEqual(frames[-1], 24)
        self.assertTrue(any(abs(f - 12) <= 1 for f in frames))
        self.assertTrue(all((b - a) / 25.0 <= 0.5 + 1e-9 for a, b in zip(frames, frames[1:])))

    def test_cleanest_recording_wins(self):
        def rec(seen):
            n = len(seen)
            pose = np.zeros((n, 33, 2))
            pose[:, 11], pose[:, 12] = [300, 400], [100, 400]
            hand = np.zeros((n, 21, 3))
            hand[:, 0] = [100, 300, 0]
            return {"pose": pose, "has_pose": np.ones(n, bool), "R": hand, "has_R": np.array(seen), "L": hand, "has_L": np.zeros(n, bool)}
        rid, _, hands = rt.pick([("a", rec([True, False] * 10)), ("b", rec([True] * 20)), ("c", rec([True] * 60))])
        self.assertEqual((rid, hands), ("b", ["R"]))


if __name__ == "__main__":
    unittest.main()
