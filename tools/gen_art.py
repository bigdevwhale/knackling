"""Draws the apprentices and the workshop art with the Codex CLI image tool.

Codex saves images into ~/.codex/generated_images/<session>/; the image of the session named in
the run's output is copied to art/sheets/<id>.png. Already present sheets are skipped.
Pet prompts attach art/ref/nightjar.png (Wildling's moon cat) as the style reference.

    python tools/gen_art.py              # everything missing
    python tools/gen_art.py guitar room  # only these
"""
import re
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from hobbies import HOBBIES, OWN, OWN_SIG  # noqa: E402

OUT = ROOT / "art" / "sheets"
REF = ROOT / "art" / "ref" / "nightjar.png"
GEN = Path.home() / ".codex" / "generated_images"

STYLE = (
    "Style: EXACTLY the style of the attached reference sprite sheet (a moon cat): detailed 16-bit pixel art like a modern cozy indie game, "
    "crisp pixels, clean dark outline, 3-4 tone shading ramps, soft fluffy fur drawn in tufts, cute round proportions, big shiny eyes with a highlight. "
    "Draw a DIFFERENT creature, not the cat from the reference: only copy its art style, outline, shading, size and cuteness. "
    "Use the creature's own colours exactly as described (no green moss or leaves unless described). It is an invented fantasy creature, not a real animal. "
    "Correct anatomy: exactly two eyes, four limbs or two legs and two wings, one tail, no extra paws."
)
OBJ = (
    "Style: detailed 16-bit pixel art like a modern cozy indie game, crisp pixels, clean dark outline, 3-4 tone shading ramps, warm cozy palette. "
    "These are plain inanimate objects: NO faces, NO eyes, NO mouths, NO blush, nothing alive about them."
)
GRID = (
    "Layout: 3 columns x 2 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. "
    "One creature per cell, centered, with clear margin; nothing touches the cell borders. No text, no grid lines, "
    "no shadows on the background, no other objects except those listed."
)
FRAMES = """1) a tiny baby: small, round and chibi, big head, sitting, a little unsure
2) young: a bit bigger, curious, one paw raised, ears up
3) grown up: sitting proud and calm, content
4) grown up, {sig}
5) grown up, cheering, jumping with joy
6) grown up, asleep, curled up, peaceful face"""


def pet_prompt(desc, sig):
    return f"""Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet for a cozy hobby app, the apprentice creature that grows while its owner practises a hobby: {desc}.
{STYLE}
{GRID}
Cells, left to right, top to bottom:
{FRAMES.format(sig=sig)}
The same character and palette in every cell (smaller in cells 1 and 2), facing three-quarters right."""


def objects(title, cols, rows, items, style=OBJ):
    listed = ", ".join(f"{i + 1} {t}" for i, t in enumerate(items))
    return f"""Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: {title}
{style}
Layout: {cols} columns x {rows} rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One object per cell, centered, filling most of the cell, with clear margin; no text, no letters, no numbers, no grid lines, no shadows on the background.
Objects in order (left to right, top to bottom): {listed}."""


SCENE = (
    "Style: detailed 16-bit pixel art like a modern cozy indie game, crisp pixels, clean dark outline, 3-4 tone shading ramps, "
    "warm honey-and-wood palette with soft afternoon light."
)

OTHER = {
    "room": f"""Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a background scene for a cozy hobby app: the inside of a big warm attic workshop where little creatures practise crafts, seen from the front, side-on like a 2D game. Portrait 2:3.
{SCENE}
Top: a sloping wooden roof with beams, a big round window in the middle (its glass is flat solid pure magenta #FF00FF, the sky will be painted later), a garland of small cloth flags.
Middle: two long wooden shelves on the walls with jars of buttons, spools of thread, paint tubes, books, a few plants; a pegboard with tools; a small wooden stage-like platform on the left and a cozy armchair corner on the right.
Bottom: a warm plank floor with a big round braided rug in the middle, a low workbench at the back.
IMPORTANT: no animals, no creatures, no people, no text. Keep FIVE clear free spots for creatures: on the rug (centre, the largest), on the left platform, by the armchair (right), on the upper left shelf end and on the window sill. Nothing alive in the picture.""",
    "eggs": objects("a sprite sheet of 12 decorated eggs for a cozy hobby app, each egg hints at a craft by its painted pattern only.", 4, 3, [
        "an egg with painted music notes and staff lines", "an egg with paint splashes in red, blue and yellow", "an egg wrapped in red thread with cross-stitch pattern",
        "an egg with a wood-grain pattern and a few shavings", "an egg with printed book pages pattern and a ribbon", "an egg with a black-and-white checkerboard pattern",
        "an egg with green leaves and a tiny sprout on top", "an egg with dancing ribbons and motion swirls", "an egg with a gingham kitchen pattern and a flour dust",
        "an egg with a dark blue star-map pattern", "a plain cream egg with a golden question mark-shaped swirl", "a glowing golden egg with sparkles"]),
    "icons": objects("a sprite sheet of 16 chunky pixel art icons for a cozy hobby app's interface.", 4, 4, [
        "a tiny attic workshop house with a round window", "an open leather journal with a ribbon", "a brass stopwatch", "a paw print made of embroidery thread",
        "an embroidered round patch badge with a golden star", "a small chest with a brass lock", "a glowing spark of golden light", "a scroll with a red wax seal",
        "a postcard with a stamp", "a bar chart made of three wooden blocks", "a small brass bell", "an iron gear cog",
        "a pencil", "a lightbulb glowing", "a heart made of red yarn", "a calendar page with a ring binding (no numbers)"]),
    "items": objects("a sprite sheet of 16 cozy decorations for an attic hobby workshop, each a separate object, front view, side-on like a 2D game.", 4, 4, [
        "a tall potted monstera plant", "a brass floor lamp glowing warmly", "a dressmaker's mannequin with a half-sewn dress", "a small wooden easel with a canvas showing a landscape",
        "a vintage gramophone with a big horn", "a wooden spinning wheel", "a stack of colourful yarn balls in a basket", "a cozy patchwork armchair cushion",
        "a little cuckoo clock (no numbers)", "a world globe on a wooden stand", "a jar of fireflies glowing", "a fishbowl with a goldfish",
        "a string of warm fairy lights (horizontal)", "a small bookcase full of books", "a cat-tree scratching post with a little bed on top", "a copper kettle on a small stove"]),
    "patches": objects("a sprite sheet of 16 embroidered fabric patches (achievement badges) for a cozy hobby app, round or shield-shaped, visible stitching, thick satin-stitched border.", 4, 4, [
        "a patch with a small flame", "a patch with a golden star", "a patch with a crescent moon and stars", "a patch with a rising sun",
        "a patch with a mountain peak", "a patch with a sprouting seed", "a patch with a heart", "a patch with a lightning bolt",
        "a patch with a crown", "a patch with a key", "a patch with an hourglass", "a patch with a feather",
        "a patch with a compass rose", "a patch with a rainbow", "a patch with a trophy cup", "a patch with a four-leaf clover"]),
}

# Hobby icons: 40 tools, one per preset hobby (order of HOBBIES), 3 sheets of 4x4.
TOOLS = {
    "guitar": "an acoustic guitar", "piano": "a few piano keys in a small block", "singing": "a vintage microphone", "drums": "a snare drum with two drumsticks",
    "violin": "a violin with a bow", "drawing": "a sketchbook with a pencil", "watercolor": "a watercolour paint box with a brush", "painting": "a painter's palette with a brush",
    "calligraphy": "a dip pen and an ink pot", "digital": "a drawing tablet with a stylus", "photo": "a vintage film camera", "embroidery": "a round embroidery hoop with red thread",
    "knitting": "a ball of yarn with two knitting needles", "crochet": "a crochet hook and a granny square", "sewing": "a spool of thread with a needle", "pottery": "a clay vase",
    "woodwork": "a wood plane and a few shavings", "beading": "a string of colourful beads", "origami": "a white paper crane", "models": "a tiny painted figurine and a fine brush",
    "candles": "a candle in a glass jar", "journal": "a decorated journal with washi tape", "reading": "an open book", "writing": "a small typewriter",
    "languages": "two speech bubbles (empty)", "chess": "a chess knight piece", "puzzles": "two jigsaw pieces", "garden": "a watering can with a sprout",
    "fishing": "a fishing rod with a red-and-white float", "birding": "a pair of binoculars", "mushrooms": "a wicker basket with mushrooms", "dance": "a pair of ballet shoes",
    "yoga": "a rolled yoga mat", "cycling": "a bicycle", "climbing": "a climbing hold and a carabiner", "cooking": "a cooking pot with a ladle",
    "baking": "a cinnamon roll on a little plate", "coding": "a laptop with a glowing screen", "electronics": "a soldering iron and a small circuit board", "astronomy": "a brass telescope",
}
tool_list = [TOOLS[h] for h in HOBBIES]
for n in range(3):
    chunk = tool_list[n * 16:(n + 1) * 16]
    chunk += ["an empty wooden frame"] * (16 - len(chunk))
    OTHER[f"tools{n}"] = objects("a sprite sheet of 16 chunky pixel art icons of hobby tools for a cozy hobby app.", 4, 4, chunk)


def image_of(output):
    """The image made in the Codex session named in its output (other sessions may draw at the same time)."""
    m = re.search(r"session id:\s*([0-9a-f-]+)", output)
    if not m:
        return None
    pngs = sorted((GEN / m.group(1)).glob("*.png"), key=lambda p: p.stat().st_mtime)
    return pngs[-1] if pngs else None


def run(job):
    name, prompt, ref = job
    dst = OUT / f"{name}.png"
    if dst.exists():
        print("skip", name, flush=True)
        return
    cmd = [shutil.which("codex.cmd") or shutil.which("codex") or "codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only"]
    if ref:
        cmd += ["-i", str(REF)]
    cmd += ["-"]
    for attempt in range(2):
        r = subprocess.run(cmd, input=prompt, text=True, encoding="utf-8", errors="replace", capture_output=True, cwd=str(ROOT))
        p = image_of((r.stdout or "") + (r.stderr or ""))
        if p is not None:
            shutil.copy(p, dst)
            print("ok", name, flush=True)
            return
    print("FAIL", name, (r.stdout or "")[-300:], (r.stderr or "")[-300:], flush=True)


def jobs():
    out = [(k, v, False) for k, v in OTHER.items()]
    out += [(k, pet_prompt(v[6], v[7]), True) for k, v in HOBBIES.items()]
    out += [(f"own_{k}", pet_prompt(v[2], OWN_SIG), True) for k, v in OWN.items()]
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = sys.argv[1:]
    todo = [j for j in jobs() if not want or j[0] in want]
    with ThreadPoolExecutor(3) as ex:
        list(ex.map(run, todo))


if __name__ == "__main__":
    main()
