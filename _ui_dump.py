import re
import sys
from pathlib import Path

t = Path(sys.argv[1]).read_text(encoding="utf-8")
for m in re.finditer(r"<node[^>]*>", t):
    n = m.group(0)
    text = re.search(r'text="([^"]*)"', n)
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    click = re.search(r'clickable="([^"]*)"', n)
    cls = re.search(r'class="([^"]*)"', n)
    if not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    tx = text.group(1) if text else ""
    if y1 > 1800 or tx in (
        "Home",
        "Markets",
        "Swap",
        "History",
        "Settings",
        "TRANSACT",
        "Sync",
        "BEGIN SWAP",
        "Continue",
    ):
        cname = cls.group(1).split(".")[-1] if cls else ""
        print(f"{tx!r:22} click={click.group(1) if click else '?':5} {x1:4},{y1:4}-{x2:4},{y2:4} {cname}")
