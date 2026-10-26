from __future__ import annotations

import os
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np
import pandas as pd
import torch
from torch.nn.utils.rnn import pad_sequence
from torch.utils.data import Dataset
from transformers import DistilBertTokenizer

FEATURE_DIM = 196
DEFAULT_MAX_LENGTH = 20


def _normalize_video_array(video_array: np.ndarray | None) -> np.ndarray:
    """Convert a video feature matrix to a fixed [frames, FEATURE_DIM] float32 array."""
    if video_array is None:
        return np.zeros((1, FEATURE_DIM), dtype=np.float32)

    array = np.asarray(video_array, dtype=np.float32)

    if array.size == 0:
        return np.zeros((1, FEATURE_DIM), dtype=np.float32)

    if array.ndim == 1:
        if array.shape[0] == FEATURE_DIM:
            array = array.reshape(1, FEATURE_DIM)
        else:
            array = array.reshape(-1, 1)

    if array.shape[-1] != FEATURE_DIM:
        pad_amount = max(0, FEATURE_DIM - array.shape[-1])
        array = np.pad(array, ((0, 0), (0, pad_amount)), mode="constant", constant_values=0.0)

    if array.shape[0] == 0:
        array = np.zeros((1, FEATURE_DIM), dtype=np.float32)

    return array.astype(np.float32)


class ISLDataset(Dataset):
    """Dataset that pairs sentence text with a per-video feature tensor."""

    def __init__(
        self,
        csv_file: str,
        npy_dir: str,
        max_frames: int = 100,
        max_source_frames: int = 300,
        tokenizer_name: str = "distilbert-base-uncased",
    ):
        self.csv_file = csv_file
        self.npy_dir = npy_dir
        self.max_frames = max_frames
        self.max_source_frames = max_source_frames
        self.tokenizer = DistilBertTokenizer.from_pretrained(tokenizer_name)
        self.annotations = pd.read_csv(csv_file)

        required_columns = {"filename", "sentence"}
        missing = required_columns - set(self.annotations.columns)
        if missing:
            raise ValueError(f"CSV file is missing required columns: {sorted(missing)}")

        # A missing video used to load as zeros, so training ran on nothing and saved a useless checkpoint.
        paths = [self._npy_path(name) for name in self.annotations["filename"]]
        absent = [p or "(blank filename)" for p in paths if not p or not os.path.exists(p)]
        if absent:
            shown = ", ".join(os.path.basename(p) for p in absent[:5])
            raise FileNotFoundError(f"{len(absent)} of {len(paths)} .npy files missing in {self.npy_dir}: {shown}")
        self.clips = [_normalize_video_array(np.load(p)) for p in paths]

    def _npy_path(self, filename) -> str:
        name = "" if pd.isna(filename) else str(filename).strip()
        if not name:
            return ""
        return os.path.join(self.npy_dir, name if name.endswith(".npy") else f"{name}.npy")

    def __len__(self) -> int:
        return len(self.annotations)

    def split(self) -> Tuple[List[int], List[int]]:
        """Holds out the first clip (by filename) of every sentence that has two or more; drops overlong clips."""
        keep = [i for i, clip in enumerate(self.clips) if len(clip) <= self.max_source_frames]
        by_sentence = {}
        for i in keep:
            by_sentence.setdefault(str(self.annotations.iloc[i]["sentence"]).strip(), []).append(i)
        test = [min(ids, key=lambda i: str(self.annotations.iloc[i]["filename"])) for ids in by_sentence.values() if len(ids) > 1]
        held = set(test)
        return [i for i in keep if i not in held], sorted(test)

    def feature_stats(self, indices: Sequence[int]) -> Tuple[np.ndarray, np.ndarray]:
        frames = np.concatenate([self.clips[i] for i in indices])
        return frames.mean(0), np.maximum(frames.std(0), 1e-3)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor]:
        row = self.annotations.iloc[idx]
        encoded = self.tokenizer(
            str(row["sentence"]).strip(),
            return_tensors="pt",
            padding="max_length",
            truncation=True,
            max_length=DEFAULT_MAX_LENGTH,
        )
        clip = self.clips[idx]
        video = torch.tensor(resample(clip, self.max_frames), dtype=torch.float32)
        return encoded["input_ids"].squeeze(0), encoded["attention_mask"].squeeze(0), video, torch.tensor(float(len(clip)))


def resample(clip: np.ndarray, frames: int) -> np.ndarray:
    """Linear time resampling to a fixed frame count, so signing speed differences line up instead of padding."""
    if len(clip) == 1:
        return np.repeat(clip, frames, axis=0)
    src = np.linspace(0, len(clip) - 1, frames)
    lo = np.floor(src).astype(int)
    hi = np.minimum(lo + 1, len(clip) - 1)
    w = (src - lo)[:, None].astype(np.float32)
    return clip[lo] * (1 - w) + clip[hi] * w


def collate_fn(batch: Sequence[Tuple[torch.Tensor, ...]]) -> Tuple[torch.Tensor, ...]:
    input_ids = pad_sequence([sample[0] for sample in batch], batch_first=True, padding_value=0)
    attention_mask = pad_sequence([sample[1] for sample in batch], batch_first=True, padding_value=0)
    return input_ids, attention_mask, torch.stack([sample[2] for sample in batch]), torch.stack([sample[3] for sample in batch])
