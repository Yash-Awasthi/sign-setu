import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Subset

from dataset_loader import ISLDataset, collate_fn
from model import TextToSignModel

MAX_FRAMES = 100
EPOCHS = 150


class ISLSignLoss(nn.Module):
    def __init__(self, w_pos=1.0, w_vel=0.5):
        super().__init__()
        self.w_pos = w_pos
        self.w_vel = w_vel
        self.l1 = nn.L1Loss()

    def forward(self, pred: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        loss_pos = self.l1(pred, target)

        pred_vel = pred[:, 1:, :] - pred[:, :-1, :]
        target_vel = target[:, 1:, :] - target[:, :-1, :]
        loss_vel = self.l1(pred_vel, target_vel)

        return self.w_pos * loss_pos + self.w_vel * loss_vel


def score(model, dataset, test, train, mean, std, device):
    """Held-out clips against two baselines, in raw coordinate units: the mean training clip of the same sentence
    (what a perfect lookup would give) and the mean of all training clips (what a collapsed model gives)."""
    sentence = lambda i: str(dataset.annotations.iloc[i]["sentence"]).strip()
    by_sentence = {}
    for i in train:
        by_sentence.setdefault(sentence(i), []).append(dataset[i][2].numpy())
    overall = np.mean([clip for clips in by_sentence.values() for clip in clips], axis=0)
    model.eval()
    rows = []
    with torch.no_grad():
        for i in test:
            ids, mask, target, length = dataset[i]
            frames, log_length = model(ids[None].to(device), attention_mask=mask[None].to(device))
            pred = frames[0].cpu().numpy() * std + mean
            target = target.numpy()
            lookup = np.mean(by_sentence[sentence(i)], axis=0)
            rows.append([
                np.abs(pred - target)[:, 52:].mean(),
                np.abs(lookup - target)[:, 52:].mean(),
                np.abs(overall - target)[:, 52:].mean(),
                abs(float(log_length.exp()) - float(length)) / float(length),
            ])
    return np.mean(rows, axis=0)


def train_model():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on: {device}")

    dataset = ISLDataset(csv_file="labels.csv", npy_dir="processed_data", max_frames=MAX_FRAMES)
    train, test = dataset.split()
    mean, std = dataset.feature_stats(train)
    mean_t, std_t = torch.tensor(mean, device=device), torch.tensor(std, device=device)
    print(f"{len(train)} training clips, {len(test)} held out, {len(dataset) - len(train) - len(test)} dropped as overlong")

    loader = DataLoader(Subset(dataset, train), batch_size=16, shuffle=True, collate_fn=collate_fn)
    model = TextToSignModel(feature_dim=196, max_frames=MAX_FRAMES).to(device)
    optimizer = optim.AdamW([
        {'params': model.text_encoder.parameters(), 'lr': 2e-5},
        {'params': [p for n, p in model.named_parameters() if not n.startswith('text_encoder.')], 'lr': 3e-4},
    ], weight_decay=1e-4)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS, eta_min=1e-6)
    criterion = ISLSignLoss(w_pos=1.0, w_vel=0.5)

    best = None
    for epoch in range(EPOCHS):
        model.train()
        total = 0.0
        for input_ids, attention_mask, video, lengths in loader:
            input_ids, attention_mask = input_ids.to(device), attention_mask.to(device)
            target = (video.to(device) - mean_t) / std_t
            frames, log_length = model(input_ids, attention_mask=attention_mask)
            loss = criterion(frames, target) + 0.1 * (log_length - lengths.to(device).log()).abs().mean()
            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            total += loss.item()
        scheduler.step()

        if (epoch + 1) % 10 == 0 or epoch == 0:
            model_l1, lookup_l1, overall_l1, length_err = score(model, dataset, test, train, mean, std, device)
            print(f"Epoch [{epoch + 1:03d}/{EPOCHS}] loss {total / len(loader):.4f} | held-out L1 model {model_l1:.4f} "
                  f"same-sentence mean {lookup_l1:.4f} all-clip mean {overall_l1:.4f} | length error {length_err:.1%}", flush=True)
            if best is None or model_l1 < best:
                best = model_l1
                torch.save({
                    "state": model.state_dict(), "mean": mean, "std": std, "max_frames": MAX_FRAMES, "epoch": epoch + 1,
                    "held_out": [str(dataset.annotations.iloc[i]["filename"]) for i in test],
                    "held_out_l1": model_l1, "same_sentence_l1": lookup_l1, "all_clip_l1": overall_l1,
                }, "isl_transformer_v1.pth")

    print(f"Training complete! Best held-out L1 {best:.4f} saved as 'isl_transformer_v1.pth'")


if __name__ == "__main__":
    train_model()
