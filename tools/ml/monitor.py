"""Resource log every 10 minutes into EVAL.md, and the landmark worker count: TARGET workers, one fewer whenever used
RAM is over GUARD_GB, and no step back up within 10 minutes of a cut (each change drains the pool to its slowest clip).

    python monitor.py
"""
import datetime
import os
import subprocess
import time

import psutil

import guard
from exp import EVAL
from landmark_split import CONTROL

LOGICAL = os.cpu_count() or 20
TARGET = LOGICAL - 2 - 4  # 4 cores stay with the other ML session on this laptop
GUARD_GB = 19


def gpu():
    r = subprocess.run(["nvidia-smi", "--query-gpu=utilization.gpu,memory.used", "--format=csv,noheader,nounits"],
                       capture_output=True, text=True)
    util, mem = (float(v) for v in r.stdout.split(",")[:2])
    return util, mem / 1024


def jobs():
    names = []
    for p in psutil.process_iter(["cmdline"]):
        c = " ".join(p.info["cmdline"] or [])
        for key in ("exp.py", "pretrain.py", "embed.py", "landmark_split.py", "fetch_rkmvu.py", "to_c.py", "curl"):
            if key in c and "-c" not in (p.info["cmdline"] or [])[1:2]:
                names.append(key.replace(".py", "") + (" " + c.split("exp.py")[1].split()[0] if key == "exp.py" else ""))
    return sorted(set(names))


def main():
    if not CONTROL.exists():
        guard.write_atomic(CONTROL, str(TARGET).encode())
    if "### Resource log" not in EVAL.read_text(encoding="utf-8"):
        with open(EVAL, "a", encoding="utf-8") as f:
            f.write("\n### Resource log (every 10 min)\n\n| time | CPU % | GPU %, VRAM GB | RAM used GB | landmark workers | "
                    "jobs | action |\n|---|---|---|---|---|---|---|\n")
    last_cut = 0.0
    while True:
        cpu = psutil.cpu_percent(interval=60)
        util, vram = gpu()
        ram = psutil.virtual_memory()
        w = int(CONTROL.read_text().strip())
        running = jobs()
        action = ""
        used = (ram.total - ram.available) / 1e9
        # A B row whose GPU idles under 70% is starved by its loaders: one landmark worker gives way.
        target = TARGET - (1 if util < 70 and any(j.startswith("exp ") for j in running) else 0)
        if used > GUARD_GB and w > 4:
            w, last_cut = w - 1, time.time()
            guard.write_atomic(CONTROL, str(w).encode())
            action = f"RAM used {used:.1f} GB: landmark workers to {w}"
        elif w > target:
            w -= 1
            guard.write_atomic(CONTROL, str(w).encode())
            action = f"GPU {util:.0f}% under a B row: landmark workers to {w}"
        elif w < target and used < GUARD_GB - 2 and time.time() - last_cut > 600:
            w += 1
            guard.write_atomic(CONTROL, str(w).encode())
            action = f"RAM used {used:.1f} GB: landmark workers back to {w}"
        elif cpu < 80 or util < 70:
            action = "under target"
        with open(EVAL, "a", encoding="utf-8") as f:
            f.write(f"| {datetime.datetime.now():%H:%M} | {cpu:.0f} | {util:.0f}, {vram:.1f} | "
                    f"{(ram.total - ram.available) / 1e9:.1f} | {w} | {', '.join(running)} | {action} |\n")
        time.sleep(540)


if __name__ == "__main__":
    main()
