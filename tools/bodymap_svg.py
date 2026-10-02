"""SVG path parsing and curve flattening for tools/bodymap_import.py.

Handles M L H V C S Q T A Z in absolute and relative form. Numbers are tokenised with a regex, and arc
flags are read as single characters because the source packs them ("a1.71 1.71 0 012.89 1.12").
"""
import math
import re

NUM = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")
CMD = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]")
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def _tokens(d):
    pos = 0
    while pos < len(d):
        ch = d[pos]
        if ch in " ,\t\r\n":
            pos += 1
        elif CMD.match(ch):
            yield ch
            pos += 1
        else:
            m = NUM.match(d, pos)
            if not m:
                raise ValueError(f"bad path data at {pos}: {d[pos:pos + 20]!r}")
            yield m.group(0)
            pos = m.end()


def _arc_points(p0, rx, ry, rot, large, sweep, p1, tol):
    """Flatten an elliptical arc (SVG implementation notes F.6.5) to points after p0, ending at p1."""
    x0, y0 = p0
    x1, y1 = p1
    if rx == 0 or ry == 0 or p0 == p1:
        return [p1]
    rx, ry = abs(rx), abs(ry)
    phi = math.radians(rot)
    c, s = math.cos(phi), math.sin(phi)
    dx, dy = (x0 - x1) / 2, (y0 - y1) / 2
    x1p, y1p = c * dx + s * dy, -s * dx + c * dy
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(0.0, num / den)) * (-1 if large == sweep else 1)
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx = c * cxp - s * cyp + (x0 + x1) / 2
    cy = s * cxp + c * cyp + (y0 + y1) / 2

    def ang(ux, uy, vx, vy):
        return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)

    th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dth > 0:
        dth -= 2 * math.pi
    elif sweep and dth < 0:
        dth += 2 * math.pi
    r = max(rx, ry)
    step = 2 * math.acos(max(0.0, 1 - tol / r)) if r > tol else math.pi / 2
    n = max(2, int(math.ceil(abs(dth) / max(step, 1e-3))))
    pts = []
    for k in range(1, n + 1):
        t = th1 + dth * k / n
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((c * ex - s * ey + cx, s * ex + c * ey + cy))
    pts[-1] = p1
    return pts


def _bezier(pts, tol):
    """Flatten a quadratic or cubic bezier from its control points (start included)."""
    length = sum(math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1))
    n = max(2, min(64, int(math.ceil(math.sqrt(length / tol)))))
    out = []
    for k in range(1, n + 1):
        t = k / n
        p = list(pts)
        while len(p) > 1:
            p = [((1 - t) * a[0] + t * b[0], (1 - t) * a[1] + t * b[1]) for a, b in zip(p, p[1:])]
        out.append(p[0])
    return out


def _items(d):
    """Split path data into (command, args) pairs, expanding implicit repeats and packed arc flags."""
    toks = list(_tokens(d))
    items = []
    cmd = None
    i = 0
    while i < len(toks):
        t = toks[i]
        if CMD.fullmatch(t):
            cmd = t
            i += 1
            if cmd in "Zz":
                items.append((cmd, []))
                continue
        elif cmd is None or cmd in "Zz":
            raise ValueError("number without a command")
        args = []
        if cmd in "Aa":
            for slot in range(7):
                tok = toks[i]
                if slot in (3, 4) and len(tok) > 1:
                    # A flag is one character, always 0 or 1, so anything longer is a flag glued to what follows.
                    args.append(float(tok[0]))
                    toks[i] = tok[1:]
                else:
                    args.append(float(tok))
                    i += 1
        else:
            for _ in range(ARGS[cmd.upper()]):
                args.append(float(toks[i]))
                i += 1
        items.append((cmd, args))
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"
    return items


def flatten(d, tol=0.05):
    """Return a list of subpaths as (points, closed). tol is the curve flattening tolerance in source units."""
    subs = []
    cur = []
    pos = (0.0, 0.0)
    start = pos
    last_c = None
    last_q = None
    for cmd, a in _items(d):
        rel = cmd.islower()
        c = cmd.upper()
        ox, oy = pos if rel else (0.0, 0.0)
        if c == "M":
            if len(cur) > 1:
                subs.append((cur, False))
            pos = (a[0] + ox, a[1] + oy)
            start = pos
            cur = [pos]
            last_c = last_q = None
            continue
        if c == "Z":
            if len(cur) > 1:
                subs.append((cur, True))
            cur = []
            pos = start
            last_c = last_q = None
            continue
        if not cur:
            cur = [pos]
        if c == "L":
            pos = (a[0] + ox, a[1] + oy)
            cur.append(pos)
            last_c = last_q = None
        elif c == "H":
            pos = (a[0] + ox, pos[1])
            cur.append(pos)
            last_c = last_q = None
        elif c == "V":
            pos = (pos[0], a[0] + oy)
            cur.append(pos)
            last_c = last_q = None
        elif c == "C":
            c1, c2, end = (a[0] + ox, a[1] + oy), (a[2] + ox, a[3] + oy), (a[4] + ox, a[5] + oy)
            cur += _bezier([pos, c1, c2, end], tol)
            last_c, last_q, pos = c2, None, end
        elif c == "S":
            c1 = (2 * pos[0] - last_c[0], 2 * pos[1] - last_c[1]) if last_c else pos
            c2, end = (a[0] + ox, a[1] + oy), (a[2] + ox, a[3] + oy)
            cur += _bezier([pos, c1, c2, end], tol)
            last_c, last_q, pos = c2, None, end
        elif c == "Q":
            c1, end = (a[0] + ox, a[1] + oy), (a[2] + ox, a[3] + oy)
            cur += _bezier([pos, c1, end], tol)
            last_q, last_c, pos = c1, None, end
        elif c == "T":
            c1 = (2 * pos[0] - last_q[0], 2 * pos[1] - last_q[1]) if last_q else pos
            end = (a[0] + ox, a[1] + oy)
            cur += _bezier([pos, c1, end], tol)
            last_q, last_c, pos = c1, None, end
        elif c == "A":
            end = (a[5] + ox, a[6] + oy)
            cur += _arc_points(pos, a[0], a[1], a[2], int(a[3]), int(a[4]), end, tol)
            pos = end
            last_c = last_q = None
    if len(cur) > 1:
        subs.append((cur, False))
    return subs
