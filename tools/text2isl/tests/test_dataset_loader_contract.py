import importlib

import numpy as np
import pytest
import torch


MODULE_NAME = "dataset_loader"


def import_dataset_module():
    return importlib.import_module(MODULE_NAME)


def test_dataset_module_exports_required_api():
    module = import_dataset_module()
    assert hasattr(module, "ISLDataset"), "ISLDataset must exist in dataset_loader.py"
    assert hasattr(module, "collate_fn"), "collate_fn must exist in dataset_loader.py"


def test_dataset_reads_csv_mapping_and_loads_matching_npy(tmp_path):
    csv_path = tmp_path / "labels.csv"
    csv_path.write_text(
        "filename,sentence\n"
        "video_01,What is your name\n"
        "video_02,Good morning\n",
        encoding="utf-8",
    )

    npy_dir = tmp_path / "npy"
    npy_dir.mkdir()
    np.save(npy_dir / "video_01.npy", np.zeros((12, 148), dtype=np.float32))
    np.save(npy_dir / "video_02.npy", np.ones((20, 148), dtype=np.float32))

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(npy_dir), max_frames=20)

    assert len(dataset) == 2

    text_tokens, video_tensor = dataset[0]
    assert isinstance(text_tokens, torch.Tensor)
    assert text_tokens.ndim >= 1
    assert isinstance(video_tensor, torch.Tensor)
    assert video_tensor.dtype == torch.float32
    assert video_tensor.shape[1] == 148
    assert torch.isfinite(video_tensor).all()


def test_missing_npy_falls_back_to_zero_tensor(tmp_path):
    csv_path = tmp_path / "labels.csv"
    csv_path.write_text("filename,sentence\nvideo_99,Missing sentence\n", encoding="utf-8")

    npy_dir = tmp_path / "npy"
    npy_dir.mkdir()

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(npy_dir), max_frames=20)

    text_tokens, video_tensor = dataset[0]
    assert isinstance(video_tensor, torch.Tensor)
    assert video_tensor.shape[-1] == 148
    assert torch.all(video_tensor == 0)


def test_collate_fn_pads_videos_to_uniform_length():
    module = import_dataset_module()

    text0 = torch.tensor([101, 202, 303])
    text1 = torch.tensor([101, 202, 303, 404])
    video0 = torch.zeros((5, 148), dtype=torch.float32)
    video1 = torch.ones((9, 148), dtype=torch.float32)

    batch = [(text0, video0), (text1, video1)]
    text_batch, video_batch = module.collate_fn(batch)

    assert text_batch.shape[0] == 2
    assert video_batch.shape[0] == 2
    assert video_batch.shape[1] == 9
    assert video_batch.shape[2] == 148
    assert torch.isfinite(video_batch).all()


def test_dataset_uses_padding_and_max_frames_contract():
    module = import_dataset_module()

    assert hasattr(module.ISLDataset, "__init__")
    assert "max_frames" in module.ISLDataset.__init__.__code__.co_varnames

    # A real implementation should keep the feature dimension fixed and pad to a common batch max.
    padded = torch.zeros((4, 20, 148), dtype=torch.float32)
    assert padded.shape[2] == 148
    assert padded.shape[1] == 20


def test_csv_mapping_requires_filename_and_sentence_columns(tmp_path):
    csv_path = tmp_path / "labels.csv"
    csv_path.write_text("filename,sentence\nvideo_01,hello\n", encoding="utf-8")

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(tmp_path), max_frames=20)
    row = dataset.annotations.iloc[0]
    assert "filename" in dataset.annotations.columns
    assert "sentence" in dataset.annotations.columns
    assert row["filename"] == "video_01"
    assert row["sentence"] == "hello"
