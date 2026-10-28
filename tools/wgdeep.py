#!/usr/bin/env python3
"""Lists WorldGuard 6 regions that stop at Y 0 in worlds that have a deep layer (read-only, changes nothing).

    tools/wgdeep.py <server dir> [--world world] [--floor -64]

WorldGuard regions are 3D. Before Akashic Bellow, a region drawn "from bedrock to sky" ends at Y 0, so it does not
protect the new layer below. This report shows each such region and the WorldEdit/WorldGuard commands that extend it
to the floor in game (run them with the WorldEdit agent active, so the selection is not clamped to 0):

    //pos1 <x>,<floor>,<z>   //pos2 <x>,<maxY>,<z>   /rg redefine <id>     (standing in that world)

Regions that do not start at Y 0 (built for a specific height) are listed apart and are left as they are.
Only the YAML storage (worlds/<world>/regions.yml) is read; with `sql.use: true` query the database instead.
"""
import argparse
import os
import re
import sys

NUM = r"(-?[\d.]+(?:[eE][-+]?\d+)?)"  # SnakeYAML writes large doubles as 3.0E7
REGION = re.compile(r"^    (\S.*):\s*$")
CORNER = re.compile(r"^        (min|max): \{x: " + NUM + r", y: " + NUM + r", z: " + NUM + r"\}")
POLY_Y = re.compile(r"^        (min-y|max-y): (-?\d+)")
TYPE = re.compile(r"^        type: (\S+)")


def unquote(key):
    """A YAML mapping key as written by SnakeYAML: plain, 'single' ('' is a quote) or "double" quoted."""
    if len(key) >= 2 and key[0] == key[-1] == "'":
        return key[1:-1].replace("''", "'")
    if len(key) >= 2 and key[0] == key[-1] == '"':
        return key[1:-1].encode().decode("unicode_escape")
    return key


def regions(path):
    out, cur = [], None
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = REGION.match(line)
            if m:
                cur = {"id": unquote(m.group(1)), "type": None}
                out.append(cur)
                continue
            if cur is None:
                continue
            m = CORNER.match(line)
            if m:
                cur[m.group(1)] = tuple(float(v) for v in m.group(2, 3, 4))
                continue
            m = POLY_Y.match(line)
            if m:
                cur[m.group(1)] = int(m.group(2))
                continue
            m = TYPE.match(line)
            if m:
                cur["type"] = m.group(1)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("server")
    ap.add_argument("--world", default="world", help="Bukkit world name with the deep layer (default: world)")
    ap.add_argument("--floor", type=int, default=-64, help="Bellow floor of that world (default: -64)")
    a = ap.parse_args()
    path = os.path.join(a.server, "plugins", "WorldGuard", "worlds", a.world, "regions.yml")
    if not os.path.isfile(path):
        sys.exit("no " + path)
    stop_at_zero, other = [], []
    for r in regions(path):
        if r["type"] == "cuboid" and "min" in r and "max" in r:
            lo, hi = r["min"], r["max"]
            (stop_at_zero if lo[1] == 0 else other).append(
                (r, "cuboid y %d..%d" % (lo[1], hi[1]),
                 "//pos1 %d,%d,%d  //pos2 %d,%d,%d  /rg redefine %s"
                 % (lo[0], a.floor, lo[2], hi[0], hi[1], hi[2], r["id"])))
        elif r["type"] == "poly2d" and "min-y" in r:
            (stop_at_zero if r["min-y"] == 0 else other).append(
                (r, "poly2d y %d..%d" % (r["min-y"], r["max-y"]),
                 "/rg select %s  //expand vert  /rg redefine %s" % (r["id"], r["id"])))
        else:
            # a cuboid or polygon whose heights were not understood must not pass for "not at Y 0"
            unknown = r["type"] in ("cuboid", "poly2d")
            other.append((r, (r["type"] or "?") + (" (heights not parsed: check by hand)" if unknown else ""), ""))
    print("%s (floor %d)" % (path, a.floor))
    print("\nStop at Y 0 (do not protect below it): %d" % len(stop_at_zero))
    for r, shape, fix in stop_at_zero:
        print("  %-24s %-22s %s" % (r["id"], shape, fix))
    print("\nOther regions (left as they are): %d" % len(other))
    for r, shape, _ in other:
        print("  %-24s %s" % (r["id"], shape))


if __name__ == "__main__":
    main()
