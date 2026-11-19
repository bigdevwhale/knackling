"""Cuts the Codex sheets in art/sheets into app drawables.

    python tools/cut.py            # everything
    python tools/cut.py guitar     # only these sheets

Apprentices (3x2): pet_<species>_<0..5> – baby, young, grown, at work, cheering, asleep.
Eggs (4x3): egg_<0..11>. Icons (4x4): ic_<name>. Decor (4x4): decor_<id>. Patches (4x4): patch_<0..15>.
Tools (3 sheets of 4x4): tool_<hobby>. Room: room_bg with the magenta window made see-through.
The magenta background is flood-filled from the edges (lavender and pink fur stays), plus enclosed pure-magenta holes; each frame is cropped to its picture.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

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


def dist(arr):
    """How far each pixel is from pure magenta (0 = exactly #FF00FF)."""
    r, g, b = arr[..., 0].astype(float), arr[..., 1].astype(float), arr[..., 2].astype(float)
    return np.sqrt((255 - r) ** 2 + g ** 2 + (255 - b) ** 2)


def key(im, flood=True):
    arr = np.array(im.convert("RGBA"))
    d = dist(arr)
    near = d < 100          # could be background (tighter than Inkling's: lavender and pink fur sit near magenta)
    if flood:
        lab, _ = ndimage.label(near)
        edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        bg = np.isin(lab, list(edge))
        # background shut inside a shape (between an arm and a bag): pure magenta patches of some size
        lab2, n2 = ndimage.label(d < 32)
        if n2:
            sizes = ndimage.sum(np.ones_like(lab2), lab2, range(n2 + 1))
            holes = np.isin(lab2, [k for k in range(1, n2 + 1) if sizes[k] >= 25])
            bg = bg | ndimage.binary_dilation(holes, iterations=2) & near
    else:
        bg = d < 110
    alpha = arr[..., 3].astype(float)
    alpha[bg & (d < 65)] = 0
    fringe = bg & (d >= 65)
    # pull the pink out of the fringe and fade it
    k = np.clip((100 - d) / 35, 0, 1)
    for c in (0, 2):
        ch = arr[..., c].astype(float)
        arr[..., c] = np.where(fringe, ch - (ch - arr[..., 1]) * k, ch).astype(np.uint8)
    alpha = np.where(fringe, alpha * (1 - k), alpha)
    # pixels next to the cut-out that still look pink lose their tint too
    grown = ndimage.binary_dilation(alpha == 0, iterations=1) & (alpha > 0) & (d < 120)
    for c in (0, 2):
        ch = arr[..., c].astype(float)
        arr[..., c] = np.where(grown, np.minimum(ch, arr[..., 1] + 40), ch).astype(np.uint8)
    arr[..., 3] = alpha.astype(np.uint8)
    return Image.fromarray(arr)


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
