#!/usr/bin/env python
"""Sweep every Monarch surface across modern phone geometries and report layout defects.

Why this exists: the owner's S25 Ultra is 411x891dp - the roomiest phone in
common use. Everything fits there, so verifying on it proves nothing about the
360dp-wide devices most people carry. This drives the SAME screens at several
densities (dp shrinks as density rises, pixels never change) and looks for the
three failures that are defects rather than taste:

  * a control measured at zero width or height - present in the tree, absent
    on screen, unreachable,
  * a control whose bounds leave the window on an axis that does not scroll,
  * two clickable controls whose bounds overlap - one is sitting on the other.

Text that merely wraps is NOT reported: wrapping is how a narrow screen is
supposed to behave. Squished text (a label given less width than one glyph)
is reported, because that is a layout failure.

Usage:
    python tools/geom_sweep.py                 # every geometry, every tab
    python tools/geom_sweep.py --density 480   # one geometry
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
PKG = "com.monarch.app"

# Density -> the dp window it produces on a 1080x2340 panel. These are the
# geometries real people hold, plus the narrowest a user can produce with the
# Display size slider.
GEOMETRIES = [
    (420, "411x891dp  S25 Ultra / Pixel Pro - the owner's phone, best case"),
    (480, "360x780dp  Galaxy S23 / A-series - the most common modern phone"),
    (540, "320x693dp  largest Display size setting - narrowest a user can make"),
]

TABS = ["Court", "Train", "Stats", "Codex", "Guild", "Shadow"]


def sh(*args: str) -> str:
    return subprocess.run(
        [ADB, "-s", SERIAL, *args], capture_output=True, text=True, errors="replace"
    ).stdout


def bounds(node) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds")))
    return x1, y1, x2, y2


def label(node) -> str:
    own = node.get("text") or node.get("content-desc")
    if own:
        return own
    for child in node.iter():
        t = child.get("text") or child.get("content-desc")
        if t:
            return t
    return "<unlabelled>"


def dump_tree():
    sh("shell", "uiautomator", "dump", "/sdcard/sweep.xml")
    raw = sh("shell", "cat", "/sdcard/sweep.xml")
    if "<hierarchy" not in raw:
        return None
    return ET.fromstring(raw)


def scrollable_ancestor(node, parents) -> bool:
    m = parents.get(node)
    while m is not None:
        if m.get("scrollable") == "true":
            return True
        m = parents.get(m)
    return False


def inspect(root, screen: str, density: int) -> list[str]:
    """Geometry defects on one dumped screen."""
    parents = {c: p for p in root.iter() for c in p}
    win = max((bounds(n)[3] for n in root.iter() if n.get("bounds")), default=0)
    width = max((bounds(n)[2] for n in root.iter() if n.get("bounds")), default=0)
    found: list[str] = []
    clickables = [n for n in root.iter() if n.get("clickable") == "true"]

    for n in clickables:
        x1, y1, x2, y2 = bounds(n)
        w, h = x2 - x1, y2 - y1
        if w <= 0 or h <= 0:
            found.append(f"{density}dpi {screen}: ZERO-SIZE control {label(n)!r} {w}x{h}px")
        elif x1 < 0 or x2 > width:
            # Horizontal scrolling is rare and deliberate; off the side is a bug.
            if not scrollable_ancestor(n, parents):
                found.append(
                    f"{density}dpi {screen}: OFF-SCREEN control {label(n)!r} x={x1}..{x2} of {width}"
                )

    # Overlapping clickables. Three filters keep this honest:
    #  * ancestor/descendant pairs nest by design,
    #  * a pair with only ONE side inside a scroller is a scroll artifact —
    #    uiautomator reports a half-scrolled row's UNCLIPPED bounds, so a list
    #    row reads as overlapping the bottom nav it is actually clipped above,
    #  * sub-8dp slivers are weight rounding and ink stroke bleed, not one
    #    control sitting on another.
    slack = max(1, round(8 * density / 160))
    kin = set()
    for n in clickables:
        m = parents.get(n)
        while m is not None:
            if m in clickables:
                kin.add((id(m), id(n)))
            m = parents.get(m)
    scrolled = {id(n): scrollable_ancestor(n, parents) for n in clickables}
    for i, a in enumerate(clickables):
        ax1, ay1, ax2, ay2 = bounds(a)
        for b in clickables[i + 1 :]:
            if (id(a), id(b)) in kin or (id(b), id(a)) in kin:
                continue
            if scrolled[id(a)] != scrolled[id(b)]:
                continue
            bx1, by1, bx2, by2 = bounds(b)
            ox = min(ax2, bx2) - max(ax1, bx1)
            oy = min(ay2, by2) - max(ay1, by1)
            if ox > slack and oy > slack:
                found.append(
                    f"{density}dpi {screen}: OVERLAP {label(a)!r} and {label(b)!r} "
                    f"({ox}x{oy}px)"
                )

    # Squished text: a label given less width than a single glyph needs.
    #
    # Nodes inside a scroller are exempt: uiautomator clips their bounds to the
    # viewport, so a chip half-scrolled off a horizontal rail reports 6px wide
    # while rendering whole. Judging those needs pixels, not bounds.
    for n in root.iter():
        t = n.get("text")
        if not t or len(t.strip()) < 2:
            continue
        if scrollable_ancestor(n, parents):
            continue
        x1, y1, x2, y2 = bounds(n)
        if 0 < (x2 - x1) < 12 or 0 < (y2 - y1) < 6:
            found.append(f"{density}dpi {screen}: SQUISHED text {t[:24]!r} {x2 - x1}x{y2 - y1}px")
    return found


def tap_label(text: str, wait: float = 2.5) -> bool:
    root = dump_tree()
    if root is None:
        return False
    parents = {c: p for p in root.iter() for c in p}
    for n in root.iter():
        if n.get("content-desc") == text or n.get("text") == text:
            m = n
            while m is not None and m.get("clickable") != "true":
                m = parents.get(m)
            m = m or n
            x1, y1, x2, y2 = bounds(m)
            sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(wait)
            return True
    return False


def set_density(density: int) -> None:
    sh("shell", "wm", "density", str(density))
    time.sleep(2)
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(5)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--density", type=int, help="sweep one density only")
    args = ap.parse_args()

    geometries = [g for g in GEOMETRIES if args.density in (None, g[0])]
    if not geometries:
        geometries = [(args.density, "custom")]

    problems: list[str] = []
    for density, note in geometries:
        print(f"\n=== {density}dpi  {note} ===")
        set_density(density)
        for tab in TABS:
            if not tap_label(tab, 3):
                # A screen that cannot be opened must never read as clean: an
                # unreachable nav slot is the worst geometry defect there is.
                print(f"  {tab:7} NOT REACHED")
                problems.append(f"{density}dpi {tab}: UNREACHABLE - no tappable nav slot")
                continue
            root = dump_tree()
            if root is None:
                print(f"  {tab:7} no dump")
                problems.append(f"{density}dpi {tab}: NO DUMP - screen could not be inspected")
                continue
            hits = inspect(root, tab, density)
            print(f"  {tab:7} {'clean' if not hits else str(len(hits)) + ' problem(s)'}")
            problems += hits

    print("\n--- findings ---")
    if not problems:
        print("none")
    for p in sorted(set(problems)):
        print(p)
    sh("shell", "wm", "density", "reset")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
