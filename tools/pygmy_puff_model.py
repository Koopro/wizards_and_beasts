#!/usr/bin/env python3
"""The Pygmy Puff: rig, skin and animation.

A miniature Puffskein, bred by Fred and George Weasley and sold in pink and purple at Weasleys'
Wizard Wheezes. Canon says outright that it is the same animal made smaller, so this is not a
second model: it is `puffskein_model.generate` with a smaller ball and a pink coat. The shared
generator is the point — the two cannot drift apart, and a fix to one is a fix to both.

Run from the repo root:  python tools/pygmy_puff_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx  # noqa: E402
from puffskein_model import PUFFSKEIN, generate  # noqa: E402

PYGMY_PUFF = {
    **PUFFSKEIN,
    "cid": "pygmy_puff",
    "tool": "pygmy_puff_model.py",
    # Two units smaller across. Any smaller and the eyes no longer fit on the front slab with
    # a texel of fur between them, which is the floor `puffskein_model.paint` is built around.
    "core": 6,
    "fur": hx("#E58BC0"),
    "fur_dark": hx("#B85C93"),
    "fur_lit": hx("#F6BDDD"),
    "tongue": hx("#C8436A"),
    # The style pass (2026-09-27) moved the Puffskein to the fleck grain but never repainted the
    # Pygmy Puff; its shipped skin is the legacy speckle, so that is what a rerun reproduces.
    # Moving it to the Puffskein's grain is a visible change: drop this line and regenerate.
    "grain": "speckle",
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    return generate(PYGMY_PUFF, ap.parse_args().force)


if __name__ == "__main__":
    sys.exit(main())
