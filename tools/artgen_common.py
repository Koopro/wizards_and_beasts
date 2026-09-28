#!/usr/bin/env python3
"""Shared helpers for the procedural art generators in this folder.

The important piece is the *generated marker*: every PNG these tools write carries a
`Generator` text chunk. Without it a tool cannot tell its own previous output from art
a human drew, so it either refuses to refresh anything (once its output stops looking
like a flat placeholder) or happily overwrites real art. With it, the rule is simple:
regenerate anything we wrote, never touch anything we didn't.
"""

import io
import os
import zipfile

from PIL import Image, PngImagePlugin

MARKER_KEY = "Generator"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

_client_jar = None
_client_cache = {}


def minecraft_version():
    """The Minecraft version the mod builds against, read from `gradle.properties`.

    Pinned rather than globbed: the Gradle caches hold every version any project on the
    machine ever built, and `sorted(glob)[-1]` sorts lexically, so `1.21.4` beats `1.21.11`.
    """
    with open(os.path.join(REPO_ROOT, "gradle.properties"), encoding="utf-8") as fh:
        for line in fh:
            key, _, value = line.partition("=")
            if key.strip() == "minecraft_version":
                return value.strip()
    raise SystemExit("minecraft_version missing from gradle.properties")


def client_jar_path():
    """Path of the vanilla client jar for `minecraft_version()`.

    Search order:
      1. `WB_MC_CLIENT_JAR` (explicit override, any vanilla client jar of the right version);
      2. ModDevGradle's cache, which `./gradlew build` on this project always fills;
      3. ForgeGradle's cache, where the generators first looked (other projects may fill it).
    Every candidate is the same vanilla jar, so the choice does not change any output.
    """
    override = os.environ.get("WB_MC_CLIENT_JAR")
    if override:
        if not os.path.isfile(override):
            raise SystemExit(f"WB_MC_CLIENT_JAR points at a missing file: {override}")
        return override
    version = minecraft_version()
    caches = os.path.join(os.environ.get("GRADLE_USER_HOME", os.path.expanduser("~/.gradle")), "caches")
    for candidate in (
            os.path.join(caches, "neoformruntime", "artifacts", f"minecraft_{version}_client.jar"),
            os.path.join(caches, "forge_gradle", "minecraft_repo", "versions", version, "client-extra.jar"),
            os.path.join(caches, "forge_gradle", "minecraft_repo", "versions", version, "client.jar")):
        if os.path.isfile(candidate):
            return candidate
    raise SystemExit(f"no cached Minecraft {version} client jar found; run `./gradlew build` once "
                     f"(or set WB_MC_CLIENT_JAR)")


def client_jar():
    """The vanilla client jar, opened once per process."""
    global _client_jar
    if _client_jar is None:
        _client_jar = zipfile.ZipFile(client_jar_path())
    return _client_jar


def client_jar_texture(rel_path):
    """Load any texture out of the cached vanilla client jar, e.g.
    {@code "textures/gui/container/creative_inventory/tab_items.png"}.

    Vanilla art is read but never redistributed: only derived output is written into
    this repo. Deriving from vanilla is also the only way to keep a layout that the
    game blits at fixed offsets (slot grids, search boxes) pixel-aligned.
    """
    if rel_path in _client_cache:
        return _client_cache[rel_path]
    with client_jar().open("assets/minecraft/" + rel_path) as fh:
        img = Image.open(io.BytesIO(fh.read())).convert("RGBA")
    _client_cache[rel_path] = img
    return img


def marker(tool_name):
    """Marker value for a given tool, e.g. marker("beast_skins.py")."""
    return "wizards_and_beasts/tools/" + tool_name


def png_meta(marker_value):
    meta = PngImagePlugin.PngInfo()
    meta.add_text(MARKER_KEY, marker_value)
    return meta


# Generators whose output replaced an older tool's for good (the 2026-09-23 art rework). Their
# files are final art: no other tool may overwrite them, not even with --force, which is how an
# old generator's `--force` would otherwise quietly repaint 200 icons in the previous style.
# The owning tool itself writes freely; a new tool claiming an *old* tool's file is unaffected.
PROTECTED = frozenset(marker(n) for n in (
    "gui_parchment.py", "gui_chrome.py", "item_sprites.py", "item_geo.py", "spell_sigils.py"))


def save(img, path, marker_value):
    if os.path.exists(path):
        with Image.open(path) as im:
            current = im.info.get(MARKER_KEY)
        if current in PROTECTED and current != marker_value:
            print(f"refusing to overwrite {path}: it is {current.rsplit('/', 1)[-1]}'s art")
            return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, pnginfo=png_meta(marker_value))
    return True


def is_generated(path, marker_value=None):
    """True when this PNG carries our marker (optionally a specific tool's)."""
    if not os.path.exists(path):
        return False
    with Image.open(path) as im:
        found = im.info.get(MARKER_KEY)
    if found is None:
        return False
    return found.startswith("wizards_and_beasts/tools/") if marker_value is None \
        else found == marker_value


def unique_colours(path):
    with Image.open(path) as im:
        rgba = im.convert("RGBA")
        data = rgba.get_flattened_data() if hasattr(rgba, "get_flattened_data") else rgba.getdata()
        return len(set(data))


def is_regenerable(path, marker_value=None, flat_threshold=8):
    """Safe to overwrite: missing, our own output, or a flat-fill placeholder."""
    if not os.path.exists(path) or is_generated(path, marker_value):
        return True
    # Another generator's output is not ours to replace. This has to be tested before
    # the flat-fill check below: that check reads "few colours" as "untouched
    # placeholder", but every generated texture is now posterised to a Minecraft-sized
    # palette, so it holds for real art too (33 of 53 marked block textures). That let
    # any tool silently overwrite any other tool's work — block_textures.py flattened
    # the animated 16x128 floating_candle strip down to a static 16x16 this way.
    if is_generated(path):
        return False
    return unique_colours(path) <= flat_threshold


# --------------------------------------------------------------------------- colour

def hx(s):
    s = s.lstrip("#")
    if len(s) == 6:
        return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), int(s[6:8], 16))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def shade(rgb, f):
    r, g, b = rgb[:3]
    a = rgb[3] if len(rgb) > 3 else 255
    return (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))),
            max(0, min(255, int(b * f))), a)


# --------------------------------------------------------------------------- resampling

def pixelate(img, size):
    """Downsample supersampled art to `size` with hard pixel edges.

    Pillow's LANCZOS (and every other smooth filter) anti-aliases, which is exactly wrong
    for this art: a 16px item sprite downsampled that way arrives soft, low-contrast and
    visibly blurry against vanilla's crisp pixels. Minecraft textures are pixel art — an
    edge belongs to one pixel or the other, never half to each.

    So alpha is decided by majority coverage rather than averaged, and colour is the mean
    of only the *covered* source pixels. Averaging in the transparent background is the
    other half of why the old output looked washed out: every edge pixel was mixed toward
    nothing.
    """
    src = img.convert("RGBA")
    factor = src.width // size
    if factor < 1 or src.width % size or src.height % size:
        return src.resize((size, size), Image.NEAREST)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    cells = factor * factor
    for y in range(size):
        for x in range(size):
            r = g = b = 0
            covered = 0
            for sy in range(y * factor, (y + 1) * factor):
                for sx in range(x * factor, (x + 1) * factor):
                    pr, pg, pb, pa = sp[sx, sy]
                    if pa < 128:
                        continue
                    covered += 1
                    r += pr
                    g += pg
                    b += pb
            if covered * 2 < cells:          # less than half covered: this pixel is empty
                continue
            op[x, y] = (r // covered, g // covered, b // covered, 255)
    return out


def quantise(img, steps=6):
    """Snap each channel to `steps` levels, so shading reads as deliberate bands.

    Smooth gradients are the other thing that makes generated art look unlike hand-drawn
    pixel art: real sprites shade in a few discrete tones, not a continuous ramp.
    """
    out = img.copy()
    px = out.load()
    span = 255.0 / (steps - 1)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (int(round(r / span) * span), int(round(g / span) * span),
                        int(round(b / span) * span), a)
    return out


def posterise(img, colours):
    """Reduce to `colours` distinct shades — the single thing that makes generated art
    read as Minecraft art.

    Measured against vanilla: `stone` uses 4 colours, `oak_planks` 7, `cobblestone` 6,
    `iron_ingot` 8. Generated output was landing at 26-48, because mixing along continuous
    ramps and then adding noise produces a new shade almost every pixel. Pixel-to-pixel
    jitter was already in vanilla's range; palette size was the whole gap.

    Median-cut over the opaque pixels only. Quantising with the transparent background
    included spends palette entries describing nothing, and on a sprite that is mostly
    background it can spend most of them.
    """
    src = img.convert("RGBA")
    alpha = src.getchannel("A")
    opaque = [p[:3] for p in
              (src.get_flattened_data() if hasattr(src, "get_flattened_data") else src.getdata())
              if p[3] > 0]
    if not opaque:
        return src

    # A flat image of just the opaque colours gives median-cut a sample set with no
    # background in it; the resulting palette is then applied to the real image.
    sample = Image.new("RGB", (len(opaque), 1))
    sample.putdata(opaque)
    palette_img = sample.quantize(colors=min(colours, len(set(opaque))), method=Image.MEDIANCUT)
    palette = palette_img.getpalette()[:colours * 3]
    entries = [tuple(palette[i:i + 3]) for i in range(0, len(palette), 3)]

    out = src.copy()
    px = out.load()
    cache = {}
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            key = (r, g, b)
            hit = cache.get(key)
            if hit is None:
                hit = min(entries, key=lambda e: (e[0] - r) ** 2 + (e[1] - g) ** 2 + (e[2] - b) ** 2)
                cache[key] = hit
            px[x, y] = (hit[0], hit[1], hit[2], a)
    out.putalpha(alpha)
    return out
