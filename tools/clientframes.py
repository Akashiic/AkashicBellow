#!/usr/bin/env python3
"""Checks the frames captured by the client test in a closed room that crosses Y 0 (red wool below, blue above,
glowstone everywhere else).

    tools/clientframes.py <screenshots dir>

Sequences (every rendered frame): still (camera fixed, eye just below Y 0), rise (camera rising through Y 0),
turn (camera turning 120 degrees), edit (camera fixed while wall blocks change above and below Y 0, so their
sections are rebuilt).

* Holes, every frame: the room is closed, so a pixel of sky or of black void means a section was not drawn.
* Seam, rise: in every frame the line between red and blue (Y 0) is straight across the wall and blue starts right
  above red (nothing drawn between the deep sections and the vanilla ones).
* Still and edit: the wall is drawn on both sides of Y 0 in every frame with a constant share of the picture, and no
  frame pops out of the sequence (differs from both neighbours while they agree). With a moving camera that test
  does not hold for textured surfaces, so motion is judged by the two checks above.
* Sky, single shots looking straight up an open pit at several heights (bellow-sky-<y>.png): the middle of the
  picture is sky, and no black band lines the opening (the box vanilla draws under the camera below the horizon
  turned upside down below Y -2 and covered the sky).
Prints PASS/FAIL lines and exits 1 on any failure.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

SEQUENCES = ("still", "rise", "turn", "edit")
SKY_HEIGHTS = (100, 40, 10, -10, -40)


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.int32)


def diff(a, b):
    return float(np.mean(np.abs(a - b)))


def masks(f, least=30):
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    # channel ratios, not differences: the dark pixels of the glowstone texture are brownish red, and the wool is
    # much darker in the middle of the wall than at its edges
    red = (r > least) & (r > 1.8 * g) & (r > 1.8 * b)
    blue = (b > least) & (b > 1.6 * r) & (b > 1.4 * g)
    sky = (b > 200) & (g > 130) & (b > r + 40)
    void = f.mean(axis=2) < 10
    return red, blue, sky | void


def grow(mask, n):
    out = mask.copy()
    for _ in range(n):
        g = out.copy()
        g[1:] |= out[:-1]
        g[:-1] |= out[1:]
        g[:, 1:] |= out[:, :-1]
        g[:, :-1] |= out[:, 1:]
        out = g
    return out


def check_sky(root, check):
    for y in SKY_HEIGHTS:
        path = os.path.join(root, "bellow-sky-%d.png" % y)
        if not os.path.isfile(path):
            check("sky at Y %d" % y, False, "no " + os.path.basename(path))
            continue
        f = load(path)
        h, w = f.shape[:2]
        r, b = f[..., 0], f[..., 2]
        sky = (b > r + 40) & (b > 150)
        centre = float(np.mean(sky[h * 2 // 5:h * 3 // 5, w * 2 // 5:w * 3 // 5]))
        # the box only turns upside down below Y -2; above 0 the rim of the pit is dark leaves, not checked
        ring = grow(sky, 5) & ~sky
        black = (f.max(axis=2) < 25) & (f.max(axis=2) - f.min(axis=2) < 8)
        edge = float(np.mean(black[ring])) if ring.any() and y < 0 else 0.0
        # measured without the fix: middle 0.13 sky at Y -40, edge 0.56 (Y -10) and 0.30 (Y -40); with it 0.82, 0.05
        # and 0.02 (a cloud can cross the middle)
        check("sky at Y %d" % y, centre > 0.5 and edge < 0.15,
              "middle %.2f sky (min 0.50), edge of the opening %.2f black (max 0.15)" % (centre, edge))


def main():
    root = sys.argv[1]
    failures = 0

    def check(name, ok, detail):
        nonlocal failures
        if not ok:
            failures += 1
        print(("PASS " if ok else "FAIL ") + name + " - " + detail)

    for seq in SEQUENCES:
        paths = sorted(glob.glob(os.path.join(root, "frames-%s-*.png" % seq)))
        if len(paths) < 10:
            check("frames " + seq, False, "%d frames captured" % len(paths))
            continue
        frames = [load(p) for p in paths]
        holes = [float(np.mean(masks(f)[2])) for f in frames]
        worst = int(np.argmax(holes))
        check("no holes (%s)" % seq, holes[worst] < 0.001,
              "%d frames, worst %.4f%% sky/void pixels at frame %d" % (len(frames), 100 * holes[worst], worst))

        if seq == "rise":
            bad = []
            for i, f in enumerate(frames):
                red, blue, _ = masks(f)
                cols = np.nonzero(red.sum(axis=0) > 20)[0]
                if len(cols) < 30:
                    continue
                rows = []
                for x in (cols[len(cols) * 3 // 10], cols[len(cols) // 2], cols[len(cols) * 7 // 10]):
                    r = np.nonzero(red[:, x])[0]
                    top = int(r.min())
                    # blue within 8 px above the red: a missing block row is 20+ px here; the darkest middle of
                    # the wall loses 3-4 px of blue to the colour test
                    glued = top == 0 or blue[max(0, top - 8):top, x].any()
                    rows.append((top, glued))
                straight = max(t for t, _ in rows) - min(t for t, _ in rows) <= 2
                if not straight or not all(g for _, g in rows):
                    bad.append(i)
            check("Y 0 seam straight and closed while rising", not bad, "frames with a broken seam: %s" % bad)

        if seq in ("still", "edit"):
            # vanilla torch flicker moves block light a little every tick and the middle of the wall is dark: with the
            # 30 floor its blue pixels came and went (0.45..0.50 with no block missing); 12 keeps them all
            shares = [(float(np.mean(m[0][m[0].shape[0] // 4:-m[0].shape[0] // 4, m[0].shape[1] // 4:-m[0].shape[1] // 4])),
                       float(np.mean(m[1][m[1].shape[0] // 4:-m[1].shape[0] // 4, m[1].shape[1] // 4:-m[1].shape[1] // 4])))
                      for m in (masks(f, 12) for f in frames)]
            reds = [s[0] for s in shares]
            blues = [s[1] for s in shares]
            ok = min(reds) > 0.05 and min(blues) > 0.05 and max(reds) - min(reds) < 0.05 \
                and max(blues) - min(blues) < 0.05
            check("wall drawn on both sides of Y 0 (%s)" % seq, ok,
                  "red %.3f..%.3f, blue %.3f..%.3f" % (min(reds), max(reds), min(blues), max(blues)))
            pops = []
            for i in range(1, len(frames) - 1):
                score = (diff(frames[i - 1], frames[i]) + diff(frames[i], frames[i + 1])
                         - diff(frames[i - 1], frames[i + 1])) / 2
                pops.append(score)
            check("no frame pops out (%s)" % seq, max(pops) < 3.0, "worst %.2f (limit 3.0)" % max(pops))
            change = max(diff(frames[i - 1], frames[i]) for i in range(1, len(frames)))
            check("stable picture (%s)" % seq, change < 2.0, "max frame-to-frame change %.2f (limit 2.0)" % change)
    check_sky(root, check)
    print("FRAMES RESULT " + ("PASS" if failures == 0 else "FAIL (%d)" % failures))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
