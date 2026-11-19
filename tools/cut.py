"""Cuts the Codex sheets in art/sheets into app drawables.

    python tools/cut.py            # everything
    python tools/cut.py guitar     # only these sheets

Apprentices (3x2): pet_<species>_<0..5> – baby, young, grown, at work, cheering, asleep.
Eggs (4x3): egg_<0..11>. Icons (4x4): ic_<name>. Decor (4x4): decor_<id>. Patches (4x4): patch_<0..15>.
Tools (3 sheets of 4x4): tool_<hobby>. Room: room_bg with the magenta window made see-through.
The magenta background is keyed out; each frame is cropped to its picture.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from hobbies import HOBBIES, OWN  # noqa: E402

SHEETS = ROOT / "art" / "sheets"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

PETS = list(HOBBIES) + [f"own_{k}" for k in OWN]
ICONS = ["house", "journal", "stopwatch", "paw", "patch", "chest", "spark", "scroll",
         "postcard", "chart", "bell", "gear", "pencil", "bulb", "heart", "calendar"]
DECOR = ["plant", "lamp", "mannequin", "easel", "gramophone", "wheel", "yarn", "armchair",
         "clock", "globe", "fireflies", "fish", "lights", "books", "cattree", "kettle"]
MAX = 320


def key(im):
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            m = min(r, b) - g  # how magenta
            if m > 90 and r > 170 and b > 170:
                px[x, y] = (0, 0, 0, 0)
            elif m > 40 and r > 150 and b > 150:
                # a fringe: pull the pink out and fade it
                k = min(1, (m - 40) / 50)
                px[x, y] = (int(r - (r - g) * k), g, int(b - (b - g) * k), int(255 * (1 - k)))
    return im


def border_clean(cell):
    """Drops bits of the neighbouring cells: anything opaque that touches the cell's edge."""
    import numpy as np
    from scipy import ndimage
    arr = np.array(cell)
    lab, n = ndimage.label(arr[:, :, 3] > 30)
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    sizes = ndimage.sum(np.ones_like(lab), lab, range(n + 1))
    big = max(range(1, n + 1), key=lambda k: sizes[k]) if n else 0
    for k in edge:
        if k != big:
            arr[lab == k, 3] = 0
    return Image.fromarray(arr)


def frames(sheet, cols, rows):
    im = key(Image.open(sheet))
    w, h = im.size
    cw, ch = w / cols, h / rows
    out = []
    for j in range(rows):
        for i in range(cols):
            cell = border_clean(im.crop((int(i * cw), int(j * ch), int((i + 1) * cw), int((j + 1) * ch))))
            box = cell.getchannel("A").point(lambda v: 255 if v > 30 else 0).getbbox()
            cell = cell.crop(box) if box else cell
            s = MAX / max(cell.size)
            if s < 1:
                cell = cell.resize((max(1, round(cell.width * s)), max(1, round(cell.height * s))), Image.LANCZOS)
            out.append(cell)
    return out


def save(im, name):
    im.save(OUT / f"{name}.webp", "WEBP", quality=90, method=6)


def grid(name, cols, rows, names, prefix, size, want):
    f = SHEETS / f"{name}.png"
    if not f.exists() or (want and name not in want):
        return
    for n, fr in zip(names, frames(f, cols, rows)):
        if n is None:
            continue
        fr.thumbnail((size, size), Image.LANCZOS)
        save(fr, f"{prefix}{n}")
    print(name)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = set(sys.argv[1:])
    for p in PETS:
        f = SHEETS / f"{p}.png"
        if want and p not in want:
            continue
        if not f.exists():
            print("missing", p)
            continue
        for n, fr in enumerate(frames(f, 3, 2)):
            save(fr, f"pet_{p}_{n}")
        print("pet", p)
    grid("icons", 4, 4, ICONS, "ic_", 160, want)
    grid("items", 4, 4, DECOR, "decor_", 240, want)
    grid("patches", 4, 4, [str(i) for i in range(16)], "patch_", 200, want)
    grid("eggs", 4, 3, [str(i) for i in range(12)], "egg_", 200, want)
    tools = list(HOBBIES)
    for n in range(3):
        chunk = tools[n * 16:(n + 1) * 16]
        grid(f"tools{n}", 4, 4, chunk + [None] * (16 - len(chunk)), "tool_", 160, want)
    if (SHEETS / "room.png").exists() and (not want or "room" in want):
        g = key(Image.open(SHEETS / "room.png"))
        g = g.resize((1024, round(1024 * g.height / g.width)), Image.LANCZOS)
        g.save(OUT / "room_bg.webp", "WEBP", quality=88, method=6)
        print("room")


if __name__ == "__main__":
    main()
