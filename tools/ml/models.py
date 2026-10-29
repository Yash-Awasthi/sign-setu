"""Format 3 readers: features computed inside the graph from raw landmarks, so the phone passes points only.

Raw frame (RAW values): left hand 21x(x,y), right hand 21x(x,y), pose 33x(x,y), face FACE_POINTS x(x,y) as
MediaPipe normalised image points, then presence of left, right, pose, face. Input (B, T, RAW) already resampled to T
frames by features.resample_index, plus aspect (B, 1) = frame width / height.
"""
import torch
from torch import nn

FACE = 32
RAW = 42 + 42 + 66 + FACE * 2 + 4
L, R, P, F, PRES = slice(0, 42), slice(42, 84), slice(84, 150), slice(150, 150 + FACE * 2), slice(RAW - 4, RAW)
ARMS = [11, 12, 13, 14, 15, 16]


class Points(nn.Module):
    """Raw frames to shoulder-centred, shoulder-width-scaled nodes (B, T, N, 2) and presence masks, in torch ops that
    convert to LiteRT (no boolean indexing, no data-dependent shapes)."""

    def forward(self, raw, aspect):
        b, t, _ = raw.shape
        ky = torch.cat([torch.ones_like(aspect), 1 / aspect], 1).view(b, 1, 1, 2)
        pres = raw[..., PRES]
        hl, hr, hp, hf = (pres[..., i:i + 1] for i in range(4))
        left = raw[..., L].reshape(b, t, 21, 2) * ky
        right = raw[..., R].reshape(b, t, 21, 2) * ky
        pose = raw[..., P].reshape(b, t, 33, 2) * ky
        face = raw[..., F].reshape(b, t, FACE, 2) * ky
        mid = (pose[:, :, 11] + pose[:, :, 12]) / 2
        width = (pose[:, :, 11] - pose[:, :, 12]).pow(2).sum(-1, keepdim=True).add(1e-12).sqrt()
        n = hp.sum(1).clamp(min=1)
        center = (mid * hp).sum(1) / n
        scale = ((width * hp).sum(1) / n).clamp(min=1e-3)
        norm = lambda x: (x - center.view(b, 1, 1, 2)) / scale.view(b, 1, 1, 1)  # noqa: E731
        arms = pose[:, :, ARMS[0]:ARMS[-1] + 1]
        nodes = torch.cat([norm(left) * hl[..., None], norm(right) * hr[..., None], norm(arms) * hp[..., None],
                           norm(face) * hf[..., None]], 2)
        mask = torch.cat([hl.expand(b, t, 21), hr.expand(b, t, 21), hp.expand(b, t, 6), hf.expand(b, t, FACE)], 2)
        return nodes, mask, pres


def no_face_flag(pres):
    """Presence flags with the face's zeroed: only some sources carry a face, so the flag alone would name the source."""
    return torch.cat([pres[..., :3], torch.zeros_like(pres[..., 3:])], -1)


def shipped_features(nodes, pres):
    """The format 1 feature vector (features.featurize): hands, arms, two presence flags."""
    b, t = nodes.shape[:2]
    return torch.cat([nodes[:, :, :48].reshape(b, t, 96), pres[..., :2]], -1)


class Shipped(nn.Module):
    """The shipped SignNet with its features moved into the graph."""

    def __init__(self, classes, hidden=160, kernel=5, dropout=0.4):
        super().__init__()
        self.points = Points()
        self.embed = nn.Linear(98, hidden)
        self.conv1 = nn.Conv1d(hidden, hidden, kernel, padding=kernel // 2)
        self.bn1 = nn.BatchNorm1d(hidden)
        self.conv2 = nn.Conv1d(hidden, hidden, kernel, padding=kernel // 2)
        self.bn2 = nn.BatchNorm1d(hidden)
        self.drop = nn.Dropout(dropout)
        self.head = nn.Linear(2 * hidden, classes)

    def forward(self, raw, aspect):
        nodes, _, pres = self.points(raw, aspect)
        h = torch.relu(self.embed(shipped_features(nodes, pres))).transpose(1, 2)
        h = torch.relu(self.bn1(self.conv1(h)))
        h = torch.relu(self.bn2(self.conv2(h)))
        return self.head(self.drop(torch.cat([h.mean(2), h.amax(2)], 1)))


class Attention(nn.Module):
    """Multi-head self-attention with separate projections: nn.MultiheadAttention's packed weights export an ONNX
    Squeeze that LiteRT cannot run."""

    def __init__(self, dim, heads):
        super().__init__()
        self.h, self.d = heads, dim // heads
        self.q, self.k, self.v, self.o = (nn.Linear(dim, dim) for _ in range(4))

    def forward(self, x, bias=None):
        b, n, c = x.shape
        q, k, v = (f(x).reshape(b, n, self.h, self.d).transpose(1, 2) for f in (self.q, self.k, self.v))
        a = q @ k.transpose(2, 3) * self.d ** -0.5
        if bias is not None:
            a = a + bias
        return self.o((a.softmax(-1) @ v).transpose(1, 2).reshape(b, n, c))


# Node groups for B3: left hand, right hand, arms, face.
GROUPS = [(0, 21), (21, 42), (42, 48), (48, 48 + FACE)]


class GraphAttn(nn.Module):
    """B3, HWGAT style: per frame, attention among landmark nodes inside a learned adjacency bias, group pooling into
    hand, arm and face tokens, then temporal convolutions over the tokens."""

    def __init__(self, classes, dim=64, hidden=256, layers=2, dropout=0.3):
        super().__init__()
        n = 48 + FACE
        self.points = Points()
        self.inp = nn.Linear(5, dim)  # x, y, dx, dy, present
        self.pos = nn.Parameter(torch.zeros(n, dim))
        self.adj = nn.ParameterList(nn.Parameter(torch.zeros(n, n)) for _ in range(layers))
        self.attn = nn.ModuleList(Attention(dim, 4) for _ in range(layers))
        self.ff = nn.ModuleList(nn.Sequential(nn.LayerNorm(dim), nn.Linear(dim, 2 * dim), nn.GELU(), nn.Linear(2 * dim, dim))
                                for _ in range(layers))
        self.norm = nn.ModuleList(nn.LayerNorm(dim) for _ in range(layers))
        self.proj = nn.Linear(dim * len(GROUPS) + 4, hidden)
        self.temporal = nn.Sequential(*[m for _ in range(3) for m in (
            nn.Conv1d(hidden, hidden, 5, padding=2, groups=hidden), nn.Conv1d(hidden, hidden, 1),
            nn.BatchNorm1d(hidden), nn.GELU())])
        self.drop = nn.Dropout(dropout)
        self.head = nn.Linear(2 * hidden, classes)

    def forward(self, raw, aspect):
        nodes, mask, pres = self.points(raw, aspect)
        b, t, n, _ = nodes.shape
        vel = torch.cat([torch.zeros_like(nodes[:, :1]), nodes[:, 1:] - nodes[:, :-1]], 1)
        x = self.inp(torch.cat([nodes, vel, mask[..., None]], -1)) + self.pos
        x = x.reshape(b * t, n, -1)
        for attn, ff, nm, adj in zip(self.attn, self.ff, self.norm, self.adj):
            y = nm(x)
            x = x + attn(y, adj)
            x = x + ff(x)
        x = x.reshape(b, t, n, -1)
        m = mask[..., None]
        tokens = [(x[:, :, a:z] * m[:, :, a:z]).sum(2) / m[:, :, a:z].sum(2).clamp(min=1) for a, z in GROUPS]
        h = self.proj(torch.cat(tokens + [no_face_flag(pres)], -1)).transpose(1, 2)
        h = h + self.temporal(h)
        return self.head(self.drop(torch.cat([h.mean(2), h.amax(2)], 1)))


class ECA(nn.Module):
    def __init__(self, k=5):
        super().__init__()
        self.conv = nn.Conv1d(1, 1, k, padding=k // 2, bias=False)

    def forward(self, x):  # (B, C, T)
        return x * torch.sigmoid(self.conv(x.mean(2, keepdim=True).transpose(1, 2))).transpose(1, 2)


class ConvBlock(nn.Module):
    def __init__(self, dim, k=17, expand=2, drop=0.2):
        super().__init__()
        self.pre = nn.Linear(dim, dim * expand)
        self.dw = nn.Conv1d(dim * expand, dim * expand, k, padding=k // 2, groups=dim * expand)
        self.bn = nn.BatchNorm1d(dim * expand)
        self.eca = ECA()
        self.post = nn.Linear(dim * expand, dim)
        self.drop = nn.Dropout(drop)

    def forward(self, x):  # (B, T, C)
        h = nn.functional.silu(self.pre(x)).transpose(1, 2)
        h = self.eca(self.bn(self.dw(h))).transpose(1, 2)
        return x + self.drop(self.post(h))


class TransformerBlock(nn.Module):
    def __init__(self, dim, heads=4, drop=0.2):
        super().__init__()
        self.n1, self.n2 = nn.LayerNorm(dim), nn.LayerNorm(dim)
        self.attn = Attention(dim, heads)
        self.ff = nn.Sequential(nn.Linear(dim, dim * 2), nn.SiLU(), nn.Linear(dim * 2, dim))
        self.drop = nn.Dropout(drop)

    def forward(self, x):
        x = x + self.drop(self.attn(self.n1(x)))
        return x + self.drop(self.ff(self.n2(x)))


class ConvTransformer(nn.Module):
    """B4, after the 1st-place Kaggle ASL Signs model: per-frame stem, then (3 conv blocks + 1 transformer) x 2."""

    def __init__(self, classes, dim=192, dropout=0.4):
        super().__init__()
        self.points = Points()
        feat = (48 + FACE) * 4 + 4
        self.stem = nn.Sequential(nn.Linear(feat, dim, bias=False), nn.LayerNorm(dim))
        self.blocks = nn.Sequential(*[b for _ in range(2) for b in (
            ConvBlock(dim), ConvBlock(dim), ConvBlock(dim), TransformerBlock(dim))])
        self.top = nn.Linear(dim, dim * 2)
        self.drop = nn.Dropout(dropout)
        self.head = nn.Linear(dim * 2, classes)

    def frames(self, raw, aspect):
        """Per-frame encoding (B, T, dim): the part masked pose pretraining trains."""
        nodes, mask, pres = self.points(raw, aspect)
        b, t = nodes.shape[:2]
        vel = torch.cat([torch.zeros_like(nodes[:, :1]), nodes[:, 1:] - nodes[:, :-1]], 1)
        return self.blocks(self.stem(torch.cat([nodes.reshape(b, t, -1), vel.reshape(b, t, -1), no_face_flag(pres)], -1)))

    def forward(self, raw, aspect):
        return self.head(self.drop(self.top(self.frames(raw, aspect)).mean(1)))


ARCHS = {"shipped": Shipped, "b3": GraphAttn, "b4": ConvTransformer}


def from_cache_frame(left, right, pose, face, has_left, has_right, has_pose, has_face):
    """Numpy raw frames (T, RAW) from per-part arrays, the layout Kotlin must pass."""
    import numpy as np
    t = len(left)
    return np.concatenate([left.reshape(t, -1), right.reshape(t, -1), pose.reshape(t, -1), face.reshape(t, -1),
                           np.stack([has_left, has_right, has_pose, has_face], 1)], 1).astype(np.float32)
