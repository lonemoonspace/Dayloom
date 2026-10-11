"""
Generates every icon drawable of the app: launcher, module and button icons, status icons and the notification icon.
Run from the repository root: `python3 scripts/icons/gen_icons.py [preview.html]`. Edit the shapes here, not the XML.
生成 App 的全部图标矢量图：启动器、模块与按钮图标、状态图标和通知图标。
在仓库根目录运行：`python3 scripts/icons/gen_icons.py [preview.html]`。要改形状就改这里，不要手改 XML。

Module and button icons use two colour keys: black is the ink and pure green the accent; `DuotoneIcon` maps them to theme
colours. Status and notification icons are single-colour and tinted as usual.
模块与按钮图标用两种颜色键：黑色是墨色，纯绿是强调色，由 `DuotoneIcon` 换成主题颜色。状态图标和通知图标是单色的，照常着色。
"""
import math
import os
import sys

from geometry import Arc, Line, Strand, ang, circle, circle_intersections, cut, dot, fmt, poly, render, roundrect

INK, ACC = "ink", "acc"
W = 1.75
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "drawable")


def strand(prims, color, closed=False, name="", width=W):
    return Strand(prims, color, width, closed, name)


# ---------------- module and button icons / 模块与按钮图标 ----------------

def weather():
    ring = strand(circle(11, 7.5, 3.5), ACC, True, "ring")
    c1, c2, c3 = (16.5, 15), (11.5, 13), (7, 15.5)
    p12 = min(circle_intersections(c1, 4, c2, 4.5), key=lambda p: p[1])
    p23 = min(circle_intersections(c2, 4.5, c3, 3.5), key=lambda p: p[1])
    a12 = ang(c1, p12)
    cloud = strand([
        Line(7, 19, 16.5, 19),
        Arc(*c1, 4, 90, a12 if a12 < 90 else a12 - 360),
        Arc(*c2, 4.5, ang(c2, p12), ang(c2, p23)),
        Arc(*c3, 3.5, ang(c3, p23), -270),
    ], INK, True, "cloud")
    cut(cloud, ring, (14.02, 9.27), 2.35)
    cut(ring, cloud, (8.35, 9.79), 2.35)
    return [ring, cloud], []


def transit():
    body = strand(roundrect(6, 3, 18, 18, 3), INK, True, "body")
    legs = [strand(poly((9, 21), (10.5, 18)), INK), strand(poly((15, 21), (13.5, 18)), INK)]
    band = strand(poly((2, 10.5), (22, 10.5)), ACC, name="band")
    cut(band, body, (6, 10.5), 3)
    cut(body, band, (18, 10.5), 3)
    return [body, *legs, band], [(INK, dot(9.5, 15, 1.1)), (INK, dot(14.5, 15, 1.1))]


def traffic():
    left = strand(poly((4.5, 21), (9, 3)), INK, name="left edge")
    right = strand(poly((19.5, 21), (15, 3)), INK, name="right edge")
    dashes = [strand(poly((12, a), (12, b)), INK, name="dash") for a, b in ((4, 6.5), (9.5, 16.5), (19.5, 21))]
    thread = strand(poly((1.5, 13), (22.5, 13)), ACC, name="thread")
    cut(thread, left, (6.5, 13), 3)
    cut(dashes[1], thread, (12, 13), 2)
    cut(thread, right, (17.5, 13), 3)
    return [left, right, *dashes, thread], []


def reminders():
    top = strand(poly((6, 4), (18, 4), (12, 10.4), closed=True), INK, True)
    bottom = strand(poly((6, 20), (18, 20), (12, 13.6), closed=True), ACC, True)
    return [top, bottom], []


def calendar():
    frame = strand(roundrect(5, 5, 19, 21, 3), INK, True, "frame")
    hooks = [strand(poly((x, 2.5), (x, 7.5)), INK, name="hook") for x in (8.5, 15.5)]
    ribbon = strand(poly((1.5, 11), (22.5, 11)), ACC, name="ribbon")
    for hook, x in zip(hooks, (8.5, 15.5)):
        cut(frame, hook, (x, 5), 2.2)
    cut(ribbon, frame, (5, 11), 2.5)
    cut(frame, ribbon, (19, 11), 2.5)
    today = "M13.8,14.5 h1.4 a0.8,0.8 0 0,1 0.8,0.8 v1.4 a0.8,0.8 0 0,1 -0.8,0.8 h-1.4 a0.8,0.8 0 0,1 -0.8,-0.8 v-1.4 a0.8,0.8 0 0,1 0.8,-0.8 Z"
    return [frame, *hooks, ribbon], [(INK, today)]


def football():
    pitch = strand(roundrect(3, 3.5, 21, 20.5, 2.5), INK, True, "pitch")
    halfway = strand(poly((12, 3.5), (12, 20.5)), ACC, name="halfway line")
    centre = strand(circle(12, 12, 4), INK, True, "centre circle")
    cut(halfway, centre, (12, 8), 2.4)
    cut(centre, halfway, (12, 16), 1.3)
    return [pitch, halfway, centre], []


def news():
    page = strand(roundrect(4, 3, 20, 21, 3), INK, True, "page")
    lines = [strand(poly((7, y), (17, y)), INK, name="text line") for y in (9, 15)]
    bookmark = strand(poly((14, 1), (14, 17)), ACC, name="bookmark")
    cut(page, bookmark, (14, 3), 1.9)
    cut(bookmark, lines[0], (14, 9), 1.5)
    cut(lines[1], bookmark, (14, 15), 1.75)
    return [page, *lines, bookmark], []


def home():
    left = strand(poly((3.5, 12), (15, 2.23)), INK, name="left board")
    right = strand(poly((20.5, 12), (9, 2.23)), ACC, name="right board")
    body = strand([Line(6, 10.3, 6, 19.5), Arc(7.5, 19.5, 1.5, 180, 90), Line(7.5, 21, 16.5, 21),
                   Arc(16.5, 19.5, 1.5, 90, 0), Line(18, 19.5, 18, 10.3)], INK, name="walls")
    door = strand([Line(10.5, 21, 10.5, 17.5), Arc(12, 17.5, 1.5, 180, 360), Line(13.5, 17.5, 13.5, 21)], INK, name="door")
    cut(left, right, (12, 4.775), 2.4)
    return [left, right, body, door], []


def settings():
    rails = [strand(poly((2.5, y), (21.5, y)), INK, name="rail") for y in (8, 16)]
    knobs = [strand(circle(10, 8, 3), ACC, True, "knob"), strand(circle(14, 16, 3), ACC, True, "knob")]
    cut(rails[0], knobs[0], (7, 8), 2.46)
    cut(knobs[0], rails[0], (13, 8), 1.5)
    cut(knobs[1], rails[1], (11, 16), 1.5)
    cut(rails[1], knobs[1], (17, 16), 2.46)
    return [*rails, *knobs], []


def refresh():
    return [
        strand([Arc(12, 12, 7.5, 192.5, 347.5)], INK),
        strand(poly((16.78, 8.43), (19.32, 10.38), (20.8, 7.54)), INK),
        strand([Arc(12, 12, 7.5, 12.5, 167.5)], ACC),
        strand(poly((7.22, 15.57), (4.68, 13.62), (3.2, 16.46)), ACC),
    ], []


def edit():
    pencil = strand(poly((14.5, 5.5), (18.5, 9.5), (8.5, 19.5), (4, 20), (4.5, 15.5), closed=True), INK, True, "pencil")
    band = strand(poly((9.5, 6.5), (18, 15)), ACC, name="band")
    cut(pencil, band, (11.5, 8.5), 2.3)
    cut(band, pencil, (15.5, 12.5), 2.3)
    return [pencil, band], []


def wave(y0, x0, halves, half, amp):
    """
    A wave of circle arcs from a crest at x0 over `halves` half-periods, ending on a crest or trough.
    由圆弧组成的浪：从 x0 处的浪尖开始，经过 `halves` 个半周期，结束在浪尖或浪谷。
    """
    r = ((half / 2) ** 2 + amp ** 2) / (2 * amp)
    da = math.degrees(math.atan2(r - amp, half / 2))
    prims = [Arc(x0, y0 - amp + r, r, -90, -da)]
    x = x0 + half
    for i in range(halves - 1):
        prims.append(Arc(x, y0 + amp - r, r, 180 - da, da) if i % 2 == 0 else Arc(x, y0 - amp + r, r, -180 + da, -da))
        x += half
    last_is_crest = (halves - 1) % 2 == 0
    prims.append(Arc(x, y0 - amp + r, r, -180 + da, -90) if last_is_crest else Arc(x, y0 + amp - r, r, 180 - da, 90))
    return prims


def menu():
    rows = [strand(wave(y, 4, 4, 4, 0.8), INK, name="wave") for y in (6, 12, 18)]
    thread = strand(poly((16, 2.5), (16, 21.5)), ACC, name="thread")
    cut(rows[0], thread, (16, 6.8), 2.5)
    cut(thread, rows[1], (16, 12.8), 6)
    cut(rows[2], thread, (16, 18.8), 2.5)
    return [*rows, thread], []


def done():
    ring = strand(circle(11, 12, 7.5), ACC, True, "ring")
    check = strand(poly((7, 12), (10.5, 15.5), (20.5, 5.5)), INK, name="check")
    cut(ring, check, (17.587, 8.413), 2)
    return [ring, check], []


def back():
    upper = strand(poly((13, 4.5), (4.5, 13)), INK, name="upper arm")
    lower = strand(poly((13, 19.5), (4.5, 11)), ACC, name="lower arm")
    shaft = strand(poly((19.5, 12), (9.5, 12)), INK, name="shaft")
    cut(upper, lower, (6.5, 12), 2.5)
    return [upper, lower, shaft], []


TWO_TONE = {
    "ic_weather": (weather, "Weather module: a sun ring linked with a cloud.", "天气模块：太阳环与云套在一起。"),
    "ic_transit": (transit, "Public transport module: a train front threaded by one line.", "公共交通模块：一根线穿过车头。"),
    "ic_traffic": (traffic, "Traffic module: a road with a thread woven under, over and under it.", "路况模块：一根线在道路上一挑一压一挑。"),
    "ic_reminders": (reminders, "Expiry reminders module: the two triangles of an hourglass, tip to tip.", "到期提醒模块：沙漏的两个三角尖对尖。"),
    "ic_calendar": (calendar, "Calendar module: hooks over the page edge and a bookmark ribbon through it.", "日历模块：挂钩压住上沿，书签带穿过页面。"),
    "ic_football": (football, "Football module: a pitch whose halfway line weaves with the centre circle.", "足球模块：中线和中圈一压一挑的球场。"),
    "ic_news": (news, "News module: a page with a bookmark woven through its lines.", "新闻模块：书签线和文字行一压一挑的页面。"),
    "ic_home": (home, "Home tab: crossed gable boards over a house.", "首页标签：屋顶两块山墙板交叉。"),
    "ic_settings": (settings, "Settings tab: two knobs threaded on rails.", "设置标签：两个旋钮串在滑轨上。"),
    "ic_refresh": (refresh, "Refresh: two arrows chasing each other.", "刷新：两段箭头首尾相追。"),
    "ic_edit": (edit, "Edit: a pencil with a band wrapped round it.", "编辑：一道带子缠在笔杆上。"),
    "ic_menu": (menu, "Menu and drag handle: three waves woven with one thread.", "菜单与拖动柄：三道浪和一根线织在一起。"),
    "ic_done": (done, "Done: a check mark breaking out of a ring.", "完成：对勾从圆环里冲出来。"),
    "ic_back": (back, "Back: an arrow whose two head strokes cross at the tip, like the home roof.", "返回：箭头的两笔在尖端交叉，像首页图标的屋顶。"),
}

# ---------------- status and marker icons (single colour) / 状态与标记图标（单色） ----------------


def star(cx, cy, outer, inner):
    pts = [(cx + (outer if i % 2 == 0 else inner) * math.cos(math.radians(-90 + 36 * i)),
            cy + (outer if i % 2 == 0 else inner) * math.sin(math.radians(-90 + 36 * i))) for i in range(10)]
    return "M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in pts) + " Z"


DISC = "M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z"
STAR = star(12, 12.8, 8.2, 3.6)
STATUS = {
    "ic_status_ok": ("OK: check mark on a tinted disc.", "正常：淡色圆底上的对勾。",
                     [("soft", DISC)], [("M8,12.4 L10.7,15.1 L16,9.6", "round")]),
    "ic_status_late": ("Late: clock hands on a tinted disc.", "晚点：淡色圆底上的时钟指针。",
                       [("soft", DISC)], [("M12,7 V12 L15.2,14", "round")]),
    "ic_status_warn": ("Warning: exclamation mark on a tinted triangle.", "警示：淡色三角上的感叹号。",
                       [("soft", "M10.3,4.5 a2,2 0 0,1 3.4,0 l7.4,12.8 a2,2 0 0,1 -1.7,3 H4.6 a2,2 0 0,1 -1.7,-3 Z"),
                        ("ink", dot(12, 16.9, 1.2))], [("M12,9.4 V13.4", "round")]),
    "ic_status_cancel": ("Cancelled: cross on a tinted disc.", "已取消：淡色圆底上的叉。",
                         [("soft", DISC)], [("M9.2,9.2 L14.8,14.8 M14.8,9.2 L9.2,14.8", "round")]),
    "ic_status_umbrella": ("Umbrella: tinted canopy and a hooked handle.", "带伞：淡色伞面与弯钩伞柄。",
                           [("soft", "M3.5,12 A8.5,8.5 0 0,1 20.5,12 Z")], [("M12,12 V18 A2,2 0 0,1 8,18", "round")]),
    "ic_status_unknown": ("No real-time data: question mark on a faint disc.", "没有实时数据：浅色圆底上的问号。",
                          [("faint", DISC), ("ink", dot(12, 16.6, 1.2))],
                          [("M9.8,9.6 a2.3,2.3 0 1,1 3.2,2.1 c-0.6,0.3 -1,0.8 -1,1.5 v0.3", "round")]),
    # The fill and an outline of the same path round off the points. / 同一路径既填充又描边，星角就是圆的。
    "ic_star": ("Starred: a solid star with rounded points.", "已收藏：圆角的实心星。", [("ink", STAR)], [(STAR, "round")]),
}
STATUS_PALETTE = {"ink": "#FF000000", "soft": "#33000000", "faint": "#24000000"}

# ---------------- launcher / 启动器 ----------------

SUN, THREAD, WAVE = "sun", "thread", "wave"


def launcher():
    """
    Sunrise over the sea on the 108dp adaptive-icon canvas: a sun sliced near its base, three swells and three threads.
    108dp 自适应图标画布上的海上日出：底部切片的太阳、三道涌浪和三缕细线。
    """
    dy = 4.5
    cols = [strand(poly((x, 49.05 + dy), (x, (67 if x == 54 else 66.55) + dy)), THREAD, name="thread", width=1.3)
            for x in (45.15, 54, 62.85)]
    r, h = 7.693, 6.293
    rows = []
    for y0 in (52.6 + dy, 59.1 + dy, 65.6 + dy):
        rows.append(strand([
            Arc(36.3, y0 - h, r, ang((36.3, y0 - h), (39.25, y0 + 0.812)), 54.88),
            Arc(45.15, y0 + h, r, -125.12, -54.88), Arc(54, y0 - h, r, 125.12, 54.88),
            Arc(62.85, y0 + h, r, -125.12, -54.88), Arc(71.7, y0 - h, r, 125.12, ang((71.7, y0 - h), (68.75, y0 + 0.812))),
        ], WAVE, name="swell", width=2))
    c0, c1, c2 = cols
    r0, r1, r2 = rows
    cut(r0, c0, (45.15, 51.2 + dy), 2.4)
    cut(r0, c2, (62.85, 51.2 + dy), 2.4)
    cut(r1, c1, (54, 60.5 + dy), 2.6)
    cut(r2, c0, (45.15, 64.2 + dy), 2.6)
    cut(r2, c2, (62.85, 64.2 + dy), 2.6)
    cut(c1, r0, (54, 54.0 + dy), 8)
    cut(c0, r1, (45.15, 57.7 + dy), 8)
    cut(c2, r1, (62.85, 57.7 + dy), 8)
    cut(c1, r2, (54, 67.0 + dy), 8)
    sun = [
        "M39.814,44.1 A16,16 0 0,1 68.186,44.1 Z",
        "M39.25,45.3 L68.75,45.3 A16,16 0 0,1 69.492,47.5 L38.508,47.5 A16,16 0 0,1 39.25,45.3 Z",
        "M38.152,49.3 L69.848,49.3 A16,16 0 0,1 70,51.5 L38,51.5 A16,16 0 0,1 38.152,49.3 Z",
    ]
    return [*cols, *rows], [(SUN, d) for d in sun]


NOTIFICATION = (
    [("white", "M6.418,9.3 A6,6 0 0,1 17.582,9.3 Z M6.121,10.3 L17.879,10.3 A6,6 0 0,1 18,11.5 L6,11.5 A6,6 0 0,1 6.121,10.3 Z")],
    [("M6.75,15 a2.314,2.314 0 0,1 3.5,0 a2.314,2.314 0 0,0 3.5,0 a2.314,2.314 0 0,1 3.5,0 "
      "M6.75,19 a2.314,2.314 0 0,1 3.5,0 a2.314,2.314 0 0,0 3.5,0 a2.314,2.314 0 0,1 3.5,0", "round")],
)

# ---------------- output / 输出 ----------------


def chunks(ds, limit=760):
    """Splits path data so no single path passes lint's long-path warning. / 拆分路径数据，避免单条路径触发 lint 的长路径警告。"""
    out, cur = [], []
    for d in ds:
        if cur and len(" ".join(cur + [d])) > limit:
            out.append(cur)
            cur = []
        cur.append(d)
    return out + ([cur] if cur else [])


def vector_xml(strokes, fills, size, viewport, palette, en, zh):
    head = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {en} -->", f"<!-- {zh} -->",
            "<!-- Generated by scripts/icons/gen_icons.py; edit the script, not this file. -->",
            "<!-- 由 scripts/icons/gen_icons.py 生成；请改脚本，不要改这个文件。 -->",
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            f'    android:width="{size}dp"', f'    android:height="{size}dp"',
            f'    android:viewportWidth="{viewport}"', f'    android:viewportHeight="{viewport}">']
    body = []
    for (color, width, cap), ds in strokes.items():
        for part in chunks(ds):
            body += ["    <path", f'        android:pathData="{" ".join(part)}"', f'        android:strokeColor="{palette[color]}"',
                     f'        android:strokeWidth="{fmt(width)}"', f'        android:strokeLineCap="{cap}"',
                     '        android:strokeLineJoin="round" />']
    for color, ds in fills.items():
        for part in chunks(ds):
            body += ["    <path", f'        android:pathData="{" ".join(part)}"', f'        android:fillColor="{palette[color]}" />']
    return "\n".join(head + body + ["</vector>"]) + "\n"


def svg(strokes, fills, palette, viewbox, px):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="{viewbox}">']
    for (color, width, cap), ds in strokes.items():
        parts.append(f'<path d="{" ".join(ds)}" fill="none" stroke="{palette[color]}" stroke-width="{fmt(width)}" '
                     f'stroke-linecap="{cap}" stroke-linejoin="round"/>')
    for color, ds in fills.items():
        parts.append(f'<path d="{" ".join(ds)}" fill="{palette[color]}"/>')
    return "".join(parts) + "</svg>"


def write(name, text):
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8") as f:
        f.write(text)


def main(preview=None):
    two_tone = {INK: "#FF000000", ACC: "#FF00FF00"}
    note_en = "Black is the ink and green the accent; draw it with DuotoneIcon. Crossings are real gaps, so a single-colour tint also works."
    note_zh = "黑色是墨色、绿色是强调色，用 DuotoneIcon 绘制；交叉处是真实的缝，单色着色也能正常显示。"
    cells = []
    for name, (build, en, zh) in TWO_TONE.items():
        strokes, fills = render(*build())
        write(name, vector_xml(strokes, fills, 24, 24, two_tone, f"{en} {note_en}", f"{zh}{note_zh}"))
        cells.append((name, svg(strokes, fills, {INK: "#17212B", ACC: "#0F8A7E"}, "0 0 24 24", 96),
                      svg(strokes, fills, {INK: "#E3E8EE", ACC: "#7FD1C7"}, "0 0 24 24", 24)))

    for name, (en, zh, fill_list, stroke_list) in STATUS.items():
        strokes = {}
        for d, cap in stroke_list:
            strokes.setdefault(("ink", 2.2, cap), []).append(d)
        fills = {}
        for color, d in fill_list:
            fills.setdefault(color, []).append(d)
        write(name, vector_xml(strokes, fills, 24, 24, STATUS_PALETTE, f"{en} Coloured by the Icon tint; tinted shapes keep their own alpha.",
                               f"{zh}颜色由 Icon 的 tint 决定，淡色部分保持各自的透明度。"))

    fill_list, stroke_list = NOTIFICATION
    write("ic_notification", vector_xml({("white", 1.8, cap): [d] for d, cap in stroke_list}, {c: [d] for c, d in fill_list},
                                        24, 24, {"white": "#FFFFFFFF"},
                                        "Status-bar icon: a sliced sun over two waves, white on transparent; the system uses only the alpha channel.",
                                        "状态栏小图标：两道浪上的切片太阳，透明底上的白色；系统只取 alpha 通道着色。"))

    strokes, fills = render(*launcher())
    write("ic_launcher_foreground", vector_xml(
        strokes, fills, 108, 108, {SUN: "#FFF4B740", THREAD: "#FF7FD1C7", WAVE: "#FFF3EFE6"},
        "Sunrise over the sea: a sliced sun rising from three swells, three threads woven through them. Inside the 66dp safe zone of the 108dp canvas.",
        "海上日出：切片的太阳从三道浪里升起，三缕细线和浪一压一挑。绘制在 108dp 画布中 66dp 的安全区内。"))
    write("ic_launcher_monochrome", vector_xml(
        strokes, fills, 108, 108, {SUN: "#FFFFFFFF", THREAD: "#FFFFFFFF", WAVE: "#FFFFFFFF"},
        "Themed-icon layer: the foreground shapes in one colour; the gaps are real cuts, so the system tint keeps them.",
        "主题图标层：与前景相同的形状，单色；缝是真实切开的，系统着色后依然保留。"))

    if preview:
        app = svg(strokes, fills, {SUN: "#F4B740", THREAD: "#7FD1C7", WAVE: "#F3EFE6"}, "18 18 72 72", 192)
        grid = "".join(f'<div class="c">{big}<span class="d">{small}</span><b>{n}</b></div>' for n, big, small in cells)
        with open(preview, "w", encoding="utf-8") as f:
            f.write("<!doctype html><meta charset='utf-8'><style>body{margin:0;padding:24px;background:#F3F4F0;font:13px sans-serif}"
                    ".a{width:192px;height:192px;border-radius:44px;overflow:hidden;background:#1E4E5F;margin-bottom:24px}"
                    ".g{display:grid;grid-template-columns:repeat(7,140px);gap:12px}.c{background:#fff;border-radius:14px;padding:12px;"
                    "display:flex;flex-direction:column;align-items:center;gap:8px}.d{background:#141A21;padding:4px;border-radius:6px;line-height:0}</style>"
                    f"<div class='a'>{app}</div><div class='g'>{grid}</div>")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
