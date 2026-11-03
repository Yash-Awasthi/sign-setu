The station's sign readers go in this folder. Each is a model file with a .json spec of the same name beside it:
sign_reader.bin (format 1, as tools/ml/export.py writes it), a .tflite (LiteRT) or an .onnx. A .pt is refused: export it
to ONNX. The spec names the labels, frames, temperature and input builder (shoulder98 or raw218).

No restart is needed: a file added, changed or removed takes effect within seconds, and a file that fails to load is
refused with a reason (shown in the station's status) while the reader it would replace keeps running. While the folder
holds no reader, phones read signs themselves (docs/18-station.md).
