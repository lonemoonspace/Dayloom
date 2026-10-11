"""
Geometry for the woven icon set: strands made of lines and circle arcs, and over-under crossings baked into the paths.
Where one strand passes under another, the lower one is cut, so the gap is real and survives any tint or theme.
编织图标的几何：由直线和圆弧组成的线条，以及烘焙进路径的一压一挑交叉。
一根线从另一根下面穿过时，下面那根直接被切开；缝是真实存在的，任何着色或主题下都不会消失。
"""
import math

# Clear space between two strokes where they cross. / 两条线交叉处留出的空隙。
GAP = 1.4


def fmt(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


class Line:
    def __init__(self, x0, y0, x1, y1):
        self.p0, self.p1 = (x0, y0), (x1, y1)
        self.length = math.hypot(x1 - x0, y1 - y0)

    def point(self, t):
        f = t / self.length
        return (self.p0[0] + (self.p1[0] - self.p0[0]) * f, self.p0[1] + (self.p1[1] - self.p0[1]) * f)

    def svg(self, t0, t1):
        x, y = self.point(t1)
        return [f"L{fmt(x)},{fmt(y)}"]


class Arc:
    """
    Circle arc from angle a0 to a1 in degrees (screen coordinates, y down); a1 < a0 runs counter-clockwise.
    圆弧，从角度 a0 到 a1（度，屏幕坐标，y 向下）；a1 < a0 时逆时针。
    """

    def __init__(self, cx, cy, r, a0, a1):
        self.c, self.r, self.a0, self.a1 = (cx, cy), r, a0, a1
        self.sgn = 1 if a1 >= a0 else -1
        self.length = r * abs(a1 - a0) * math.pi / 180

    def point(self, t):
        a = math.radians(self.a0 + self.sgn * (t / self.r) * 180 / math.pi)
        return (self.c[0] + self.r * math.cos(a), self.c[1] + self.r * math.sin(a))

    def svg(self, t0, t1):
        # A full turn cannot be one SVG arc command, so long spans are split in two.
        # 整圈无法用一条 SVG 弧线命令表示，所以长弧拆成两段。
        out, span = [], abs(t1 - t0) / self.r * 180 / math.pi
        stops = [t0, (t0 + t1) / 2, t1] if span > 359 else [t0, t1]
        for a, b in zip(stops, stops[1:]):
            x, y = self.point(b)
            large = 1 if abs(b - a) / self.r * 180 / math.pi > 180 else 0
            out.append(f"A{fmt(self.r)},{fmt(self.r)} 0 {large},{1 if self.sgn > 0 else 0} {fmt(x)},{fmt(y)}")
        return out


def circle(cx, cy, r, start=-90):
    return [Arc(cx, cy, r, start, start + 360)]


def roundrect(x0, y0, x1, y1, r):
    return [
        Line(x0 + r, y0, x1 - r, y0), Arc(x1 - r, y0 + r, r, -90, 0),
        Line(x1, y0 + r, x1, y1 - r), Arc(x1 - r, y1 - r, r, 0, 90),
        Line(x1 - r, y1, x0 + r, y1), Arc(x0 + r, y1 - r, r, 90, 180),
        Line(x0, y1 - r, x0, y0 + r), Arc(x0 + r, y0 + r, r, 180, 270),
    ]


def poly(*pts, closed=False):
    pts = list(pts) + ([pts[0]] if closed else [])
    return [Line(*a, *b) for a, b in zip(pts, pts[1:])]


class Strand:
    """
    One continuous line of the drawing with its colour key and stroke width; `cuts` collects the gaps.
    图中一根连续的线，带颜色键与线宽；`cuts` 收集它被切开的缝。
    """

    def __init__(self, prims, color, width, closed=False, name=""):
        self.prims, self.color, self.width, self.closed, self.name = prims, color, width, closed, name
        self.L = sum(p.length for p in prims)
        self.cuts = []

    def locate(self, s):
        if self.closed:
            s %= self.L
        s = min(max(s, 0), self.L)
        acc = 0
        for p in self.prims:
            if s <= acc + p.length + 1e-9:
                return p, s - acc
            acc += p.length
        return self.prims[-1], self.prims[-1].length

    def point(self, s):
        p, t = self.locate(s)
        return p.point(t)

    def samples(self, s0, s1, ds=0.01):
        n = max(2, int(abs(s1 - s0) / ds) + 1)
        return [self.point(s0 + (s1 - s0) * i / (n - 1)) for i in range(n)]

    def nearest_s(self, pt):
        best, bs, s = 1e9, 0, 0.0
        while s <= self.L:
            x, y = self.point(s)
            d = math.hypot(x - pt[0], y - pt[1])
            if d < best:
                best, bs = d, s
            s += 0.01
        return bs

    def emit(self, sa, sb):
        """Path data from arc length sa to sb; sb may pass L on a closed strand. / 从弧长 sa 到 sb 的路径；闭合线条上 sb 可以超过 L。"""
        x, y = self.point(sa)
        out = [f"M{fmt(x)},{fmt(y)}"]
        s = sa
        while s < sb - 1e-6:
            p, t = self.locate(s)
            if self.closed and t >= p.length - 1e-9:
                p, t = self.locate(s + 1e-6)
                t = max(0.0, t - 1e-6)
            take = min(p.length - t, sb - s)
            out += p.svg(t, t + take)
            s += max(take, 1e-4)
        return " ".join(out)


def cut(under, over, near, patch, gap=GAP):
    """
    Records the gap where `under` passes beneath `over` near the point `near`. Only `over` within `patch` (arc length either
    side of the crossing) counts, so distant parts of the same strand never cut anything.
    记录 `under` 在 `near` 附近从 `over` 下面穿过时的缝。只计算 `over` 在交点两侧 `patch` 弧长以内的部分，
    同一根线远处的部分不会误切。
    """
    so = over.nearest_s(near)
    lo, hi = so - patch, so + patch
    if not over.closed:
        lo, hi = max(lo, 0), min(hi, over.L)
    pts = over.samples(lo, hi, 0.01)
    thr = over.width / 2 + gap

    def blocked(s):
        if not under.closed and (s < 0 or s > under.L):
            return False
        x, y = under.point(s)
        return any((px - x) ** 2 + (py - y) ** 2 < thr * thr for px, py in pts)

    su = under.nearest_s(near)
    assert blocked(su), f"{under.name}: no crossing near {near}"
    a = b = su
    ds = 0.004
    while blocked(a - ds) and (under.closed or a - ds >= 0):
        a -= ds
    while blocked(b + ds) and (under.closed or b + ds <= under.L):
        b += ds
    under.cuts.append((a - ds / 2, b + ds / 2))


def pieces(strand):
    """Visible pieces as (start, end, start_is_free, end_is_free). / 可见的段：(起点, 终点, 起点是否自由端, 终点是否自由端)。"""
    if not strand.cuts:
        return [(0, strand.L, True, True)]
    if strand.closed:
        L = strand.L
        cs = sorted(((a % L, (a % L) + (b - a)) for a, b in strand.cuts))
        out = []
        for i, (_, b) in enumerate(cs):
            na = cs[(i + 1) % len(cs)][0] + (L if i + 1 == len(cs) else 0)
            if na - b > 1e-3:
                out.append((b, na, False, False))
        return out
    cs = sorted((max(a, 0), min(b, strand.L)) for a, b in strand.cuts)
    out, s = [], 0.0
    for a, b in cs:
        if a - s > 1e-3:
            out.append((s, a, s == 0.0, False))
        s = max(s, b)
    if strand.L - s > 1e-3:
        out.append((s, strand.L, s == 0.0, True))
    return out


def dot(x, y, r):
    return f"M{fmt(x - r)},{fmt(y)} a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0 a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0 Z"


def render(strands, fills=()):
    """
    Groups the drawing into stroke paths keyed by (colour, width, cap) and fill paths keyed by colour. Cut ends are flat
    like the edge of the gap; free ends keep a round cap, drawn as a dot because a path has a single cap style.
    把图形分组为按 (颜色, 线宽, 线头) 归类的描边路径和按颜色归类的填充路径。切口是平头，与缝的边缘一致；
    自由端保持圆头，因为一条路径只能有一种线头样式，所以用圆点补上。
    """
    strokes, extra = {}, {}
    for st in strands:
        for sa, sb, free_start, free_end in pieces(st):
            if st.closed and not st.cuts:
                strokes.setdefault((st.color, st.width, "round"), []).append(st.emit(0, st.L) + " Z")
                continue
            whole = free_start and free_end
            strokes.setdefault((st.color, st.width, "round" if whole else "butt"), []).append(st.emit(sa, sb))
            if not whole:
                for free, s in ((free_start, sa), (free_end, sb)):
                    if free:
                        x, y = st.point(s)
                        extra.setdefault(st.color, []).append(dot(x, y, st.width / 2))
    filled = {}
    for color, d in fills:
        filled.setdefault(color, []).append(d)
    for color, ds in extra.items():
        filled.setdefault(color, []).extend(ds)
    return strokes, filled


def circle_intersections(c1, r1, c2, r2):
    dx, dy = c2[0] - c1[0], c2[1] - c1[1]
    d = math.hypot(dx, dy)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(r1 * r1 - a * a)
    px, py = c1[0] + a * dx / d, c1[1] + a * dy / d
    return [(px + h * dy / d, py - h * dx / d), (px - h * dy / d, py + h * dx / d)]


def ang(c, p):
    return math.degrees(math.atan2(p[1] - c[1], p[0] - c[0]))
