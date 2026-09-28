#!/usr/bin/env python3
"""Assign polar coordinates to every wizard skill node, derived from the graph itself.

The 2026-09-17 education rework deleted the 34 pathway connector nodes, and those
connectors had been carrying the intermediate radii. Every surviving node in a spoke
inherited its spoke's single anchor, so 57 of 84 nodes ended up at exactly r=140 and
54 of them shared a position with at least one sibling. Both showcase lines -- Lumos
and Defence -- drew as one dot each.

The fix derives the two polar components from the two things that already describe a
node's place in the web, rather than hand-placing 84 pairs:

  radius  <- BFS depth from Polaris (wizard_core). Progression runs outward, so an edge
             always travels away from the hub and a prerequisite is always the node
             nearer the centre. This also repairs an inversion the old data carried:
             wandlore's depth-2 nodes sat at r=232 while its depth-5 keystone sat at
             r=140, i.e. the ladder ran backwards.
  angle   <- even spread across the tree's own 60-degree sector, siblings ordered by
             their parent's angle so edges fan outward instead of crossing.

Invariants this must not break, each enforced by SkillNodeJsonTest:
  * A node outside HUB_RADIUS (112) stays inside its tree's sector, so
    noEdgeCrossesASealedBorder still reads the same sector for every node. Trunk nodes
    are pinned to r=96, deliberately inside the hub zone, because every cross-tree edge
    in the web runs trunk -> wizard_core and is exempt only while one end is in the hub.
  * Polaris stays at the origin.
  * The two heritage webs (elf_bond, goblin_craft) are left exactly as they are. They
    share coordinates with each other on purpose: their audiences are mutually
    exclusive, so a goblin never sees the elf web and the overlap is invisible in play.

Run from the repo root:  python tools/skill_web_layout.py [--check]
                         --check reports drift and exits non-zero instead of writing.
"""

import argparse
import collections
import glob
import json
import math
import sys

NODE_GLOB = "src/main/resources/data/wizards_and_beasts/skill_nodes/*/*.json"

# Sector order must match SkillNodeJsonTest.SPOKE_ORDER: index 0 centred at -90 degrees
# (straight up, y-down), then clockwise in 60-degree steps.
SPOKE_ORDER = ["herbology", "magizoology", "spell_mastery", "dark_arts", "alchemy", "wandlore"]

# Webs whose coordinates are not ours to assign: audience-exclusive, deliberately overlapping.
LEFT_ALONE = {"elf_bond", "goblin_craft"}

HUB_NODE = "wizard_core"

# Depth -> radius. Depth 1 is the trunk ring, pinned below HUB_RADIUS (112) so the
# trunk -> Polaris edges stay sector-exempt. The rest keep the ~46px spacing the alchemy
# and wandlore webs were already authored with.
TRUNK_RADIUS = 96.0
FIRST_RING = 140.0
RING_STEP = 46.0

# Usable half-width inside a 60-degree sector. The nine degrees of margin each side does two
# jobs: it keeps a node clear of the border after rounding, so sector() never reads it into a
# neighbour, and it leaves an 18-degree lane between the outermost nodes of adjacent sectors.
# At the first ring that lane is ~44px, comfortably clear of the 26px keystone sprite; at 24
# degrees it was 29px and two neighbouring keystones very nearly touched.
SECTOR_HALF_WIDTH = 21.0


def radius_for(depth):
    if depth <= 1:
        return TRUNK_RADIUS
    return FIRST_RING + RING_STEP * (depth - 2)


def sector_centre(tree):
    return -90.0 + 60.0 * SPOKE_ORDER.index(tree)


def load_nodes():
    nodes = {}
    for path in sorted(glob.glob(NODE_GLOB)):
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        data["__path"] = path
        nodes[data["id"]] = data
    return nodes


def undirected_adjacency(nodes):
    adj = collections.defaultdict(set)
    for node_id, data in nodes.items():
        for edge in data.get("edges", []):
            if edge in nodes:
                adj[node_id].add(edge)
                adj[edge].add(node_id)
    return adj


def depths_from_hub(adj):
    """BFS layer index per node. Deterministic: neighbours are visited in id order."""
    depth = {HUB_NODE: 0}
    queue = collections.deque([HUB_NODE])
    while queue:
        current = queue.popleft()
        for neighbour in sorted(adj[current]):
            if neighbour not in depth:
                depth[neighbour] = depth[current] + 1
                queue.append(neighbour)
    return depth


def assign(nodes, adj, depth):
    """Returns {node_id: (x, y)} for every node this tool owns."""
    placed = {HUB_NODE: (0.0, 0.0)}
    angle_of = {HUB_NODE: None}

    owned = [
        n for n, d in nodes.items()
        if d["tree"] in SPOKE_ORDER and n != HUB_NODE and n in depth
    ]
    by_tree_depth = collections.defaultdict(list)
    for node_id in owned:
        by_tree_depth[(nodes[node_id]["tree"], depth[node_id])].append(node_id)

    max_depth = max((depth[n] for n in owned), default=0)
    for current_depth in range(1, max_depth + 1):
        for tree in SPOKE_ORDER:
            ring = by_tree_depth.get((tree, current_depth), [])
            if not ring:
                continue
            centre = sector_centre(tree)

            def parent_angle(node_id):
                """Angle of already-placed shallower neighbours, for edge readability."""
                known = [
                    angle_of[p] for p in adj[node_id]
                    if angle_of.get(p) is not None and depth.get(p, 99) < current_depth
                ]
                return sum(known) / len(known) if known else centre

            ring.sort(key=lambda n: (parent_angle(n), n))

            count = len(ring)
            if count == 1:
                angles = [centre]
            else:
                span = 2 * SECTOR_HALF_WIDTH
                angles = [
                    centre - SECTOR_HALF_WIDTH + span * i / (count - 1)
                    for i in range(count)
                ]

            radius = radius_for(current_depth)
            for node_id, angle in zip(ring, angles):
                radians = math.radians(angle)
                placed[node_id] = (
                    round(radius * math.cos(radians), 1),
                    round(radius * math.sin(radians), 1),
                )
                angle_of[node_id] = angle
    return placed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true",
                        help="report drift and exit non-zero instead of writing")
    args = parser.parse_args()

    nodes = load_nodes()
    adj = undirected_adjacency(nodes)
    depth = depths_from_hub(adj)

    orphans = sorted(
        n for n, d in nodes.items()
        if d["tree"] in SPOKE_ORDER and n not in depth
    )
    if orphans:
        print("ERROR: wizard nodes unreachable from " + HUB_NODE + ": " + ", ".join(orphans),
              file=sys.stderr)
        return 2

    placed = assign(nodes, adj, depth)

    drift, written = [], 0
    for node_id, (x, y) in sorted(placed.items()):
        data = nodes[node_id]
        if abs(data.get("x", 0.0) - x) < 1e-9 and abs(data.get("y", 0.0) - y) < 1e-9:
            continue
        drift.append((node_id, data.get("x"), data.get("y"), x, y))
        if args.check:
            continue
        data["x"], data["y"] = x, y
        path = data.pop("__path")
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(data, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
        data["__path"] = path
        written += 1

    skipped = sum(1 for d in nodes.values() if d["tree"] in LEFT_ALONE)
    print("nodes: %d  owned: %d  left alone (heritage webs): %d"
          % (len(nodes), len(placed), skipped))
    if args.check:
        for node_id, ox, oy, nx, ny in drift:
            print("  DRIFT %s: (%s, %s) -> (%s, %s)" % (node_id, ox, oy, nx, ny))
        print("%d node(s) differ from the generated layout" % len(drift))
        return 1 if drift else 0
    print("rewrote %d node file(s)" % written)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
