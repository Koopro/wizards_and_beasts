#!/usr/bin/env python3
"""UV-aware beast skin painter for Wizards & Beasts.

The placeholder generator (`tools/creature_gen.py`) filled every entity texture
with a flat two-colour block, so a fully-rigged 25-bone dragon rendered as a
solid green silhouette. This tool replaces that with real skins by reading each
`.geo.json`, resolving the *box-UV* rectangle of every cube face, and painting
each face with:

  - a per-creature canon palette, keyed by bone role (head / limb / wing / horn / ...)
  - directional shading, so top faces read lit and undersides read dark
  - a per-species surface pattern (scales, fur, feathers, chitin, bark, ...)
  - contour darkening on face borders, which is what makes 16px art read at range
  - hand-placed details: eyes on the head's front face, teeth on jaws, claws on feet

Output size always comes from the model's declared `texture_width/height`, which
also repairs the 14 models whose shipped PNG had the wrong aspect ratio (every
dragon declared 128x144 but shipped a 64x64 fill, so its UVs sampled garbage).

Creatures listed with a `glow` tag additionally get `<id>_glowmask.png` for
GeckoLib's `AutoGlowingGeoLayer` (eyes, fire vents, bioluminescence).

Run from the repo root:  python tools/beast_skins.py [--force] [--only id,id]
Deterministic — safe to re-run. Textures this tool wrote carry a PNG `Generator`
marker and are refreshed; anything hand-authored is left alone unless --force.
"""

import argparse
import glob
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_generated, is_regenerable, marker, posterise, save  # noqa: E402

# Stamped into every PNG this tool writes, so a re-run refreshes its own output while
# leaving hand-drawn skins alone. See tools/artgen_common.py.
MARKER = marker("beast_skins.py")

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "entity")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")

# Models that are not creature skins and must never be repainted.
EXCLUDE = {"protego_shield", "broom"}


# --------------------------------------------------------------------------- palette

def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def shift(rgb, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in rgb)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# Relief is applied by blending toward a warm highlight / cool shadow rather than by
# multiplying the channel values: multiplying a near-black dragon scale by 1.2 is
# invisible, blending it 20% toward the highlight is not.
HILIGHT = (255, 246, 224)
SHADOW = (16, 14, 24)


def lit(rgb, f):
    if f >= 1.0:
        return mix(rgb, HILIGHT, min(0.55, (f - 1.0) * 0.95))
    return mix(rgb, SHADOW, min(0.60, (1.0 - f) * 0.95))


class Skin:
    """One creature's colour scheme. Unset roles derive from `body`."""

    def __init__(self, style, body, belly=None, head=None, limb=None, wing=None,
                 horn=None, eye="#c8b45a", accent=None, glow=None):
        self.style = style
        self.body = hx(body)
        self.belly = hx(belly) if belly else shift(self.body, 1.35)
        self.head = hx(head) if head else shift(self.body, 1.06)
        self.limb = hx(limb) if limb else shift(self.body, 0.88)
        self.wing = hx(wing) if wing else shift(self.body, 0.80)
        self.horn = hx(horn) if horn else (196, 184, 156)
        self.eye = hx(eye)
        self.accent = hx(accent) if accent else shift(self.body, 0.62)
        # glow: set of tags among {"eye", "accent", "belly", "body", "horn"}
        self.glow = set(glow.split("+")) if glow else set()

    def role_colour(self, role):
        return {
            "head": self.head,
            "jaw": self.head,
            "horn": self.horn,
            "wing": self.wing,
            "membrane": mix(self.wing, self.belly, 0.35),
            "limb": self.limb,
            "foot": mix(self.limb, self.horn, 0.25),
            "tail": mix(self.body, self.accent, 0.25),
            "shell": mix(self.body, self.horn, 0.3),
            "hair": mix(self.body, self.accent, 0.5),
            "fin": mix(self.wing, self.belly, 0.2),
            "body": self.body,
        }.get(role, self.body)


P = Skin

# Canon-grounded palettes. Colours follow Fantastic Beasts / HP book descriptions
# where one exists ("brilliant green with scarlet plume", "sapphire blue", ...),
# otherwise the creature's habitat and body plan.
SKINS = {
    # --- dragons (ten breeds, canon scale colours) -------------------------
    "antipodean_opaleye": P("SCALES", "#e8e2ee", belly="#fbf8ff", horn="#cfc4d8",
                            eye="#e8d9f5", accent="#bcaed2", wing="#d8cfe4", glow="eye"),
    "chinese_fireball":   P("SCALES", "#b3241d", belly="#e8843a", horn="#e8c24a",
                            eye="#ffcc33", accent="#7d1410", wing="#8f1c17", glow="eye"),
    "common_welsh_green": P("SCALES", "#3f6b2c", belly="#93ab5c", horn="#8f7a4a",
                            eye="#d8c14a", accent="#2b4a1d", wing="#35592a"),
    "hebridean_black":    P("SCALES", "#322a40", belly="#4a3a5c", horn="#6b5a7a",
                            eye="#a855f7", accent="#160f1e", wing="#1d1726", glow="eye"),
    "hungarian_horntail": P("SCALES", "#33302f", belly="#4a3f33", horn="#a8763a",
                            eye="#ffd24a", accent="#0f0e10", wing="#2b2426", glow="eye"),
    "norwegian_ridgeback": P("SCALES", "#2b2622", belly="#5c4a38", horn="#c08a3a",
                             eye="#e0a83a", accent="#191614", wing="#332c27"),
    "peruvian_vipertooth": P("SCALES", "#a8622c", belly="#d69a5a", horn="#2b2622",
                             eye="#c93a2b", accent="#6b3a17", wing="#8f5024", glow="eye"),
    "romanian_longhorn":  P("SCALES", "#2f4a2b", belly="#6b7a4a", horn="#d6b054",
                            eye="#d6b054", accent="#1c2e19", wing="#284024"),
    "swedish_short_snout": P("SCALES", "#7e93ad", belly="#c2d4e0", horn="#9aa8b5",
                             eye="#6fc7e8", accent="#5a6d85", wing="#6e809a", glow="eye"),
    "ukrainian_ironbelly": P("METAL", "#8c8f93", belly="#b8bcc0", horn="#6b6e72",
                             eye="#c93a2b", accent="#63666a", wing="#7a7d81"),

    # --- serpents & reptiles ----------------------------------------------
    "basilisk":       P("SCALES", "#2f7a2c", belly="#93bf5a", horn="#c8442e",
                        eye="#f2e14a", accent="#1d5220", glow="eye"),
    "ashwinder":      P("SCALES", "#8f8a85", belly="#c2bdb5", horn="#5c5751",
                        eye="#e8452b", accent="#5c5751", glow="eye"),
    "horned_serpent": P("SCALES", "#2f5a6b", belly="#7aa8b5", horn="#d6c25a",
                        eye="#d6c25a", accent="#1c3a47", glow="horn"),
    "occamy":         P("FEATHER", "#3f7a8c", belly="#9ed6d4", horn="#c2b05a",
                        eye="#e8d24a", accent="#245663", wing="#2f6270"),
    "runespoor":      P("SCALES", "#c26a1d", belly="#e0a04a", horn="#241d17",
                        eye="#e8b83a", accent="#241d17"),
    "sea_serpent":    P("SCALES", "#2b6b7a", belly="#8fc2bd", horn="#5a8f8a",
                        eye="#9ee0d6", accent="#1a4750"),
    "salamander":     P("SCALES", "#e05a1d", belly="#f2b04a", horn="#8f2b0f",
                        eye="#ffd24a", accent="#a8320f", glow="eye+accent"),
    "moke":           P("SCALES", "#5c6b52", belly="#8f9a7a", horn="#3f4a38",
                        eye="#c2b05a", accent="#3f4a38"),
    "snallygaster":   P("SCALES", "#5a4a3f", belly="#8f7a63", horn="#c2a05a",
                        eye="#e0c24a", accent="#3a2f28", wing="#4a3d33"),
    "hidebehind":     P("SHAGGY", "#3a3129", belly="#5c4f42", horn="#1c1714",
                        eye="#e8e0c2", accent="#221c17", glow="eye"),
    "lobalug":        P("SLIME", "#4a6b7a", belly="#7aa0ad", horn="#2f4550",
                        eye="#c2d6de", accent="#2f4550"),

    # --- arthropods & shelled ---------------------------------------------
    "acromantula":       P("CHITIN", "#332c27", belly="#4a3f38", horn="#8f7a5a",
                           eye="#c93a2b", accent="#151210", glow="eye"),
    "blast_ended_skrewt": P("CHITIN", "#c2a882", belly="#e0cda8", horn="#8f6b3a",
                            eye="#5c4a2b", accent="#8f7552", glow="accent"),
    "chizpurfle":        P("CHITIN", "#5a3a1d", belly="#8f6b3a", horn="#e0d6c2",
                           eye="#c9b03a", accent="#38240f"),
    "fire_crab":         P("CHITIN", "#a8442b", belly="#d6874a", horn="#e8c24a",
                           eye="#241d17", accent="#e8c24a", glow="accent"),
    "billywig":          P("CHITIN", "#2b52c9", belly="#6b93e8", horn="#d6d6e8",
                           eye="#e8e8f2", accent="#1a3a8f", wing="#93b0f2", glow="eye"),
    "doxy":              P("CHITIN", "#332a33", belly="#4a3f4a", horn="#6b5a6b",
                           eye="#c95a8f", accent="#150f15", wing="#5a4a5c"),
    "glumbumble":        P("SHAGGY", "#3a3129", belly="#5c4f3a", horn="#241d17",
                           eye="#c2a03a", accent="#221c14", wing="#4a4038"),
    "horklump":          P("SLIME", "#d68fa8", belly="#f2c2d4", horn="#241d1c",
                           eye="#8f5a6b", accent="#241d1c"),
    "shrake":            P("SCALES", "#3f4a5c", belly="#7a8899", horn="#c2c9d4",
                           eye="#d6d6c2", accent="#28303d"),
    "quintaped":         P("SHAGGY", "#4a2f1d", belly="#6b4a2f", horn="#241a10",
                           eye="#c93a2b", accent="#2f1c10", glow="eye"),
    "mackled_malaclaw":  P("CHITIN", "#7a8f5a", belly="#a8bd82", horn="#4a5a33",
                           eye="#c2c93a", accent="#4a5a33"),
    "streeler":          P("CHITIN", "#8f5ac2", belly="#c29ae0", horn="#5a2f8f",
                           eye="#e8d64a", accent="#5a2f8f"),

    # --- aquatic -----------------------------------------------------------
    "grindylow":   P("SLIME", "#5a8f5a", belly="#8fbd8f", horn="#2f4a2f",
                     eye="#e8e05a", accent="#2f4a2f", glow="eye"),
    "hippocampus": P("SCALES", "#3f7a9a", belly="#9ad6e0", horn="#c2b05a",
                     eye="#d6e8f2", accent="#28566b", limb="#5a93a8"),
    "kappa":       P("SCALES", "#4a6b3a", belly="#8fa85a", horn="#2f4526",
                     eye="#c2c93a", accent="#2f4526"),
    "merperson":   P("SCALES", "#5a7a6b", belly="#93a89a", horn="#2f4538",
                     eye="#c9d6b0", accent="#3a5247"),
    "plimpy":      P("SCALES", "#8fa8b5", belly="#c2d4de", horn="#5a6b75",
                     eye="#e0e8ee", accent="#5a6b75"),
    "ramora":      P("SCALES", "#b5bcc2", belly="#e0e4e8", horn="#7a8288",
                     eye="#c2d6e8", accent="#8f979d", glow="eye"),
    "kelpie":      P("SHAGGY", "#2f4a52", belly="#5a7a82", horn="#1c2e33",
                     eye="#9ee0d6", accent="#1c2e33", glow="eye"),

    # --- birds & flyers ----------------------------------------------------
    "diricawl":       P("FEATHER", "#8f7a5a", belly="#c2ad8f", horn="#c9a03a",
                        eye="#241d17", accent="#6b5a3f", wing="#7a6647"),
    "fwooper":        P("FEATHER", "#e05a9a", belly="#f2a8c9", horn="#e8c24a",
                        eye="#241d17", accent="#c23a7a", wing="#c93a8f"),
    "golden_snidget": P("FEATHER", "#e8c24a", belly="#f2dd93", horn="#c99a2b",
                        eye="#241d17", accent="#c99a2b", wing="#dbb03a", glow="body"),
    "griffin":        P("FEATHER", "#c2a05a", belly="#e0cd93", horn="#8f6b2b",
                        eye="#e8c24a", accent="#8f7a3a", limb="#a8823a", wing="#b5934a"),
    "jobberknoll":    P("FEATHER", "#4a6bc2", belly="#93aae8", horn="#c2c9d6",
                        eye="#241d2b", accent="#2f4a8f", wing="#3f5ca8"),
    "thunderbird":    P("FEATHER", "#5a6b8f", belly="#93a8c2", horn="#c2b05a",
                        eye="#e8e05a", accent="#3a4763", wing="#4a5a7a", glow="eye+accent"),
    "augurey_alt":    P("FEATHER", "#2f3a2b", belly="#5a6b4a", horn="#4a4a3a",
                        eye="#c2c93a", accent="#1c2419"),
    "swooping_evil":  P("CHITIN", "#2b4a8f", belly="#5a8fd6", horn="#e0d64a",
                        eye="#e8e8a8", accent="#e0d64a", wing="#3f6bb5", glow="accent"),
    "zouwu":          P("FUR", "#c25a3a", belly="#e8a882", horn="#e8d64a",
                        eye="#5ad6e0", accent="#8f2f1d", glow="eye"),

    # --- mammals & fur -----------------------------------------------------
    "abraxan":     P("FUR", "#e0c99a", belly="#f2e4c2", horn="#c2a87a",
                     eye="#5a4a2f", accent="#c2a87a", wing="#e8d6b0"),
    "aethonan":    P("FUR", "#8f4a2b", belly="#c2825a", horn="#5a2f1a",
                     eye="#3a2419", accent="#5a2f1a", wing="#a8603a"),
    "granian":     P("FUR", "#93999e", belly="#c2c7cc", horn="#5a6065",
                     eye="#3a3f44", accent="#6b7176", wing="#a8aeb3"),
    "unicorn":     P("FUR", "#f2f0ee", belly="#ffffff", horn="#e0e4ea",
                     eye="#6b93c2", accent="#d6d6de", glow="horn"),
    "qilin":       P("SCALES", "#e8e4d6", belly="#fbf8ee", horn="#d6c25a",
                     eye="#c2a03a", accent="#c9c2a8", glow="horn+eye"),
    "crup":        P("FUR", "#c2b09a", belly="#e0d4c2", horn="#5a4a3a",
                     eye="#3a2f24", accent="#8f7a63"),
    "kneazle":     P("FUR", "#8f7a5a", belly="#c2ad8f", horn="#3a2f24",
                     eye="#c2c93a", accent="#5a4a33", glow="eye"),
    "jarvey":      P("FUR", "#7a5a3a", belly="#a8875a", horn="#3a2819",
                     eye="#c9a03a", accent="#4a3524"),
    "knarl":       P("SHAGGY", "#5a4a38", belly="#8f7a5a", horn="#c2ad8f",
                     eye="#241d17", accent="#3a2f24"),
    "murtlap":     P("SLIME", "#6b5a4a", belly="#8f7a63", horn="#c25a8f",
                     eye="#241d1c", accent="#c25a8f"),
    "niffler":     P("SHAGGY", "#332c33", belly="#4a3f47", horn="#c2ad5a",
                     eye="#3f2f1d", accent="#151215"),
    "nundu":       P("FUR", "#c2a05a", belly="#e0cd9a", horn="#3a2f1d",
                     eye="#8fc93a", accent="#8f7033", glow="eye"),
    "puffskein":   P("SHAGGY", "#e0c25a", belly="#f2dd9a", horn="#c99a3a",
                     eye="#3a2f1d", accent="#c99a3a"),
    "pygmy_puff":  P("SHAGGY", "#e08fc2", belly="#f2c2de", horn="#c25a9a",
                     eye="#3a2436", accent="#c25a9a"),
    "tebo":        P("FUR", "#8f8a82", belly="#b5b0a8", horn="#d6cdb5",
                     eye="#3a352f", accent="#63605a"),
    "reem":        P("FUR", "#c29a5a", belly="#e0c293", horn="#8f6b2b",
                     eye="#e8c24a", accent="#8f7033", glow="eye"),
    "graphorn":    P("SCALES", "#6b5a7a", belly="#93829a", horn="#d6c25a",
                     eye="#c9a03a", accent="#463a52", glow="horn"),
    "erumpent":    P("SCALES", "#8f8275", belly="#b5aa9a", horn="#d6c9a8",
                     eye="#3a332b", accent="#63594f"),
    "hodag":       P("SCALES", "#5a4a2f", belly="#8f7a52", horn="#c2b05a",
                     eye="#c93a2b", accent="#3a2f1c", glow="eye"),
    "matagot":     P("SHAGGY", "#332c3a", belly="#3f3a4a", horn="#5a5263",
                     eye="#e8b83a", accent="#151220", glow="eye"),
    "wampus_cat":  P("FUR", "#8f6b3a", belly="#c29a63", horn="#3a2b17",
                     eye="#e8d64a", accent="#5a4224", glow="eye"),
    "rougarou":    P("SHAGGY", "#3a2f24", belly="#5c4a38", horn="#241a12",
                     eye="#c93a2b", accent="#241a12", glow="eye"),
    "werewolf":    P("SHAGGY", "#4a4038", belly="#6b5f52", horn="#d6cdbd",
                     eye="#c9a03a", accent="#2f2823", glow="eye"),
    "maledictus":  P("SCALES", "#5a4a63", belly="#8f7a9a", horn="#3a2f42",
                     eye="#c2a8d6", accent="#3a2f42", glow="eye"),
    "nogtail":     P("FUR", "#d6a8a8", belly="#e8c9c9", horn="#8f5a5a",
                     eye="#3a2424", accent="#a87a7a"),
    "porlock":     P("SHAGGY", "#7a6b4a", belly="#a89a75", horn="#4a3f2b",
                     eye="#3a2f1d", accent="#4a3f2b"),
    "pogrebin":    P("STONE", "#6b6660", belly="#8f8a82", horn="#4a4640",
                     eye="#c93a2b", accent="#4a4640", glow="eye"),
    "demiguise_alt": P("SHAGGY", "#d6d2c9", belly="#eeebe4", horn="#a8a49a",
                       eye="#5a93c2", accent="#b5b0a8"),
    "demiguise":   P("SHAGGY", "#d6d2c9", belly="#eeebe4", horn="#a8a49a",
                     eye="#5a93c2", accent="#b5b0a8", glow="eye"),
    "clabbert":    P("SKIN", "#5a8f6b", belly="#8fbd93", horn="#3a5a44",
                     eye="#c2d63a", accent="#c93a2b", glow="accent"),
    "dugbog":      P("BARK", "#4a4a33", belly="#6b6b4a", horn="#c2b08f",
                     eye="#c9a03a", accent="#2f2f1f", glow="eye"),
    "bundimun":    P("SLIME", "#7aa83a", belly="#a8c95a", horn="#4a6b1d",
                     eye="#e8e05a", accent="#4a6b1d", glow="eye"),
    "flobberworm": P("SLIME", "#8f7a5a", belly="#b5a082", horn="#6b5a3f",
                     eye="#6b5a3f", accent="#6b5a3f"),

    # --- humanoids ---------------------------------------------------------
    "centaur":     P("FUR", "#7a5a3a", belly="#a8875a", horn="#3a2819",
                     eye="#3a2819", accent="#c2a882", head="#c2996b"),
    "erkling":     P("SKIN", "#7a8f4a", belly="#a8bd75", horn="#3a4a1d",
                     eye="#c9c93a", accent="#4a5a24", glow="eye"),
    "gnome":       P("SKIN", "#a8825a", belly="#c9a882", horn="#5a3f24",
                     eye="#3a2b17", accent="#6b4a2b"),
    "goblin_teller": P("SKIN", "#93a882", belly="#b5c9a8", horn="#4a5a3a",
                       eye="#3a2b17", accent="#5a6b47"),
    "leprechaun":  P("CLOTH", "#2f8f4a", belly="#5ac27a", horn="#d6c25a",
                     eye="#3a2b17", accent="#1d5c2f"),
    "imp":         P("SKIN", "#8f4a5a", belly="#b5757a", horn="#4a2429",
                     eye="#e8c24a", accent="#4a2429", glow="eye"),
    "red_cap":     P("CLOTH", "#5a4a3a", belly="#7a6b52", horn="#8f1d1d",
                     eye="#c93a2b", accent="#8f1d1d", head="#a8825a", glow="eye"),
    "pukwudgie":   P("SKIN", "#8f6b4a", belly="#b5906b", horn="#3a2b1d",
                     eye="#3a2b1d", accent="#5a4230"),
    "giant":       P("SKIN", "#a88a6b", belly="#c9ad8f", horn="#5a4230",
                     eye="#3a2b17", accent="#6b5240"),
    "troll":       P("STONE", "#7a8272", belly="#9aa192", horn="#4a5245",
                     eye="#c9a03a", accent="#5a6155"),
    "yeti":        P("SHAGGY", "#e4e8ee", belly="#ffffff", horn="#a8b0bd",
                     eye="#5ac2e0", accent="#c2cdd6", glow="eye"),
    "sphinx":      P("FUR", "#c2a05a", belly="#e0cd9a", horn="#3a2f1d",
                     eye="#e8d64a", accent="#8f7033", head="#c9a87a"),
    "manticore":   P("FUR", "#8f3a2b", belly="#c26b4a", horn="#e8c24a",
                     eye="#e8c24a", accent="#5a2117", head="#a8825a", glow="eye"),
    "chimaera":    P("FUR", "#a8622b", belly="#d69a5a", horn="#e0cd93",
                     eye="#c93a2b", accent="#3f6b2c", glow="eye"),
    "hippogriff":  P("FEATHER", "#8f8a82", belly="#c2bdb5", horn="#c9a03a",
                     eye="#e8c24a", accent="#63605a", limb="#7a756d", wing="#a8a49a"),
    "ghoul":       P("SLIME", "#7a6b6b", belly="#9a8a8a", horn="#4a3f3f",
                     eye="#c9c93a", accent="#4a3f3f", glow="eye"),
    "boggart":     P("SMOKE", "#2e2838", belly="#3f3747", horn="#151220",
                     eye="#c93a2b", accent="#151220", glow="eye"),
    "obscurus":    P("SMOKE", "#26212f", belly="#332c40", horn="#0f0d14",
                     eye="#8f5ac2", accent="#5a2f8f", glow="eye+accent"),
    "lethifold":   P("CLOTH", "#221e2b", belly="#241f2b", horn="#0d0b10",
                     eye="#3a2f47", accent="#0d0b10"),
    "fairy":       P("FEATHER", "#e8c9f2", belly="#fbeeff", horn="#c29ad6",
                     eye="#8f5ac2", accent="#c29ad6", wing="#d6b0e8", glow="body"),
    "toad":        P("SLIME", "#6b7a3a", belly="#a8b575", horn="#4a5524",
                     eye="#c9a03a", accent="#4a5524"),
}

# The Kelpie's lure guise is a docile-looking tame horse, deliberately unremarkable —
# the whole point is that it reads as safe until it drags you under.
SKINS["kelpie_disguise"] = P("FUR", "#6b4a2f", belly="#93673f", horn="#3a2417",
                             eye="#3a2b1d", accent="#4a3320")

DEFAULT_SKIN = P("SKIN", "#7a7268", belly="#9a938a", horn="#c2b8a8",
                 eye="#c2a03a", accent="#5a544c")


# --------------------------------------------------------------------------- roles

ROLE_KEYWORDS = [
    ("membrane", "membrane"),
    ("wing", "wing"),
    ("jaw", "jaw"),
    ("beak", "jaw"),
    ("snout", "jaw"),
    ("muzzle", "jaw"),
    ("head", "head"),
    ("skull", "head"),
    ("ear", "head"),
    ("eye", "head"),
    ("horn", "horn"),
    ("antler", "horn"),
    ("tusk", "horn"),
    ("spike", "horn"),
    ("spine", "horn"),
    ("barb", "horn"),
    ("sting", "horn"),
    ("claw", "horn"),
    ("fang", "horn"),
    ("tooth", "horn"),
    ("crest", "horn"),
    ("plume", "hair"),
    ("mane", "hair"),
    ("hair", "hair"),
    ("fur", "hair"),
    ("foot", "foot"),
    ("hoof", "foot"),
    ("paw", "foot"),
    ("hand", "foot"),
    ("fin", "fin"),
    ("flipper", "fin"),
    ("shell", "shell"),
    ("carapace", "shell"),
    ("plate", "shell"),
    ("tail", "tail"),
    ("leg", "limb"),
    ("arm", "limb"),
    ("tentacle", "limb"),
    ("neck", "body"),
    ("thorax", "body"),
    ("abdomen", "body"),
    ("torso", "body"),
    ("chest", "body"),
    ("body", "body"),
]


def bone_role(name):
    n = name.lower()
    for key, role in ROLE_KEYWORDS:
        if key in n:
            return role
    return "body"


# --------------------------------------------------------------------------- noise

def noise(seed, x, y):
    """Deterministic 0..1 hash noise — no numpy, no RNG state."""
    n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# --------------------------------------------------------------------------- patterns

def pattern_factor(style, lx, ly, fw, fh, seed):
    """Brightness multiplier for one pixel of one face, by surface style.

    lx/ly are pixel coordinates local to the face; fw/fh the face size.
    """
    if style == "SCALES":
        row = ly // 3
        cy = ly % 3
        cx = (lx + (row % 2) * 2) % 3
        f = 1.12 if cy == 0 else (0.84 if cy == 2 else 1.0)
        if cx == 0:
            f *= 0.92
        return f * (0.97 + 0.06 * noise(seed, lx, ly))

    if style == "METAL":
        f = 1.14 if ly % 4 == 0 else (0.86 if ly % 4 == 3 else 1.0)
        return f * (0.98 + 0.04 * noise(seed, lx, ly))

    if style in ("FUR", "SHAGGY"):
        amp = 0.30 if style == "SHAGGY" else 0.18
        col = noise(seed, lx, ly // (4 if style == "SHAGGY" else 3))
        return (1.0 - amp / 2) + amp * col

    if style == "FEATHER":
        dx = abs((lx % 5) - 2)
        band = 1.10 - 0.08 * dx
        if ly % 4 == 3:
            band *= 0.84
        return band * (0.98 + 0.04 * noise(seed, lx, ly))

    if style == "CHITIN":
        px, py = lx % 4, ly % 4
        if px == 3 or py == 3:
            return 0.76
        if px == 0 and py == 0:
            return 1.22
        return 1.0

    if style == "BARK":
        f = 0.88 + 0.22 * noise(seed, lx // 2, ly // 5)
        if lx % 3 == 2:
            f *= 0.86
        return f

    if style == "SLIME":
        blob = noise(seed, lx // 3, ly // 3)
        return 0.88 + 0.26 * blob

    if style == "STONE":
        return 0.86 + 0.28 * noise(seed, lx, ly)

    if style == "CLOTH":
        f = 1.0
        if lx % 5 == 0:
            f = 1.10
        elif lx % 5 == 3:
            f = 0.82
        return f * (0.99 + 0.02 * noise(seed, lx, ly))

    if style == "SMOKE":
        return 0.72 + 0.5 * noise(seed, lx // 2, ly // 2)

    # SKIN and anything unknown: subtle mottling only.
    return 0.95 + 0.10 * noise(seed, lx // 2, ly // 2)


# --------------------------------------------------------------------------- box UV

# Directional light: top lit, underside dark, front slightly brighter than back.
FACE_SHADE = {
    "up": 1.22,
    "down": 0.70,
    "north": 1.04,
    "south": 0.92,
    "east": 0.86,
    "west": 0.98,
}


def box_faces(u, v, w, h, d):
    """Standard Minecraft box-UV unwrap. Returns name -> (x0, y0, x1, y1)."""
    return {
        "up":    (u + d,             v,     u + d + w,             v + d),
        "down":  (u + d + w,         v,     u + d + w + w,         v + d),
        "east":  (u,                 v + d, u + d,                 v + d + h),
        "north": (u + d,             v + d, u + d + w,             v + d + h),
        "west":  (u + d + w,         v + d, u + d + w + d,         v + d + h),
        "south": (u + d + w + d,     v + d, u + d + w + d + w,     v + d + h),
    }


def net_size(w, h, d):
    """Footprint the box-UV net of one cube occupies, in whole texels."""
    return int(math.ceil(2 * d + 2 * w)), int(math.ceil(d + h))


# --------------------------------------------------------------------------- unwrap

ATLAS_WIDTHS = (32, 64, 96, 128, 160, 192, 256, 320, 384, 512)


def shelf_pack(boxes, width, gap=1):
    """Greedy shelf packer. `boxes` = [(w, h, key)] sorted by caller.

    Returns (placements, total_height) or (None, None) if any box is too wide.
    """
    placements = {}
    x = gap
    y = gap
    shelf_h = 0
    for bw, bh, key in boxes:
        if bw + 2 * gap > width:
            return None, None
        if x + bw + gap > width:
            x = gap
            y += shelf_h + gap
            shelf_h = 0
        placements[key] = (x, y)
        x += bw + gap
        shelf_h = max(shelf_h, bh)
    return placements, y + shelf_h + gap


def unwrap_model(geo_path):
    """Assign every cube a non-overlapping box-UV slot and resize the atlas.

    The placeholder rig generator emitted every cube at uv [0, 0], so all 25 bones
    of a dragon sampled the same texture corner — no texture could ever show a head
    that differed from a foot. This lays the cubes out on a shelf-packed atlas and
    rewrites the model in place. Geometry is untouched; only UVs and the declared
    texture size change.

    Returns (texture_width, texture_height, changed).
    """
    doc = json.load(open(geo_path, encoding="utf-8"))
    geo = doc["minecraft:geometry"][0]

    boxes = []
    cubes = []
    for bi, bone in enumerate(geo["bones"]):
        for ci, cube in enumerate(bone.get("cubes", [])):
            w, h, d = (float(s) for s in cube["size"])
            nw, nh = net_size(w, h, d)
            key = (bi, ci)
            boxes.append((nw, nh, key))
            cubes.append((key, cube))
    if not cubes:
        return None, None, False

    # Tall-first shelf packing wastes far less than insertion order.
    boxes.sort(key=lambda b: (-b[1], -b[0]))

    best = None
    for width in ATLAS_WIDTHS:
        placements, height = shelf_pack(boxes, width)
        if placements is None:
            continue
        height = int(math.ceil(height / 8.0) * 8)
        # Prefer roughly square atlases; never taller than 2x the width.
        if height > width * 2:
            continue
        best = (width, height, placements)
        break
    if best is None:
        width = ATLAS_WIDTHS[-1]
        placements, height = shelf_pack(boxes, width)
        best = (width, int(math.ceil(height / 8.0) * 8), placements)

    width, height, placements = best
    for key, cube in cubes:
        cube["uv"] = list(placements[key])
    geo["description"]["texture_width"] = width
    geo["description"]["texture_height"] = height

    with open(geo_path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(doc, fh, indent=2)
        fh.write("\n")
    return width, height, True


# --------------------------------------------------------------------------- painter

def paint_creature(cid, geo_path, out_path, glow_path):
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    desc = geo["description"]
    tw = int(desc.get("texture_width") or 64)
    th = int(desc.get("texture_height") or 64)

    skin = SKINS.get(cid, DEFAULT_SKIN)
    seed = sum(ord(c) * (i + 7) for i, c in enumerate(cid)) & 0xFFFF

    img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    px = img.load()
    gx = glow.load()

    def put(x, y, rgb, glowing=False):
        if 0 <= x < tw and 0 <= y < th:
            px[x, y] = (rgb[0], rgb[1], rgb[2], 255)
            if glowing:
                gx[x, y] = (rgb[0], rgb[1], rgb[2], 255)

    # Whether "horn" glow has anywhere to land on this particular rig.
    rig_has_horn = any(bone_role(b["name"]) == "horn" for b in geo["bones"])

    for bone in geo["bones"]:
        role = bone_role(bone["name"])
        base = skin.role_colour(role)
        for ci, cube in enumerate(bone.get("cubes", [])):
            uv = cube.get("uv")
            if not isinstance(uv, list):
                continue  # per-face UV: not produced by this rig generator
            u, v = float(uv[0]), float(uv[1])
            w, h, d = (float(s) for s in cube["size"])
            faces = box_faces(u, v, w, h, d)
            cube_seed = (seed + ci * 131 + len(bone["name"]) * 17) & 0xFFFF

            for fname, (fx0, fy0, fx1, fy1) in faces.items():
                x0, y0 = int(math.floor(fx0)), int(math.floor(fy0))
                x1, y1 = int(math.ceil(fx1)), int(math.ceil(fy1))
                fw, fh = max(1, x1 - x0), max(1, y1 - y0)
                shade = FACE_SHADE[fname]

                # Undersides of the trunk read as belly/plating on most animals.
                face_base = base
                if fname == "down" and role in ("body", "tail"):
                    face_base = mix(base, skin.belly, 0.65)
                elif fname == "up" and role in ("body", "tail"):
                    face_base = mix(base, skin.accent, 0.30)
                if role == "horn":
                    # Horns/claws/spikes fade to a pale tip.
                    face_base = skin.horn

                for ly in range(fh):
                    for lx in range(fw):
                        f = shade * pattern_factor(skin.style, lx, ly, fw, fh, cube_seed)

                        # Contour: darken the face border so cubes stay legible at range.
                        if lx == 0 or ly == 0 or lx == fw - 1 or ly == fh - 1:
                            f *= 0.86
                        if fname == "up" and ly == 0:
                            f *= 1.12

                        col = lit(face_base, f)

                        # Horn tip gradient (top of the face = brightest).
                        if role == "horn" and fh > 2:
                            col = mix(col, (255, 255, 255), 0.28 * (1.0 - ly / max(1, fh - 1)))

                        # "accent" was accepted by the tag parser and then handled by nobody,
                        # so four creatures asked to glow and shipped a blank mask: the fire
                        # crab's shell, the swooping evil, the clabbert and the blast-ended
                        # skrewt. Accent is the colour mixed into upper surfaces, so that is
                        # what lights — the mirror of the belly rule directly above.
                        glowing = ("body" in skin.glow) or \
                                  ("horn" in skin.glow and role == "horn") or \
                                  ("belly" in skin.glow and fname == "down" and role in ("body", "tail")) or \
                                  ("accent" in skin.glow and fname == "up" and role in ("body", "tail"))
                        put(x0 + lx, y0 + ly, col, glowing)

                # --- hand-placed details -------------------------------------
                # Guard is 2x2, not 3x3: at 3x3 a head face one texel narrower than that got
                # no eyes at all rather than small ones, which is why the Billywig shipped
                # blank-faced with an empty glowmask despite asking for eye glow.
                if role == "head" and fname == "north" and fw >= 2 and fh >= 2:
                    # Eyes stay one texel tall: on a 5-wide face a 1x2 eye reads as a
                    # black slab, not an eye. They only widen once the face can carry it.
                    glow_eye = "eye" in skin.glow
                    ey = y0 + max(1, fh // 3)
                    inset = max(1, fw // 5)
                    left, right = x0 + inset, x0 + fw - 1 - inset
                    if right - left >= 2:
                        # Deliberately one texel each: widening them to two merges the pair
                        # into a single band across wide heads (the Red Cap read as a visor).
                        put(left, ey, skin.eye, glow_eye)
                        put(right, ey, skin.eye, glow_eye)
                        if fh >= 6:
                            # Brow line: one darker row above the eyes sells the face at range.
                            for bx in range(left, right + 1):
                                put(bx, ey - 1, lit(face_base, 0.72))
                    else:
                        put(x0 + fw // 2, ey, skin.eye, glow_eye)

                    # A horn glow needs a horn bone to land on, and the Graphorn, Horned
                    # Serpent and Unicorn rigs have none — plain quadruped and serpent
                    # skeletons — so the tag could never fire and all three shipped blank.
                    # Fall back to a mark on the brow, which is where each of them carries
                    # the feature anyway: a horn base, a jewel, a horn.
                    if "horn" in skin.glow and not rig_has_horn and fh >= 3:
                        hy = y0 + max(0, fh // 3 - 1)
                        hx = x0 + fw // 2
                        put(hx, hy, skin.horn, True)
                        if fw >= 5:
                            put(hx - 1, hy, mix(skin.horn, face_base, 0.45), True)
                            put(hx + 1, hy, mix(skin.horn, face_base, 0.45), True)

                if role == "jaw" and fname == "north" and fw >= 4:
                    ty = y1 - 1
                    for tx in range(x0 + 1, x1 - 1, 2):
                        put(tx, ty, (236, 232, 220))

                if role == "foot" and fname == "north" and fw >= 3:
                    ty = y1 - 1
                    for tx in range(x0, x1):
                        if (tx - x0) % 2 == 0:
                            put(tx, ty, skin.horn)

                if role in ("wing", "membrane") and fname in ("up", "down") and fw >= 6 and fh >= 4:
                    # Wing veins: a few radiating darker lines.
                    for k in range(1, 4):
                        vx = x0 + int(fw * k / 4.0)
                        for vy in range(y0, y1):
                            px[min(tw - 1, vx), min(th - 1, vy)] = tuple(
                                list(shift(skin.accent, 0.9)) + [255])

                if "accent" in skin.glow and role in ("shell", "horn", "tail"):
                    for ly in range(fh):
                        for lx in range(fw):
                            if (lx + ly) % 5 == 0:
                                put(x0 + lx, y0 + ly, skin.accent, True)

    # Vanilla mob skins run 13-37 colours (spider 13, villager 23, horse 25, zombie 37).
    # Painting from continuous ramps was landing near 120, which is most of why these
    # read as rendered rather than drawn.
    save(posterise(img, 20), out_path, MARKER)
    if skin.glow:
        save(posterise(glow, 8), glow_path, MARKER)
        return True
    if os.path.exists(glow_path) and is_regenerable(glow_path, MARKER):
        os.remove(glow_path)
    return False


# --------------------------------------------------------------------------- driver

def needs_unwrap(geo_path):
    """True when the rig's cubes share UV slots (the placeholder generator's all-zero UVs)."""
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    uvs = [tuple(c["uv"]) for b in geo["bones"] for c in b.get("cubes", [])
           if isinstance(c.get("uv"), list)]
    return bool(uvs) and len(set(uvs)) < len(uvs)


def main():
    # Retired 2026-09-28 (art pipeline audit): every creature skin now belongs to its own
    # `tools/<id>_model.py`, and a run here paints nothing live -- it only drops placeholder
    # skins for rigs that are not creatures (the broom variants, `player`). The module stays as a
    # library: spawn_eggs.py, tent_skin.py and wand_model.py import its palettes and painters.
    raise SystemExit("beast_skins.py is retired as a writer -- run the creature's tools/<id>_model.py")
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true",
                    help="repaint even hand-authored textures")
    ap.add_argument("--only", default="", help="comma-separated creature ids")
    ap.add_argument("--no-unwrap", action="store_true",
                    help="paint against existing UVs; do not rewrite any .geo.json")
    args = ap.parse_args()

    only = {s.strip() for s in args.only.split(",") if s.strip()}
    painted, skipped, glowed, unwrapped = [], [], [], []

    for geo_path in sorted(glob.glob(os.path.join(GEO_DIR, "*.geo.json"))):
        cid = os.path.basename(geo_path)[:-len(".geo.json")]
        if cid in EXCLUDE or (only and cid not in only):
            continue
        tex_path = os.path.join(TEX_DIR, cid + ".png")
        glow_path = os.path.join(TEX_DIR, cid + "_glowmask.png")

        # Never touch a rig whose texture was drawn by hand — re-unwrapping it would
        # invalidate that art. Hand-authored art is detected by colour count.
        if not args.force and not is_regenerable(tex_path, MARKER):
            skipped.append(cid)
            continue

        # ...and never touch one owned by a dedicated rig generator, --force or not. Those
        # tools (ghoul, phoenix, thestral, obscurus) emit geo and skin as a matched pair, so
        # re-unwrapping the geo here would silently desync a rig from a skin this tool did
        # not paint and cannot repaint. --force means "redo my own output", not "take
        # someone else's".
        if is_generated(tex_path) and not is_generated(tex_path, MARKER):
            skipped.append(cid)
            continue

        if not args.no_unwrap and needs_unwrap(geo_path):
            w, h, _ = unwrap_model(geo_path)
            unwrapped.append(f"{cid}({w}x{h})")

        if paint_creature(cid, geo_path, tex_path, glow_path):
            glowed.append(cid)
        painted.append(cid)

    # The Animagus stag used to get a flat hide here for its texOffs(0, 0) box model. It is a
    # rig now, painted by its own generator (tools/animagus_stag_model.py), and skipped above.


    print(f"painted {len(painted)} skins, {len(glowed)} with glowmasks")
    print(f"unwrapped {len(unwrapped)} rigs")
    print(f"skipped {len(skipped)} hand-authored: {', '.join(skipped) or '-'}")
    missing = [c for c in painted if c not in SKINS]
    if missing:
        print(f"WARNING: no palette for {len(missing)}: {', '.join(sorted(missing))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
