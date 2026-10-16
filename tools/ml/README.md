# Sign reader training

Trains the on-device isolated sign classifier that `LandmarkSignReader` runs in the app.

## Data

INCLUDE, the Indian Sign Language isolated-word dataset by AI4Bharat (Sridhar et al., "INCLUDE: A Large Scale
Dataset for Indian Sign Language Recognition", ACM Multimedia 2020), licensed CC BY 4.0. The scripts use the
OpenHands pose release (zenodo.org/records/6674324, CC BY 4.0), which already holds MediaPipe Holistic keypoints
for every video, so no video is downloaded or processed. The official train and test split ships with it.
Data stays outside the repository (default `C:/Users/Yash/dev/ml-data/include_pose`).

## Environment

A Python 3.12 virtual environment with PyTorch (CUDA build) and NumPy:

    uv venv --python 3.12 signsetu-ml
    uv pip install --python signsetu-ml/Scripts/python.exe torch --index-url https://download.pytorch.org/whl/cu128
    uv pip install --python signsetu-ml/Scripts/python.exe numpy

## Steps

Run from `tools/ml`:

    python prepare.py   # download, unzip, cache normalised landmarks in include.pkl
    python train.py     # train on the GPU, print test-split top-1 and top-3, save sign_reader.pt
    python export.py    # write assets/content/models/sign_reader.{bin,json} and the parity fixture
    python ../content/build_content.py --manifest-only

The app loads the model only when both files match the content manifest. `SignReaderTest` checks that the
Kotlin forward pass reproduces the Python logits on the fixture.

## Features

`features.py` is the specification; `LandmarkSignReader.kt` mirrors it. Per frame: both hands (21 points, x and y)
and the arm points of the pose (shoulders, elbows, wrists), with y scaled to the frame width, centred on the mean
shoulder midpoint and divided by the mean shoulder width of the segment. A missing hand is zeros with a presence
flag. Each segment is resampled to 32 frames by nearest index. Training augments with mirroring (which swaps the
hands), small rotation and anisotropic scale, point noise, and time cropping and resampling.
