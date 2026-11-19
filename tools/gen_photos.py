"""Demo photos of finished work for the screenshots (realistic phone photos, made by Codex) → app/src/demo/assets/photos.

    python tools/gen_photos.py
"""
import shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_art import image_of

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "demo" / "assets" / "photos"
SHOTS = {
    "embroidery1": "a round wooden embroidery hoop with a half-finished cross-stitch of a red fox among ferns, threads and scissors on a linen tablecloth",
    "embroidery2": "a finished cross-stitch of wildflowers in a wooden hoop hanging on a white wall",
    "watercolor1": "a small watercolour painting of a misty lake with pines on textured paper, a paint box and brush next to it",
    "watercolor2": "a watercolour sketchbook page with three pears painted loosely, a cup of tea nearby",
    "baking1": "a tray of freshly baked cinnamon buns on a wooden table, warm morning light",
    "baking2": "a round loaf of homemade sourdough bread with a scored top on a cutting board",
    "guitar1": "an acoustic guitar leaning on a sofa with a songbook open on chord charts",
    "reading1": "an open paperback book and a mug of cocoa on a knitted blanket, cosy evening lamp light",
    "macrame1": "a cream macramé plant hanger with a small green plant, hanging by a window",
    "yoga1": "a rolled purple yoga mat and a candle on a wooden floor in morning sun",
}
PROMPT = "Use your image generation tool to create ONE image. Do nothing else (no file copying).\n\nImage: a realistic, natural smartphone photo, square, taken by a hobbyist at home, no people, no faces, no text, no watermark: {}."

def run(item):
    name, desc = item
    dst = OUT / f"{name}.jpg"
    if dst.exists(): return
    r = subprocess.run([shutil.which("codex.cmd") or "codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "-"], input=PROMPT.format(desc),
                       text=True, encoding="utf-8", errors="replace", capture_output=True, cwd=str(ROOT))
    p = image_of((r.stdout or "") + (r.stderr or ""))
    if p is None: print("FAIL", name, flush=True); return
    from PIL import Image
    Image.open(p).convert("RGB").resize((1024, 1024)).save(dst, "JPEG", quality=85)
    print("ok", name, flush=True)

OUT.mkdir(parents=True, exist_ok=True)
with ThreadPoolExecutor(3) as ex: list(ex.map(run, SHOTS.items()))
