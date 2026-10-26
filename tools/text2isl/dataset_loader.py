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
    """Convert a video feature matrix to a fixed [frames, 148] float32 array."""
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
        tokenizer_name: str = "distilbert-base-uncased",
    ):
        self.csv_file = csv_file
        self.npy_dir = npy_dir
        self.max_frames = max_frames
        self.tokenizer = DistilBertTokenizer.from_pretrained(tokenizer_name)
        self.annotations = pd.read_csv(csv_file)

        required_columns = {"filename", "sentence"}
        missing = required_columns - set(self.annotations.columns)
        if missing:
            raise ValueError(f"CSV file is missing required columns: {sorted(missing)}")

    def __len__(self) -> int:
        return len(self.annotations)

    def _load_video_tensor(self, filename: str) -> torch.Tensor:
        if not str(filename).strip():
            return torch.zeros((self.max_frames, FEATURE_DIM), dtype=torch.float32)

        filename = str(filename).strip()
        if not filename.endswith(".npy"):
            filename = f"{filename}.npy"

        file_path = os.path.join(self.npy_dir, filename)
        if os.path.exists(file_path):
            video_array = np.load(file_path)
            video_array = _normalize_video_array(video_array)
        else:
            video_array = np.zeros((self.max_frames, FEATURE_DIM), dtype=np.float32)
            return torch.tensor(video_array, dtype=torch.float32)

        if video_array.shape[0] > self.max_frames:
            video_array = video_array[: self.max_frames]

        if video_array.shape[0] < self.max_frames:
            pad_rows = self.max_frames - video_array.shape[0]
            video_array = np.pad(video_array, ((0, pad_rows), (0, 0)), mode="constant", constant_values=0.0)

        return torch.tensor(video_array, dtype=torch.float32)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        row = self.annotations.iloc[idx]
        sentence = str(row["sentence"]).strip()

        encoded = self.tokenizer(
            sentence,
            return_tensors="pt",
            padding="max_length",
            truncation=True,
            max_length=DEFAULT_MAX_LENGTH,
        )
        input_ids = encoded["input_ids"].squeeze(0)
        attention_mask = encoded["attention_mask"].squeeze(0)

        filename = str(row["filename"]).strip()
        video_tensor = self._load_video_tensor(filename)

        return input_ids, attention_mask, video_tensor


def collate_fn(
    batch: Sequence[Tuple[torch.Tensor, torch.Tensor, torch.Tensor]]
) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    input_ids = [sample[0] for sample in batch]
    attention_masks = [sample[1] for sample in batch]
    videos = [sample[2] for sample in batch]

    input_ids_batch = pad_sequence(input_ids, batch_first=True, padding_value=0)
    attention_mask_batch = pad_sequence(attention_masks, batch_first=True, padding_value=0)
    video_batch = pad_sequence(videos, batch_first=True, padding_value=0.0)

    return input_ids_batch, attention_mask_batch, video_batch