import torch

from model import TextToSignModel


def test_model_returns_frames_and_log_length():
    model = TextToSignModel(feature_dim=196, max_frames=12)
    ids = torch.tensor([[101, 2023, 102, 0], [101, 2054, 2003, 102]])
    mask = (ids != 0).long()
    frames, log_length = model(ids, attention_mask=mask)
    assert frames.shape == (2, 12, 196)
    assert log_length.shape == (2,)
