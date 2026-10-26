import importlib

import numpy as np
import pytest
import torch


MODULE_NAME = "dataset_loader"


def import_dataset_module():
    return importlib.import_module(MODULE_NAME)


def write_clips(tmp_path, rows, lengths, value=0.0):
    csv_path = tmp_path / "labels.csv"
    csv_path.write_text("filename,sentence\n" + "".join(f"{f},{s}\n" for f, s in rows), encoding="utf-8")
    for name, n in lengths.items():
        np.save(tmp_path / f"{name}.npy", np.full((n, 196), value, dtype=np.float32))
    return csv_path


def test_dataset_module_exports_required_api():
    module = import_dataset_module()
    assert hasattr(module, "ISLDataset"), "ISLDataset must exist in dataset_loader.py"
    assert hasattr(module, "collate_fn"), "collate_fn must exist in dataset_loader.py"


def test_dataset_reads_csv_mapping_and_loads_matching_npy(tmp_path):
    csv_path = write_clips(tmp_path, [("video_01", "What is your name"), ("video_02", "Good morning")], {"video_01": 12, "video_02": 20})

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(tmp_path), max_frames=20)

    assert len(dataset) == 2
    text_tokens, attention_mask, video_tensor, length = dataset[0]
    assert isinstance(text_tokens, torch.Tensor)
    assert attention_mask.shape == text_tokens.shape
    assert video_tensor.dtype == torch.float32
    assert video_tensor.shape == (20, module.FEATURE_DIM)
    assert float(length) == 12


def test_long_clip_is_resampled_not_truncated(tmp_path):
    csv_path = write_clips(tmp_path, [("long", "hello")], {})
    np.save(tmp_path / "long.npy", np.repeat(np.linspace(0, 1, 50, dtype=np.float32)[:, None], 196, axis=1))

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(tmp_path), max_frames=10)
    _, _, video, length = dataset[0]

    assert video.shape == (10, 196)
    assert float(video[0, 0]) == 0.0
    assert float(video[-1, 0]) == pytest.approx(1.0)
    assert float(length) == 50


def test_resample_keeps_endpoints_and_interpolates():
    module = import_dataset_module()
    out = module.resample(np.array([[0.0], [10.0]], dtype=np.float32), 5)
    assert out[:, 0].tolist() == pytest.approx([0.0, 2.5, 5.0, 7.5, 10.0])


def test_split_holds_out_one_clip_per_repeated_sentence_and_drops_overlong(tmp_path):
    rows = [("a2", "hi"), ("a1", "hi"), ("b1", "bye"), ("c1", "yes"), ("c2", "yes"), ("c3", "yes")]
    csv_path = write_clips(tmp_path, rows, {"a1": 5, "a2": 5, "b1": 5, "c1": 5, "c2": 5, "c3": 999})

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(tmp_path), max_frames=8, max_source_frames=300)
    train, test = dataset.split()
    names = dataset.annotations["filename"].tolist()

    assert sorted(names[i] for i in test) == ["a1", "c1"]
    assert sorted(names[i] for i in train) == ["a2", "b1", "c2"]


def test_feature_stats_give_mean_and_floored_std(tmp_path):
    csv_path = write_clips(tmp_path, [("v1", "hi"), ("v2", "bye")], {"v1": 4})
    np.save(tmp_path / "v2.npy", np.full((4, 196), 2.0, dtype=np.float32))

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(tmp_path), max_frames=4)
    mean, std = dataset.feature_stats([0, 1])

    assert mean.shape == (196,) and std.shape == (196,)
    assert float(mean[0]) == pytest.approx(1.0)
    assert float(std[0]) == pytest.approx(1.0)
    _, std_single = dataset.feature_stats([0])
    assert float(std_single.min()) > 0


def test_missing_npy_raises_at_construction(tmp_path):
    csv_path = tmp_path / "labels.csv"
    csv_path.write_text("filename,sentence\nvideo_01,Present\nvideo_99,Missing sentence\n,Blank filename\n", encoding="utf-8")

    npy_dir = tmp_path / "npy"
    npy_dir.mkdir()
    np.save(npy_dir / "video_01.npy", np.zeros((5, 196), dtype=np.float32))

    module = import_dataset_module()
    with pytest.raises(FileNotFoundError) as excinfo:
        module.ISLDataset(csv_file=str(csv_path), npy_dir=str(npy_dir), max_frames=20)
    message = str(excinfo.value)
    assert "2 of 3" in message
    assert "video_99.npy" in message
    assert "video_01.npy" not in message


def test_collate_fn_stacks_fixed_length_videos_and_lengths():
    module = import_dataset_module()

    text0 = torch.tensor([101, 202, 303])
    text1 = torch.tensor([101, 202, 303, 404])
    video = torch.zeros((9, 196), dtype=torch.float32)

    batch = [(text0, torch.ones_like(text0), video, torch.tensor(5.0)), (text1, torch.ones_like(text1), video, torch.tensor(9.0))]
    text_batch, mask_batch, video_batch, lengths = module.collate_fn(batch)

    assert text_batch.shape == (2, 4)
    assert mask_batch.tolist() == [[1, 1, 1, 0], [1, 1, 1, 1]]
    assert video_batch.shape == (2, 9, 196)
    assert lengths.tolist() == [5.0, 9.0]


def test_csv_mapping_requires_filename_and_sentence_columns(tmp_path):
    csv_path = write_clips(tmp_path, [("video_01", "hello")], {"video_01": 5})

    module = import_dataset_module()
    dataset = module.ISLDataset(csv_file=str(csv_path), npy_dir=str(tmp_path), max_frames=20)
    row = dataset.annotations.iloc[0]
    assert row["filename"] == "video_01"
    assert row["sentence"] == "hello"
