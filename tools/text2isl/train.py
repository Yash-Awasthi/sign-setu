import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader

from dataset_loader import ISLDataset, collate_fn
from model import TextToSignModel


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


def train_model():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on: {device}")
    if torch.cuda.is_available():
        print(f"Device: {torch.cuda.get_device_name(0)}")

    dataset = ISLDataset(csv_file="labels.csv", npy_dir="processed_data", max_frames=100)
    batch_size = 16 if torch.cuda.is_available() else 4
    dataloader = DataLoader(
        dataset, 
        batch_size=batch_size, 
        shuffle=True, 
        collate_fn=collate_fn, 
        pin_memory=torch.cuda.is_available()
    )

    model = TextToSignModel(feature_dim=196, max_frames=100).to(device)

    optimizer = optim.AdamW([
        {'params': model.text_encoder.parameters(), 'lr': 2e-5},
        {'params': model.text_proj.parameters(), 'lr': 3e-4},
        {'params': model.frame_queries, 'lr': 5e-4},
        {'params': model.frame_pos, 'lr': 5e-4},
        {'params': model.decoder.parameters(), 'lr': 3e-4},
        {'params': model.output_head.parameters(), 'lr': 3e-4},
    ], weight_decay=1e-4)

    epochs = 120  # Increased for multi-class memoization
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs, eta_min=1e-6)
    criterion = ISLSignLoss(w_pos=1.0, w_vel=0.5)

    print(f"Starting training on {len(dataset)} samples across all classes...")

    for epoch in range(epochs):
        model.train()
        epoch_loss = 0.0

        for batch_idx, (input_ids, attention_mask, target_video) in enumerate(dataloader):
            input_ids = input_ids.to(device, non_blocking=True)
            attention_mask = attention_mask.to(device, non_blocking=True)
            target_video = target_video.to(device, non_blocking=True)

            optimizer.zero_grad()

            predicted_frames = model(input_ids, attention_mask=attention_mask)

            seq_len = min(predicted_frames.size(1), target_video.size(1))
            loss = criterion(predicted_frames[:, :seq_len, :], target_video[:, :seq_len, :])

            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()

            epoch_loss += loss.item()

        scheduler.step()
        avg_loss = epoch_loss / len(dataloader)

        if (epoch + 1) % 15 == 0 or epoch == 0:
            print(f"Epoch [{epoch + 1:03d}/{epochs}] - Loss: {avg_loss:.4f}")

    torch.save(model.state_dict(), "isl_transformer_v1.pth")
    print("Training complete! Model saved as 'isl_transformer_v1.pth'")


if __name__ == "__main__":
    train_model()