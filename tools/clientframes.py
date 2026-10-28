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
Prints PASS/FAIL lines and exits 1 on any failure.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

SEQUENCES = ("still", "rise", "turn", "edit")


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.int32)


def diff(a, b):
    return float(np.mean(np.abs(a - b)))


def masks(f):
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    # channel ratios, not differences: the dark pixels of the glowstone texture are brownish red, and the wool is
    # much darker in the middle of the wall than at its edges
    red = (r > 30) & (r > 1.8 * g) & (r > 1.8 * b)
    blue = (b > 30) & (b > 1.6 * r) & (b > 1.4 * g)
    sky = (b > 200) & (g > 130) & (b > r + 40)
    void = f.mean(axis=2) < 10
    return red, blue, sky | void


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
            shares = [(float(np.mean(m[0][m[0].shape[0] // 4:-m[0].shape[0] // 4, m[0].shape[1] // 4:-m[0].shape[1] // 4])),
                       float(np.mean(m[1][m[1].shape[0] // 4:-m[1].shape[0] // 4, m[1].shape[1] // 4:-m[1].shape[1] // 4])))
                      for m in (masks(f) for f in frames)]
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
    print("FRAMES RESULT " + ("PASS" if failures == 0 else "FAIL (%d)" % failures))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
