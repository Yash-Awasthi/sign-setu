import torch
import torch.nn as nn
from transformers import AutoModel


class TextToSignModel(nn.Module):
    def __init__(
        self,
        feature_dim=196,
        max_frames=100,
        d_model=256,
        nhead=4,
        num_layers=3,
        dim_feedforward=512,
        dropout=0.1,
        pretrained_text_model="distilbert-base-uncased",
    ):
        super().__init__()
        self.feature_dim = feature_dim
        self.max_frames = max_frames
        self.d_model = d_model

        # 1. Pretrained Text Encoder (DistilBERT)
        self.text_encoder = AutoModel.from_pretrained(pretrained_text_model)
        self.text_proj = nn.Linear(self.text_encoder.config.hidden_size, d_model)

        # 2. 100 Distinct Temporal Frame Queries & Positional Encodings
        self.frame_queries = nn.Parameter(torch.randn(1, max_frames, d_model) * 0.02)
        self.frame_pos = nn.Parameter(torch.randn(1, max_frames, d_model) * 0.02)

        # 3. Transformer Decoder: Enables cross-attention between frames and text tokens
        decoder_layer = nn.TransformerDecoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            activation="gelu",
            batch_first=True,
            norm_first=True,
        )
        self.decoder = nn.TransformerDecoder(decoder_layer, num_layers=num_layers)

        # 4. Per-Frame Output Head: maps each frame's d_model -> 196 features (52 blendshapes + 48 points x 3)
        self.output_head = nn.Sequential(
            nn.Linear(d_model, d_model),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(d_model, feature_dim),
        )
        # Frames are time-resampled to max_frames, so the clip's real frame count is predicted separately.
        self.length_head = nn.Linear(d_model, 1)

    def forward(self, input_ids, attention_mask=None):
        batch_size = input_ids.shape[0]

        # A. Encode input text
        text_outputs = self.text_encoder(input_ids=input_ids, attention_mask=attention_mask)
        memory = self.text_proj(text_outputs.last_hidden_state)  # (batch_size, seq_len, d_model)

        # B. Construct batch of frame queries with temporal position encoding
        tgt = self.frame_queries.repeat(batch_size, 1, 1) + self.frame_pos  # (batch_size, 100, d_model)

        # C. Attention mask for padded text tokens
        key_padding_mask = (attention_mask == 0) if attention_mask is not None else None

        # D. Decode cross-attention across all 100 distinct timesteps
        decoded_frames = self.decoder(
            tgt=tgt,
            memory=memory,
            memory_key_padding_mask=key_padding_mask,
        )  # (batch_size, 100, d_model)

        # E. Project to (batch_size, 100, 196), plus log frame count from the masked mean of the text
        frame_sequence = self.output_head(decoded_frames)
        keep = attention_mask.unsqueeze(-1).float() if attention_mask is not None else torch.ones_like(memory[..., :1])
        pooled = (memory * keep).sum(1) / keep.sum(1).clamp(min=1.0)
        return frame_sequence, self.length_head(pooled).squeeze(-1)