"""Emit player.geo.json from vanilla's own PlayerModel mesh.

The pose layer writes to vanilla `ModelPart`s at render time; this geo is never rendered. It exists
so GeckoLib can bake keyframe clips against named bones, and so those clips can be authored in
Blockbench against a rig that is the player rather than something that looks like one.

That distinction is the reason this is generated rather than hand-built: every number here is a
mechanical transform of a number in `HumanoidModel.createMesh` / `PlayerModel.createMesh`. Typing
them out by hand would make a divergence between the animation rig and the thing being animated
invisible until someone noticed a limb rotating about the wrong point.

Vanilla mesh, verified against 1.21.11 sources (`PartPose.offset` / `CubeListBuilder.addBox`):

    part        pivot            box origin        size
    head        ( 0.0,  0, 0)    (-4, -8, -4)      8 x  8 x 8
    body        ( 0.0,  0, 0)    (-4,  0, -2)      8 x 12 x 4
    right_arm   (-5.0,  2, 0)    (-3, -2, -2)      4 x 12 x 4
    left_arm    ( 5.0,  2, 0)    (-1, -2, -2)      4 x 12 x 4
    right_leg   (-1.9, 12, 0)    (-2,  0, -2)      4 x 12 x 4
    left_leg    ( 1.9, 12, 0)    (-2,  0, -2)      4 x 12 x 4

Two coordinate conventions are in play and they are not the same one:

  * Java model space has its origin at the neck joint with Y increasing DOWNWARD, and `addBox`
    coordinates are relative to the owning part's pivot.
  * Bedrock geo has its origin at the feet with Y increasing UPWARD, and both pivots and cube
    origins are absolute.

So: absolute = pivot + box, then `bedrockY = 24 - javaY`, with the cube's min corner taken from what
was its max corner. X and Z pass through untouched. `convert_cube` is that, and it is the only place
the arithmetic appears.

The slim (3px arm) variant is deliberately not emitted. Slim changes the arm cubes' width and their
x offset but leaves both shoulder pivots at (+/-5, 2, 0) — and a rotation clip only reads pivots, so
a second file would animate identically while doubling what has to stay in sync.
"""

from __future__ import annotations

import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "src/main/resources/assets/wizards_and_beasts/geckolib/models/entity/player.geo.json"

#: Java model height. The constant that flips the Y axis.
GROUND = 24.0


def round_coord(value: float) -> float:
    """Kill float noise. `1.9 + -2` is `-0.10000000000000009`, and that lands in the JSON."""
    rounded = round(value, 4)
    return 0.0 if rounded == 0 else rounded


def convert_pivot(x: float, y: float, z: float) -> list[float]:
    """Java model-space point -> Bedrock geo point."""
    return [round_coord(x), round_coord(GROUND - y), round_coord(z)]


def convert_cube(pivot, box, size, uv, inflate=0.0):
    """Java `addBox` relative to `pivot` -> an absolute Bedrock cube.

    `box` is the corner nearest the origin in Java space, which is the corner with the SMALLEST y —
    and therefore, once the axis flips, the corner with the LARGEST y. The min corner Bedrock wants
    is the opposite one, hence `y + size_y`.
    """
    px, py, pz = pivot
    bx, by, bz = box
    sx, sy, sz = size
    cube = {
        "origin": [round_coord(px + bx), round_coord(GROUND - (py + by + sy)), round_coord(pz + bz)],
        "size": [sx, sy, sz],
        "uv": list(uv),
    }
    if inflate:
        cube["inflate"] = inflate
    return cube


def bone(name, parent, pivot, cubes=None):
    out = {"name": name, "pivot": convert_pivot(*pivot)}
    if parent:
        out["parent"] = parent
    if cubes:
        out["cubes"] = cubes
    return out


def build() -> dict:
    head_pivot = (0.0, 0.0, 0.0)
    body_pivot = (0.0, 0.0, 0.0)
    right_arm_pivot = (-5.0, 2.0, 0.0)
    left_arm_pivot = (5.0, 2.0, 0.0)
    right_leg_pivot = (-1.9, 12.0, 0.0)
    left_leg_pivot = (1.9, 12.0, 0.0)

    bones = [
        # The virtual BODY part. It carries no geometry because it is not a body part — it is the
        # whole-avatar transform, and giving it a cube would put a box in the preview that nothing
        # in the game ever draws.
        #
        # Its pivot is given in Java space like every other pivot here, so y=GROUND means the feet,
        # and it converts to Bedrock (0, 0, 0). That is deliberate and it is not the neck joint that
        # vanilla's own `root` ModelPart sits at: BODY resolves against the PoseStack, whose origin
        # at that point is the entity position. Pivoting this bone anywhere else would let a clip
        # look right in Blockbench and swing about the wrong point in game.
        bone("root", None, (0.0, GROUND, 0.0)),

        bone("body", "root", body_pivot,
             [convert_cube(body_pivot, (-4, 0, -2), (8, 12, 4), (16, 16))]),
        bone("jacket", "body", body_pivot,
             [convert_cube(body_pivot, (-4, 0, -2), (8, 12, 4), (16, 32), inflate=0.25)]),

        bone("head", "root", head_pivot,
             [convert_cube(head_pivot, (-4, -8, -4), (8, 8, 8), (0, 0))]),
        bone("hat", "head", head_pivot,
             [convert_cube(head_pivot, (-4, -8, -4), (8, 8, 8), (32, 0), inflate=0.5)]),

        # Arms and legs hang off `root`, not off `body`, because that is how vanilla parents them.
        # Reparenting them under the torso would look tidier and would be wrong: a clip authored
        # against it would double-apply the torso's rotation once the pose reached the real model.
        bone("right_arm", "root", right_arm_pivot,
             [convert_cube(right_arm_pivot, (-3, -2, -2), (4, 12, 4), (40, 16))]),
        bone("right_sleeve", "right_arm", right_arm_pivot,
             [convert_cube(right_arm_pivot, (-3, -2, -2), (4, 12, 4), (40, 32), inflate=0.25)]),

        bone("left_arm", "root", left_arm_pivot,
             [convert_cube(left_arm_pivot, (-1, -2, -2), (4, 12, 4), (32, 48))]),
        bone("left_sleeve", "left_arm", left_arm_pivot,
             [convert_cube(left_arm_pivot, (-1, -2, -2), (4, 12, 4), (48, 48), inflate=0.25)]),

        bone("right_leg", "root", right_leg_pivot,
             [convert_cube(right_leg_pivot, (-2, 0, -2), (4, 12, 4), (0, 16))]),
        bone("right_pants", "right_leg", right_leg_pivot,
             [convert_cube(right_leg_pivot, (-2, 0, -2), (4, 12, 4), (0, 32), inflate=0.25)]),

        bone("left_leg", "root", left_leg_pivot,
             [convert_cube(left_leg_pivot, (-2, 0, -2), (4, 12, 4), (16, 48))]),
        bone("left_pants", "left_leg", left_leg_pivot,
             [convert_cube(left_leg_pivot, (-2, 0, -2), (4, 12, 4), (0, 48), inflate=0.25)]),
    ]

    return {
        # No other geo asset in this project carries a comment, so there is no house mechanism to
        # follow — this key is new. GeckoLib's loader is Gson with named fields
        # (`Model.deserializer()`), so an unknown top-level key is ignored rather than rejected, and
        # the note costs nothing even if the rig is ever loaded.
        "_comment": (
            "AUTHORING ONLY. This rig is never rendered. The pose layer writes to vanilla's own "
            "ModelParts at render time; this file exists so keyframe clips can be authored in "
            "Blockbench against the real player rig. Generated by tools/player_geo.py from vanilla's "
            "LayerDefinition - edit that, not this. Parity with vanilla is enforced by "
            "PlayerGeoRigParityTest."
        ),
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.player",
                    "texture_width": 64,
                    "texture_height": 64,
                    # Sized for a player lying flat along a broom, not for one standing up. Blockbench
                    # culls the preview against this, and the default humanoid bounds clip a prone
                    # pose at exactly the moment it becomes worth looking at.
                    "visible_bounds_width": 3.0,
                    "visible_bounds_height": 3.0,
                    "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": bones,
            }
        ],
    }


def verify(model: dict) -> None:
    """Check the emitted rig against facts that hold independently of the arithmetic above."""
    bones = model["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones}

    # The pose layer addresses exactly these. A missing one is a clip that silently animates nothing.
    required = {"root", "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}
    missing = required - by_name.keys()
    assert not missing, f"missing pose targets: {sorted(missing)}"

    # Feet on the ground and the crown of the head at 32 texels up: 24 for the body, 8 for the head.
    # Both are properties of the player, not of the conversion, so they catch a sign error.
    lowest = min(c["origin"][1] for b in bones for c in b.get("cubes", ()))
    highest = max(c["origin"][1] + c["size"][1] for b in bones for c in b.get("cubes", ()))
    assert lowest == 0.0, f"model does not stand on the ground: lowest y = {lowest}"
    assert highest == 32.0, f"head is not at full height: highest y = {highest}"

    # Shoulders level with the top of the torso, hips at its bottom.
    assert by_name["right_arm"]["pivot"] == [-5.0, 22.0, 0.0], by_name["right_arm"]["pivot"]
    assert by_name["left_leg"]["pivot"] == [1.9, 12.0, 0.0], by_name["left_leg"]["pivot"]

    # Left and right mirror each other. Authoring a splay as `+/-angle` assumes it.
    for right, left in (("right_arm", "left_arm"), ("right_leg", "left_leg")):
        rp, lp = by_name[right]["pivot"], by_name[left]["pivot"]
        assert rp[0] == -lp[0] and rp[1:] == lp[1:], f"{right}/{left} are not mirrored"

    # Every cube must be an integral number of texels or the box-UV unwrap lands between pixels.
    for b in bones:
        for c in b.get("cubes", ()):
            assert all(float(s).is_integer() for s in c["size"]), f"{b['name']} has a fractional size"


if __name__ == "__main__":
    model = build()
    verify(model)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(REPO)} ({len(model['minecraft:geometry'][0]['bones'])} bones)")
